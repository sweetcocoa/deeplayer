package com.deeplayer.core.contracts

interface LyricsAligner {
  /**
   * Align lyrics text to a phoneme probability matrix.
   *
   * @param lyrics line-by-line lyrics text
   * @param phonemeProbabilities per-frame phoneme probabilities [frames x vocab_size]
   * @param frameDurationMs duration of one frame in ms (typically 20ms)
   * @param language KO, EN, or MIXED
   * @return per-word timestamps and confidence scores
   */
  fun align(
    lyrics: List<String>,
    phonemeProbabilities: FloatArray,
    frameDurationMs: Float,
    language: Language = Language.KO,
  ): AlignmentResult
}
