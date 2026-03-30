#include "screen_podcasts.h"
#include "screen_pod_detail.h"
#include "ui_manager.h"
#include "podcast_index.h"
#include "subscriptions.h"
#include "esp_log.h"
#include <string.h>
#include <stdlib.h>

static const char *TAG = "screen_podcasts";

/* Weak references so refresh can update the lists */
static lv_obj_t *s_list_popular    = NULL;
static lv_obj_t *s_list_subscribed = NULL;
static lv_obj_t *s_list_new        = NULL;
static lv_obj_t *s_spinner         = NULL;

static void add_info_row(lv_obj_t *list, const char *text)
{
    lv_obj_t *lbl = lv_label_create(list);
    lv_label_set_text(lbl, text);
    lv_obj_set_style_text_color(lbl, UI_COLOR_SUBTEXT, LV_PART_MAIN);
    lv_obj_set_style_pad_top(lbl, 10, LV_PART_MAIN);
    lv_obj_set_style_pad_left(lbl, 8, LV_PART_MAIN);
}

static void on_podcast_clicked(lv_event_t *e)
{
    podcast_t *p = (podcast_t *)lv_event_get_user_data(e);
    lv_obj_t  *scr = screen_pod_detail_create(p);
    ui_push_screen(scr, LV_SCR_LOAD_ANIM_MOVE_LEFT);
}

static void add_podcast_btn(lv_obj_t *list, podcast_t *p)
{
    lv_obj_t *btn = lv_list_add_btn(list, LV_SYMBOL_LIST, p->title);
    lv_obj_set_style_bg_color(btn, UI_COLOR_CARD_BG, LV_PART_MAIN);
    lv_obj_set_style_text_color(btn, UI_COLOR_TEXT,    LV_PART_MAIN);
    ui_mark_selectable(btn);
    lv_obj_add_event_cb(btn, on_podcast_clicked, LV_EVENT_CLICKED, p);
}

static void populate_popular(void)
{
    if (!s_list_popular) return;
    lv_obj_clean(s_list_popular);

    size_t     count;
    podcast_t *all = podcast_index_get_all(&count);
    if (!all || count == 0) {
        add_info_row(s_list_popular, podcast_index_is_ready()
            ? "No popular podcasts available"
            : "Loading popular podcasts...");
        return;
    }

    /* Show podcasts that have a popularity rank, sorted ascending */
    for (int rank = 1; rank <= (int)count; rank++) {
        for (size_t i = 0; i < count; i++) {
            if (all[i].popularity_rank == rank) {
                add_podcast_btn(s_list_popular, &all[i]);
            }
        }
    }
    /* Also show unranked entries if list is empty */
    if (lv_obj_get_child_cnt(s_list_popular) == 0) {
        for (size_t i = 0; i < count && i < 50; i++) {
            add_podcast_btn(s_list_popular, &all[i]);
        }
    }
}

static void populate_subscribed(void)
{
    if (!s_list_subscribed) return;
    lv_obj_clean(s_list_subscribed);

    size_t count = subscriptions_count();
    if (count == 0) {
        add_info_row(s_list_subscribed, "No subscriptions found");
        return;
    }

    for (size_t i = 0; i < count; i++) {
        const subscribed_podcast_t *sub = subscriptions_get(i);
        if (!sub) continue;
        lv_obj_t *btn = lv_list_add_btn(s_list_subscribed, LV_SYMBOL_OK, sub->title);
        lv_obj_set_style_bg_color(btn, UI_COLOR_CARD_BG, LV_PART_MAIN);
        lv_obj_set_style_text_color(btn, UI_COLOR_TEXT,    LV_PART_MAIN);
        ui_mark_selectable(btn);
        /* subscriptions don't yet have a full podcast_t; open RSS directly */
        /* TODO: resolve to podcast_t and use screen_pod_detail */
    }
}

static void populate_new(void)
{
    if (!s_list_new) return;
    lv_obj_clean(s_list_new);

    size_t     count;
    podcast_t *all = podcast_index_get_all(&count);
    if (!all || count == 0) {
        add_info_row(s_list_new, podcast_index_is_ready()
            ? "No new podcasts available"
            : "Loading new podcasts...");
        return;
    }

    size_t new_count = 0;
    for (size_t i = 0; i < count; i++) {
        if (all[i].is_new) {
            add_podcast_btn(s_list_new, &all[i]);
            new_count++;
        }
    }
    if (new_count == 0) {
        add_info_row(s_list_new, "No new podcasts available");
    }
}

