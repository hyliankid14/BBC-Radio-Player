#include "screen_now_playing.h"
#include "ui_manager.h"
#include "playback_state.h"
#include "cJSON.h"
#include "esp_crt_bundle.h"
#include "esp_heap_caps.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

static lv_obj_t   *s_lbl_title     = NULL;
static lv_obj_t   *s_lbl_subtitle  = NULL;
static lv_obj_t   *s_btn_playpause = NULL;
static lv_obj_t   *s_btn_prev      = NULL;
static lv_obj_t   *s_btn_next      = NULL;
static lv_obj_t   *s_bar_progress  = NULL;
static lv_obj_t   *s_lbl_elapsed   = NULL;
static lv_obj_t   *s_lbl_remaining = NULL;
static lv_timer_t *s_timer         = NULL;
static TaskHandle_t s_rms_task     = NULL;
static uint32_t     s_rms_gen      = 0;
static char         s_rms_service_id[48] = {0};
static char         s_rms_text[128] = {0};

/* Station navigation dispatch — runs off the LVGL task so the UI stays responsive. */
#define NAV_DIR_PREV  (-1)
#define NAV_DIR_NEXT  ( 1)
#define RMS_TEXT_MAX  128

static void station_nav_task(void *arg);

static void rms_clear_async(void *arg)
{
    LV_UNUSED(arg);
    s_rms_text[0] = '\0';
    screen_now_playing_refresh(NULL);
}

static void rms_apply_text_async(void *arg)
{
    char *text = (char *)arg;
    if (text) {
        strlcpy(s_rms_text, text, sizeof(s_rms_text));
        free(text);
    } else {
        s_rms_text[0] = '\0';
    }
    screen_now_playing_refresh(NULL);
}

static void rms_stop_tracking(void)
{
    s_rms_gen++;
    s_rms_task = NULL;
    s_rms_service_id[0] = '\0';
    s_rms_text[0] = '\0';
}

static char *rms_fetch_json(const char *service_id)
{
    char url[160];
    snprintf(url, sizeof(url), "https://rms.api.bbc.co.uk/v2/services/%s/segments/latest", service_id);

    size_t cap = 8192;
    char *buf = heap_caps_malloc(cap, MALLOC_CAP_SPIRAM);
    if (!buf) {
        buf = malloc(cap);
    }
    if (!buf) {
        return NULL;
    }

    esp_http_client_config_t cfg = {
        .url = url,
        .timeout_ms = 2500,
        .buffer_size = 1024,
        .buffer_size_tx = 512,
        .crt_bundle_attach = esp_crt_bundle_attach,
        .keep_alive_enable = false,
    };
    esp_http_client_handle_t client = esp_http_client_init(&cfg);
    if (!client) {
        free(buf);
        return NULL;
    }

    if (esp_http_client_open(client, 0) != ESP_OK) {
        esp_http_client_cleanup(client);
        free(buf);
        return NULL;
    }

    esp_http_client_fetch_headers(client);
    size_t len = 0;
    int read_len = 0;
    while ((read_len = esp_http_client_read(client, buf + len, (int)(cap - len - 1))) > 0) {
        len += (size_t)read_len;
        if (len >= cap - 1) {
            break;
        }
    }
    buf[len] = '\0';

    int status = esp_http_client_get_status_code(client);
    esp_http_client_close(client);
    esp_http_client_cleanup(client);
    if (status != 200 || len == 0) {
        free(buf);
        return NULL;
    }

    return buf;
}

