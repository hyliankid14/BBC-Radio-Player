#!/usr/bin/env python3
"""
BBC Radio Player — Podcast Search Cloud Function
=================================================
HTTP Cloud Function that loads the pre-built podcast index from Google Cloud
Storage and serves lightweight search queries to the Android app.  This
replaces the need to download the full ~46 MB index file to the phone — the
app sends a search term and receives only the matching results.

Endpoints
---------
GET /index/status
    Returns podcast_count, episode_count and generated_at from the cached
    index.  Used by the Android app for a cheap freshness check.

GET /search/podcasts?q=QUERY[&limit=50]
    Full-text search over podcast titles and descriptions.  Returns a JSON
    array of { podcastId, title, description } objects.

GET /search/episodes?q=QUERY[&limit=100][&offset=0]
    Full-text search over episode titles and descriptions.  Returns a JSON
    array of { episodeId, podcastId, title, description, pubDate } objects.

Environment variables
---------------------
GCS_BUCKET   (required) — name of the GCS bucket that holds the index files.
INDEX_OBJECT (optional) — GCS object name for the gzip index
             (default: podcast-index.json.gz).

Deployment
----------
See api/GOOGLE_CLOUD_SETUP.md for full instructions.  Quick start:

    gcloud functions deploy podcast-search \\
        --gen2 \\
        --runtime python312 \\
        --region europe-west2 \\
        --source api/cloud_function \\
        --entry-point search \\
        --trigger-http \\
        --allow-unauthenticated \\
        --memory 512Mi \\
        --timeout 60s \\
        --set-env-vars GCS_BUCKET=YOUR_BUCKET_NAME
"""

import gzip
import json
import logging
import os
import re
import time
from typing import Any

import functions_framework  # type: ignore[import-untyped]
from flask import Request, jsonify, make_response
from google.cloud import storage as gcs  # type: ignore[import-untyped]

logger = logging.getLogger(__name__)

# ── Index cache (module-level so it persists across warm invocations) ─────────

_podcasts: list[dict] = []
_episodes: list[dict] = []
_meta: dict = {}
_loaded_at: float = 0.0
INDEX_CACHE_TTL_S = 6 * 3600  # 6 hours — matches Android app TTL

GCS_BUCKET = os.environ.get("GCS_BUCKET", "")
INDEX_OBJECT = os.environ.get("INDEX_OBJECT", "podcast-index.json.gz")


def _load_index(force: bool = False) -> None:
    """Download the gzip index from GCS and populate the in-memory cache.

    The index is kept in module-level lists so that warm Cloud Function
    instances reuse it without paying the GCS download cost on every request.
    A 6-hour TTL ensures the nightly rebuild is picked up within the same
    window used by the Android app.
    """
    global _podcasts, _episodes, _meta, _loaded_at

    now = time.monotonic()
    if not force and _loaded_at > 0 and (now - _loaded_at) < INDEX_CACHE_TTL_S:
        return

    if not GCS_BUCKET:
        raise RuntimeError(
            "GCS_BUCKET environment variable is not set. "
            "Set it to the name of your GCS bucket."
        )

    logger.info("Loading podcast index from gs://%s/%s …", GCS_BUCKET, INDEX_OBJECT)
    t0 = time.monotonic()

    client = gcs.Client()
    bucket = client.bucket(GCS_BUCKET)
    blob = bucket.blob(INDEX_OBJECT)
    compressed = blob.download_as_bytes()
    raw = gzip.decompress(compressed)
    index = json.loads(raw)

    _podcasts = index.get("podcasts", [])
    _episodes = index.get("episodes", [])
    _meta = {
        "generated_at": index.get("generated_at", ""),
        "podcast_count": index.get("podcast_count", len(_podcasts)),
        "episode_count": index.get("episode_count", len(_episodes)),
    }
    _loaded_at = time.monotonic()

    elapsed = time.monotonic() - t0
    logger.info(
        "Loaded %d podcasts, %d episodes in %.1f s",
        len(_podcasts),
        len(_episodes),
        elapsed,
    )


# ── Search helpers ─────────────────────────────────────────────────────────────

