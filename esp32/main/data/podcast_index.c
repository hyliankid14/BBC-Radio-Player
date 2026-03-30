#include "podcast_index.h"
#include "podcast_rankings.h"
#include "esp_http_client.h"
#include "esp_crt_bundle.h"
#include "esp_log.h"
#include "esp_heap_caps.h"
#include "cJSON.h"
#include <stdlib.h>
#include <string.h>
#include <time.h>

static void *podcast_malloc(size_t bytes)
{
    void *p = heap_caps_malloc(bytes, MALLOC_CAP_SPIRAM);
    if (!p) {
        p = malloc(bytes);
    }
    return p;
}

static void *podcast_realloc(void *ptr, size_t bytes)
{
    void *p = heap_caps_realloc(ptr, bytes, MALLOC_CAP_SPIRAM);
    if (!p) {
        p = realloc(ptr, bytes);
    }
    return p;
}

/* strnstr is not available in ESP-IDF newlib; provide a portable version */
static const char *str_in_mem(const char *haystack, size_t hay_len,
                               const char *needle)
{
    size_t nlen = strlen(needle);
    if (nlen == 0) return haystack;
    if (hay_len < nlen) return NULL;
    for (size_t i = 0; i <= hay_len - nlen; i++) {
        if (memcmp(haystack + i, needle, nlen) == 0)
            return haystack + i;
    }
    return NULL;
}

static const char *TAG = "podcast_index";

/* ── BBC OPML feed ─────────────────────────────────────────────────── */
#define BBC_OPML_URL "https://www.bbc.co.uk/radio/opml/bbc_podcast_opml.xml"

/* ── Podcast storage in PSRAM ─────────────────────────────────────── */
#define MAX_PODCASTS  600

static podcast_t *s_podcasts  = NULL;
static size_t     s_count     = 0;
static bool       s_ready     = false;

/* ── HTTP helper: download URL into a heap buffer ─────────────────── */

typedef struct {
    char  *buf;
    size_t len;
    size_t cap;
} http_buf_t;

static esp_err_t http_event_handler(esp_http_client_event_t *evt)
{
    http_buf_t *b = (http_buf_t *)evt->user_data;
    if (evt->event_id == HTTP_EVENT_ON_DATA && b) {
        size_t needed = b->len + evt->data_len + 1;
        if (needed > b->cap) {
            size_t new_cap = needed + 65536;   /* grow in 64 KB chunks */
            char *tmp = podcast_realloc(b->buf, new_cap);
            if (!tmp) {
                ESP_LOGE(TAG, "OOM — realloc failed for %zu bytes", new_cap);
                return ESP_FAIL;
            }
            b->buf = tmp;
            b->cap = new_cap;
        }
        memcpy(b->buf + b->len, evt->data, evt->data_len);
        b->len += evt->data_len;
        b->buf[b->len] = '\0';
    }
    return ESP_OK;
}

static esp_err_t http_get(const char *url, http_buf_t *out)
{
    esp_http_client_config_t cfg = {
        .url            = url,
        .event_handler  = http_event_handler,
        .user_data      = out,
        .timeout_ms     = 15000,
        .buffer_size    = 4096,
        .crt_bundle_attach = esp_crt_bundle_attach,
    };
    esp_http_client_handle_t client = esp_http_client_init(&cfg);
    if (!client) return ESP_FAIL;

    esp_err_t ret = esp_http_client_perform(client);
    if (ret == ESP_OK) {
        int status = esp_http_client_get_status_code(client);
        if (status != 200) {
            ESP_LOGE(TAG, "HTTP %d for %s", status, url);
            ret = ESP_FAIL;
        }
    }
    esp_http_client_cleanup(client);
    return ret;
}

/* ── Minimal OPML parser ──────────────────────────────────────────── */
/*
 * Parses attributes from an <outline> tag in the BBC OPML.
 * Looks for: text="..." xmlUrl="..."
 * BBC feeds use lowercase `xmlUrl` and `text`.
 *
 * BBC podcast IDs (pXXXXXXXX) are embedded in the RSS URL:
 *   https://feeds.bbci.co.uk/podcasts/p01234567/rss.xml
 */

