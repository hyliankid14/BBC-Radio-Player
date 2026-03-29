#include "playback_state.h"
#include "bbc_audio.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include <string.h>

static const char *TAG = "playback_state";

static playback_state_t s_state;
static SemaphoreHandle_t s_mutex;

void playback_state_init(void)
{
    memset(&s_state, 0, sizeof(s_state));
    s_state.type = PLAYBACK_IDLE;
    s_mutex = xSemaphoreCreateMutex();
}

esp_err_t playback_play_station(const station_t *station)
{
    esp_err_t ret = bbc_audio_play_url(station->stream_url, /*is_live=*/true);
    if (ret != ESP_OK) return ret;

    xSemaphoreTake(s_mutex, portMAX_DELAY);
    s_state.type       = PLAYBACK_STATION;
    s_state.is_playing = true;
    s_state.is_live    = true;
    s_state.station    = station;
    xSemaphoreGive(s_mutex);

    ESP_LOGI(TAG, "Playing station: %s", station->title);
    return ESP_OK;
}

esp_err_t playback_play_episode(const podcast_t *podcast, const episode_t *episode)
{
    esp_err_t ret = bbc_audio_play_url(episode->audio_url, /*is_live=*/false);
    if (ret != ESP_OK) return ret;

    xSemaphoreTake(s_mutex, portMAX_DELAY);
    s_state.type       = PLAYBACK_EPISODE;
    s_state.is_playing = true;
    s_state.is_live    = false;
    s_state.station    = NULL;
    strncpy(s_state.podcast_title, podcast->title,   sizeof(s_state.podcast_title) - 1);
    strncpy(s_state.episode_title, episode->title,   sizeof(s_state.episode_title) - 1);
    strncpy(s_state.audio_url,     episode->audio_url, sizeof(s_state.audio_url) - 1);
    xSemaphoreGive(s_mutex);

    ESP_LOGI(TAG, "Playing episode: %s — %s", podcast->title, episode->title);
    return ESP_OK;
}

esp_err_t playback_stop(void)
{
    bbc_audio_stop();
    xSemaphoreTake(s_mutex, portMAX_DELAY);
    s_state.type       = PLAYBACK_IDLE;
    s_state.is_playing = false;
    xSemaphoreGive(s_mutex);
    return ESP_OK;
}

esp_err_t playback_toggle(void)
{
    bbc_audio_toggle();
    xSemaphoreTake(s_mutex, portMAX_DELAY);
    s_state.is_playing = bbc_audio_is_playing();
    xSemaphoreGive(s_mutex);
    return ESP_OK;
}

playback_state_t playback_get_state(void)
{
    xSemaphoreTake(s_mutex, portMAX_DELAY);
    playback_state_t copy = s_state;
    xSemaphoreGive(s_mutex);
    return copy;
}
