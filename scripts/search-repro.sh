#!/usr/bin/env bash
# Automated search repro + log capture for BBC Radio Player
# Usage:
#   ./scripts/search-repro.sh "louis armstrong" [device-id] [--slow] [--timeout 5]
# Outputs: ./artifacts/podcast-search-<query>-<ts>.log and summary

set -euo pipefail
IFS=$'\n\t'

QUERY=${1:-}
DEVICE=${2:-}
SLOW_MODE=false
TIMEOUT_SECS=6

shift 1 || true
if [[ "$DEVICE" == "--slow" || "$DEVICE" == "--timeout" ]]; then
  DEVICE=""
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --slow) SLOW_MODE=true; shift ;;
    --timeout) TIMEOUT_SECS=${2:-6}; shift 2 ;;
    *) shift ;;
  esac
done

if [[ -z "$QUERY" ]]; then
  echo "Usage: $0 \"search query\" [device-id] [--slow] [--timeout <secs>]" >&2
  exit 2
fi

ADB=(adb)
if [[ -n "$DEVICE" ]]; then
  ADB=(adb -s "$DEVICE")
fi

TS=$(date +%Y%m%dT%H%M%S)
SAFE_QUERY=$(echo "$QUERY" | tr ' ' '_' | tr -c '[:alnum:]_-' '_')
OUT_DIR="./artifacts"
mkdir -p "$OUT_DIR"
LOG_FILE="$OUT_DIR/podcast-search-${SAFE_QUERY}-${TS}.log"
SUMMARY_FILE="$OUT_DIR/podcast-search-${SAFE_QUERY}-${TS}.summary.txt"
UI_DUMP_LOCAL="$OUT_DIR/window_dump-${TS}.xml"

APP_ACTIVITY="com.hyliankid14.bbcradioplayer/.MainActivity"
SEARCH_RES_ID="com.hyliankid14.bbcradioplayer:id/search_podcast_edittext"

cleanup() {
  if [[ -n "${LOGPID:-}" ]]; then
    kill "$LOGPID" 2>/dev/null || true
    unset LOGPID
  fi
}
trap cleanup EXIT

echo "[info] Clearing device logcat..."
"${ADB[@]}" logcat -c

echo "[info] Launching app and requesting Podcasts screen: $APP_ACTIVITY"
# Use the documented open_podcast_id intent extra to force MainActivity -> showPodcasts()
"${ADB[@]}" shell am start -W -n "$APP_ACTIVITY" --es open_podcast_id "__search_repro__" >/dev/null 2>&1 || true
sleep 0.8

# Re-dump UI so subsequent lookups can find podcast-specific views
"${ADB[@]}" shell uiautomator dump /sdcard/window_dump.xml >/dev/null || true
"${ADB[@]}" pull /sdcard/window_dump.xml "$UI_DUMP_LOCAL" >/dev/null 2>&1 || true

# Verify Podcasts UI is present (look for the podcasts search hint). If not found, continue with fallbacks below.
if grep -qF "Search podcasts" "$UI_DUMP_LOCAL" 2>/dev/null; then
  echo "[info] Podcasts UI detected after intent launch"
else
  echo "[warn] Podcasts UI not detected immediately after intent; will attempt UI fallbacks (bottom-nav / coordinates)"
fi

# Dump UI hierarchy and pull it so we can locate the search field reliably
echo "[info] Dumping UI hierarchy to find search field ($SEARCH_RES_ID)"
"${ADB[@]}" shell uiautomator dump /sdcard/window_dump.xml >/dev/null || true
"${ADB[@]}" pull /sdcard/window_dump.xml "$UI_DUMP_LOCAL" >/dev/null 2>&1 || true

