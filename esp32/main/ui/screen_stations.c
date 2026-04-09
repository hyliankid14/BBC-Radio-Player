#include "screen_stations.h"
#include "screen_now_playing.h"
#include "screen_settings.h"
#include "ui_manager.h"
#include "stations.h"
#include "playback_state.h"
#include "esp_log.h"

static const char *TAG = "screen_stations";

static void on_station_clicked(lv_event_t *e)
{
    const station_t *st = (const station_t *)lv_event_get_user_data(e);
    esp_err_t err = playback_play_station(st);
    if (err != ESP_OK) {
        ESP_LOGW(TAG, "Could not play station %s", st->title);
        return;
    }
    lv_obj_t *np_scr = screen_now_playing_create();
    ui_push_screen(np_scr, LV_SCR_LOAD_ANIM_MOVE_LEFT);
}

static void on_settings_clicked(lv_event_t *e)
{
    LV_UNUSED(e);
    lv_obj_t *settings = screen_settings_create();
    ui_push_screen(settings, LV_SCR_LOAD_ANIM_MOVE_LEFT);
}

lv_obj_t *screen_stations_create(void)
{
    lv_obj_t *scr = lv_obj_create(NULL);
    lv_obj_set_style_bg_color(scr, UI_COLOR_DARK_BG, LV_PART_MAIN);
    lv_obj_set_style_pad_all(scr, 0, LV_PART_MAIN);

    ui_create_header(scr, "BBC Radio", false);

    lv_obj_t *settings_btn = lv_btn_create(scr);
    lv_obj_set_size(settings_btn, 44, 40);
    lv_obj_align(settings_btn, LV_ALIGN_TOP_LEFT, 2, 6);
    lv_obj_set_style_bg_opa(settings_btn, LV_OPA_TRANSP, LV_PART_MAIN);
    lv_obj_set_style_bg_opa(settings_btn, LV_OPA_TRANSP, LV_PART_MAIN | LV_STATE_PRESSED);
    lv_obj_set_style_border_width(settings_btn, 0, LV_PART_MAIN);
    lv_obj_set_style_shadow_width(settings_btn, 0, LV_PART_MAIN);
    lv_obj_set_style_outline_width(settings_btn, 0, LV_PART_MAIN);
    lv_obj_set_style_radius(settings_btn, 0, LV_PART_MAIN);
    lv_obj_clear_flag(settings_btn, LV_OBJ_FLAG_SCROLLABLE);
    ui_mark_selectable(settings_btn);
    lv_obj_add_event_cb(settings_btn, on_settings_clicked, LV_EVENT_CLICKED, NULL);
    lv_obj_t *settings_icon = lv_label_create(settings_btn);
    lv_label_set_text(settings_icon, LV_SYMBOL_SETTINGS);
    lv_obj_set_style_text_color(settings_icon, UI_COLOR_TEXT, LV_PART_MAIN);
    lv_obj_center(settings_icon);

    lv_obj_t *list = lv_list_create(scr);
    lv_obj_set_size(list, 240, 240 - UI_HEADER_HEIGHT);
    lv_obj_align(list, LV_ALIGN_TOP_MID, 0, UI_HEADER_HEIGHT);
    lv_obj_set_style_bg_color(list, UI_COLOR_DARK_BG, LV_PART_MAIN);
    lv_obj_set_style_border_width(list, 0, LV_PART_MAIN);
    lv_obj_set_style_radius(list, 0, LV_PART_MAIN);
    lv_obj_set_style_pad_all(list, 0, LV_PART_MAIN);
    lv_obj_set_style_pad_top(list, 0, LV_PART_MAIN);
    lv_obj_set_style_pad_row(list, 2, LV_PART_MAIN);
    /* Disable horizontal scrolling/panning and scrollbars. */
    lv_obj_set_scroll_dir(list, LV_DIR_VER);
    lv_obj_set_scrollbar_mode(list, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(list, LV_OBJ_FLAG_SCROLL_CHAIN_HOR);
    lv_obj_clear_flag(list, LV_OBJ_FLAG_SCROLL_ELASTIC);

    size_t count = stations_count();
    const station_t *stations = stations_get_all();

    for (size_t i = 0; i < count; i++) {
        lv_obj_t *btn = lv_list_add_btn(list, LV_SYMBOL_AUDIO, stations[i].title);
        lv_obj_set_width(btn, LV_PCT(100));
        lv_obj_set_style_bg_color(btn, UI_COLOR_CARD_BG, LV_PART_MAIN);
        lv_obj_set_style_text_color(btn, UI_COLOR_TEXT, LV_PART_MAIN);
        lv_obj_clear_flag(btn, LV_OBJ_FLAG_SCROLLABLE);

        lv_obj_t *label = lv_obj_get_child(btn, 1);
        if (label) {
            lv_label_set_long_mode(label, LV_LABEL_LONG_DOT);
            lv_obj_set_width(label, 170);
        }

        ui_mark_selectable(btn);
        lv_obj_add_event_cb(btn, on_station_clicked, LV_EVENT_CLICKED,
                             (void *)&stations[i]);
    }

    lv_obj_scroll_to_y(list, 0, LV_ANIM_OFF);

    return scr;
}
