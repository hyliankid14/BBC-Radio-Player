#pragma once

#include <stdbool.h>
#include <stddef.h>
#include "esp_err.h"

#ifdef __cplusplus
extern "C" {
#endif

bool wifi_settings_load(char *ssid, size_t ssid_len, char *password, size_t password_len);
void wifi_settings_get_boot(char *ssid, size_t ssid_len, char *password, size_t password_len);
esp_err_t wifi_settings_save(const char *ssid, const char *password);

#ifdef __cplusplus
}
#endif