static const char *attr_find(const char *tag_start, const char *tag_end,
                              const char *attr_name, char *out, size_t out_len)
{
    size_t name_len = strlen(attr_name);
    const char *p = tag_start;
    while (p < tag_end) {
        /* Find attr_name=" */
        p = strstr(p, attr_name);
        if (!p || p >= tag_end) return NULL;
        p += name_len;
        if (p >= tag_end || *p != '=') { p++; continue; }
        p++;   /* skip '=' */
        char quote = *p++;
        if (quote != '"' && quote != '\'') continue;
        const char *val_start = p;
        const char *val_end   = memchr(p, quote, (size_t)(tag_end - p));
        if (!val_end) return NULL;
        size_t val_len = (size_t)(val_end - val_start);
        if (val_len >= out_len) val_len = out_len - 1;
        memcpy(out, val_start, val_len);
        out[val_len] = '\0';
        return out;
    }
    return NULL;
}

static void extract_pid_from_rss(const char *rss_url, char *pid_out, size_t pid_len)
{
    /* e.g. https://feeds.bbci.co.uk/podcasts/p01234567/rss.xml */
    pid_out[0] = '\0';
    const char *p = strstr(rss_url, "/podcasts/");
    if (!p) return;
    p += strlen("/podcasts/");
    const char *end = strchr(p, '/');
    size_t len = end ? (size_t)(end - p) : strlen(p);
    if (len >= pid_len) len = pid_len - 1;
    memcpy(pid_out, p, len);
    pid_out[len] = '\0';
}

static esp_err_t parse_opml(const char *xml, size_t xml_len)
{
    if (!s_podcasts) {
        s_podcasts = podcast_malloc(MAX_PODCASTS * sizeof(podcast_t));
        if (!s_podcasts) { ESP_LOGE(TAG, "OOM allocating podcast array"); return ESP_ERR_NO_MEM; }
    }
    s_count = 0;

    const char *p = xml;
    while (s_count < MAX_PODCASTS) {
        /* Find start of an <outline ... type="rss" ... > tag   */
        const char *tag = strstr(p, "<outline ");
        if (!tag) break;

        const char *tag_end = strchr(tag, '>');
        if (!tag_end) break;

        /* Only process RSS outlines */
        size_t tag_len = (size_t)(tag_end - tag);
        if (str_in_mem(tag, tag_len, "type=\"rss\"") ||
            str_in_mem(tag, tag_len, "xmlUrl=")) {

            podcast_t *pod = &s_podcasts[s_count];
            memset(pod, 0, sizeof(*pod));

            char text[PODCAST_TITLE_MAX];
            char rss[PODCAST_URL_MAX];

            if (attr_find(tag, tag_end, "text",   text, sizeof(text)) &&
                attr_find(tag, tag_end, "xmlUrl", rss,  sizeof(rss))) {
                strncpy(pod->title,   text, PODCAST_TITLE_MAX - 1);
                strncpy(pod->rss_url, rss,  PODCAST_URL_MAX  - 1);
                extract_pid_from_rss(rss, pod->id, PODCAST_ID_MAX);
                s_count++;
            }
        }
        p = tag_end + 1;
    }

    ESP_LOGI(TAG, "Parsed %zu podcasts from OPML", s_count);
    return ESP_OK;
}

/* ── Public API ───────────────────────────────────────────────────── */

esp_err_t podcast_index_fetch(void)
{
    ESP_LOGI(TAG, "Fetching BBC OPML feed...");

    http_buf_t buf = {
        .buf = podcast_malloc(65536),
        .len = 0,
        .cap = 65536,
    };
    if (!buf.buf) return ESP_ERR_NO_MEM;

    esp_err_t ret = http_get(BBC_OPML_URL, &buf);
    if (ret != ESP_OK) {
        ESP_LOGE(TAG, "Failed to fetch OPML");
        free(buf.buf);
        return ret;
    }

    ret = parse_opml(buf.buf, buf.len);
    free(buf.buf);
    if (ret != ESP_OK) return ret;

    /* Apply popularity / new-podcast rankings */
    podcast_rankings_apply(s_podcasts, s_count);

    s_ready = true;
    return ESP_OK;
}