# Try to open the Podcasts tab first (bottom navigation). Prefer resource-id, fall back to item title text.
NAV_PODCASTS_ID="com.hyliankid14.bbcradioplayer:id/navigation_podcasts"
PODCASTS_BOUNDS=""
if [[ -f "$UI_DUMP_LOCAL" ]]; then
  PODCASTS_BOUNDS=$(grep "resource-id=\"$NAV_PODCASTS_ID\"" "$UI_DUMP_LOCAL" 2>/dev/null \
    | sed -n 's/.*bounds="\([^"]*\)".*/\1/p' | head -n1 || true)
  # Fallback: look for menu item with title 'Podcasts'
  if [[ -z "$PODCASTS_BOUNDS" ]]; then
    PODCASTS_BOUNDS=$(grep -i "Podcasts" "$UI_DUMP_LOCAL" 2>/dev/null \
      | sed -n 's/.*bounds="\([^"]*\)".*/\1/p' | head -n1 || true)
  fi
fi
if [[ -n "$PODCASTS_BOUNDS" ]]; then
  nums=$(echo "$PODCASTS_BOUNDS" | tr -d '[]' | tr '][' ' ')
  left=$(echo "$nums" | awk -F'[ ,]+' '{print $1}')
  top=$(echo "$nums" | awk -F'[ ,]+' '{print $2}')
  right=$(echo "$nums" | awk -F'[ ,]+' '{print $3}')
  bottom=$(echo "$nums" | awk -F'[ ,]+' '{print $4}')
  x=$(( (left + right) / 2 ))
  y=$(( (top + bottom) / 2 ))
  echo "[info] Tapping bottom-nav Podcasts at approx (x=$x,y=$y)"
  "${ADB[@]}" shell input tap "$x" "$y" || true
  # Let the fragment transaction settle and re-dump the UI so we can find podcast-specific views
  sleep 0.35
  "${ADB[@]}" shell uiautomator dump /sdcard/window_dump.xml >/dev/null || true
  "${ADB[@]}" pull /sdcard/window_dump.xml "$UI_DUMP_LOCAL" >/dev/null 2>&1 || true
else
  echo "[warn] Podcasts bounds not found in UI dump — using screen-coordinate fallback"
  # Try a screen-coordinate tap based on display size (3rd of 4 bottom-nav items -> ~5/8 width)
  SIZE_RAW=$("${ADB[@]}" shell wm size 2>/dev/null | tr -d '\r') || SIZE_RAW=""
  if [[ "$SIZE_RAW" =~ ([0-9]+)x([0-9]+) ]]; then
    W=${BASH_REMATCH[1]}
    H=${BASH_REMATCH[2]}
    # x at ~5/8, y a bit above the bottom (keep a safe margin)
    x=$(( (W * 5) / 8 ))
    y=$(( H - (H / 14) ))
    echo "[info] screen-size=${W}x${H}; tapping approx (x=$x,y=$y)"
    "${ADB[@]}" shell input tap "$x" "$y" || true
    sleep 0.45
    "${ADB[@]}" shell uiautomator dump /sdcard/window_dump.xml >/dev/null || true
    "${ADB[@]}" pull /sdcard/window_dump.xml "$UI_DUMP_LOCAL" >/dev/null 2>&1 || true
  else
    echo "[warn] Could not determine screen size; falling back to KEYCODE_NAVIGATE (may not work)"
    "${ADB[@]}" shell input keyevent 61 || true
    sleep 0.35
  fi
fi

# Parse bounds for the view with resource-id (robust: tolerate missing/odd UI dumps)
BOUNDS=""
if [[ -f "$UI_DUMP_LOCAL" ]]; then
  BOUNDS=$(grep "resource-id=\"$SEARCH_RES_ID\"" "$UI_DUMP_LOCAL" 2>/dev/null \
    | sed -n 's/.*bounds="\([^"]*\)".*/\1/p' | head -n1 || true)
  # Fallback: try matching the hint text shown in the Podcasts layout ("Search podcasts...")
  if [[ -z "$BOUNDS" ]]; then
    BOUNDS=$(grep -F "Search podcasts" "$UI_DUMP_LOCAL" 2>/dev/null \
      | sed -n 's/.*bounds="\([^"]*\)".*/\1/p' | head -n1 || true)
  fi
