#include "wifi_settings.h"

#include <string.h>
#include "nvs.h"
#include "nvs_flash.h"
#include "config.h"

#define WIFI_NAMESPACE "wifi_cfg"
#define WIFI_KEY_SSID  "ssid"
#define WIFI_KEY_PASS  "pass"

static void copy_default_wifi(char *ssid, size_t ssid_len, char *password, size_t password_len)
{
    if (ssid && ssid_len > 0) {
        strlcpy(ssid, WIFI_SSID, ssid_len);
    }
    if (password && password_len > 0) {
        strlcpy(password, WIFI_PASSWORD, password_len);
    }
}

bool wifi_settings_load(char *ssid, size_t ssid_len, char *password, size_t password_len)
{
    if (!ssid || ssid_len == 0 || !password || password_len == 0) {
        return false;
    }

    nvs_handle_t nvs = 0;
    if (nvs_open(WIFI_NAMESPACE, NVS_READONLY, &nvs) != ESP_OK) {
        ssid[0] = '\0';
        password[0] = '\0';
        return false;
    }

    esp_err_t ssid_err = nvs_get_str(nvs, WIFI_KEY_SSID, ssid, &ssid_len);
    esp_err_t pass_err = nvs_get_str(nvs, WIFI_KEY_PASS, password, &password_len);
    nvs_close(nvs);

    if (ssid_err != ESP_OK || pass_err != ESP_OK || ssid[0] == '\0') {
        ssid[0] = '\0';
        password[0] = '\0';
        return false;
    }
    return true;
}

void wifi_settings_get_boot(char *ssid, size_t ssid_len, char *password, size_t password_len)
{
    if (!wifi_settings_load(ssid, ssid_len, password, password_len)) {
        copy_default_wifi(ssid, ssid_len, password, password_len);
    }
}

esp_err_t wifi_settings_save(const char *ssid, const char *password)
{
    if (!ssid || ssid[0] == '\0' || !password) {
        return ESP_ERR_INVALID_ARG;
    }

    nvs_handle_t nvs = 0;
    esp_err_t err = nvs_open(WIFI_NAMESPACE, NVS_READWRITE, &nvs);
    if (err != ESP_OK) {
        return err;
    }

    err = nvs_set_str(nvs, WIFI_KEY_SSID, ssid);
    if (err == ESP_OK) {
        err = nvs_set_str(nvs, WIFI_KEY_PASS, password);
    }
    if (err == ESP_OK) {
        err = nvs_commit(nvs);
    }
    nvs_close(nvs);
    return err;
}
