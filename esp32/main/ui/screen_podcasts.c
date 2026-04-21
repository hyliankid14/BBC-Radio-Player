#include "screen_podcasts.h"
#include "screen_pod_detail.h"
#include "ui_manager.h"
#include "podcast_index.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>

static const char *TAG = "screen_podcasts";

#define PODCAST_LIST_MAX_ITEMS 96

static lv_obj_t *s_popular_list = NULL;
static lv_obj_t *s_popular_spinner = NULL;
static bool      s_fetch_in_progress = false;
static bool      s_fetch_attempted = false;
static esp_err_t s_last_fetch_err = ESP_OK;

typedef enum {
    PODCAST_LIST_POPULAR = 0,
    PODCAST_LIST_NEW,
} podcast_list_mode_t;

static podcast_list_mode_t s_list_mode = PODCAST_LIST_POPULAR;

static const char *popular_loading_message(void)
{
    const char *kind = (s_list_mode == PODCAST_LIST_NEW) ? "new" : "popular";
    if (podcast_index_is_ready()) {
        static char msg[40];
        snprintf(msg, sizeof(msg), "No %s podcasts available", kind);
        return msg;
    }
    if (s_last_fetch_err == ESP_ERR_NO_MEM) {
        return "Not enough memory for podcasts";
    }
    if (s_fetch_attempted && !s_fetch_in_progress && s_last_fetch_err != ESP_OK) {
        return "Could not load podcasts";
    }
    return (s_list_mode == PODCAST_LIST_NEW)
        ? "Loading new podcasts..."
        : "Loading popular podcasts...";
}

static void on_podcast_clicked(lv_event_t *e)
{
    podcast_t *podcast = (podcast_t *)lv_event_get_user_data(e);
    if (!podcast) {
        return;
    }
    lv_obj_t *scr = screen_pod_detail_create(podcast);
    ui_push_screen(scr, LV_SCR_LOAD_ANIM_MOVE_LEFT);
}

static void podcasts_fetch_task(void *arg)
{
    LV_UNUSED(arg);

    ESP_LOGI(TAG, "Fetching podcast index from Podcasts screen");
    esp_err_t err = podcast_index_fetch();
    s_last_fetch_err = err;
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "podcast_index_fetch failed: %s", esp_err_to_name(err));
    }

    s_fetch_in_progress = false;
    lv_async_call(screen_podcasts_refresh, NULL);
    vTaskDelete(NULL);
}

static void ensure_podcast_fetch_started(void)
{
    if (podcast_index_is_ready() || s_fetch_in_progress || s_fetch_attempted) {
        return;
    }

    s_fetch_attempted = true;
    s_fetch_in_progress = true;
    if (xTaskCreate(podcasts_fetch_task, "pod_fetch_ui", 8192, NULL, 4, NULL) != pdPASS) {
        s_fetch_in_progress = false;
        s_last_fetch_err = ESP_FAIL;
        ESP_LOGW(TAG, "Could not start podcast fetch task");
    }
}

static void add_popular_rows(void)
{
    size_t count = 0;
    podcast_t *all = podcast_index_get_all(&count);

    lv_obj_clean(s_popular_list);

    if (!all || count == 0) {
        lv_obj_t *lbl = lv_label_create(s_popular_list);
        lv_label_set_text(lbl, popular_loading_message());
        lv_obj_set_style_text_color(lbl, UI_COLOR_SUBTEXT, LV_PART_MAIN);
        lv_obj_set_style_pad_top(lbl, 10, LV_PART_MAIN);
        lv_obj_set_style_pad_left(lbl, 8, LV_PART_MAIN);
        return;
    }

    podcast_t *filtered[PODCAST_LIST_MAX_ITEMS];
    size_t filtered_count = 0;
    for (size_t i = 0; i < count && filtered_count < PODCAST_LIST_MAX_ITEMS; i++) {
        bool include = (s_list_mode == PODCAST_LIST_NEW)
            ? all[i].is_new
            : (all[i].popularity_rank > 0);
        if (include) {
            filtered[filtered_count++] = &all[i];
        }
    }

    for (size_t i = 0; i < filtered_count; i++) {
        for (size_t j = i + 1; j < filtered_count; j++) {
            int lhs = (s_list_mode == PODCAST_LIST_NEW)
                ? filtered[i]->new_rank
                : filtered[i]->popularity_rank;
            int rhs = (s_list_mode == PODCAST_LIST_NEW)
                ? filtered[j]->new_rank
                : filtered[j]->popularity_rank;
            if (lhs == 0 || (rhs != 0 && rhs < lhs)) {
                podcast_t *tmp = filtered[i];
                filtered[i] = filtered[j];
                filtered[j] = tmp;
            }
        }
    }

    for (size_t i = 0; i < filtered_count; i++) {
        lv_obj_t *btn = lv_list_add_btn(s_popular_list, LV_SYMBOL_AUDIO, filtered[i]->title);
        lv_obj_set_style_bg_color(btn, UI_COLOR_CARD_BG, LV_PART_MAIN);
        lv_obj_set_style_text_color(btn, UI_COLOR_TEXT, LV_PART_MAIN);
        lv_obj_set_style_border_width(btn, 0, LV_PART_MAIN);
        lv_obj_set_style_radius(btn, 0, LV_PART_MAIN);
        lv_obj_set_style_outline_width(btn, 0, LV_PART_MAIN);
        lv_obj_set_style_shadow_width(btn, 0, LV_PART_MAIN);
        lv_obj_t *btn_text = lv_obj_get_child(btn, 1);
        if (btn_text) {
            lv_label_set_long_mode(btn_text, LV_LABEL_LONG_SCROLL_CIRCULAR);
            lv_obj_set_style_anim_speed(btn_text, 24, LV_PART_MAIN);
        }
        ui_mark_selectable(btn);
        lv_obj_add_event_cb(btn, on_podcast_clicked, LV_EVENT_CLICKED, filtered[i]);
    }
}

