#pragma once

#include "esp_err.h"
#include "data/stations.h"
#include "data/podcast_index.h"
#include <stdbool.h>

/*
 * Singleton playback state — tracks what is currently playing and
 * exposes it to all UI screens.
 */

typedef enum {
    PLAYBACK_IDLE,
    PLAYBACK_STATION,
    PLAYBACK_EPISODE,
} playback_type_t;

typedef struct {
    playback_type_t type;
    bool            is_playing;
    bool            is_live;

    /* Populated when type == PLAYBACK_STATION */
    const station_t *station;

    /* Populated when type == PLAYBACK_EPISODE */
    char podcast_title[96];
    char episode_title[128];
    char audio_url[256];
} playback_state_t;

#ifdef __cplusplus
extern "C" {
#endif

/** Initialise playback state (call once at startup). */
void playback_state_init(void);

/** Start playing a radio station. */
esp_err_t playback_play_station(const station_t *station);

/** Start playing a podcast episode. */
esp_err_t playback_play_episode(const podcast_t *podcast, const episode_t *episode);

/** Stop playback. */
esp_err_t playback_stop(void);

/** Toggle play/pause for non-live content. */
esp_err_t playback_toggle(void);

/** Read-only view of current state (thread-safe copy). */
playback_state_t playback_get_state(void);

#ifdef __cplusplus
}
#endif