_NORMALISE_RE = re.compile(r"[^\w\s]", re.UNICODE)
_SPACE_RE = re.compile(r"\s+")


def _normalise(text: str) -> str:
    """Lower-case and strip punctuation/extra whitespace for fuzzy matching."""
    import unicodedata
    nfd = unicodedata.normalize("NFD", text)
    no_diacritics = "".join(c for c in nfd if unicodedata.category(c) != "Mn")
    return _SPACE_RE.sub(" ", _NORMALISE_RE.sub(" ", no_diacritics.lower())).strip()


def _matches(text: str, tokens: list[str]) -> bool:
    """Return True if *all* query tokens appear in *text* (case-insensitive)."""
    t = _normalise(text)
    return all(tok in t for tok in tokens)


def _tokenise(query: str) -> list[str]:
    """Split a query into lowercase tokens, filtering empty strings."""
    return [t for t in _normalise(query).split() if t]


# ── CORS helper ────────────────────────────────────────────────────────────────

def _cors_response(data: Any, status: int = 200):
    """Wrap *data* in a JSON response with permissive CORS headers."""
    resp = make_response(jsonify(data), status)
    resp.headers["Access-Control-Allow-Origin"] = "*"
    resp.headers["Cache-Control"] = "public, max-age=300"  # 5-min CDN cache
    return resp


# ── Entry point ────────────────────────────────────────────────────────────────

@functions_framework.http
def search(request: Request):
    """HTTP entry point for all podcast search requests."""

    # Handle CORS pre-flight
    if request.method == "OPTIONS":
        resp = make_response("", 204)
        resp.headers["Access-Control-Allow-Origin"] = "*"
        resp.headers["Access-Control-Allow-Methods"] = "GET, OPTIONS"
        resp.headers["Access-Control-Allow-Headers"] = "Content-Type"
        return resp

    if request.method != "GET":
        return _cors_response({"error": "Method not allowed"}, 405)

    # Route on path
    path = request.path.rstrip("/")

    try:
        _load_index()
    except Exception as exc:
        logger.exception("Failed to load index")
        return _cors_response({"error": f"Index unavailable: {exc}"}, 503)

    if path == "/index/status":
        return _cors_response(_meta)

    if path == "/search/podcasts":
        return _handle_search_podcasts(request)

    if path == "/search/episodes":
        return _handle_search_episodes(request)

    return _cors_response({"error": "Not found"}, 404)


def _handle_search_podcasts(request: Request):
    query = (request.args.get("q") or "").strip()
    if not query:
        return _cors_response([])

    try:
        limit = min(int(request.args.get("limit", 50)), 200)
    except ValueError:
        limit = 50

    tokens = _tokenise(query)
    if not tokens:
        return _cors_response([])

    results = []
    for p in _podcasts:
        if _matches(p.get("title", ""), tokens) or _matches(
            p.get("description", ""), tokens
        ):
            results.append(
                {
                    "podcastId": p.get("id", ""),
                    "title": p.get("title", ""),
                    "description": p.get("description", ""),
                }
            )
        if len(results) >= limit:
            break

    return _cors_response(results)


def _handle_search_episodes(request: Request):
    query = (request.args.get("q") or "").strip()
    if not query:
        return _cors_response([])

    try:
        limit = min(int(request.args.get("limit", 100)), 500)
    except ValueError:
        limit = 100

    try:
        offset = max(int(request.args.get("offset", 0)), 0)
    except ValueError:
        offset = 0

    tokens = _tokenise(query)
    if not tokens:
        return _cors_response([])

    matched = []
    for ep in _episodes:
        if _matches(ep.get("title", ""), tokens) or _matches(
            ep.get("description", ""), tokens
        ):
            matched.append(
                {
                    "episodeId": ep.get("id", ""),
                    "podcastId": ep.get("podcastId", ""),
                    "title": ep.get("title", ""),
                    "description": ep.get("description", ""),
                    "pubDate": ep.get("pubDate", ""),
                }
            )

    page = matched[offset : offset + limit]
    return _cors_response(page)
