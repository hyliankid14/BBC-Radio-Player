#include "wifi_portal.h"

#include <string.h>
#include <stdlib.h>
#include <ctype.h>

#include "esp_log.h"
#include "esp_system.h"
#include "esp_wifi.h"
#include "esp_netif.h"
#include "esp_http_server.h"
#include "esp_timer.h"

#include "audio/bbc_audio.h"
#include "wifi_settings.h"

static const char *TAG = "wifi_portal";

#define PORTAL_AP_SSID    "BBCRadio-Setup"
#define POST_BUF_SIZE     512

static httpd_handle_t  s_server   = NULL;
static bool            s_running  = false;
static esp_netif_t    *s_ap_netif = NULL;

/* ── HTML pages ────────────────────────────────────────────────────── */

static const char HTML_FORM[] =
    "<!DOCTYPE html><html><head>"
    "<meta charset='utf-8'>"
    "<meta name='viewport' content='width=device-width,initial-scale=1'>"
    "<title>BBC Radio Wi-Fi Setup</title>"
    "<style>"
    "body{font-family:sans-serif;background:#111;color:#fff;"
          "padding:24px;max-width:440px;margin:0 auto}"
    "h1{color:#e00;margin-bottom:4px}"
    "p{color:#aaa;margin-bottom:24px}"
    "label{display:block;margin-bottom:6px;font-size:15px;color:#bbb}"
    "input{width:100%;padding:12px;font-size:16px;margin-bottom:20px;"
          "border:1px solid #444;border-radius:6px;"
          "background:#222;color:#fff;box-sizing:border-box}"
    "button{width:100%;padding:14px;font-size:18px;font-weight:bold;"
           "background:#e00;color:#fff;border:none;border-radius:6px;"
           "cursor:pointer}"
    "</style></head><body>"
    "<h1>BBC Radio Player</h1>"
    "<p>Enter your Wi-Fi credentials then tap Save.</p>"
    "<form method='post' action='/save'>"
    "<label>Wi-Fi Network (SSID)</label>"
    "<input name='ssid' type='text'"
           " autocomplete='off' autocorrect='off'"
           " autocapitalize='none' spellcheck='false'>"
    "<label>Password (leave blank if open network)</label>"
    "<input name='password' type='password'"
           " autocomplete='current-password'>"
    "<button type='submit'>Save &amp; Connect</button>"
    "</form></body></html>";

static const char HTML_SUCCESS[] =
    "<!DOCTYPE html><html><head><meta charset='utf-8'>"
    "<title>Saved</title>"
    "<style>"
    "body{font-family:sans-serif;background:#111;color:#fff;"
          "padding:24px;max-width:440px;margin:0 auto;text-align:center}"
    "h1{color:#0c0}"
    "p{color:#aaa}"
    "small{color:#666}"
    "</style></head><body>"
    "<h1>&#10003; Saved!</h1>"
    "<p>BBC Radio Player is rebooting and connecting to your network.</p>"
    "<p><small>You can close this page.</small></p>"
    "</body></html>";

static const char HTML_ERROR[] =
    "<!DOCTYPE html><html><head><meta charset='utf-8'><title>Error</title>"
    "<style>body{font-family:sans-serif;background:#111;color:#fff;padding:24px}"
    "a{color:#e00}</style></head><body>"
    "<h1>SSID is required</h1>"
    "<p><a href='/'>Go back</a></p>"
    "</body></html>";

/* ── URL decoding ──────────────────────────────────────────────────── */

static void url_decode(char *dst, const char *src, size_t dst_len)
{
    size_t di = 0;
    for (size_t i = 0; src[i] && di + 1 < dst_len; i++) {
        if (src[i] == '%' &&
            isxdigit((unsigned char)src[i + 1]) &&
            isxdigit((unsigned char)src[i + 2])) {
            char hex[3] = {src[i + 1], src[i + 2], '\0'};
            dst[di++] = (char)strtol(hex, NULL, 16);
            i += 2;
        } else if (src[i] == '+') {
            dst[di++] = ' ';
        } else {
            dst[di++] = src[i];
        }
    }
    dst[di] = '\0';
}

