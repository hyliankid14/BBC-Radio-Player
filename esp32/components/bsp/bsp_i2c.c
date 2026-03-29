#include "bsp.h"
#include "esp_log.h"

static const char *TAG = "bsp_i2c";

i2c_master_bus_handle_t bsp_i2c_init(void)
{
    i2c_master_bus_config_t bus_cfg = {
        .i2c_port        = BSP_I2C_PORT,
        .sda_io_num      = BSP_I2C_SDA_GPIO,
        .scl_io_num      = BSP_I2C_SCL_GPIO,
        .clk_source      = I2C_CLK_SRC_DEFAULT,
        .glitch_ignore_cnt = 7,
        .flags.enable_internal_pullup = true,
    };

    i2c_master_bus_handle_t handle;
    ESP_ERROR_CHECK(i2c_new_master_bus(&bus_cfg, &handle));
    ESP_LOGI(TAG, "I2C bus initialised (SDA=%d SCL=%d)", BSP_I2C_SDA_GPIO, BSP_I2C_SCL_GPIO);
    return handle;
}
