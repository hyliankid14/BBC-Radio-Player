#pragma once

#include "lvgl.h"
#include "podcast_index.h"

/** Create and return the episode list screen for @p podcast. */
lv_obj_t *screen_pod_detail_create(podcast_t *podcast);
