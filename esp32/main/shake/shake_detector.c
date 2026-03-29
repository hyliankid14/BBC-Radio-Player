#include "shake_detector.h"
#include "bsp_imu.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <math.h>

static const char *TAG = "shake";

/* Tune these to taste – match the Android app's 2.7 G / 1000 ms values. */
#define SHAKE_THRESHOLD_G  2.7f   /* total acceleration (g) to count as a shake  */
#define SHAKE_DURATION_MS  100    /* must exceed threshold for this long           */
#define SHAKE_DEBOUNCE_MS  1000   /* ignore further shakes for this long after one */
#define POLL_PERIOD_MS     20     /* 50 Hz polling rate                             */

static shake_callback_t s_cb;

static void shake_task(void *arg)
{
    bsp_imu_data_t data;
    esp_err_t err = bsp_imu_read(&data);
    if (err == ESP_ERR_NOT_FOUND || err == ESP_ERR_INVALID_STATE) {
        ESP_LOGW(TAG, "IMU not present – shake detection disabled (normal in Wokwi)");
        vTaskDelete(NULL);
        return;
    }

    TickType_t over_threshold_since = 0;
    bool       in_shake             = false;
    TickType_t debounce_until       = 0;

    while (true) {
        vTaskDelay(pdMS_TO_TICKS(POLL_PERIOD_MS));

        if (bsp_imu_read(&data) != ESP_OK) continue;

        float g = sqrtf((float)data.ax * data.ax +
                        (float)data.ay * data.ay +
                        (float)data.az * data.az);

        /* QMI8658 default full-scale ±8 g, 16-bit → 1 mg per LSB factor depends
         * on the configured range.  After bsp_imu_init() the scale is ±8 g with
         * a sensitivity of 4096 LSB/g. */
        float g_real = g / 4096.0f;

        TickType_t now = xTaskGetTickCount();

        if (g_real > SHAKE_THRESHOLD_G) {
            if (!in_shake) {
                in_shake             = true;
                over_threshold_since = now;
            } else {
                uint32_t duration = pdTICKS_TO_MS(now - over_threshold_since);
                if (duration >= SHAKE_DURATION_MS && now >= debounce_until) {
                    debounce_until = now + pdMS_TO_TICKS(SHAKE_DEBOUNCE_MS);
                    in_shake       = false;
                    if (s_cb) s_cb();
                }
            }
        } else {
            in_shake = false;
        }
    }
}

esp_err_t shake_detector_start(shake_callback_t cb)
{
    s_cb = cb;
    BaseType_t rc = xTaskCreate(shake_task, "shake", 3072, NULL, 5, NULL);
    return (rc == pdPASS) ? ESP_OK : ESP_FAIL;
}
