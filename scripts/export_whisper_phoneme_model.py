#!/usr/bin/env python3
"""
Export Whisper tiny encoder + CTC phoneme head to ONNX.

Produces a model that takes mel spectrogram input [1, 80, 3000] and outputs
phoneme logits [1, 1500, 81] for CTC forced alignment.

The model architecture:
  1. Whisper tiny encoder (pre-trained, frozen) → [1, 1500, 384]
  2. Linear CTC head (384 → 81) → [1, 1500, 81]

The CTC head is initialized using Xavier uniform initialization. For production
quality, fine-tune on phoneme recognition data (e.g., LibriSpeech + phoneme labels).

Usage:
    cd scripts/
    uv run python export_whisper_phoneme_model.py

Output:
    ../app/src/main/assets/whisper-tiny-phoneme-ctc.onnx
"""

import os
import sys
from pathlib import Path

import torch
import torch.nn as nn
from transformers import WhisperModel


# Deeplayer phoneme vocabulary (81 tokens)
# Index 0: CTC blank
# 1-19: Korean consonants (ㄱ ㄲ ㄴ ㄷ ㄸ ㄹ ㅁ ㅂ ㅃ ㅅ ㅆ ㅇ ㅈ ㅉ ㅊ ㅋ ㅌ ㅍ ㅎ)
# 20-40: Korean vowels (ㅏ ㅐ ㅑ ㅒ ㅓ ㅔ ㅕ ㅖ ㅗ ㅘ ㅙ ㅚ ㅛ ㅜ ㅝ ㅞ ㅟ ㅠ ㅡ ㅢ ㅣ)
# 41-79: ARPAbet phonemes (AA AE AH AO AW AY B CH D DH EH ER EY F G HH IH IY
#         JH K L M N NG OW OY P R S SH T TH UH UW V W Y Z ZH)
# 80: Space
VOCAB_SIZE = 81
ENCODER_DIM = 384  # Whisper tiny hidden size


class WhisperPhonemeModel(nn.Module):
    """Whisper encoder + linear CTC head for phoneme recognition."""

    def __init__(self, encoder: nn.Module):
        super().__init__()
        self.encoder = encoder
        self.ctc_head = nn.Linear(ENCODER_DIM, VOCAB_SIZE)
        nn.init.xavier_uniform_(self.ctc_head.weight)
        nn.init.zeros_(self.ctc_head.bias)

    def forward(self, input_features: torch.Tensor) -> torch.Tensor:
        """
        Args:
            input_features: Mel spectrogram [batch, 80, 3000]
        Returns:
            Phoneme logits [batch, 1500, 81]
        """
        # Whisper encoder outputs last_hidden_state [batch, 1500, 384]
        encoder_output = self.encoder(input_features).last_hidden_state
        # Project to phoneme vocabulary
        logits = self.ctc_head(encoder_output)
        return logits


def export_model(output_path: str):
    print("Loading Whisper tiny model from HuggingFace...")
    whisper = WhisperModel.from_pretrained("openai/whisper-tiny")
    encoder = whisper.encoder

    print("Building WhisperPhonemeModel...")
    model = WhisperPhonemeModel(encoder)
    model.eval()

    # Dummy input: mel spectrogram [1, 80, 3000]
    dummy_input = torch.randn(1, 80, 3000)

    print(f"Exporting to ONNX: {output_path}")
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    torch.onnx.export(
        model,
        dummy_input,
        output_path,
        input_names=["input"],
        output_names=["logits"],
        dynamic_axes={
            "input": {0: "batch"},
            "logits": {0: "batch"},
        },
        opset_version=17,
        do_constant_folding=True,
        dynamo=False,
    )

    # Verify the exported model
    import onnx

    onnx_model = onnx.load(output_path)
    onnx.checker.check_model(onnx_model)

    file_size_mb = os.path.getsize(output_path) / (1024 * 1024)
    print(f"Model exported successfully: {output_path} ({file_size_mb:.1f} MB)")
    print(f"  Input:  input [batch, 80, 3000] (mel spectrogram)")
    print(f"  Output: logits [batch, 1500, {VOCAB_SIZE}] (phoneme logits)")
    print()
    print("NOTE: The CTC head is randomly initialized. For production quality,")
    print("fine-tune on phoneme-labeled audio data.")


if __name__ == "__main__":
    project_root = Path(__file__).parent.parent
    output = str(project_root / "app" / "src" / "main" / "assets" / "whisper-tiny-phoneme-ctc.onnx")

    if "--output" in sys.argv:
        idx = sys.argv.index("--output")
        output = sys.argv[idx + 1]

    export_model(output)
