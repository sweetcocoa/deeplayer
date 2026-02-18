package com.deeplayer.feature.lyricsaligner.postprocess

import com.deeplayer.feature.lyricsaligner.alignment.CtcForcedAligner

/** Converts frame indices from the CTC aligner to millisecond timestamps. */
class TimestampConverter {

  data class TimestampedPhoneme(
    val phonemeIndex: Int,
    val phonemeLabel: Int,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float,
    val isBlank: Boolean,
  )

  /**
   * Convert frame-based alignments to millisecond timestamps.
   *
   * @param alignments frame-level alignment from CTC aligner
   * @param frameDurationMs duration of one frame in milliseconds
   * @param chunkOffsetMs offset of this chunk in the overall audio
   */
  fun convert(
    alignments: List<CtcForcedAligner.AlignedPhoneme>,
    frameDurationMs: Float,
    chunkOffsetMs: Long = 0,
  ): List<TimestampedPhoneme> {
    return alignments.map { a ->
      TimestampedPhoneme(
        phonemeIndex = a.phonemeIndex,
        phonemeLabel = a.phonemeLabel,
        startMs = (a.startFrame * frameDurationMs).toLong() + chunkOffsetMs,
        endMs = ((a.endFrame + 1) * frameDurationMs).toLong() + chunkOffsetMs,
        confidence = a.confidence,
        isBlank = a.isBlank,
      )
    }
  }

  /** Convert a single frame index to milliseconds. */
  fun frameToMs(frame: Int, frameDurationMs: Float, offsetMs: Long = 0): Long {
    return (frame * frameDurationMs).toLong() + offsetMs
  }
}
