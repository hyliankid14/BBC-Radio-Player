#include "screen_settings.h"

#include "ui_manager.h"
#include "wifi_settings.h"
#include "wifi_portal.h"

static lv_obj_t *s_setup_btn   = NULL;
static lv_obj_t *s_instructions = NULL;

static void on_screen_delete(lv_event_t *e)
{
    LV_UNUSED(e);
    s_setup_btn    = NULL;
    s_instructions = NULL;
}

static void on_start_portal(lv_event_t *e)
{
    LV_UNUSED(e);
    if (wifi_portal_start() == ESP_OK) {
        lv_obj_add_flag(s_setup_btn, LV_OBJ_FLAG_HIDDEN);
        lv_obj_clear_flag(s_instructions, LV_OBJ_FLAG_HIDDEN);
    }
}

lv_obj_t *screen_settings_create(void)
{
    lv_obj_t *scr = lv_obj_create(NULL);
    lv_obj_set_style_bg_color(scr, UI_COLOR_DARK_BG, LV_PART_MAIN);
    lv_obj_set_style_pad_all(scr, 0, LV_PART_MAIN);
    lv_obj_clear_flag(scr, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_event_cb(scr, on_screen_delete, LV_EVENT_DELETE, NULL);

    ui_create_header(scr, "Settings", true);

    /* ── Current network label ───────────────────────────────────── */
    char ssid[33] = {0};
    char password[65] = {0};
    wifi_settings_get_boot(ssid, sizeof(ssid), password, sizeof(password));

    lv_obj_t *net_title = lv_label_create(scr);
    lv_label_set_text(net_title, "Wi-Fi Network");
    lv_obj_set_style_text_color(net_title, UI_COLOR_SUBTEXT, LV_PART_MAIN);
    lv_obj_align(net_title, LV_ALIGN_TOP_LEFT, 14, UI_HEADER_HEIGHT + 14);

    lv_obj_t *ssid_val = lv_label_create(scr);
    lv_label_set_text(ssid_val, ssid[0] ? ssid : "Not configured");
    lv_obj_set_style_text_color(ssid_val, UI_COLOR_TEXT, LV_PART_MAIN);
    lv_obj_set_width(ssid_val, 212);
    lv_label_set_long_mode(ssid_val, LV_LABEL_LONG_DOT);
    lv_obj_align(ssid_val, LV_ALIGN_TOP_LEFT, 14, UI_HEADER_HEIGHT + 32);

    /* ── Divider ─────────────────────────────────────────────────── */
    lv_obj_t *div = lv_obj_create(scr);
    lv_obj_set_size(div, 212, 1);
    lv_obj_set_style_bg_color(div, lv_color_hex(0x333333), LV_PART_MAIN);
    lv_obj_set_style_bg_opa(div, LV_OPA_COVER, LV_PART_MAIN);
    lv_obj_set_style_border_width(div, 0, LV_PART_MAIN);
    lv_obj_align(div, LV_ALIGN_TOP_LEFT, 14, UI_HEADER_HEIGHT + 56);

    /* ── "Change Wi-Fi" button ───────────────────────────────────── */
    s_setup_btn = lv_btn_create(scr);
    lv_obj_set_size(s_setup_btn, 212, 48);
    lv_obj_align(s_setup_btn, LV_ALIGN_TOP_LEFT, 14, UI_HEADER_HEIGHT + 66);
    lv_obj_set_style_bg_color(s_setup_btn, lv_color_hex(0xCC0000), LV_PART_MAIN);
    lv_obj_clear_flag(s_setup_btn, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_event_cb(s_setup_btn, on_start_portal, LV_EVENT_CLICKED, NULL);

    lv_obj_t *btn_lbl = lv_label_create(s_setup_btn);
    lv_label_set_text(btn_lbl, "Change Wi-Fi");
    lv_obj_center(btn_lbl);

    /* ── Instructions shown after portal starts ──────────────────── */
    s_instructions = lv_label_create(scr);
    lv_label_set_text(s_instructions,
        "On your phone:\n\n"
        "1. Connect to Wi-Fi:\n"
        "   BBCRadio-Setup\n\n"
        "2. Open browser and go to:\n"
        "   192.168.4.1\n\n"
        "3. Enter credentials\n"
        "   and tap Save & Connect");
    lv_obj_set_style_text_color(s_instructions, UI_COLOR_TEXT, LV_PART_MAIN);
    lv_obj_set_width(s_instructions, 212);
    lv_label_set_long_mode(s_instructions, LV_LABEL_LONG_WRAP);
    lv_obj_align(s_instructions, LV_ALIGN_TOP_LEFT, 14, UI_HEADER_HEIGHT + 66);
    lv_obj_add_flag(s_instructions, LV_OBJ_FLAG_HIDDEN);

    return scr;
}