static bool rms_parse_text(const char *json, char *out, size_t out_len)
{
    cJSON *root = cJSON_Parse(json);
    if (!root) {
        return false;
    }

    bool ok = false;
    cJSON *data = cJSON_GetObjectItemCaseSensitive(root, "data");
    cJSON *chosen = NULL;
    if (cJSON_IsArray(data)) {
        cJSON *item = NULL;
        cJSON_ArrayForEach(item, data) {
            cJSON *offset = cJSON_GetObjectItemCaseSensitive(item, "offset");
            cJSON *now_playing = offset ? cJSON_GetObjectItemCaseSensitive(offset, "now_playing") : NULL;
            if (cJSON_IsBool(now_playing) && cJSON_IsTrue(now_playing)) {
                chosen = item;
                break;
            }
            if (!chosen) {
                chosen = item;
            }
        }
    }

    if (chosen) {
        cJSON *titles = cJSON_GetObjectItemCaseSensitive(chosen, "titles");
        cJSON *primary = titles ? cJSON_GetObjectItemCaseSensitive(titles, "primary") : NULL;
        cJSON *secondary = titles ? cJSON_GetObjectItemCaseSensitive(titles, "secondary") : NULL;
        if (cJSON_IsString(primary) && primary->valuestring[0] != '\0') {
            if (cJSON_IsString(secondary) && secondary->valuestring[0] != '\0') {
                snprintf(out, out_len, "%s - %s", primary->valuestring, secondary->valuestring);
            } else {
                strlcpy(out, primary->valuestring, out_len);
            }
            ok = true;
        }
    }

    cJSON_Delete(root);
    return ok;
}

static void rms_task(void *arg)
{
    uint32_t my_gen = (uint32_t)(uintptr_t)arg;
    char last_text[RMS_TEXT_MAX] = {0};

    while (my_gen == s_rms_gen) {
        playback_state_t st = playback_get_state();
        if (st.type != PLAYBACK_STATION || !st.station || !st.is_live) {
            break;
        }

        char *json = rms_fetch_json(st.station->service_id);
        if (my_gen != s_rms_gen) {
            free(json);
            break;
        }

        if (json) {
            char text[RMS_TEXT_MAX] = {0};
            if (rms_parse_text(json, text, sizeof(text)) && strcmp(text, last_text) != 0) {
                char *copy = strdup(text);
                if (copy) {
                    strlcpy(last_text, text, sizeof(last_text));
                    lv_async_call(rms_apply_text_async, copy);
                }
            }
            free(json);
        }

        for (int i = 0; i < 100 && my_gen == s_rms_gen; i++) {
            vTaskDelay(pdMS_TO_TICKS(100));
        }
    }

    if (my_gen == s_rms_gen) {
        s_rms_task = NULL;
    }
    vTaskDelete(NULL);
}

static void rms_ensure_tracking(const station_t *station)
{
    if (!station || !station->service_id || station->service_id[0] == '\0') {
        rms_stop_tracking();
        return;
    }

    if (strcmp(s_rms_service_id, station->service_id) == 0 && s_rms_task != NULL) {
        return;
    }

    s_rms_gen++;
    s_rms_task = NULL;
    strlcpy(s_rms_service_id, station->service_id, sizeof(s_rms_service_id));
    lv_async_call(rms_clear_async, NULL);

    if (xTaskCreate(rms_task, "rms_meta", 6144, (void *)(uintptr_t)s_rms_gen, 1, &s_rms_task) != pdPASS) {
        s_rms_task = NULL;
        ESP_LOGW("now_playing", "Failed to start RMS metadata task");
    }
}

static void dispatch_station_nav(int dir)
{
    BaseType_t rc = xTaskCreate(station_nav_task,
                                (dir == NAV_DIR_NEXT) ? "nav_next" : "nav_prev",
                                4096,
                                (void *)(intptr_t)dir,
                                5,
                                NULL);
    if (rc != pdPASS) {
        if (dir == NAV_DIR_NEXT) {
            playback_next_station();
        } else {
            playback_prev_station();
        }
        lv_async_call(screen_now_playing_refresh, NULL);
    }
}

static void station_nav_task(void *arg)
{
    int dir = (int)(intptr_t)arg;
    if (dir == NAV_DIR_NEXT) {
        playback_next_station();
    } else {
        playback_prev_station();
    }
    lv_async_call(screen_now_playing_refresh, NULL);
    vTaskDelete(NULL);
}

static void on_playpause_clicked(lv_event_t *e)
{
    playback_state_t st = playback_get_state();
    if (st.type == PLAYBACK_STATION && st.is_playing) {
        playback_stop();
    } else {
        playback_toggle();
    }
    lv_async_call(screen_now_playing_refresh, NULL);
}

