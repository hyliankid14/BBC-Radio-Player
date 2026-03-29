#include "bbc_audio.h"
#include "bsp_codec.h"
#include "driver/ledc.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#if BBC_HAS_ADF
#include "audio_element.h"
#include "audio_pipeline.h"
#include "http_stream.h"
#include "raw_stream.h"
#include "aac_decoder.h"
#include "mp3_decoder.h"
#endif

static const char *TAG = "audio_pipeline";

static volatile bool s_playing = false;
static volatile bool s_is_live = false;
static char s_current_url[256];
static TaskHandle_t s_hw_tone_task = NULL;
static volatile uint32_t s_hw_tone_freq = 0;
static bool s_use_adf = false;

#define AUDIO_STUB_BUZZER_GPIO GPIO_NUM_3
#define AUDIO_TONE_FRAME_SAMPLES 256
#define AUDIO_TONE_AMPLITUDE     10000
static bool s_buzzer_ready = false;

#if BBC_HAS_ADF
typedef struct {
    audio_pipeline_handle_t pipeline;
    audio_element_handle_t http;
    audio_element_handle_t decoder;
    audio_element_handle_t raw;
    TaskHandle_t pcm_task;
    bool use_aac;
} adf_state_t;

static adf_state_t s_adf = {0};

static bool str_contains(const char *haystack, const char *needle)
{
    return haystack && needle && strstr(haystack, needle) != NULL;
}

static bool should_use_aac_decoder(const char *url, bool is_live)
{
    if (is_live) {
        return true;
    }
    return str_contains(url, ".aac") || str_contains(url, ".m3u8") ||
           str_contains(url, "format=aac") || str_contains(url, "type=aac");
}

static int http_stream_evt(http_stream_event_msg_t *msg)
{
    if (msg == NULL) {
        return ESP_OK;
    }
    if (msg->event_id == HTTP_STREAM_RESOLVE_ALL_TRACKS) {
        return ESP_OK;
    }
    if (msg->event_id == HTTP_STREAM_FINISH_TRACK) {
        return http_stream_next_track(msg->el);
    }
    if (msg->event_id == HTTP_STREAM_FINISH_PLAYLIST) {
        return http_stream_fetch_again(msg->el);
    }
    return ESP_OK;
}

static void adf_pcm_task(void *arg)
{
    (void)arg;
    char *buf = malloc(4096);
    if (!buf) {
        ESP_LOGE(TAG, "ADF PCM task out of memory");
        s_adf.pcm_task = NULL;
        vTaskDelete(NULL);
        return;
    }

    while (s_use_adf && s_playing && s_adf.raw != NULL) {
        int read_len = raw_stream_read(s_adf.raw, buf, 4096);
        if (read_len > 0) {
            size_t written = 0;
            bsp_codec_write(buf, (size_t)read_len, &written);
            continue;
        }
        vTaskDelay(pdMS_TO_TICKS(10));
    }

    free(buf);
    s_adf.pcm_task = NULL;
    vTaskDelete(NULL);
}

static void adf_stop_locked(void)
{
    if (s_adf.pipeline != NULL) {
        audio_pipeline_stop(s_adf.pipeline);
        audio_pipeline_wait_for_stop(s_adf.pipeline);
        audio_pipeline_terminate(s_adf.pipeline);
    }

    if (s_adf.pcm_task != NULL) {
        vTaskDelete(s_adf.pcm_task);
        s_adf.pcm_task = NULL;
    }

    if (s_adf.pipeline != NULL && s_adf.http != NULL) {
        audio_pipeline_unregister(s_adf.pipeline, s_adf.http);
    }
    if (s_adf.pipeline != NULL && s_adf.decoder != NULL) {
        audio_pipeline_unregister(s_adf.pipeline, s_adf.decoder);
    }
    if (s_adf.pipeline != NULL && s_adf.raw != NULL) {
        audio_pipeline_unregister(s_adf.pipeline, s_adf.raw);
    }

    if (s_adf.pipeline != NULL) {
        audio_pipeline_deinit(s_adf.pipeline);
    }
    if (s_adf.http != NULL) {
        audio_element_deinit(s_adf.http);
    }
    if (s_adf.decoder != NULL) {
        audio_element_deinit(s_adf.decoder);
    }
    if (s_adf.raw != NULL) {
        audio_element_deinit(s_adf.raw);
    }

    memset(&s_adf, 0, sizeof(s_adf));
}

