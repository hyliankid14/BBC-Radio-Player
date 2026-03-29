#pragma once

#include "esp_err.h"
#include "driver/i2c_master.h"

/* ── QMI8658 6-axis IMU (I2C) ────────────────────────────────────── */
/* Two possible addresses: 0x6A (SA0=L) or 0x6B (SA0=H). Waveshare    */
/* boards typically wire SA0 high → 0x6B.                             */
#define BSP_IMU_I2C_ADDR_PRIMARY   0x6B
#define BSP_IMU_I2C_ADDR_SECONDARY 0x6A

typedef struct {
    float ax, ay, az;   /* accelerometer, in g units */
    float gx, gy, gz;   /* gyroscope, in deg/s       */
} bsp_imu_data_t;

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Initialise QMI8658 accelerometer + gyroscope.
 * Returns ESP_OK even if the chip is absent (Wokwi); subsequent
 * bsp_imu_read() calls will return zeroed data.
 */
esp_err_t bsp_imu_init(i2c_master_bus_handle_t bus_handle);

/**
 * Read latest accelerometer and gyroscope values.
 * Returns ESP_ERR_NOT_FOUND if IMU is absent (Wokwi).
 */
esp_err_t bsp_imu_read(bsp_imu_data_t *out);

#ifdef __cplusplus
}
#endif
