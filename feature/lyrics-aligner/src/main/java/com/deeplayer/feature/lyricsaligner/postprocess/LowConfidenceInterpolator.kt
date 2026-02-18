package com.deeplayer.feature.lyricsaligner.postprocess

import com.deeplayer.core.contracts.WordAlignment

/**
 * Interpolates timestamps for low-confidence alignment sections. Low-confidence sections typically
 * correspond to instrumental or non-vocal parts where the alignment is unreliable.
 */
class LowConfidenceInterpolator {

  /**
   * Interpolate low-confidence words by distributing time evenly between high-confidence anchors.
   *
   * @param words word alignments with confidence scores
   * @param confidenceThreshold below this threshold, timestamps are interpolated
   * @return words with interpolated timestamps for low-confidence sections
   */
  fun interpolate(
    words: List<WordAlignment>,
    confidenceThreshold: Float = 0.3f,
  ): List<WordAlignment> {
    if (words.isEmpty()) return words

    val result = words.toMutableList()

    // Find runs of low-confidence words between high-confidence anchors
    var i = 0
    while (i < result.size) {
      if (result[i].confidence >= confidenceThreshold) {
        i++
        continue
      }

      // Find the start and end anchors of this low-confidence run
      val runStart = i
      while (i < result.size && result[i].confidence < confidenceThreshold) {
        i++
      }
      val runEnd = i // exclusive

      // Get anchor timestamps
      val startMs = if (runStart > 0) result[runStart - 1].endMs else result[runStart].startMs
      val endMs = if (runEnd < result.size) result[runEnd].startMs else result[runEnd - 1].endMs

      // Distribute time evenly across low-confidence words
      val runLength = runEnd - runStart
      if (runLength > 0 && endMs > startMs) {
        val duration = endMs - startMs
        val perWord = duration / runLength
        for (j in runStart until runEnd) {
          val wordStart = startMs + (j - runStart) * perWord
          val wordEnd = startMs + (j - runStart + 1) * perWord
          result[j] = result[j].copy(startMs = wordStart, endMs = wordEnd)
        }
      }
    }

    return result
  }
}
