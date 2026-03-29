#include "subscriptions.h"
#include "tf_card.h"
#include "cJSON.h"
#include "esp_log.h"
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

static const char *TAG = "subscriptions";

#define SUBS_FILE BSP_SD_MOUNT_POINT "/subscriptions.json"

static subscribed_podcast_t s_subs[SUBSCRIPTIONS_MAX];
static size_t               s_count = 0;

esp_err_t subscriptions_load(void)
{
    s_count = 0;

    if (!tf_card_is_mounted()) {
        ESP_LOGW(TAG, "TF card not mounted — no subscriptions loaded");
        return ESP_OK;
    }

    FILE *f = fopen(SUBS_FILE, "r");
    if (!f) {
        ESP_LOGI(TAG, "No subscriptions.json found on TF card");
        return ESP_OK;
    }

    fseek(f, 0, SEEK_END);
    long fsize = ftell(f);
    rewind(f);

    char *buf = malloc(fsize + 1);
    if (!buf) { fclose(f); return ESP_ERR_NO_MEM; }

    fread(buf, 1, fsize, f);
    buf[fsize] = '\0';
    fclose(f);

    cJSON *root = cJSON_Parse(buf);
    free(buf);

    if (!root) {
        ESP_LOGE(TAG, "Invalid JSON in subscriptions.json");
        return ESP_FAIL;
    }

    cJSON *arr = cJSON_GetObjectItemCaseSensitive(root, "subscribed");
    if (!arr || !cJSON_IsArray(arr)) {
        ESP_LOGW(TAG, "subscriptions.json missing 'subscribed' array");
        cJSON_Delete(root);
        return ESP_OK;
    }

    cJSON *item;
    cJSON_ArrayForEach(item, arr) {
        if (s_count >= SUBSCRIPTIONS_MAX) break;
        subscribed_podcast_t *sub = &s_subs[s_count];
        memset(sub, 0, sizeof(*sub));

        cJSON *jid  = cJSON_GetObjectItemCaseSensitive(item, "id");
        cJSON *jtit = cJSON_GetObjectItemCaseSensitive(item, "title");
        cJSON *jrss = cJSON_GetObjectItemCaseSensitive(item, "rss_url");

        if (cJSON_IsString(jid))  strncpy(sub->id,      jid->valuestring,  15);
        if (cJSON_IsString(jtit)) strncpy(sub->title,   jtit->valuestring, 95);
        if (cJSON_IsString(jrss)) strncpy(sub->rss_url, jrss->valuestring, 191);

        if (sub->rss_url[0] != '\0') s_count++;
    }

    cJSON_Delete(root);
    ESP_LOGI(TAG, "Loaded %zu subscriptions", s_count);
    return ESP_OK;
}

size_t subscriptions_count(void)
{
    return s_count;
}

const subscribed_podcast_t *subscriptions_get(size_t i)
{
    if (i >= s_count) return NULL;
    return &s_subs[i];
}