static esp_err_t adf_start_stream(const char *url, bool is_live)
{
    adf_stop_locked();

    audio_pipeline_cfg_t pipeline_cfg = DEFAULT_AUDIO_PIPELINE_CONFIG();
    s_adf.pipeline = audio_pipeline_init(&pipeline_cfg);
    if (s_adf.pipeline == NULL) {
        ESP_LOGE(TAG, "ADF pipeline init failed");
        return ESP_FAIL;
    }

    http_stream_cfg_t http_cfg = HTTP_STREAM_CFG_DEFAULT();
    http_cfg.type = AUDIO_STREAM_READER;
    http_cfg.event_handle = http_stream_evt;
    http_cfg.enable_playlist_parser = true;
    s_adf.http = http_stream_init(&http_cfg);

    s_adf.use_aac = should_use_aac_decoder(url, is_live);
    if (s_adf.use_aac) {
        aac_decoder_cfg_t cfg = DEFAULT_AAC_DECODER_CONFIG();
        s_adf.decoder = aac_decoder_init(&cfg);
    } else {
        mp3_decoder_cfg_t cfg = DEFAULT_MP3_DECODER_CONFIG();
        s_adf.decoder = mp3_decoder_init(&cfg);
    }

    raw_stream_cfg_t raw_cfg = RAW_STREAM_CFG_DEFAULT();
    raw_cfg.type = AUDIO_STREAM_WRITER;
    s_adf.raw = raw_stream_init(&raw_cfg);

    if (s_adf.http == NULL || s_adf.decoder == NULL || s_adf.raw == NULL) {
        ESP_LOGE(TAG, "ADF element init failed");
        adf_stop_locked();
        return ESP_FAIL;
    }

    audio_pipeline_register(s_adf.pipeline, s_adf.http, "http");
    audio_pipeline_register(s_adf.pipeline, s_adf.decoder, s_adf.use_aac ? "aac" : "mp3");
    audio_pipeline_register(s_adf.pipeline, s_adf.raw, "raw");

    const char *link_tag[3] = {"http", s_adf.use_aac ? "aac" : "mp3", "raw"};
    if (audio_pipeline_link(s_adf.pipeline, &link_tag[0], 3) != ESP_OK) {
        ESP_LOGE(TAG, "ADF pipeline link failed");
        adf_stop_locked();
        return ESP_FAIL;
    }

    audio_element_set_uri(s_adf.http, url);
    if (audio_pipeline_run(s_adf.pipeline) != ESP_OK) {
        ESP_LOGE(TAG, "ADF pipeline run failed");
        adf_stop_locked();
        return ESP_FAIL;
    }

    BaseType_t rc = xTaskCreatePinnedToCore(adf_pcm_task, "adf_pcm", 6144, NULL, 5, &s_adf.pcm_task, 1);
    if (rc != pdPASS) {
        ESP_LOGE(TAG, "ADF PCM task create failed");
        adf_stop_locked();
        return ESP_FAIL;
    }

    ESP_LOGI(TAG, "ADF stream started (%s)", s_adf.use_aac ? "AAC/HLS" : "MP3");
    return ESP_OK;
}
#endif

static uint32_t audio_tone_frequency(const char *url, bool is_live)
{
    uint32_t hash = 5381;
    for (const unsigned char *p = (const unsigned char *)url; *p != 0; ++p) {
        hash = ((hash << 5) + hash) ^ *p;
    }
    uint32_t base = is_live ? 220 : 330;
    return base + (hash % 6) * 55;
}

static void fill_square_wave_stereo(int16_t *buffer, size_t frames, uint32_t freq, uint32_t *phase)
{
    uint32_t step = (uint32_t)(((uint64_t)freq << 32) / BSP_AUDIO_SAMPLE_RATE);
    for (size_t i = 0; i < frames; ++i) {
        int16_t sample = (*phase & 0x80000000u) ? AUDIO_TONE_AMPLITUDE : -AUDIO_TONE_AMPLITUDE;
        buffer[i * 2] = sample;
        buffer[i * 2 + 1] = sample;
        *phase += step;
    }
}