void screen_podcasts_refresh(void *arg)
{
    LV_UNUSED(arg);

    if (s_popular_spinner && lv_obj_is_valid(s_popular_spinner)) {
        if (podcast_index_is_ready()) {
            lv_obj_add_flag(s_popular_spinner, LV_OBJ_FLAG_HIDDEN);
        } else {
            lv_obj_clear_flag(s_popular_spinner, LV_OBJ_FLAG_HIDDEN);
        }
    }

    if (s_popular_list && lv_obj_is_valid(s_popular_list)) {
        add_popular_rows();
        ui_refresh_navigation();
    }
}

static lv_obj_t *create_popular_screen(const char *header_title, podcast_list_mode_t mode)
{
    s_list_mode = mode;
    lv_obj_t *scr = lv_obj_create(NULL);
    lv_obj_remove_style_all(scr);
    lv_obj_set_style_bg_color(scr, UI_COLOR_DARK_BG, LV_PART_MAIN);
    lv_obj_set_style_bg_opa(scr, LV_OPA_COVER, LV_PART_MAIN);
    lv_obj_set_style_border_width(scr, 0, LV_PART_MAIN);
    lv_obj_set_style_outline_width(scr, 0, LV_PART_MAIN);
    lv_obj_set_style_shadow_width(scr, 0, LV_PART_MAIN);
    lv_obj_set_style_radius(scr, 0, LV_PART_MAIN);
    lv_obj_set_style_pad_all(scr, 0, LV_PART_MAIN);
    lv_obj_clear_flag(scr, LV_OBJ_FLAG_SCROLLABLE);

    ui_create_header(scr, header_title, true);

    s_popular_list = lv_list_create(scr);
    lv_obj_set_size(s_popular_list, 240, 240 - UI_HEADER_HEIGHT);
    lv_obj_align(s_popular_list, LV_ALIGN_TOP_LEFT, 0, UI_HEADER_HEIGHT);
    lv_obj_set_style_bg_color(s_popular_list, UI_COLOR_DARK_BG, LV_PART_MAIN);
    lv_obj_set_style_bg_opa(s_popular_list, LV_OPA_COVER, LV_PART_MAIN);
    lv_obj_set_style_border_width(s_popular_list, 0, LV_PART_MAIN);
    lv_obj_set_style_outline_width(s_popular_list, 0, LV_PART_MAIN);
    lv_obj_set_style_shadow_width(s_popular_list, 0, LV_PART_MAIN);
    lv_obj_set_style_radius(s_popular_list, 0, LV_PART_MAIN);
    lv_obj_set_style_pad_all(s_popular_list, 0, LV_PART_MAIN);
    lv_obj_set_style_pad_left(s_popular_list, 0, LV_PART_MAIN);
    lv_obj_set_style_pad_right(s_popular_list, 0, LV_PART_MAIN);
    lv_obj_set_style_pad_row(s_popular_list, 2, LV_PART_MAIN);
    lv_obj_set_scroll_dir(s_popular_list, LV_DIR_VER);
    lv_obj_set_scrollbar_mode(s_popular_list, LV_SCROLLBAR_MODE_OFF);

    s_popular_spinner = lv_spinner_create(scr, 1000, 60);
    lv_obj_set_size(s_popular_spinner, 42, 42);
    lv_obj_center(s_popular_spinner);

    screen_podcasts_refresh(NULL);
    return scr;
}

