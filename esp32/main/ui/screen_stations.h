#pragma once

#include "lvgl.h"

/** Create and return the BBC Radio stations list screen. */
lv_obj_t *screen_stations_create(void);

/** Start background fetch of current show titles for station rows after networking is ready. */
void screen_stations_start_title_fetch(void);
