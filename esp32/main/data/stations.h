#pragma once

#include <stddef.h>

#define STATIONS_MAX  16    /* national stations only */

typedef struct {
    const char *id;         /* short key, e.g. "radio1" */
    const char *title;      /* display name             */
    const char *service_id; /* BBC service ID           */
    const char *stream_url; /* lsn.lv HLS URL (128kbps) */
    const char *logo_url;   /* BBC CDN logo PNG URL     */
} station_t;

#ifdef __cplusplus
extern "C" {
#endif

/** Return pointer to the static station array. */
const station_t *stations_get_all(void);

/** Number of entries in the station array. */
size_t stations_count(void);

#ifdef __cplusplus
}
#endif
