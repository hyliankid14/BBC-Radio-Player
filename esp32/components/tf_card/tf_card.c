#include "tf_card.h"
#include "driver/sdmmc_host.h"
#include "sdmmc_cmd.h"
#include "esp_vfs_fat.h"
#include "esp_log.h"

static const char *TAG    = "tf_card";
static bool        s_mounted = false;

esp_err_t tf_card_mount(void)
{
    esp_vfs_fat_sdmmc_mount_config_t mount_cfg = {
        .format_if_mount_failed = false,
        .max_files              = 8,
        .allocation_unit_size   = 16 * 1024,
    };

    sdmmc_host_t host = SDMMC_HOST_DEFAULT();
    host.max_freq_khz = SDMMC_FREQ_DEFAULT;

    sdmmc_slot_config_t slot_cfg = SDMMC_SLOT_CONFIG_DEFAULT();
    slot_cfg.clk  = BSP_SD_CLK;
    slot_cfg.cmd  = BSP_SD_CMD;
    slot_cfg.d0   = BSP_SD_D0;
    slot_cfg.d1   = BSP_SD_D1;
    slot_cfg.d2   = BSP_SD_D2;
    slot_cfg.d3   = BSP_SD_D3;
    slot_cfg.width = 4;
    slot_cfg.flags |= SDMMC_SLOT_FLAG_INTERNAL_PULLUP;

    sdmmc_card_t *card;
    esp_err_t ret = esp_vfs_fat_sdmmc_mount(BSP_SD_MOUNT_POINT, &host, &slot_cfg,
                                             &mount_cfg, &card);
    if (ret != ESP_OK) {
        ESP_LOGW(TAG, "TF card mount failed (0x%x) — continuing without card", ret);
        return ESP_OK;   /* non-fatal */
    }

    s_mounted = true;
    ESP_LOGI(TAG, "TF card mounted at %s (%.1f MB)",
             BSP_SD_MOUNT_POINT,
             (float)((uint64_t)card->csd.capacity * card->csd.sector_size) / (1024 * 1024));
    return ESP_OK;
}

esp_err_t tf_card_unmount(void)
{
    if (!s_mounted) return ESP_OK;
    esp_err_t ret = esp_vfs_fat_sdcard_unmount(BSP_SD_MOUNT_POINT, NULL);
    if (ret == ESP_OK) s_mounted = false;
    return ret;
}

bool tf_card_is_mounted(void)
{
    return s_mounted;
}
