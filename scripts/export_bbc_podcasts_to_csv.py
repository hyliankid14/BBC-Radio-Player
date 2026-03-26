#!/usr/bin/env python3
"""
Export every BBC podcast and episode to a spreadsheet-friendly CSV file.

Columns:
- Podcast name
- Episode name
- Episode length
- Publish date of episode

Usage:
    python3 scripts/export_bbc_podcasts_to_csv.py
    python3 scripts/export_bbc_podcasts_to_csv.py --output docs/bbc-podcasts.csv
    python3 scripts/export_bbc_podcasts_to_csv.py --workers 20 --max-podcasts 50
"""

from __future__ import annotations

import argparse
import csv
import re
import ssl
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from email.utils import parsedate_to_datetime
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from xml.etree import ElementTree as ET

BBC_OPML_URL = "https://www.bbc.co.uk/radio/opml/bbc_podcast_opml.xml"
USER_AGENT = "BBC-Podcast-Spreadsheet-Exporter/1.0"
REQUEST_TIMEOUT = 20
DEFAULT_WORKERS = 16


def build_ssl_context() -> ssl.SSLContext:
    """Build an SSL context with a certifi fallback for local environments."""
    try:
        import certifi  # type: ignore[import-not-found]

        return ssl.create_default_context(cafile=certifi.where())
    except Exception:
        return ssl.create_default_context()


SSL_CONTEXT = build_ssl_context()


def fetch_bytes(url: str, max_retries: int = 2) -> bytes:
    """Fetch a URL and return response bytes with small retry logic."""
    safe_url = url.replace("http://", "https://")
    request = Request(safe_url, headers={"User-Agent": USER_AGENT})
    last_error: Exception | None = None

    for attempt in range(max_retries + 1):
        try:
            with urlopen(request, timeout=REQUEST_TIMEOUT, context=SSL_CONTEXT) as response:
                return response.read()
        except (HTTPError, URLError) as exc:
            last_error = exc
            if attempt < max_retries:
                time.sleep(2 ** attempt)

    if last_error is None:
        raise RuntimeError(f"Unknown fetch error for URL: {url}")
    raise last_error


def parse_opml(content: bytes) -> list[tuple[str, str]]:
    """Return (podcast_name, rss_url) entries from BBC OPML."""
    root = ET.fromstring(content)
    podcasts: list[tuple[str, str]] = []
    seen_rss: set[str] = set()

    for outline in root.iter("outline"):
        rss_url = (outline.get("xmlUrl") or outline.get("url") or "").strip()
        title = (outline.get("text") or outline.get("title") or "").strip()
        if not rss_url or not title:
            continue
        if rss_url in seen_rss:
            continue
        seen_rss.add(rss_url)
        podcasts.append((title, rss_url))

    return podcasts


def parse_pub_date(value: str) -> str:
    """Normalise publish dates as YYYY-MM-DD (blank when unknown)."""
    if not value:
        return ""
    try:
        return parsedate_to_datetime(value).date().isoformat()
    except Exception:
        pass

    date_prefix = re.match(r"^(\d{4}-\d{2}-\d{2})", value.strip())
    if date_prefix:
        return date_prefix.group(1)

    try:
        # Keep a fallback for less common feed date formats.
        return datetime.fromisoformat(value.strip().replace("Z", "+00:00")).date().isoformat()
    except Exception:
        return value.strip()


def normalise_duration(value: str) -> str:
    """
    Return duration in HH:MM:SS where possible.

    Feeds can provide duration as seconds ("3600") or h:m:s/m:s strings.
    """
    raw = value.strip()
    if not raw:
        return ""

    if raw.isdigit():
        total_seconds = int(raw)
        hours = total_seconds // 3600
        minutes = (total_seconds % 3600) // 60
        seconds = total_seconds % 60
        return f"{hours:02d}:{minutes:02d}:{seconds:02d}"

    parts = raw.split(":")
    if len(parts) == 2 and all(p.isdigit() for p in parts):
        minutes, seconds = (int(parts[0]), int(parts[1]))
        return f"00:{minutes:02d}:{seconds:02d}"
    if len(parts) == 3 and all(p.isdigit() for p in parts):
        hours, minutes, seconds = (int(parts[0]), int(parts[1]), int(parts[2]))
        return f"{hours:02d}:{minutes:02d}:{seconds:02d}"

    return raw


