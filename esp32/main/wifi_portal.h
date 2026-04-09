#pragma once

#include <stdbool.h>
#include "esp_err.h"

/**
 * Start the Wi-Fi configuration portal.
 *
 * Stops any active audio, switches the radio to SoftAP mode (SSID:
 * "BBCRadio-Setup", open / no password), then starts a tiny HTTP server.
 * Visiting http://192.168.4.1 from a phone connected to that network
 * shows a web form where the user can enter new SSID / password.  On
 * submit the credentials are written to NVS and the device reboots.
 *
 * Returns ESP_OK if the portal started successfully.
 */
esp_err_t wifi_portal_start(void);

/** Returns true if the portal is currently running. */
bool wifi_portal_is_running(void);
