#pragma once

#include "esp_err.h"
#include <stddef.h>

/*
 * TF-card subscriptions.
 *
 * The file /sdcard/subscriptions.json has the format:
 *   {
 *     "subscribed": [
 *       { "id": "p01234567", "title": "My Podcast",
 *         "rss_url": "https://feeds.bbci.co.uk/..." }
 *     ]
 *   }
 *
 * To subscribe to a podcast, add its entry to that file manually or via
 * a companion tool (e.g. the Android/iOS app's export feature).
 */

#define SUBSCRIPTIONS_MAX  50

typedef struct {
    char id[16];
    char title[96];
    char rss_url[192];
} subscribed_podcast_t;

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Load subscriptions from the TF card.  Must be called after the card
 * is mounted.  Returns ESP_OK (with count = 0) if the file is absent.
 */
esp_err_t subscriptions_load(void);

/** Number of subscribed podcasts currently loaded. */
size_t subscriptions_count(void);

/** Return pointer to subscription i (0-based). */
const subscribed_podcast_t *subscriptions_get(size_t i);

#ifdef __cplusplus
}
#endif
