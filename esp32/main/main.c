#include <string.h>
#include <ctype.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/event_groups.h"
#include "esp_system.h"
#include "esp_wifi.h"
#include "esp_event.h"
#include "esp_log.h"
#include "nvs_flash.h"

#include "bsp.h"
#include "bsp_display.h"
#include "bsp_touch.h"
#include "bsp_imu.h"
#include "lvgl_port.h"
#include "tf_card.h"

#include "data/stations.h"
#include "data/podcast_index.h"
#include "data/subscriptions.h"
#include "audio/playback_state.h"
#include "audio/bbc_audio.h"
#include "shake/shake_detector.h"
#include "ui/ui_manager.h"
#include "ui/screen_home.h"
#include "ui/screen_podcasts.h"

#include "config.h"   /* copy config.h.example → config.h and fill in credentials */

static const char *TAG = "main";

static void serial_input_task(void *arg)
{
    while (true) {
        int ch = fgetc(stdin);
        if (ch == EOF) {
            vTaskDelay(pdMS_TO_TICKS(20));
            continue;
        }
        if (ch == '\r' || ch == '\n') {
            continue;
        }

        ESP_LOGI(TAG, "Serial RX: '%c' (0x%02X)",
                 isprint((unsigned char)ch) ? ch : '.',
                 (unsigned int)(unsigned char)ch);
    }
}

/* ── WiFi ─────────────────────────────────────────────────────────────── */
#define WIFI_CONNECTED_BIT BIT0
#define WIFI_FAIL_BIT      BIT1
#define WIFI_MAX_RETRY     5

static EventGroupHandle_t s_wifi_events;
static int                s_wifi_retries = 0;

static void wifi_event_handler(void *arg, esp_event_base_t base,
                                int32_t id, void *data)
{
    if (base == WIFI_EVENT && id == WIFI_EVENT_STA_START) {
        esp_wifi_connect();
        return;
    }
    if (base == WIFI_EVENT && id == WIFI_EVENT_STA_DISCONNECTED) {
        if (s_wifi_retries < WIFI_MAX_RETRY) {
            esp_wifi_connect();
            s_wifi_retries++;
            ESP_LOGI(TAG, "WiFi retry %d/%d", s_wifi_retries, WIFI_MAX_RETRY);
        } else {
            xEventGroupSetBits(s_wifi_events, WIFI_FAIL_BIT);
            ESP_LOGW(TAG, "WiFi connection failed");
        }
        return;
    }
    if (base == IP_EVENT && id == IP_EVENT_STA_GOT_IP) {
        ip_event_got_ip_t *ev = (ip_event_got_ip_t *)data;
        ESP_LOGI(TAG, "Got IP: " IPSTR, IP2STR(&ev->ip_info.ip));
        s_wifi_retries = 0;
        xEventGroupSetBits(s_wifi_events, WIFI_CONNECTED_BIT);
    }
}

static bool wifi_connect(void)
{
    s_wifi_events = xEventGroupCreate();

    esp_netif_init();
    esp_event_loop_create_default();
    esp_netif_create_default_wifi_sta();

    wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&cfg));

    esp_event_handler_instance_t h_any, h_ip;
    ESP_ERROR_CHECK(esp_event_handler_instance_register(
            WIFI_EVENT, ESP_EVENT_ANY_ID,    wifi_event_handler, NULL, &h_any));
    ESP_ERROR_CHECK(esp_event_handler_instance_register(
            IP_EVENT,   IP_EVENT_STA_GOT_IP, wifi_event_handler, NULL, &h_ip));

    wifi_config_t wcfg = {
        .sta = {
            .ssid     = WIFI_SSID,
            .password = WIFI_PASSWORD,
            .threshold.authmode = WIFI_AUTH_WPA2_PSK,
        },
    };
    /* Allow open networks (e.g. Wokwi-GUEST has no password) */
    if (strlen(WIFI_PASSWORD) == 0) {
        wcfg.sta.threshold.authmode = WIFI_AUTH_OPEN;
    }

    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_STA, &wcfg));
    ESP_ERROR_CHECK(esp_wifi_start());

    EventBits_t bits = xEventGroupWaitBits(s_wifi_events,
            WIFI_CONNECTED_BIT | WIFI_FAIL_BIT, pdFALSE, pdFALSE,
            pdMS_TO_TICKS(30000));

    bool ok = (bits & WIFI_CONNECTED_BIT) != 0;
    ESP_LOGI(TAG, "WiFi %s", ok ? "connected" : "FAILED");
    return ok;
}