static void hardware_tone_write(uint32_t freq, uint32_t duration_ms)
{
    if (!bsp_codec_is_ready()) {
        return;
    }

    int16_t buffer[AUDIO_TONE_FRAME_SAMPLES * BSP_AUDIO_CHANNELS];
    uint32_t phase = 0;
    uint32_t total_frames = (BSP_AUDIO_SAMPLE_RATE * duration_ms) / 1000;
    while (total_frames > 0) {
        size_t frames = total_frames > AUDIO_TONE_FRAME_SAMPLES ? AUDIO_TONE_FRAME_SAMPLES : total_frames;
        fill_square_wave_stereo(buffer, frames, freq, &phase);
        size_t written = 0;
        bsp_codec_write(buffer, frames * sizeof(int16_t) * BSP_AUDIO_CHANNELS, &written);
        total_frames -= (uint32_t)frames;
    }
}

static void hardware_tone_task(void *arg)
{
    int16_t buffer[AUDIO_TONE_FRAME_SAMPLES * BSP_AUDIO_CHANNELS];
    uint32_t phase = 0;
    uint32_t last_freq = 0;

    while (true) {
        ulTaskNotifyTake(pdTRUE, portMAX_DELAY);
        while (s_playing && bsp_codec_is_ready()) {
            uint32_t freq = s_hw_tone_freq ? s_hw_tone_freq : 440;
            if (freq != last_freq) {
                last_freq = freq;
                phase = 0;
            }

            fill_square_wave_stereo(buffer, AUDIO_TONE_FRAME_SAMPLES, freq, &phase);
            size_t written = 0;
            bsp_codec_write(buffer, sizeof(buffer), &written);
            if (written != sizeof(buffer)) {
                vTaskDelay(pdMS_TO_TICKS(5));
            }
        }
    }
}

static void stub_buzzer_init(void)
{
    ledc_timer_config_t timer_cfg = {
        .speed_mode = LEDC_LOW_SPEED_MODE,
        .duty_resolution = LEDC_TIMER_10_BIT,
        .timer_num = LEDC_TIMER_1,
        .freq_hz = 440,
        .clk_cfg = LEDC_AUTO_CLK,
    };
    esp_err_t err = ledc_timer_config(&timer_cfg);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Buzzer timer init failed: %s", esp_err_to_name(err));
        return;
    }

    ledc_channel_config_t ch_cfg = {
        .gpio_num = AUDIO_STUB_BUZZER_GPIO,
        .speed_mode = LEDC_LOW_SPEED_MODE,
        .channel = LEDC_CHANNEL_1,
        .intr_type = LEDC_INTR_DISABLE,
        .timer_sel = LEDC_TIMER_1,
        .duty = 0,
        .hpoint = 0,
    };
    err = ledc_channel_config(&ch_cfg);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "Buzzer channel init failed: %s", esp_err_to_name(err));
        return;
    }
    s_buzzer_ready = true;
    ESP_LOGI(TAG, "Buzzer ready on GPIO%d", AUDIO_STUB_BUZZER_GPIO);
}

static void stub_buzzer_set(bool on, bool is_live)
{
    if (!s_buzzer_ready) {
        return;
    }
    uint32_t freq = s_hw_tone_freq ? s_hw_tone_freq : audio_tone_frequency(s_current_url, is_live);
    ESP_LOGI(TAG, "Buzzer: %s freq=%luHz", on ? "ON" : "OFF", (unsigned long)freq);
    if (on) {
        ledc_set_freq(LEDC_LOW_SPEED_MODE, LEDC_TIMER_1, freq);
    }
    ledc_set_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_1, on ? 768 : 0);
    ledc_update_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_1);
}