size_t podcast_index_count(void)      { return s_count; }
bool   podcast_index_ready(void)      { return s_ready; }

podcast_t *podcast_index_get(size_t i)
{
    if (i >= s_count) return NULL;
    return &s_podcasts[i];
}

podcast_t *podcast_index_get_all(size_t *count_out)
{
    if (count_out) *count_out = s_count;
    return s_podcasts;
}

podcast_t *podcast_index_random(void)
{
    if (s_count == 0) return NULL;
    return &s_podcasts[esp_random() % s_count];
}

/* ── Episode fetcher (downloads individual RSS feed) ───────────────── */

static esp_err_t parse_rss_episodes(const char *xml, podcast_t *podcast)
{
#define MAX_EPS 30
    episode_t *eps = podcast_malloc(MAX_EPS * sizeof(episode_t));
    if (!eps) return ESP_ERR_NO_MEM;
    size_t n = 0;
    const char *p = xml;
    while (n < MAX_EPS) {
        const char *item = strstr(p, "<item>");
        if (!item) break;
        const char *item_end = strstr(item, "</item>");
        if (!item_end) break;

        episode_t *ep = &eps[n];
        memset(ep, 0, sizeof(*ep));
        strncpy(ep->podcast_id, podcast->id, PODCAST_ID_MAX - 1);

        /* title */
        const char *ts = strstr(item, "<title>");
        const char *te = ts ? strstr(ts, "</title>") : NULL;
        if (ts && te) {
            ts += 7;
            size_t tl = (size_t)(te - ts);
            if (tl >= EPISODE_TITLE_MAX) tl = EPISODE_TITLE_MAX - 1;
            /* Strip leading CDATA if present */
            if (strncmp(ts, "<![CDATA[", 9) == 0) { ts += 9; tl -= 9 + 3; }
            strncpy(ep->title, ts, tl);
        }

        /* enclosure url (audio) */
        const char *enc = strstr(item, "<enclosure ");
        if (enc && enc < item_end) {
            const char *enc_end = strchr(enc, '>');
            if (enc_end) {
                attr_find(enc, enc_end, "url", ep->audio_url, EPISODE_URL_MAX);
            }
        }

        /* pubDate */
        const char *ds = strstr(item, "<pubDate>");
        const char *de = ds ? strstr(ds, "</pubDate>") : NULL;
        if (ds && de) {
            ds += 9;
            size_t dl = (size_t)(de - ds);
            if (dl >= sizeof(ep->pub_date)) dl = sizeof(ep->pub_date) - 1;
            strncpy(ep->pub_date, ds, dl);
        }

        if (ep->audio_url[0] != '\0') n++;
        p = item_end + 7;
    }

    podcast->_episodes      = eps;
    podcast->_episode_count = n;
    return ESP_OK;
}

esp_err_t podcast_fetch_episodes(podcast_t *podcast)
{
    if (podcast->_episodes) return ESP_OK;  /* already cached */

    http_buf_t buf = {
        .buf = podcast_malloc(131072),
        .len = 0,
        .cap = 131072,
    };
    if (!buf.buf) return ESP_ERR_NO_MEM;

    esp_err_t ret = http_get(podcast->rss_url, &buf);
    if (ret == ESP_OK) {
        ret = parse_rss_episodes(buf.buf, podcast);
    }
    free(buf.buf);
    return ret;
}

episode_t *podcast_get_episodes(podcast_t *podcast, size_t *count_out)
{
    if (count_out) *count_out = podcast->_episode_count;
    return (episode_t *)podcast->_episodes;
}

bool podcast_episodes_cached(const podcast_t *podcast)
{
    return podcast->_episodes != NULL;
}