/* ── Podcast index background task ───────────────────────────────────── */
static void podcast_fetch_task(void *arg)
{
    /* podcast_index_fetch() internally calls podcast_rankings_apply() */
    podcast_index_fetch();
    /* Notify any open podcast screen */
    lv_async_call(screen_podcasts_refresh, NULL);
    vTaskDelete(NULL);
}

/* ── Shake callback ───────────────────────────────────────────────────── */
static void on_shake(void)
{
    podcast_t *p = podcast_index_random();
    if (!p) return;
    ESP_LOGI(TAG, "Shake! Random podcast: %s", p->title);
    podcast_fetch_episodes(p);
    size_t count;
    episode_t *eps = podcast_get_episodes(p, &count);
    if (eps && count > 0) {
        playback_play_episode(p, &eps[0]);
    }
}

/* ── Entry point ─────────────────────────────────────────────────────── */
void app_main(void)
{
    /* NVS (required by WiFi) */
    esp_err_t err = nvs_flash_init();
    if (err == ESP_ERR_NVS_NO_FREE_PAGES || err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ESP_ERROR_CHECK(nvs_flash_init());
    }

    /* I2C bus (shared by touch, IMU, codec) */
    i2c_master_bus_handle_t i2c_bus = bsp_i2c_init();

    /* IMU (non-fatal – absent in Wokwi) */
    bsp_imu_init(i2c_bus);

    /* Display */
    esp_lcd_panel_handle_t    panel  = NULL;
    esp_lcd_panel_io_handle_t io     = NULL;
    ESP_ERROR_CHECK(bsp_display_init(&panel, &io));
    ESP_ERROR_CHECK(bsp_display_brightness_init());
    ESP_ERROR_CHECK(bsp_display_brightness_set(80));
    ESP_LOGI(TAG, "Display stack ready");

    /* Touch (non-fatal – absent in Wokwi) */
    esp_lcd_touch_handle_t touch = NULL;
    bsp_touch_init(i2c_bus, &touch);

    /* Audio codec init is skipped in Wokwi to avoid I2S bring-up hangs. */
    ESP_LOGI(TAG, "Skipping codec init in emulator startup path");

    /* LVGL port */
    lv_disp_t *disp = NULL;
    ESP_ERROR_CHECK(bsp_lvgl_port_init(panel, io, touch, &disp));

    /* TF card for subscriptions (non-fatal) */
    tf_card_mount();

    /* Subscriptions from TF card */
    subscriptions_load();

    /* Playback state init */
    playback_state_init();

    /* Audio subsystem (ADF decode on hardware when available, tone fallback otherwise) */
    bbc_audio_init();

    /* Show home screen */
    if (bsp_lvgl_port_lock(100)) {
        ESP_LOGI(TAG, "Creating home screen");
        ui_manager_init();
        lv_obj_t *home = screen_home_create();
        ui_push_screen(home, LV_SCR_LOAD_ANIM_NONE);
        lv_refr_now(NULL);
        ESP_LOGI(TAG, "Home screen loaded");
        bsp_lvgl_port_unlock();
    } else {
        ESP_LOGW(TAG, "Could not acquire LVGL lock for home screen");
    }

    /* WiFi */
    bool wifi_ok = wifi_connect();

    /* Start podcast index download in background (only if WiFi connected) */
    if (wifi_ok) {
        xTaskCreate(podcast_fetch_task, "pod_fetch", 8192, NULL, 4, NULL);
    } else {
        ESP_LOGW(TAG, "No WiFi – podcast index not available");
    }

    /* Shake to play random podcast */
    shake_detector_start(on_shake);

    /* Helpful in Wokwi: confirms terminal input path is working. */
    xTaskCreate(serial_input_task, "serial_rx", 4096, NULL, 2, NULL);

    ESP_LOGI(TAG, "BBC Radio Player ready");
}
