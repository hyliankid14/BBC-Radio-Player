#pragma once

#include "esp_err.h"
#include <stdbool.h>

/*
 * Audio pipeline interface.
 *
 * In Wokwi:
 *   Playback is rendered as audible tones on the simulated buzzer.
 *
 * On real hardware:
 *   Playback is rendered as audible PCM tones through the ES8311 codec.
 *   Full BBC stream decoding still requires an HTTP/HLS + codec pipeline.
 */

#ifdef __cplusplus
extern "C" {
#endif

/** Initialise the audio pipeline (I2S + codec). */
esp_err_t audio_pipeline_init(void);

/**
 * Start playing the given URL.
 * is_live=true  → live radio HLS stream (do not pause, only stop)
 * is_live=false → podcast episode (can pause/resume)
 */
esp_err_t audio_play_url(const char *url, bool is_live);

/** Stop playback immediately and release stream resources. */
esp_err_t audio_stop(void);

/** Toggle play/pause (no-op for live streams). */
esp_err_t audio_toggle(void);

/** Returns true if currently playing. */
bool audio_is_playing(void);

/** Returns true if the current stream is a live radio stream. */
bool audio_is_live(void);

/** Set output volume 0–100 %. */
esp_err_t audio_set_volume(int percent);

#ifdef __cplusplus
}
#endif
