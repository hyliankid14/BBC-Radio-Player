#pragma once

#include "podcast_index.h"
#include "esp_err.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Fetch popular-podcasts.json and new-podcasts.json from GCS, then
 * annotate the given podcast array with popularity_rank and is_new flags.
 *
 * Safe to call even if the network is unavailable — rankings will remain
 * at their default zero / false values.
 */
esp_err_t podcast_rankings_apply(podcast_t *podcasts, size_t count);

#ifdef __cplusplus
}
#endif