static void parse_form_field(const char *body, const char *key,
                              char *out, size_t out_len)
{
    size_t key_len = strlen(key);
    const char *p = body;
    while (*p) {
        if (strncmp(p, key, key_len) == 0 && p[key_len] == '=') {
            p += key_len + 1;
            const char *end = strchr(p, '&');
            size_t val_len = end ? (size_t)(end - p) : strlen(p);
            char tmp[POST_BUF_SIZE] = {0};
            if (val_len >= sizeof(tmp)) val_len = sizeof(tmp) - 1;
            memcpy(tmp, p, val_len);
            url_decode(out, tmp, out_len);
            return;
        }
        const char *next = strchr(p, '&');
        if (!next) break;
        p = next + 1;
    }
    out[0] = '\0';
}

/* ── HTTP handlers ─────────────────────────────────────────────────── */

static esp_err_t get_handler(httpd_req_t *req)
{
    httpd_resp_set_type(req, "text/html");
    httpd_resp_send(req, HTML_FORM, HTTPD_RESP_USE_STRLEN);
    return ESP_OK;
}

static void restart_cb(void *arg)
{
    (void)arg;
    esp_restart();
}

static esp_err_t save_handler(httpd_req_t *req)
{
    char body[POST_BUF_SIZE] = {0};
    int received = httpd_req_recv(req, body, sizeof(body) - 1);
    if (received <= 0) {
        httpd_resp_send_err(req, HTTPD_400_BAD_REQUEST, "Empty body");
        return ESP_FAIL;
    }
    body[received] = '\0';

    char ssid[33]     = {0};
    char password[65] = {0};
    parse_form_field(body, "ssid",     ssid,     sizeof(ssid));
    parse_form_field(body, "password", password, sizeof(password));

    if (ssid[0] == '\0') {
        httpd_resp_set_type(req, "text/html");
        httpd_resp_send(req, HTML_ERROR, HTTPD_RESP_USE_STRLEN);
        return ESP_OK;
    }

    wifi_settings_save(ssid, password);
    ESP_LOGI(TAG, "Credentials saved for SSID: %s — rebooting", ssid);

    httpd_resp_set_type(req, "text/html");
    httpd_resp_send(req, HTML_SUCCESS, HTTPD_RESP_USE_STRLEN);

    /* Restart 1.5 s after response is delivered */
    esp_timer_handle_t t;
    esp_timer_create_args_t ta = {.callback = restart_cb, .name = "portal_rst"};
    if (esp_timer_create(&ta, &t) == ESP_OK) {
        esp_timer_start_once(t, 1500000ULL); /* 1.5 s in µs */
    } else {
        esp_restart(); /* fallback — response may not flush */
    }
    return ESP_OK;
}

/* ── Public API ────────────────────────────────────────────────────── */

esp_err_t wifi_portal_start(void)
{
    if (s_running) return ESP_OK;

    /* Stop any active audio stream before killing Wi-Fi */
    bbc_audio_stop();

    /* Stop Wi-Fi so we can change mode */
    esp_wifi_stop();

    /* Create AP netif once */
    if (!s_ap_netif) {
        s_ap_netif = esp_netif_create_default_wifi_ap();
        if (!s_ap_netif) {
            ESP_LOGE(TAG, "Failed to create AP netif");
            return ESP_FAIL;
        }
    }

    /* Switch to AP-only mode */
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_AP));

    wifi_config_t ap_cfg = {
        .ap = {
            .ssid           = PORTAL_AP_SSID,
            .ssid_len       = sizeof(PORTAL_AP_SSID) - 1,
            .channel        = 6,
            .password       = "",
            .max_connection = 4,
            .authmode       = WIFI_AUTH_OPEN,
        },
    };
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_AP, &ap_cfg));
    ESP_ERROR_CHECK(esp_wifi_start());

    /* Start HTTP server */
    httpd_config_t cfg = HTTPD_DEFAULT_CONFIG();
    cfg.lru_purge_enable = true;

    if (httpd_start(&s_server, &cfg) != ESP_OK) {
        ESP_LOGE(TAG, "Failed to start HTTP server");
        return ESP_FAIL;
    }

    static const httpd_uri_t uri_get = {
        .uri     = "/",
        .method  = HTTP_GET,
        .handler = get_handler,
    };
    static const httpd_uri_t uri_save = {
        .uri     = "/save",
        .method  = HTTP_POST,
        .handler = save_handler,
    };
    httpd_register_uri_handler(s_server, &uri_get);
    httpd_register_uri_handler(s_server, &uri_save);

    s_running = true;
    ESP_LOGI(TAG, "Portal running — join '%s', visit http://192.168.4.1",
             PORTAL_AP_SSID);
    return ESP_OK;
}

bool wifi_portal_is_running(void)
{
    return s_running;
}
