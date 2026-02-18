package com.deeplayer.feature.lyricsaligner.postprocess

import com.deeplayer.feature.lyricsaligner.alignment.CtcForcedAligner
import com.google.common.truth.Truth.assertThat
import kotlin.math.ln
import org.junit.Before
import org.junit.Test

class ConfidenceScoreTest {

  private lateinit var aligner: CtcForcedAligner
  private lateinit var timestampConverter: TimestampConverter
  private lateinit var confidenceCalculator: ConfidenceCalculator

  private val vocabSize = 5
  private val frameDurationMs = 20f

  @Before
  fun setUp() {
    aligner = CtcForcedAligner()
    aligner.blankIndex = 0
    timestampConverter = TimestampConverter()
    confidenceCalculator = ConfidenceCalculator()
  }

  @Test
  fun `clean matrix produces high confidence above 0_7`() {
    val numFrames = 100
    val logProbs = FloatArray(numFrames * vocabSize)
    val logHigh = ln(0.95f)
    val logLow = ln(0.0125f)

    // Clear signal: blank(0-9), phoneme1(10-49), blank(50-54), phoneme2(55-89), blank(90-99)
    for (t in 0 until numFrames) {
      val dominant =
        when (t) {
          in 0..9 -> 0
          in 10..49 -> 1
          in 50..54 -> 0
          in 55..89 -> 2
          in 90..99 -> 0
          else -> 0
        }
      for (v in 0 until vocabSize) {
        logProbs[t * vocabSize + v] = if (v == dominant) logHigh else logLow
      }
    }

    val aligned = aligner.align(logProbs, numFrames, vocabSize, intArrayOf(1, 2))
    val timestamped = timestampConverter.convert(aligned, frameDurationMs)
    val overall = confidenceCalculator.calculateOverall(timestamped)

    assertThat(overall).isGreaterThan(0.7f)
  }

  @Test
  fun `noisy matrix produces low confidence below 0_5`() {
    val numFrames = 100
    val logProbs = FloatArray(numFrames * vocabSize)

    // Noisy: uniform distribution (all phonemes equally likely)
    val logUniform = ln(1.0f / vocabSize)
    for (i in logProbs.indices) {
      logProbs[i] = logUniform
    }

    val aligned = aligner.align(logProbs, numFrames, vocabSize, intArrayOf(1, 2))
    val timestamped = timestampConverter.convert(aligned, frameDurationMs)
    val overall = confidenceCalculator.calculateOverall(timestamped)

    assertThat(overall).isLessThan(0.5f)
  }

  @Test
  fun `partially clean matrix produces medium confidence`() {
    val numFrames = 100
    val logProbs = FloatArray(numFrames * vocabSize)

    // First half clean, second half noisy
    for (t in 0 until numFrames) {
      if (t < 50) {
        // Clean: phoneme 1 dominant
        for (v in 0 until vocabSize) {
          logProbs[t * vocabSize + v] = if (v == 1) ln(0.9f) else ln(0.025f)
        }
      } else {
        // Noisy: uniform
        for (v in 0 until vocabSize) {
          logProbs[t * vocabSize + v] = ln(1.0f / vocabSize)
        }
      }
    }

    val aligned = aligner.align(logProbs, numFrames, vocabSize, intArrayOf(1, 2))
    val timestamped = timestampConverter.convert(aligned, frameDurationMs)
    val overall = confidenceCalculator.calculateOverall(timestamped)

    // Should be between clean and noisy (Viterbi may favor the clean region)
    assertThat(overall).isGreaterThan(0.2f)
    assertThat(overall).isLessThan(1.0f)
  }

  @Test
  fun `empty phoneme list returns zero confidence`() {
    val overall = confidenceCalculator.calculateOverall(emptyList())
    assertThat(overall).isEqualTo(0f)
  }
}