def parse_rss_episodes(content: bytes, podcast_name: str) -> list[dict[str, str]]:
    """Extract episode rows from one RSS document."""
    try:
        root = ET.fromstring(content)
    except ET.ParseError:
        return []

    channel = root.find("channel")
    if channel is None:
        return []

    ns = {
        "itunes": "http://www.itunes.com/dtds/podcast-1.0.dtd",
        "media": "http://search.yahoo.com/mrss/",
    }

    rows: list[dict[str, str]] = []

    for item in channel.findall("item"):
        title_elem = item.find("title")
        title = (title_elem.text or "").strip() if title_elem is not None else ""
        if not title:
            continue

        pub_date_elem = item.find("pubDate")
        pub_date = (pub_date_elem.text or "").strip() if pub_date_elem is not None else ""

        duration = ""
        duration_elem = item.find("itunes:duration", ns)
        if duration_elem is not None and duration_elem.text:
            duration = normalise_duration(duration_elem.text)
        else:
            media_content = item.find("media:content", ns)
            if media_content is not None:
                media_duration = (media_content.get("duration") or "").strip()
                if media_duration:
                    duration = normalise_duration(media_duration)

        rows.append(
            {
                "Podcast name": podcast_name,
                "Episode name": title,
                "Episode length": duration,
                "Publish date of episode": parse_pub_date(pub_date),
            }
        )

    return rows


def fetch_and_parse_podcast(podcast_name: str, rss_url: str) -> tuple[str, list[dict[str, str]], str | None]:
    """Fetch and parse one podcast RSS feed."""
    try:
        content = fetch_bytes(rss_url)
        rows = parse_rss_episodes(content, podcast_name)
        return podcast_name, rows, None
    except Exception as exc:
        return podcast_name, [], str(exc)


def export_bbc_podcasts(output_path: Path, workers: int, max_podcasts: int | None) -> None:
    """Fetch OPML and all podcast RSS feeds, then write a CSV file."""
    print(f"Fetching OPML from {BBC_OPML_URL}...")
    podcast_entries = parse_opml(fetch_bytes(BBC_OPML_URL))

    if max_podcasts is not None:
        podcast_entries = podcast_entries[:max_podcasts]

    print(f"Discovered {len(podcast_entries)} podcasts. Fetching RSS feeds...")

    all_rows: list[dict[str, str]] = []
    failures: list[tuple[str, str]] = []

    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {
            pool.submit(fetch_and_parse_podcast, podcast_name, rss_url): (podcast_name, rss_url)
            for podcast_name, rss_url in podcast_entries
        }

        completed = 0
        total = len(futures)
        for future in as_completed(futures):
            completed += 1
            podcast_name, rows, error = future.result()
            if error:
                failures.append((podcast_name, error))
                print(f"WARN [{completed}/{total}] {podcast_name}: {error}", file=sys.stderr)
            else:
                all_rows.extend(rows)
                print(f"OK   [{completed}/{total}] {podcast_name}: {len(rows)} episodes")

    all_rows.sort(
        key=lambda row: (
            row.get("Publish date of episode", ""),
            row.get("Podcast name", ""),
            row.get("Episode name", ""),
        ),
        reverse=True,
    )

    output_path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = [
        "Podcast name",
        "Episode name",
        "Episode length",
        "Publish date of episode",
    ]

    with output_path.open("w", encoding="utf-8", newline="") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(all_rows)

    print("\nExport complete")
    print(f"CSV file: {output_path}")
    print(f"Rows written: {len(all_rows)}")
    print(f"Podcast feeds processed: {len(podcast_entries)}")
    print(f"Podcast feeds failed: {len(failures)}")

    if failures:
        print("\nFailed feeds:", file=sys.stderr)
        for podcast_name, error in failures:
            print(f"- {podcast_name}: {error}", file=sys.stderr)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export BBC podcasts and episodes to a spreadsheet-friendly CSV file."
    )
    parser.add_argument(
        "--output",
        default="docs/bbc-podcast-episodes.csv",
        help="Output CSV file path (default: docs/bbc-podcast-episodes.csv)",
    )
    parser.add_argument(
        "--workers",
        type=int,
        default=DEFAULT_WORKERS,
        help=f"Number of concurrent RSS fetch workers (default: {DEFAULT_WORKERS})",
    )
    parser.add_argument(
        "--max-podcasts",
        type=int,
        default=None,
        help="Optional cap for testing a smaller subset of podcasts",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    if args.workers < 1:
        print("ERROR: --workers must be >= 1", file=sys.stderr)
        return 2
    if args.max_podcasts is not None and args.max_podcasts < 1:
        print("ERROR: --max-podcasts must be >= 1 when set", file=sys.stderr)
        return 2

    output_path = Path(args.output)
    export_bbc_podcasts(output_path=output_path, workers=args.workers, max_podcasts=args.max_podcasts)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