static void on_prev_clicked(lv_event_t *e)
{
    LV_UNUSED(e);
    playback_state_t st = playback_get_state();
    if (st.type == PLAYBACK_STATION) {
        dispatch_station_nav(NAV_DIR_PREV);
    } else if (st.type == PLAYBACK_EPISODE) {
        playback_seek_relative(-10);
        lv_async_call(screen_now_playing_refresh, NULL);
    }
}

static void on_next_clicked(lv_event_t *e)
{
    LV_UNUSED(e);
    playback_state_t st = playback_get_state();
    if (st.type == PLAYBACK_STATION) {
        dispatch_station_nav(NAV_DIR_NEXT);
    } else if (st.type == PLAYBACK_EPISODE) {
        playback_seek_relative(30);
        lv_async_call(screen_now_playing_refresh, NULL);
    }
}

static void on_screen_delete(lv_event_t *e)
{
    LV_UNUSED(e);
    rms_stop_tracking();
    s_lbl_title     = NULL;
    s_lbl_subtitle  = NULL;
    s_btn_playpause = NULL;
    s_btn_prev      = NULL;
    s_btn_next      = NULL;
    s_bar_progress  = NULL;
    s_lbl_elapsed   = NULL;
    s_lbl_remaining = NULL;
    if (s_timer) { lv_timer_del(s_timer); s_timer = NULL; }
}

static void on_progress_timer(lv_timer_t *t)
{
    (void)t;
    playback_state_t st = playback_get_state();
    if (st.type == PLAYBACK_EPISODE) {
        lv_async_call(screen_now_playing_refresh, NULL);
    }
}

static void time_fmt(char *buf, size_t len, int32_t secs)
{
    if (secs < 0) secs = 0;
    int m = (int)(secs / 60);
    int s = (int)(secs % 60);
    if (m >= 60) {
        snprintf(buf, len, "%d:%02d:%02d", m / 60, m % 60, s);
    } else {
        snprintf(buf, len, "%d:%02d", m, s);
    }
}

void screen_now_playing_refresh(void *arg)
{
    if (!s_lbl_title) return;

    playback_state_t st = playback_get_state();

    if (st.type == PLAYBACK_STATION && st.station) {
        rms_ensure_tracking(st.station);
        lv_label_set_text(s_lbl_title,    st.station->title);
        lv_label_set_text(s_lbl_subtitle, s_rms_text[0] ? s_rms_text : "BBC Radio Live");
        lv_obj_add_flag(s_bar_progress,  LV_OBJ_FLAG_HIDDEN);
        lv_obj_add_flag(s_lbl_elapsed,   LV_OBJ_FLAG_HIDDEN);
        lv_obj_add_flag(s_lbl_remaining, LV_OBJ_FLAG_HIDDEN);
    } else if (st.type == PLAYBACK_EPISODE) {
        rms_stop_tracking();
        lv_label_set_text(s_lbl_title,    st.episode_title);
        lv_label_set_text(s_lbl_subtitle, st.podcast_title);
        lv_obj_clear_flag(s_bar_progress,  LV_OBJ_FLAG_HIDDEN);
        lv_obj_clear_flag(s_lbl_elapsed,   LV_OBJ_FLAG_HIDDEN);
        lv_obj_clear_flag(s_lbl_remaining, LV_OBJ_FLAG_HIDDEN);
        int32_t pos = playback_get_position_secs();
        int32_t dur = st.episode_duration_secs;
        lv_bar_set_range(s_bar_progress, 0, (dur > 0) ? dur : 100);
        lv_bar_set_value(s_bar_progress, pos, LV_ANIM_OFF);
        char buf[16];
        time_fmt(buf, sizeof(buf), pos);
        lv_label_set_text(s_lbl_elapsed, buf);
        if (dur > 0) {
            int32_t rem = dur - pos;
            if (rem < 0) rem = 0;
            char rbuf[18];
            time_fmt(rbuf, sizeof(rbuf), rem);
            char disp[20];
            snprintf(disp, sizeof(disp), "-%s", rbuf);
            lv_label_set_text(s_lbl_remaining, disp);
        } else {
            lv_label_set_text(s_lbl_remaining, "");
        }
    } else {
        rms_stop_tracking();
        lv_label_set_text(s_lbl_title,    "Nothing playing");
        lv_label_set_text(s_lbl_subtitle, "");
        lv_obj_add_flag(s_bar_progress,  LV_OBJ_FLAG_HIDDEN);
        lv_obj_add_flag(s_lbl_elapsed,   LV_OBJ_FLAG_HIDDEN);
        lv_obj_add_flag(s_lbl_remaining, LV_OBJ_FLAG_HIDDEN);
    }

    lv_obj_t *btn_lbl = lv_obj_get_child(s_btn_playpause, 0);
    if (st.type == PLAYBACK_STATION && st.is_playing) {
        lv_label_set_text(btn_lbl, LV_SYMBOL_STOP);
    } else {
        lv_label_set_text(btn_lbl, st.is_playing ? LV_SYMBOL_PAUSE : LV_SYMBOL_PLAY);
    }
}

