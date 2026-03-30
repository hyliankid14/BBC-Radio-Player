#include "bsp_codec.h"
#include "driver/i2s_std.h"
#include "driver/gpio.h"
#include "esp_codec_dev.h"
#include "esp_codec_dev_defaults.h"
#include "esp_log.h"

static const char *TAG    = "bsp_codec";
static i2s_chan_handle_t      s_tx_chan   = NULL;
static esp_codec_dev_handle_t s_codec_dev = NULL;
static bool                   s_ready     = false;

esp_err_t bsp_codec_init(i2c_master_bus_handle_t bus_handle)
{
    /* In Wokwi there is no ES8311 on I2C, so skip codec and I2S bring-up early. */
    esp_err_t probe = i2c_master_probe(bus_handle, ES8311_CODEC_DEFAULT_ADDR, 100);
    if (probe != ESP_OK) {
        ESP_LOGW(TAG, "ES8311 not detected on I2C addr 0x%02x — audio codec disabled",
                 ES8311_CODEC_DEFAULT_ADDR);
        return ESP_OK;
    }

    /* PA enable pin — drive low during init to avoid pop noise */
    gpio_config_t pa_cfg = {
        .pin_bit_mask = (1ULL << BSP_PA_CTRL),
        .mode         = GPIO_MODE_OUTPUT,
        .pull_up_en   = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
        .intr_type    = GPIO_INTR_DISABLE,
    };
    gpio_config(&pa_cfg);
    gpio_set_level(BSP_PA_CTRL, 0);

    /* I2S TX channel (output to DAC) */
    i2s_chan_config_t chan_cfg = I2S_CHANNEL_DEFAULT_CONFIG(BSP_I2S_NUM, I2S_ROLE_MASTER);
    chan_cfg.auto_clear_after_cb = true;

    esp_err_t ret = i2s_new_channel(&chan_cfg, &s_tx_chan, NULL);
    if (ret != ESP_OK) {
        ESP_LOGW(TAG, "I2S channel create failed — audio disabled");
        return ESP_OK;   /* non-fatal */
    }

    i2s_std_config_t std_cfg = {
        .clk_cfg  = I2S_STD_CLK_DEFAULT_CONFIG(BSP_AUDIO_SAMPLE_RATE),
        .slot_cfg = I2S_STD_MSB_SLOT_DEFAULT_CONFIG(I2S_DATA_BIT_WIDTH_16BIT,
                                                     I2S_SLOT_MODE_STEREO),
        .gpio_cfg = {
            .mclk = BSP_I2S_MCLK,
            .bclk = BSP_I2S_BCLK,
            .ws   = BSP_I2S_LRCK,
            .dout = BSP_I2S_DOUT,
            .din  = I2S_GPIO_UNUSED,
        },
    };
    std_cfg.clk_cfg.mclk_multiple = I2S_MCLK_MULTIPLE_256;

    ret = i2s_channel_init_std_mode(s_tx_chan, &std_cfg);
    if (ret != ESP_OK) {
        ESP_LOGW(TAG, "I2S std mode init failed — audio disabled");
        i2s_del_channel(s_tx_chan);
        s_tx_chan = NULL;
        return ESP_OK;
    }

    i2s_channel_enable(s_tx_chan);

    audio_codec_i2s_cfg_t i2s_cfg = {
        .port = BSP_I2S_NUM,
        .tx_handle = s_tx_chan,
    };
    const audio_codec_data_if_t *data_if = audio_codec_new_i2s_data(&i2s_cfg);
    if (data_if == NULL) {
        ESP_LOGW(TAG, "I2S data interface create failed — codec output disabled");
        return ESP_OK;
    }

    audio_codec_i2c_cfg_t i2c_cfg = {
        .port = 0,
        .addr = ES8311_CODEC_DEFAULT_ADDR,
        .bus_handle = bus_handle,
    };
    const audio_codec_ctrl_if_t *ctrl_if = audio_codec_new_i2c_ctrl(&i2c_cfg);
    if (ctrl_if == NULL) {
        ESP_LOGW(TAG, "I2C codec control create failed — codec output disabled");
        return ESP_OK;
    }

    const audio_codec_gpio_if_t *gpio_if = audio_codec_new_gpio();
    if (gpio_if == NULL) {
        ESP_LOGW(TAG, "Codec GPIO interface create failed — codec output disabled");
        return ESP_OK;
    }

    es8311_codec_cfg_t es8311_cfg = {
        .codec_mode = ESP_CODEC_DEV_WORK_MODE_DAC,
        .ctrl_if = ctrl_if,
        .gpio_if = gpio_if,
        .pa_pin = BSP_PA_CTRL,
        .pa_reverted = false,
        .master_mode = false,
        .use_mclk = true,
        .digital_mic = false,
        .invert_mclk = false,
        .invert_sclk = false,
        .mclk_div = 256,
    };
    const audio_codec_if_t *codec_if = es8311_codec_new(&es8311_cfg);
    if (codec_if == NULL) {
        ESP_LOGW(TAG, "ES8311 interface create failed — codec output disabled");
        return ESP_OK;
    }

    esp_codec_dev_cfg_t dev_cfg = {
        .dev_type = ESP_CODEC_DEV_TYPE_OUT,
        .codec_if = codec_if,
        .data_if = data_if,
    };
    s_codec_dev = esp_codec_dev_new(&dev_cfg);
    if (s_codec_dev == NULL) {
        ESP_LOGW(TAG, "ES8311 device create failed — codec output disabled");
        return ESP_OK;
    }

    esp_codec_dev_sample_info_t fs = {
        .sample_rate = BSP_AUDIO_SAMPLE_RATE,
        .channel = BSP_AUDIO_CHANNELS,
        .bits_per_sample = BSP_AUDIO_BITS,
    };
    int codec_ret = esp_codec_dev_open(s_codec_dev, &fs);
    if (codec_ret != ESP_CODEC_DEV_OK) {
        ESP_LOGW(TAG, "ES8311 open failed (%d) — likely absent in Wokwi", codec_ret);
        esp_codec_dev_delete(s_codec_dev);
        s_codec_dev = NULL;
        return ESP_OK;
    }

    esp_codec_dev_set_out_vol(s_codec_dev, 70);
    s_ready = true;
    ESP_LOGI(TAG, "ES8311 codec ready (%d Hz, %d-bit, %d ch)",
             BSP_AUDIO_SAMPLE_RATE, BSP_AUDIO_BITS, BSP_AUDIO_CHANNELS);
    return ESP_OK;
}

esp_err_t bsp_codec_set_volume(int percent)
{
    if (!s_ready || s_codec_dev == NULL) {
        return ESP_OK;
    }
    if (percent < 0) percent = 0;
    if (percent > 100) percent = 100;
    esp_codec_dev_set_out_vol(s_codec_dev, percent);
    return ESP_OK;
}

bool bsp_codec_is_ready(void)
{
    return s_ready && s_codec_dev != NULL;
}

esp_err_t bsp_codec_write(const void *data, size_t bytes, size_t *written)
{
    if (!s_ready || s_codec_dev == NULL) {
        if (written) *written = bytes;   /* pretend it worked */
        return ESP_OK;
    }
    int ret = esp_codec_dev_write(s_codec_dev, (void *)data, (int)bytes);
    size_t w = ret == ESP_CODEC_DEV_OK ? bytes : 0;
    if (written) *written = w;
    return ret == ESP_CODEC_DEV_OK ? ESP_OK : ESP_FAIL;
}
