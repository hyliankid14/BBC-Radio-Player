#pragma once

#include "lvgl.h"

/** Create (or recreate) the Now Playing screen from the current playback state. */
lv_obj_t *screen_now_playing_create(void);

/** Refresh all Now Playing labels from the current playback state. */
void screen_now_playing_refresh(void *arg);