static void stub_buzzer_self_test(void)
{
    if (!s_buzzer_ready) {
        return;
    }

    ESP_LOGI(TAG, "Buzzer self-test start");
    ledc_set_freq(LEDC_LOW_SPEED_MODE, LEDC_TIMER_1, 880);
    ledc_set_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_1, 768);
    ledc_update_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_1);
    vTaskDelay(pdMS_TO_TICKS(120));

    ledc_set_freq(LEDC_LOW_SPEED_MODE, LEDC_TIMER_1, 1319);
    ledc_set_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_1, 768);
    ledc_update_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_1);
    vTaskDelay(pdMS_TO_TICKS(120));

    ledc_set_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_1, 0);
    ledc_update_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_1);
    ESP_LOGI(TAG, "Buzzer self-test end");
}

static void hardware_audio_self_test(void)
{
    if (!bsp_codec_is_ready()) {
        return;
    }

    ESP_LOGI(TAG, "Hardware audio self-test start");
    hardware_tone_write(880, 120);
    hardware_tone_write(1319, 120);
    ESP_LOGI(TAG, "Hardware audio self-test end");
}

esp_err_t bbc_audio_init(void)
{
    stub_buzzer_init();
    stub_buzzer_self_test();
    hardware_audio_self_test();
    if (s_hw_tone_task == NULL) {
        xTaskCreatePinnedToCore(hardware_tone_task, "audio_tone", 4096, NULL, 4, &s_hw_tone_task, 1);
    }
    bsp_codec_set_volume(70);
    ESP_LOGI(TAG, "Audio pipeline ready (Wokwi buzzer on GPIO%d, hardware codec %s, ADF %s)",
             AUDIO_STUB_BUZZER_GPIO,
             bsp_codec_is_ready() ? "enabled" : "unavailable",
#if BBC_HAS_ADF
             "enabled");
#else
             "disabled");
#endif
    return ESP_OK;
}

esp_err_t bbc_audio_play_url(const char *url, bool is_live)
{
    strncpy(s_current_url, url, sizeof(s_current_url) - 1);
    s_current_url[sizeof(s_current_url) - 1] = '\0';

    s_use_adf = false;
#if BBC_HAS_ADF
    if (bsp_codec_is_ready()) {
        if (adf_start_stream(url, is_live) == ESP_OK) {
            s_use_adf = true;
        } else {
            ESP_LOGW(TAG, "ADF stream start failed, falling back to tone playback");
        }
    }
#endif

    s_playing = true;
    s_is_live = is_live;
    s_hw_tone_freq = audio_tone_frequency(url, is_live);
    ESP_LOGI(TAG, "Playing %s: %.80s...", is_live ? "live stream" : "episode", url);

    if (!s_use_adf) {
        stub_buzzer_set(true, is_live);
        if (s_hw_tone_task != NULL) {
            xTaskNotifyGive(s_hw_tone_task);
        }
    }

    return ESP_OK;
}

esp_err_t bbc_audio_stop(void)
{
    s_playing = false;
    ESP_LOGI(TAG, "Stopped");

#if BBC_HAS_ADF
    if (s_use_adf) {
        adf_stop_locked();
    }
#endif

    if (!s_use_adf) {
        stub_buzzer_set(false, s_is_live);
        if (s_hw_tone_task != NULL) {
            xTaskNotifyGive(s_hw_tone_task);
        }
    }

    s_use_adf = false;
    return ESP_OK;
}

esp_err_t bbc_audio_toggle(void)
{
    if (s_is_live) return ESP_OK;  /* live streams cannot be paused */

#if BBC_HAS_ADF
    if (s_use_adf && s_adf.pipeline != NULL) {
        if (s_playing) {
            audio_pipeline_pause(s_adf.pipeline);
        } else {
            audio_pipeline_resume(s_adf.pipeline);
        }
        s_playing = !s_playing;
        ESP_LOGI(TAG, "%s", s_playing ? "Resumed" : "Paused");
        return ESP_OK;
    }
#endif

    s_playing = !s_playing;
    ESP_LOGI(TAG, "%s", s_playing ? "Resumed" : "Paused");
    stub_buzzer_set(s_playing, s_is_live);
    if (s_playing && s_hw_tone_task != NULL) {
        xTaskNotifyGive(s_hw_tone_task);
    }
    return ESP_OK;
}

bool bbc_audio_is_playing(void) { return s_playing; }
bool bbc_audio_is_live(void)    { return s_is_live;  }

esp_err_t bbc_audio_set_volume(int percent)
{
    return bsp_codec_set_volume(percent);
}
