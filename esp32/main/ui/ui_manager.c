#include "ui_manager.h"
#include "esp_log.h"
#include "driver/gpio.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <stdbool.h>
#include <string.h>

static const char *TAG = "ui_mgr";

/* ── Navigation stack ──────────────────────────────────────────────────── */
#define SCREEN_STACK_DEPTH 8
static lv_obj_t  *s_stack[SCREEN_STACK_DEPTH];
static int         s_top = -1;

#define MAX_FOCUSABLE_OBJS 64
static lv_obj_t *s_focusable[MAX_FOCUSABLE_OBJS];
static int       s_focusable_count = 0;
static int       s_focus_index = -1;

/* ── Button-driven LVGL input device ──────────────────────────────────── */
#define BTN_PLUS_GPIO  4
#define BTN_PWR_GPIO   5
#define BTN_BOOT_GPIO  19

static void ui_pop_screen_async(void *arg)
{
    LV_UNUSED(arg);
    ui_pop_screen();
}

static void ui_focus_next_async(void *arg)
{
    LV_UNUSED(arg);
    if (s_focusable_count <= 0) return;

    lv_obj_t *prev = (s_focus_index >= 0) ? s_focusable[s_focus_index] : NULL;
    if (prev && lv_obj_is_valid(prev)) {
        lv_obj_set_style_outline_width(prev, 0, LV_PART_MAIN);
    }

    s_focus_index = (s_focus_index + 1) % s_focusable_count;
    lv_obj_t *cur = s_focusable[s_focus_index];
    if (cur && lv_obj_is_valid(cur)) {
        lv_obj_set_style_outline_color(cur, lv_color_white(), LV_PART_MAIN);
        lv_obj_set_style_outline_width(cur, 2, LV_PART_MAIN);
        lv_obj_set_style_outline_pad(cur, 2, LV_PART_MAIN);

        /* Keep focused controls visible when navigating long scrollable lists. */
        lv_obj_scroll_to_view_recursive(cur, LV_ANIM_ON);
    }
}

static void ui_activate_focused_async(void *arg)
{
    LV_UNUSED(arg);
    if (s_focus_index < 0 || s_focus_index >= s_focusable_count) return;

    lv_obj_t *focused = s_focusable[s_focus_index];
    if (focused && lv_obj_is_valid(focused)) {
        lv_event_send(focused, LV_EVENT_CLICKED, NULL);
    }
}

static void ui_group_add_focusable_recursive(lv_obj_t *obj)
{
    if (obj == NULL) return;

    bool clickable = lv_obj_has_flag(obj, LV_OBJ_FLAG_CLICKABLE);
    bool marked = lv_obj_has_flag(obj, LV_OBJ_FLAG_USER_1);
    bool hidden = lv_obj_has_flag(obj, LV_OBJ_FLAG_HIDDEN);
    if (clickable && marked && !hidden && s_focusable_count < MAX_FOCUSABLE_OBJS) {
        s_focusable[s_focusable_count++] = obj;
    }

    uint32_t child_count = lv_obj_get_child_cnt(obj);
    for (uint32_t i = 0; i < child_count; i++) {
        lv_obj_t *child = lv_obj_get_child(obj, i);
        ui_group_add_focusable_recursive(child);
    }
}

static void ui_rebuild_focus_group(lv_obj_t *root)
{
    if (root == NULL) return;

    for (int i = 0; i < s_focusable_count; i++) {
        lv_obj_t *obj = s_focusable[i];
        if (obj && lv_obj_is_valid(obj)) {
            lv_obj_set_style_outline_width(obj, 0, LV_PART_MAIN);
        }
    }

    s_focusable_count = 0;
    s_focus_index = -1;
    ui_group_add_focusable_recursive(root);

    if (s_focusable_count > 0) {
        s_focus_index = -1;
        ui_focus_next_async(NULL);
        ESP_LOGI(TAG, "Focus targets: %d", s_focusable_count);
    } else {
        ESP_LOGW(TAG, "No focusable widgets on current screen");
    }
}

