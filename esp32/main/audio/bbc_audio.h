#pragma once

#include "esp_err.h"
#include <stdbool.h>
#include <stdint.h>

/*
 * BBC audio interface.
 *
 * In Wokwi:
 *   Playback is rendered as audible tones on the simulated buzzer.
 *
 * On real hardware:
 *   If ESP-ADF is available at build time, playback uses HTTP/HLS + AAC/MP3
 *   decode and outputs decoded PCM through the ES8311 codec.
 *   Otherwise it falls back to generated tones.
 */

#ifdef __cplusplus
extern "C" {
#endif

/** Initialise the audio subsystem. */
esp_err_t bbc_audio_init(void);

/**
 * Start playing the given URL.
 * is_live=true  → live radio HLS stream (do not pause, only stop)
 * is_live=false → podcast episode (can pause/resume)
 */
esp_err_t bbc_audio_play_url(const char *url, bool is_live);

/** Stop playback immediately and release stream resources. */
esp_err_t bbc_audio_stop(void);

/** Toggle play/pause (no-op for live streams). */
esp_err_t bbc_audio_toggle(void);

/** Returns true if currently playing. */
bool bbc_audio_is_playing(void);

/** Returns true if the current stream is a live radio stream. */
bool bbc_audio_is_live(void);

/** Set output volume 0–100 %. */
esp_err_t bbc_audio_set_volume(int percent);

/** Seek to an absolute position (seconds) within the current non-live stream. */
esp_err_t bbc_audio_seek_to(int32_t position_secs, int32_t duration_secs);

#ifdef __cplusplus
}
#endif
