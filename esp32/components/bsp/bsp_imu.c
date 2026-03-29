#include "bsp_imu.h"
#include "driver/i2c_master.h"
#include "esp_log.h"
#include <math.h>
#include <string.h>

static const char *TAG = "bsp_imu";

/* ── QMI8658 register map ─────────────────────────────────────────── */
#define QMI8658_REG_WHO_AM_I  0x00   /* expected: 0x05 */
#define QMI8658_REG_CTRL1     0x02
#define QMI8658_REG_CTRL2     0x03   /* accel: ODR + FS */
#define QMI8658_REG_CTRL3     0x04   /* gyro: ODR  + FS */
#define QMI8658_REG_CTRL7     0x08   /* accel+gyro enable */
#define QMI8658_REG_STATUS0   0x2E
#define QMI8658_REG_AX_L      0x35

/* ── CTRL2: accel 4g, 250Hz ODR ──────────────────────────────────── */
#define QMI8658_CTRL2_VAL     0x64   /* FS=4g, ODR=250Hz */
/* ── CTRL3: gyro 512dps, 250Hz ODR ──────────────────────────────── */
#define QMI8658_CTRL3_VAL     0x54
/* ── CTRL7: enable accel + gyro ──────────────────────────────────── */
#define QMI8658_CTRL7_VAL     0x03

/* Sensitivity: 4g FS → 8192 LSB/g; 512dps → 64 LSB/(deg/s) */
#define ACCEL_SENSITIVITY     8192.0f
#define GYRO_SENSITIVITY      64.0f

static i2c_master_dev_handle_t s_imu_dev = NULL;
static bool                    s_present  = false;

/* ── Low-level I2C helpers ─────────────────────────────────────────── */

static esp_err_t imu_reg_write(uint8_t reg, uint8_t val)
{
    uint8_t buf[2] = {reg, val};
    return i2c_master_transmit(s_imu_dev, buf, 2, 100);
}

static esp_err_t imu_reg_read(uint8_t reg, uint8_t *data, size_t len)
{
    esp_err_t ret = i2c_master_transmit(s_imu_dev, &reg, 1, 100);
    if (ret != ESP_OK) return ret;
    return i2c_master_receive(s_imu_dev, data, len, 100);
}

/* ── Public API ───────────────────────────────────────────────────── */

esp_err_t bsp_imu_init(i2c_master_bus_handle_t bus_handle)
{
    s_present = false;

    /* Try primary address first, then secondary */
    uint8_t addrs[] = {BSP_IMU_I2C_ADDR_PRIMARY, BSP_IMU_I2C_ADDR_SECONDARY};
    for (int i = 0; i < 2; i++) {
        i2c_device_config_t dev_cfg = {
            .dev_addr_length = I2C_ADDR_BIT_LEN_7,
            .device_address  = addrs[i],
            .scl_speed_hz    = 400000,
        };
        esp_err_t ret = i2c_master_bus_add_device(bus_handle, &dev_cfg, &s_imu_dev);
        if (ret != ESP_OK) continue;

        uint8_t who = 0;
        ret = imu_reg_read(QMI8658_REG_WHO_AM_I, &who, 1);
        if (ret == ESP_OK && who == 0x05) {
            s_present = true;
            break;
        }
        /* Wrong chip — remove device and try next address */
        i2c_master_bus_rm_device(s_imu_dev);
        s_imu_dev = NULL;
    }

    if (!s_present) {
        ESP_LOGW(TAG, "QMI8658 not found — shake detection disabled (expected in Wokwi)");
        return ESP_OK;   /* non-fatal */
    }

    /* Configure and enable accel + gyro */
    imu_reg_write(QMI8658_REG_CTRL1, 0x40);   /* SPI 4-wire disable, auto-increment */
    imu_reg_write(QMI8658_REG_CTRL2, QMI8658_CTRL2_VAL);
    imu_reg_write(QMI8658_REG_CTRL3, QMI8658_CTRL3_VAL);
    imu_reg_write(QMI8658_REG_CTRL7, QMI8658_CTRL7_VAL);

    ESP_LOGI(TAG, "QMI8658 initialised");
    return ESP_OK;
}

esp_err_t bsp_imu_read(bsp_imu_data_t *out)
{
    if (!s_present) {
        memset(out, 0, sizeof(*out));
        return ESP_ERR_NOT_FOUND;
    }

    uint8_t raw[12];   /* AX_L … AZ_H, GX_L … GZ_H */
    esp_err_t ret = imu_reg_read(QMI8658_REG_AX_L, raw, 12);
    if (ret != ESP_OK) return ret;

    int16_t ax = (int16_t)((raw[1]  << 8) | raw[0]);
    int16_t ay = (int16_t)((raw[3]  << 8) | raw[2]);
    int16_t az = (int16_t)((raw[5]  << 8) | raw[4]);
    int16_t gx = (int16_t)((raw[7]  << 8) | raw[6]);
    int16_t gy = (int16_t)((raw[9]  << 8) | raw[8]);
    int16_t gz = (int16_t)((raw[11] << 8) | raw[10]);

    out->ax = ax / ACCEL_SENSITIVITY;
    out->ay = ay / ACCEL_SENSITIVITY;
    out->az = az / ACCEL_SENSITIVITY;
    out->gx = gx / GYRO_SENSITIVITY;
    out->gy = gy / GYRO_SENSITIVITY;
    out->gz = gz / GYRO_SENSITIVITY;
    return ESP_OK;
}
