#include "bsp_touch.h"
#include "esp_lcd_touch_cst816s.h"
#include "esp_lcd_panel_io.h"
#include "esp_log.h"

static const char *TAG = "bsp_touch";

esp_err_t bsp_touch_init(i2c_master_bus_handle_t bus_handle,
                          esp_lcd_touch_handle_t *touch_handle)
{
    *touch_handle = NULL;

    esp_lcd_panel_io_handle_t     touch_io = NULL;
    esp_lcd_panel_io_i2c_config_t io_cfg   = {
        .dev_addr                  = ESP_LCD_TOUCH_IO_I2C_CST816S_ADDRESS,
        .control_phase_bytes       = 1,
        .dc_bit_offset             = 0,
        .lcd_cmd_bits              = 8,
        .flags.disable_control_phase = 1,
        .scl_speed_hz              = 400000,
    };

    esp_err_t ret = esp_lcd_new_panel_io_i2c(bus_handle, &io_cfg, &touch_io);
    if (ret != ESP_OK) {
        ESP_LOGW(TAG, "Touch I2C IO create failed (0x%x) — running without touch", ret);
        return ESP_OK;   /* non-fatal */
    }

    esp_lcd_touch_config_t tp_cfg = {
        .x_max            = BSP_TOUCH_INT_GPIO,   /* reused field — set via x_max */
        .y_max            = 240,
        .rst_gpio_num     = BSP_TOUCH_RST_GPIO,
        .int_gpio_num     = BSP_TOUCH_INT_GPIO,
        .flags.swap_xy    = 0,
        .flags.mirror_x   = 0,
        .flags.mirror_y   = 0,
    };
    /* x_max / y_max must match display resolution */
    tp_cfg.x_max = 240;
    tp_cfg.y_max = 240;

    ret = esp_lcd_touch_new_i2c_cst816s(touch_io, &tp_cfg, touch_handle);
    if (ret != ESP_OK) {
        ESP_LOGW(TAG, "CST816S init failed (0x%x) — running without touch", ret);
        *touch_handle = NULL;
        return ESP_OK;   /* non-fatal */
    }

    ESP_LOGI(TAG, "Touch initialised");
    return ESP_OK;
}
