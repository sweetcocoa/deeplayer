#!/usr/bin/env bash
# Fetch synced lyrics from LRCLIB API for benchmark testing.
# Usage: ./scripts/fetch_test_lyrics.sh
#
# LRCLIB (https://lrclib.net) is a free, no-auth-required synced lyrics database.
# This script downloads LRC files for copyright-free tracks and saves them
# as test resources for the alignment accuracy benchmark.
#
# Note: The current test resource LRC files were manually curated for
# copyright-free content. This script serves as a template for fetching
# additional test data from LRCLIB.

set -euo pipefail

RESOURCE_DIR="feature/lyrics-aligner/src/test/resources/benchmark"
mkdir -p "$RESOURCE_DIR"

BASE_URL="https://lrclib.net/api/get"

fetch_lyrics() {
  local track_name="$1"
  local artist_name="$2"
  local output_file="$3"

  echo "Fetching: $track_name by $artist_name..."

  local response
  response=$(curl -s -G "$BASE_URL" \
    --data-urlencode "track_name=$track_name" \
    --data-urlencode "artist_name=$artist_name" \
    -H "User-Agent: Deeplayer Test Data Fetcher v1.0")

  local synced
  synced=$(echo "$response" | python3 -c "
import sys, json
data = json.load(sys.stdin)
lyrics = data.get('syncedLyrics', '')
if lyrics:
    print(lyrics)
else:
    print('ERROR: No synced lyrics found', file=sys.stderr)
    sys.exit(1)
" 2>&1)

  if [ $? -eq 0 ]; then
    echo "$synced" > "$RESOURCE_DIR/$output_file"
    echo "  -> Saved to $RESOURCE_DIR/$output_file"
  else
    echo "  -> Failed: $synced"
  fi
}

# Example usage (replace with actual copyright-free tracks):
# fetch_lyrics "Track Name" "Artist Name" "en_track_01.lrc"
# fetch_lyrics "Track Name" "Artist Name" "en_track_02.lrc"
# fetch_lyrics "Track Name" "Artist Name" "ko_track_01.lrc"

echo ""
echo "Note: Current test LRC files are manually curated."
echo "Edit this script with specific LRCLIB track queries to fetch new data."
echo "API docs: https://lrclib.net/docs"
