#include "podcast_rankings.h"
#include "esp_http_client.h"
#include "esp_crt_bundle.h"
#include "esp_log.h"
#include "esp_heap_caps.h"
#include "cJSON.h"
#include <string.h>

static const char *TAG = "podcast_rankings";

#define GCS_POPULAR_URL \
    "https://storage.googleapis.com/bbc-radio-player-index-20260317-bc149e38/popular-podcasts.json"
#define GCS_NEW_URL \
    "https://storage.googleapis.com/bbc-radio-player-index-20260317-bc149e38/new-podcasts.json"

/* ── HTTP buffer (re-used from podcast_index.c's static helpers) ─── */
/* We implement a self-contained mini downloader here. */

static char *fetch_json(const char *url)
{
    /* Simple synchronous GET into a heap buffer (max 64 KB) */
    char *buf = heap_caps_malloc(65536, MALLOC_CAP_SPIRAM);
    if (!buf) {
        buf = malloc(65536);
    }
    if (!buf) return NULL;
    size_t len = 0;

    esp_http_client_config_t cfg = {
        .url                = url,
        .timeout_ms         = 8000,
        .buffer_size        = 2048,
        .crt_bundle_attach  = esp_crt_bundle_attach,
    };
    esp_http_client_handle_t c = esp_http_client_init(&cfg);
    if (!c) { heap_caps_free(buf); return NULL; }

    if (esp_http_client_open(c, 0) != ESP_OK) {
        esp_http_client_cleanup(c);
        heap_caps_free(buf);
        return NULL;
    }

    int content_len = esp_http_client_fetch_headers(c);
    (void)content_len;

    int r;
    while ((r = esp_http_client_read(c, buf + len, 65535 - len)) > 0)
        len += r;
    buf[len] = '\0';

    int status = esp_http_client_get_status_code(c);

    esp_http_client_close(c);
    esp_http_client_cleanup(c);

    if (status != 200) {
        free(buf);
        return NULL;
    }
    return buf;
}

esp_err_t podcast_rankings_apply(podcast_t *podcasts, size_t count)
{
    /* ── Popular ──────────────────────────────────────────────────── */
    ESP_LOGI(TAG, "Fetching popular-podcasts.json...");
    char *pop_json = fetch_json(GCS_POPULAR_URL);
    if (pop_json) {
        cJSON *root = cJSON_Parse(pop_json);
        free(pop_json);
        if (root) {
            /* Expected format: {"ranks": [{"id": "p01...", "rank": 1}, ...]} */
            cJSON *ranks = cJSON_GetObjectItemCaseSensitive(root, "ranks");
            if (!ranks) ranks = root;   /* fall back to root array */

            cJSON *item;
            cJSON_ArrayForEach(item, ranks) {
                cJSON *jid   = cJSON_GetObjectItemCaseSensitive(item, "id");
                cJSON *jrank = cJSON_GetObjectItemCaseSensitive(item, "rank");
                if (!cJSON_IsString(jid) || !cJSON_IsNumber(jrank)) continue;

                const char *pid = jid->valuestring;
                int rank        = (int)jrank->valuedouble;

                for (size_t i = 0; i < count; i++) {
                    if (strncmp(podcasts[i].id, pid, PODCAST_ID_MAX) == 0) {
                        podcasts[i].popularity_rank = rank;
                        break;
                    }
                }
            }
            cJSON_Delete(root);
        }
    } else {
        ESP_LOGW(TAG, "Failed to fetch popular-podcasts.json — rankings will be unsorted");
    }

    /* ── New podcasts ─────────────────────────────────────────────── */
    ESP_LOGI(TAG, "Fetching new-podcasts.json...");
    char *new_json = fetch_json(GCS_NEW_URL);
    if (new_json) {
        cJSON *root = cJSON_Parse(new_json);
        free(new_json);
        if (root) {
            /* Expected format: {"ids": ["p01...", "p02...", ...]} */
            cJSON *ids = cJSON_GetObjectItemCaseSensitive(root, "ids");
            if (!ids) ids = root;

            cJSON *jid;
            cJSON_ArrayForEach(jid, ids) {
                if (!cJSON_IsString(jid)) continue;
                const char *pid = jid->valuestring;
                for (size_t i = 0; i < count; i++) {
                    if (strncmp(podcasts[i].id, pid, PODCAST_ID_MAX) == 0) {
                        podcasts[i].is_new = true;
                        break;
                    }
                }
            }
            cJSON_Delete(root);
        }
    } else {
        ESP_LOGW(TAG, "Failed to fetch new-podcasts.json");
    }

    return ESP_OK;
}