void screen_podcasts_refresh(void *arg)
{
    /* Hide spinner, show populated lists */
    if (s_spinner) { lv_obj_add_flag(s_spinner, LV_OBJ_FLAG_HIDDEN); }
    populate_popular();
    populate_subscribed();
    populate_new();
    ui_refresh_navigation();
    ESP_LOGI(TAG, "Podcast lists refreshed");
}

lv_obj_t *screen_podcasts_create(void)
{
    lv_obj_t *scr = lv_obj_create(NULL);
    lv_obj_set_style_bg_color(scr, UI_COLOR_DARK_BG, LV_PART_MAIN);

    ui_create_header(scr, "Podcasts", true);

    lv_obj_t *tv = lv_tabview_create(scr, LV_DIR_TOP, 30);
    lv_obj_set_size(tv, LV_PCT(100), LV_PCT(100) - 36);
    lv_obj_align(tv, LV_ALIGN_BOTTOM_MID, 0, 0);
    lv_obj_set_style_bg_color(tv, UI_COLOR_DARK_BG, LV_PART_MAIN);

    /* Style tab bar */
    lv_obj_t *tab_bar = lv_tabview_get_tab_btns(tv);
    lv_obj_set_style_bg_color(tab_bar, UI_COLOR_CARD_BG, LV_PART_MAIN);
    lv_obj_set_style_text_color(tab_bar, UI_COLOR_TEXT,    LV_PART_ITEMS);

    lv_obj_t *tab_pop  = lv_tabview_add_tab(tv, "Popular");
    lv_obj_t *tab_sub  = lv_tabview_add_tab(tv, "Subscribed");
    lv_obj_t *tab_new  = lv_tabview_add_tab(tv, "New");

    lv_obj_set_style_bg_color(tab_pop, UI_COLOR_DARK_BG, LV_PART_MAIN);
    lv_obj_set_style_border_width(tab_pop, 0, LV_PART_MAIN);
    lv_obj_set_style_bg_color(tab_sub, UI_COLOR_DARK_BG, LV_PART_MAIN);
    lv_obj_set_style_border_width(tab_sub, 0, LV_PART_MAIN);
    lv_obj_set_style_bg_color(tab_new, UI_COLOR_DARK_BG, LV_PART_MAIN);
    lv_obj_set_style_border_width(tab_new, 0, LV_PART_MAIN);

    s_list_popular    = lv_list_create(tab_pop);
    lv_obj_set_size(s_list_popular, LV_PCT(100), LV_PCT(100));
    lv_obj_set_style_bg_color(s_list_popular, UI_COLOR_DARK_BG, LV_PART_MAIN);
    lv_obj_set_style_border_width(s_list_popular, 0, LV_PART_MAIN);
    lv_obj_set_style_pad_all(s_list_popular, 0, LV_PART_MAIN);
    lv_obj_set_style_pad_row(s_list_popular, 2, LV_PART_MAIN);
    lv_obj_scroll_to_y(s_list_popular, 0, LV_ANIM_OFF);

    s_list_subscribed = lv_list_create(tab_sub);
    lv_obj_set_size(s_list_subscribed, LV_PCT(100), LV_PCT(100));
    lv_obj_set_style_bg_color(s_list_subscribed, UI_COLOR_DARK_BG, LV_PART_MAIN);
    lv_obj_set_style_border_width(s_list_subscribed, 0, LV_PART_MAIN);
    lv_obj_set_style_pad_all(s_list_subscribed, 0, LV_PART_MAIN);
    lv_obj_set_style_pad_row(s_list_subscribed, 2, LV_PART_MAIN);
    lv_obj_scroll_to_y(s_list_subscribed, 0, LV_ANIM_OFF);

    s_list_new        = lv_list_create(tab_new);
    lv_obj_set_size(s_list_new, LV_PCT(100), LV_PCT(100));
    lv_obj_set_style_bg_color(s_list_new, UI_COLOR_DARK_BG, LV_PART_MAIN);
    lv_obj_set_style_border_width(s_list_new, 0, LV_PART_MAIN);
    lv_obj_set_style_pad_all(s_list_new, 0, LV_PART_MAIN);
    lv_obj_set_style_pad_row(s_list_new, 2, LV_PART_MAIN);
    lv_obj_scroll_to_y(s_list_new, 0, LV_ANIM_OFF);

    /* Loading spinner while podcast index is being fetched */
    s_spinner = lv_spinner_create(scr, 1000, 60);
    lv_obj_center(s_spinner);
    lv_obj_set_size(s_spinner, 50, 50);

    /* Populate immediately so subscribed and placeholders appear without delay. */
    lv_async_call(screen_podcasts_refresh, NULL);

    return scr;
}
