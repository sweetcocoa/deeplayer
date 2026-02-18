package com.deeplayer.feature.lyricsaligner.postprocess

import com.deeplayer.core.contracts.WordAlignment

/** Calculates alignment confidence scores from the alignment path probabilities. */
class ConfidenceCalculator {

  /**
   * Calculate overall confidence from timestamped phoneme data.
   *
   * @param phonemes phonemes with individual confidence scores
   * @return weighted average confidence (0.0-1.0)
   */
  fun calculateOverall(phonemes: List<TimestampConverter.TimestampedPhoneme>): Float {
    val nonBlank = phonemes.filter { !it.isBlank }
    if (nonBlank.isEmpty()) return 0f

    // Weight by duration
    var totalWeight = 0L
    var weightedSum = 0.0
    for (p in nonBlank) {
      val duration = p.endMs - p.startMs
      weightedSum += p.confidence * duration
      totalWeight += duration
    }
    return if (totalWeight > 0) (weightedSum / totalWeight).toFloat() else 0f
  }

  /**
   * Calculate per-word confidence from constituent phoneme confidences.
   *
   * @param words word alignments to update
   * @param phonemes underlying phoneme alignments
   * @param wordPhonemeMapping mapping from word index to phoneme index ranges
   * @return words with updated confidence scores
   */
  fun calculateWordConfidence(
    words: List<WordAlignment>,
    phonemes: List<TimestampConverter.TimestampedPhoneme>,
    wordPhonemeMapping: List<IntRange>,
  ): List<WordAlignment> {
    return words.mapIndexed { i, word ->
      if (i < wordPhonemeMapping.size) {
        val range = wordPhonemeMapping[i]
        val relevantPhonemes = phonemes.filter { !it.isBlank && it.phonemeIndex in range }
        if (relevantPhonemes.isEmpty()) {
          word
        } else {
          val avgConf = relevantPhonemes.map { it.confidence }.average().toFloat()
          word.copy(confidence = avgConf)
        }
      } else {
        word
      }
    }
  }
}
