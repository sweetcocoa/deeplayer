#!/bin/bash
# Test the Whisper transcription + lyrics alignment pipeline on the host machine.
#
# Usage:
#   ./scripts/test_alignment.sh <audio_file> <lyrics_file> [language]
#
# Arguments:
#   audio_file  - Any audio format supported by ffmpeg (mp3, m4a, flac, wav, ...)
#   lyrics_file - Plain text file, one lyric line per line
#   language    - ko (default), en, or mixed
#
# Prerequisites:
#   - ffmpeg (brew install ffmpeg)
#   - JDK 17+ (JAVA_HOME)
#
# Example:
#   ./scripts/test_alignment.sh ~/Music/song.mp3 ~/lyrics.txt ko
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_ROOT/build/whisper-host"
MODEL_FILE="$BUILD_DIR/ggml-tiny.bin"
NATIVE_LIB="$BUILD_DIR/libwhisper_jni.dylib"

# --- Parse arguments ---
if [ $# -lt 2 ]; then
  echo "Usage: $0 <audio_file> <lyrics_file> [language]"
  echo ""
  echo "  audio_file   Any audio file (mp3, m4a, flac, wav, ...)"
  echo "  lyrics_file  Plain text, one lyric line per line"
  echo "  language     ko (default), en, or mixed"
  exit 1
fi

AUDIO_FILE="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
LYRICS_FILE="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"
LANGUAGE="${3:-ko}"

if [ ! -f "$AUDIO_FILE" ]; then
  echo "ERROR: Audio file not found: $AUDIO_FILE"
  exit 1
fi
if [ ! -f "$LYRICS_FILE" ]; then
  echo "ERROR: Lyrics file not found: $LYRICS_FILE"
  exit 1
fi

# --- Check ffmpeg ---
if ! command -v ffmpeg &>/dev/null; then
  echo "ERROR: ffmpeg not found. Install with: brew install ffmpeg"
  exit 1
fi

# --- Step 1: Build native library if needed ---
if [ ! -f "$NATIVE_LIB" ]; then
  echo "=== Step 1: Building whisper.cpp for host ==="
  "$SCRIPT_DIR/build_whisper_host.sh"
else
  echo "=== Step 1: Native library already built ==="
fi

# --- Step 2: Download model if needed ---
if [ ! -f "$MODEL_FILE" ]; then
  echo ""
  echo "=== Step 2: Downloading ggml-tiny.bin ==="
  curl -L --progress-bar \
    "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin" \
    -o "$MODEL_FILE"
else
  echo "=== Step 2: Model already downloaded ==="
fi

# --- Step 3: Convert audio to 16kHz mono PCM ---
PCM_FILE="$BUILD_DIR/test_audio.pcm"
echo ""
echo "=== Step 3: Converting audio to 16kHz mono PCM ==="
echo "  Input: $AUDIO_FILE"
ffmpeg -y -i "$AUDIO_FILE" -ar 16000 -ac 1 -f f32le -acodec pcm_f32le "$PCM_FILE" 2>/dev/null
PCM_SIZE=$(stat -f%z "$PCM_FILE" 2>/dev/null || stat -c%s "$PCM_FILE" 2>/dev/null)
DURATION_SEC=$((PCM_SIZE / 4 / 16000))
echo "  PCM size: $PCM_SIZE bytes ($DURATION_SEC seconds)"

# --- Step 4: Run pipeline test ---
echo ""
echo "=== Step 4: Running alignment pipeline ==="
echo "  Language: $LANGUAGE"
echo "  Lyrics:   $LYRICS_FILE ($(wc -l < "$LYRICS_FILE" | tr -d ' ') lines)"
echo ""

# Find JAVA_HOME
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -x /usr/libexec/java_home ]; then
    JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || true)
  fi
fi
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home"
fi

export JAVA_HOME
cd "$PROJECT_ROOT"

# Run the Gradle test with integration test parameters
./gradlew :feature:alignment-orchestrator:testDebugUnitTest \
  --tests "com.deeplayer.feature.alignmentorchestrator.PipelineIntegrationTest" \
  -Dwhisper.native.lib="$NATIVE_LIB" \
  -Dwhisper.model.path="$MODEL_FILE" \
  -Dwhisper.pcm.path="$PCM_FILE" \
  -Dwhisper.lyrics.path="$LYRICS_FILE" \
  -Dwhisper.language="$LANGUAGE" \
  2>&1 | grep -v "^$" | grep -v "^>" | grep -v "^BUILD" | grep -v "^Configuration" || true

# Also show the output from stdout
RESULT_FILE="$BUILD_DIR/alignment_result.txt"
if [ -f "$RESULT_FILE" ]; then
  echo ""
  cat "$RESULT_FILE"
else
  echo ""
  echo "Test output not found. Check Gradle output above for errors."
  echo "You can also run directly:"
  echo "  ./gradlew :feature:alignment-orchestrator:testDebugUnitTest \\"
  echo "    --tests '*.PipelineIntegrationTest' --info"
fi