lv_obj_t *screen_now_playing_create(void)
{
    lv_obj_t *scr = lv_obj_create(NULL);
    lv_obj_set_style_bg_color(scr, UI_COLOR_DARK_BG, LV_PART_MAIN);
    lv_obj_set_style_pad_all(scr, 0, LV_PART_MAIN);
    lv_obj_clear_flag(scr, LV_OBJ_FLAG_SCROLLABLE);

    lv_obj_add_event_cb(scr, on_screen_delete, LV_EVENT_DELETE, NULL);

    ui_create_header(scr, "Now Playing", true);

    /* Station / episode title */
    s_lbl_title = lv_label_create(scr);
    lv_label_set_long_mode(s_lbl_title, LV_LABEL_LONG_DOT);
    lv_obj_set_width(s_lbl_title, 200);
    lv_obj_set_style_text_font(s_lbl_title, &lv_font_montserrat_14, LV_PART_MAIN);
    lv_obj_set_style_text_color(s_lbl_title, UI_COLOR_TEXT, LV_PART_MAIN);
    lv_obj_align(s_lbl_title, LV_ALIGN_CENTER, 0, -46);

    /* Podcast / subtitle */
    s_lbl_subtitle = lv_label_create(scr);
    lv_label_set_long_mode(s_lbl_subtitle, LV_LABEL_LONG_DOT);
    lv_obj_set_width(s_lbl_subtitle, 200);
    lv_obj_set_style_text_font(s_lbl_subtitle, &lv_font_montserrat_14, LV_PART_MAIN);
    lv_obj_set_style_text_color(s_lbl_subtitle, UI_COLOR_TEXT, LV_PART_MAIN);
    lv_obj_align(s_lbl_subtitle, LV_ALIGN_CENTER, 0, -24);

    /* Progress bar (episode mode only, hidden for live radio) */
    s_bar_progress = lv_bar_create(scr);
    lv_obj_set_size(s_bar_progress, 190, 6);
    lv_obj_align(s_bar_progress, LV_ALIGN_CENTER, 0, -8);
    lv_obj_set_style_bg_color(s_bar_progress, lv_color_make(0x44, 0x44, 0x44), LV_PART_MAIN);
    lv_obj_set_style_bg_color(s_bar_progress, UI_COLOR_BBC_RED, LV_PART_INDICATOR);
    lv_obj_set_style_border_width(s_bar_progress, 0, LV_PART_MAIN);
    lv_obj_set_style_radius(s_bar_progress, 3, LV_PART_MAIN);
    lv_obj_set_style_radius(s_bar_progress, 3, LV_PART_INDICATOR);
    lv_bar_set_range(s_bar_progress, 0, 100);
    lv_bar_set_value(s_bar_progress, 0, LV_ANIM_OFF);
    lv_obj_add_flag(s_bar_progress, LV_OBJ_FLAG_HIDDEN);

    /* Elapsed time label (left of bar) */
    s_lbl_elapsed = lv_label_create(scr);
    lv_label_set_text(s_lbl_elapsed, "0:00");
    lv_obj_set_style_text_color(s_lbl_elapsed, UI_COLOR_SUBTEXT, LV_PART_MAIN);
    lv_obj_set_style_text_font(s_lbl_elapsed, &lv_font_montserrat_14, LV_PART_MAIN);
    lv_obj_align(s_lbl_elapsed, LV_ALIGN_CENTER, -70, 8);
    lv_obj_add_flag(s_lbl_elapsed, LV_OBJ_FLAG_HIDDEN);

    /* Remaining time label (right of bar) */
    s_lbl_remaining = lv_label_create(scr);
    lv_label_set_text(s_lbl_remaining, "");
    lv_obj_set_style_text_color(s_lbl_remaining, UI_COLOR_SUBTEXT, LV_PART_MAIN);
    lv_obj_set_style_text_font(s_lbl_remaining, &lv_font_montserrat_14, LV_PART_MAIN);
    lv_obj_align(s_lbl_remaining, LV_ALIGN_CENTER, 70, 8);
    lv_obj_add_flag(s_lbl_remaining, LV_OBJ_FLAG_HIDDEN);

    /* Previous button */
    s_btn_prev = lv_btn_create(scr);
    lv_obj_set_size(s_btn_prev, 40, 40);
    lv_obj_set_style_bg_color(s_btn_prev, UI_COLOR_CARD_BG, LV_PART_MAIN);
    lv_obj_set_style_radius(s_btn_prev, LV_RADIUS_CIRCLE, LV_PART_MAIN);
    lv_obj_align(s_btn_prev, LV_ALIGN_BOTTOM_MID, -62, -16);
    lv_obj_clear_flag(s_btn_prev, LV_OBJ_FLAG_SCROLLABLE);
    ui_mark_selectable(s_btn_prev);
    lv_obj_add_event_cb(s_btn_prev, on_prev_clicked, LV_EVENT_PRESSED, NULL);
    lv_obj_t *prev_lbl = lv_label_create(s_btn_prev);
    lv_label_set_text(prev_lbl, LV_SYMBOL_PREV);
    lv_obj_set_style_text_color(prev_lbl, lv_color_white(), LV_PART_MAIN);
    lv_obj_center(prev_lbl);

    /* Play / Pause button */
    s_btn_playpause = lv_btn_create(scr);
    lv_obj_set_size(s_btn_playpause, 52, 52);
    lv_obj_set_style_bg_color(s_btn_playpause, UI_COLOR_BBC_RED, LV_PART_MAIN);
    lv_obj_set_style_radius(s_btn_playpause, LV_RADIUS_CIRCLE, LV_PART_MAIN);
    lv_obj_align(s_btn_playpause, LV_ALIGN_BOTTOM_MID, 0, -12);
    lv_obj_clear_flag(s_btn_playpause, LV_OBJ_FLAG_SCROLLABLE);
    ui_mark_selectable(s_btn_playpause);
    lv_obj_add_event_cb(s_btn_playpause, on_playpause_clicked, LV_EVENT_CLICKED, NULL);
    lv_obj_t *btn_lbl = lv_label_create(s_btn_playpause);
    lv_label_set_text(btn_lbl, LV_SYMBOL_PAUSE);
    lv_obj_set_style_text_color(btn_lbl, lv_color_white(), LV_PART_MAIN);
    lv_obj_center(btn_lbl);

    /* Next button */
    s_btn_next = lv_btn_create(scr);
    lv_obj_set_size(s_btn_next, 40, 40);
    lv_obj_set_style_bg_color(s_btn_next, UI_COLOR_CARD_BG, LV_PART_MAIN);
    lv_obj_set_style_radius(s_btn_next, LV_RADIUS_CIRCLE, LV_PART_MAIN);
    lv_obj_align(s_btn_next, LV_ALIGN_BOTTOM_MID, 62, -16);
    lv_obj_clear_flag(s_btn_next, LV_OBJ_FLAG_SCROLLABLE);
    ui_mark_selectable(s_btn_next);
    lv_obj_add_event_cb(s_btn_next, on_next_clicked, LV_EVENT_PRESSED, NULL);
    lv_obj_t *next_lbl = lv_label_create(s_btn_next);
    lv_label_set_text(next_lbl, LV_SYMBOL_NEXT);
    lv_obj_set_style_text_color(next_lbl, lv_color_white(), LV_PART_MAIN);
    lv_obj_center(next_lbl);

    /* 1-second timer to keep progress bar live */
    s_timer = lv_timer_create(on_progress_timer, 1000, NULL);

    /* Populate from current state */
    screen_now_playing_refresh(NULL);

    return scr;
}