static lv_obj_t *make_tile(lv_obj_t *parent, const char *icon, const char *label,
                           lv_color_t colour, lv_event_cb_t cb)
{
    lv_obj_t *card = lv_obj_create(parent);
    lv_obj_remove_style_all(card);
    lv_obj_set_size(card, 116, 100);
    lv_obj_set_style_bg_color(card, colour, LV_PART_MAIN);
    lv_obj_set_style_bg_opa(card, LV_OPA_COVER, LV_PART_MAIN);
    lv_obj_set_style_border_width(card, 0, LV_PART_MAIN);
    lv_obj_set_style_shadow_width(card, 0, LV_PART_MAIN);
    lv_obj_set_style_outline_width(card, 0, LV_PART_MAIN);
    lv_obj_set_style_radius(card, 12, LV_PART_MAIN);
    lv_obj_set_style_pad_all(card, 8, LV_PART_MAIN);
    lv_obj_clear_flag(card, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(card, LV_OBJ_FLAG_CLICKABLE);
    ui_mark_selectable(card);
    lv_obj_add_event_cb(card, cb, LV_EVENT_CLICKED, NULL);

    lv_obj_t *ico = lv_label_create(card);
    lv_label_set_text(ico, icon);
    lv_obj_set_style_text_color(ico, lv_color_white(), LV_PART_MAIN);
    lv_obj_align(ico, LV_ALIGN_CENTER, 0, -10);

    lv_obj_t *lbl = lv_label_create(card);
    lv_label_set_text(lbl, label);
    lv_obj_set_style_text_color(lbl, lv_color_white(), LV_PART_MAIN);
    lv_obj_set_style_text_font(lbl, &lv_font_montserrat_14, LV_PART_MAIN);
    lv_obj_align(lbl, LV_ALIGN_BOTTOM_MID, 0, 0);

    return card;
}

static void on_open_popular(lv_event_t *e)
{
    LV_UNUSED(e);
    lv_obj_t *scr = create_popular_screen("Popular Podcasts", PODCAST_LIST_POPULAR);
    ui_push_screen(scr, LV_SCR_LOAD_ANIM_MOVE_LEFT);
}

static void on_open_new(lv_event_t *e)
{
    LV_UNUSED(e);
    lv_obj_t *scr = create_popular_screen("New Podcasts", PODCAST_LIST_NEW);
    ui_push_screen(scr, LV_SCR_LOAD_ANIM_MOVE_LEFT);
}

lv_obj_t *screen_podcasts_create(void)
{
    lv_obj_t *scr = lv_obj_create(NULL);
    lv_obj_remove_style_all(scr);
    lv_obj_set_style_bg_color(scr, UI_COLOR_DARK_BG, LV_PART_MAIN);
    lv_obj_set_style_bg_opa(scr, LV_OPA_COVER, LV_PART_MAIN);
    lv_obj_set_style_border_width(scr, 0, LV_PART_MAIN);
    lv_obj_set_style_outline_width(scr, 0, LV_PART_MAIN);
    lv_obj_set_style_shadow_width(scr, 0, LV_PART_MAIN);
    lv_obj_set_style_radius(scr, 0, LV_PART_MAIN);
    lv_obj_set_style_pad_all(scr, 0, LV_PART_MAIN);
    lv_obj_clear_flag(scr, LV_OBJ_FLAG_SCROLLABLE);

    ui_create_header(scr, "Podcasts", true);

    lv_obj_t *row = lv_obj_create(scr);
    lv_obj_remove_style_all(row);
    lv_obj_set_size(row, LV_PCT(100), LV_SIZE_CONTENT);
    lv_obj_set_style_bg_opa(row, LV_OPA_TRANSP, LV_PART_MAIN);
    lv_obj_set_style_border_width(row, 0, LV_PART_MAIN);
    lv_obj_set_style_shadow_width(row, 0, LV_PART_MAIN);
    lv_obj_set_style_outline_width(row, 0, LV_PART_MAIN);
    lv_obj_set_style_radius(row, 0, LV_PART_MAIN);
    lv_obj_set_style_pad_all(row, 0, LV_PART_MAIN);
    lv_obj_set_flex_flow(row, LV_FLEX_FLOW_ROW);
    lv_obj_set_flex_align(row, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER);
    lv_obj_set_style_pad_column(row, 8, LV_PART_MAIN);
    lv_obj_align(row, LV_ALIGN_CENTER, 0, 26);
    lv_obj_clear_flag(row, LV_OBJ_FLAG_SCROLLABLE);

    make_tile(row, LV_SYMBOL_LIST, "Popular", lv_color_make(0x1A, 0x73, 0xE8), on_open_popular);
    make_tile(row, LV_SYMBOL_PLUS, "New", lv_color_make(0x00, 0x77, 0x49), on_open_new);

    s_popular_list = NULL;
    s_popular_spinner = NULL;
    s_fetch_in_progress = false;
    s_fetch_attempted = false;
    s_last_fetch_err = ESP_OK;
    ensure_podcast_fetch_started();

    return scr;
}