fi

if [[ -n "$BOUNDS" ]]; then
  # bounds format: [left,top][right,bottom]
  nums=$(echo "$BOUNDS" | tr -d '[]' | tr '][' ' ')
  left=$(echo "$nums" | awk -F'[ ,]+' '{print $1}')
  top=$(echo "$nums" | awk -F'[ ,]+' '{print $2}')
  right=$(echo "$nums" | awk -F'[ ,]+' '{print $3}')
  bottom=$(echo "$nums" | awk -F'[ ,]+' '{print $4}')
  x=$(( (left + right) / 2 ))
  y=$(( (top + bottom) / 2 ))
  echo "[info] Tapping search field at approx (x=$x,y=$y)"
  "${ADB[@]}" shell input tap "$x" "$y"
  sleep 0.18
else
  echo "[warn] Could not find resource-id in UI dump; attempting to focus via KEYCODE_SEARCH as fallback"
  "${ADB[@]}" shell input keyevent 84 || true
  sleep 0.18
fi

# Type the query. adb input text requires %s for spaces on many devices.
ADB_TEXT=$(echo "$QUERY" | sed -e 's/ /%s/g' -e 's/"/\\\"/g')

if [[ "$SLOW_MODE" == true ]]; then
  echo "[info] Typing query slowly: $QUERY"
  for ch in $(echo -n "$QUERY" | sed -e 's/\(.\)/\1\n/g'); do
    if [[ "$ch" == ' ' ]]; then
      "${ADB[@]}" shell input text "%s"
    else
      # escape % and other special shell chars
      esc=$(printf '%s' "$ch" | sed -e 's/%/%%/g')
      "${ADB[@]}" shell input text "$esc"
    fi
    sleep 0.08
  done
else
  echo "[info] Pasting query: $QUERY"
  "${ADB[@]}" shell input text "$ADB_TEXT"
fi

# Small pause to let IME commit composition
sleep 0.12
# Optionally press IME search/enter to commit
"${ADB[@]}" shell input keyevent 66 || true

# Start filtered logcat capture in background
echo "[info] Capturing logs to $LOG_FILE (timeout ${TIMEOUT_SECS}s)"
"${ADB[@]}" logcat -v threadtime 'PodcastsFragment:D' 'PodcastRepository:D' 'IndexStore:D' *:S > "$LOG_FILE" &
LOGPID=$!

# Wait and then stop capture
sleep "$TIMEOUT_SECS"
kill "$LOGPID" || true
wait 2>/dev/null || true
unset LOGPID

# Produce a short summary with useful grep patterns
echo "query=${QUERY}" > "$SUMMARY_FILE"
echo "logfile=$(realpath "$LOG_FILE")" >> "$SUMMARY_FILE"

grep -E "FTS|Episode search: checking|quick FTS lookup failed|quick cached-episode lookup failed|Episode search job failed|SEARCHTRACE|isComplete" "$LOG_FILE" | sed -n '1,200p' > "$OUT_DIR/summary-lines-${SAFE_QUERY}-${TS}.txt" || true

echo "[info] Done — logs: $LOG_FILE"
echo "[info] Summary: $SUMMARY_FILE (and $OUT_DIR/summary-lines-${SAFE_QUERY}-${TS}.txt)"

echo
echo "Quick investigations you can run against the saved log:" 
echo "  grep -E 'FTS (global|episode).+returned|FTS episode variant returned' $LOG_FILE" 
echo "  grep -E 'Episode search: checking [0-9]+' $LOG_FILE" 
echo "  grep -E 'quick FTS lookup failed|quick cached-episode lookup failed' $LOG_FILE"

echo
echo "Tip: run the script multiple times (fast vs --slow) to compare quick-path vs slow-path behavior."

exit 0
