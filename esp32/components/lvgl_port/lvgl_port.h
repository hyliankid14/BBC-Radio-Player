#pragma once

#include "esp_err.h"
#include "esp_lcd_panel_ops.h"
#include "esp_lcd_touch.h"
#include "lvgl.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Initialise the LVGL port: tick timer, display flush, and (optionally)
 * touch input.  touch_handle may be NULL (Wokwi / non-touch version).
 *
 * Populates *disp_out with the created LVGL display handle.
 */
esp_err_t bsp_lvgl_port_init(esp_lcd_panel_handle_t    panel_handle,
                              esp_lcd_panel_io_handle_t io_handle,
                              esp_lcd_touch_handle_t    touch_handle,
                              lv_disp_t               **disp_out);

/** Acquire the LVGL mutex (must be held before calling any lv_ function). */
bool bsp_lvgl_port_lock(uint32_t timeout_ms);

/** Release the LVGL mutex. */
void bsp_lvgl_port_unlock(void);

#ifdef __cplusplus
}
#endif
