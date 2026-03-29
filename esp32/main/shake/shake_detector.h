#pragma once

#include "esp_err.h"
#include "bsp_imu.h"

/** Callback fired when a shake is detected. Called from a FreeRTOS task. */
typedef void (*shake_callback_t)(void);

/**
 * Start the shake-detector task.
 * Polls the QMI8658 at 50 Hz; if |g| exceeds SHAKE_THRESHOLD_G for at least
 * SHAKE_DURATION_MS, fires @p cb after a SHAKE_DEBOUNCE_MS cooldown.
 *
 * If the IMU is absent (Wokwi simulation), the task exits immediately.
 */
esp_err_t shake_detector_start(shake_callback_t cb);
