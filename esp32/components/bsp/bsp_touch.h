#pragma once

#include "esp_err.h"
#include "driver/gpio.h"
#include "driver/i2c_master.h"
#include "esp_lcd_touch.h"

/* ── CST816S touch controller (I2C) ─────────────────────────────── */
#define BSP_TOUCH_INT_GPIO   GPIO_NUM_48
#define BSP_TOUCH_RST_GPIO   GPIO_NUM_47

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Initialise the CST816S touch controller.
 *
 * On success, *touch_handle is populated. Returns ESP_FAIL (not a fatal
 * error) if the chip is not detected — this happens in Wokwi where no
 * I2C touch IC is present; the caller should tolerate a NULL handle.
 */
esp_err_t bsp_touch_init(i2c_master_bus_handle_t bus_handle,
                          esp_lcd_touch_handle_t *touch_handle);

#ifdef __cplusplus
}
#endif
