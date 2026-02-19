#!/bin/bash
# Download whisper.cpp tiny model (GGML format) for on-device inference.
# Output: app/src/main/assets/ggml-tiny.bin (~75MB FP16)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_ROOT/app/src/main/assets"
MODEL_FILE="$ASSETS_DIR/ggml-tiny.bin"

if [ -f "$MODEL_FILE" ]; then
  echo "Model already exists: $MODEL_FILE"
  exit 0
fi

mkdir -p "$ASSETS_DIR"

echo "Downloading ggml-tiny.bin from Hugging Face..."
curl -L \
  "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin" \
  -o "$MODEL_FILE"

echo "Done. Model saved to: $MODEL_FILE"
ls -lh "$MODEL_FILE"