static void button_poll_task(void *arg)
{
    const TickType_t poll_period = pdMS_TO_TICKS(15);
    const TickType_t debounce_ms = pdMS_TO_TICKS(80);

    int last_plus = 1;
    int last_pwr  = 1;
    int last_boot = 1;

    TickType_t plus_unlock = 0;
    TickType_t pwr_unlock  = 0;
    TickType_t boot_unlock = 0;

    while (true) {
        vTaskDelay(poll_period);
        TickType_t now = xTaskGetTickCount();

        int plus = gpio_get_level(BTN_PLUS_GPIO);
        int pwr  = gpio_get_level(BTN_PWR_GPIO);
        int boot = gpio_get_level(BTN_BOOT_GPIO);

        if (last_plus == 1 && plus == 0 && now >= plus_unlock) {
            ESP_LOGI(TAG, "Button PLUS");
            lv_async_call(ui_focus_next_async, NULL);
            plus_unlock = now + debounce_ms;
        }
        if (last_pwr == 1 && pwr == 0 && now >= pwr_unlock) {
            ESP_LOGI(TAG, "Button PWR");
            lv_async_call(ui_pop_screen_async, NULL);
            pwr_unlock = now + debounce_ms;
        }
        if (last_boot == 1 && boot == 0 && now >= boot_unlock) {
            ESP_LOGI(TAG, "Button BOOT");
            lv_async_call(ui_activate_focused_async, NULL);
            boot_unlock = now + debounce_ms;
        }

        last_plus = plus;
        last_pwr  = pwr;
        last_boot = boot;
    }
}

static void register_buttons(void)
{
    gpio_config_t io_cfg = {
        .pin_bit_mask = (1ULL << BTN_PLUS_GPIO) | (1ULL << BTN_PWR_GPIO) | (1ULL << BTN_BOOT_GPIO),
        .mode = GPIO_MODE_INPUT,
        .pull_up_en = GPIO_PULLUP_ENABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type = GPIO_INTR_DISABLE,
    };
    gpio_config(&io_cfg);

    xTaskCreate(button_poll_task, "ui_btn_poll", 2048, NULL, 5, NULL);
}

static void register_indev(void)
{
    /* Kept for API symmetry; navigation is handled explicitly. */
}

/* ── Public API ────────────────────────────────────────────────────────── */

void ui_manager_init(void)
{
    register_buttons();
    register_indev();
    ESP_LOGI(TAG, "UI manager initialised (PLUS=next, BOOT=select, PWR=back)");
}

void ui_mark_selectable(lv_obj_t *obj)
{
    if (obj == NULL) return;
    lv_obj_add_flag(obj, LV_OBJ_FLAG_USER_1);
}

void ui_refresh_navigation(void)
{
    lv_obj_t *root = ui_current_screen();
    if (root != NULL) {
        ui_rebuild_focus_group(root);
    }
}

void ui_push_screen(lv_obj_t *scr, lv_scr_load_anim_t anim)
{
    if (s_top >= SCREEN_STACK_DEPTH - 1) {
        ESP_LOGW(TAG, "Screen stack full");
        return;
    }
    s_stack[++s_top] = scr;
    lv_scr_load_anim(scr, anim, 200, 0, false);
    ui_rebuild_focus_group(scr);
}

void ui_pop_screen(void)
{
    if (s_top <= 0) return;
    s_top--;
    lv_scr_load_anim(s_stack[s_top], LV_SCR_LOAD_ANIM_MOVE_RIGHT, 200, 0, false);
    ui_rebuild_focus_group(s_stack[s_top]);
}

lv_obj_t *ui_current_screen(void)
{
    return (s_top >= 0) ? s_stack[s_top] : NULL;
}

void ui_create_header(lv_obj_t *parent, const char *title, bool show_back)
{
    lv_obj_t *hdr = lv_obj_create(parent);
    lv_obj_set_size(hdr, LV_PCT(100), 36);
    lv_obj_align(hdr, LV_ALIGN_TOP_MID, 0, 0);
    lv_obj_set_style_bg_color(hdr, UI_COLOR_BBC_RED,   LV_PART_MAIN);
    lv_obj_set_style_border_width(hdr, 0, LV_PART_MAIN);
    lv_obj_set_style_radius(hdr, 0, LV_PART_MAIN);
    lv_obj_set_style_pad_all(hdr, 4, LV_PART_MAIN);

    lv_obj_t *lbl = lv_label_create(hdr);
    lv_label_set_text(lbl, title);
    lv_obj_set_style_text_color(lbl, UI_COLOR_TEXT, LV_PART_MAIN);
    lv_obj_set_style_text_font(lbl, &lv_font_montserrat_14, LV_PART_MAIN);
    lv_obj_align(lbl, LV_ALIGN_CENTER, 0, 0);

    if (show_back) {
        lv_obj_t *back = lv_label_create(hdr);
        lv_label_set_text(back, LV_SYMBOL_LEFT);
        lv_obj_set_style_text_color(back, UI_COLOR_TEXT, LV_PART_MAIN);
        lv_obj_align(back, LV_ALIGN_LEFT_MID, 4, 0);
    }
}
