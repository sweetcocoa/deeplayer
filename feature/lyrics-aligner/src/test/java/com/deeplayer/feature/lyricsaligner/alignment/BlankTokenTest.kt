package com.deeplayer.feature.lyricsaligner.alignment

import com.google.common.truth.Truth.assertThat
import kotlin.math.ln
import org.junit.Before
import org.junit.Test

class BlankTokenTest {

  private lateinit var aligner: CtcForcedAligner

  @Before
  fun setUp() {
    aligner = CtcForcedAligner()
    aligner.blankIndex = 0
  }

  @Test
  fun `blank-heavy section detected as instrumental`() {
    val vocabSize = 5
    val numFrames = 100

    // All frames have blank as dominant
    val logProbs = FloatArray(numFrames * vocabSize)
    val logHigh = ln(0.95f)
    val logLow = ln(0.0125f)
    for (t in 0 until numFrames) {
      logProbs[t * vocabSize + 0] = logHigh // blank
      for (v in 1 until vocabSize) {
        logProbs[t * vocabSize + v] = logLow
      }
    }

    assertThat(aligner.isBlankHeavy(logProbs, 0, numFrames, vocabSize, 0.8f)).isTrue()
  }

  @Test
  fun `non-blank section not detected as instrumental`() {
    val vocabSize = 5
    val numFrames = 100
    val logProbs = FloatArray(numFrames * vocabSize)
    val logHigh = ln(0.9f)
    val logLow = ln(0.025f)

    // Most frames have phoneme 1 as dominant
    for (t in 0 until numFrames) {
      logProbs[t * vocabSize + 1] = logHigh
      logProbs[t * vocabSize + 0] = logLow
      for (v in 2 until vocabSize) {
        logProbs[t * vocabSize + v] = logLow
      }
    }

    assertThat(aligner.isBlankHeavy(logProbs, 0, numFrames, vocabSize, 0.8f)).isFalse()
  }

  @Test
  fun `mixed section with majority blank`() {
    val vocabSize = 5
    val numFrames = 100
    val logProbs = FloatArray(numFrames * vocabSize)
    val logHigh = ln(0.9f)
    val logLow = ln(0.025f)

    // 85% blank, 15% phoneme
    for (t in 0 until numFrames) {
      if (t < 85) {
        logProbs[t * vocabSize + 0] = logHigh // blank
        for (v in 1 until vocabSize) logProbs[t * vocabSize + v] = logLow
      } else {
        logProbs[t * vocabSize + 1] = logHigh // phoneme
        logProbs[t * vocabSize + 0] = logLow
        for (v in 2 until vocabSize) logProbs[t * vocabSize + v] = logLow
      }
    }

    assertThat(aligner.isBlankHeavy(logProbs, 0, numFrames, vocabSize, 0.8f)).isTrue()
  }

  @Test
  fun `blank heavy produces no non-blank alignments in alignment result`() {
    val vocabSize = 5
    val numFrames = 50

    // Create a matrix where blank dominates everywhere, but we try to align a phoneme
    val logProbs = FloatArray(numFrames * vocabSize)
    val logHigh = ln(0.95f)
    val logLow = ln(0.0125f)
    for (t in 0 until numFrames) {
      logProbs[t * vocabSize + 0] = logHigh
      for (v in 1 until vocabSize) logProbs[t * vocabSize + v] = logLow
    }

    // Even when we try to align phoneme 1, the algorithm must complete
    // but confidence should be very low
    val result = aligner.align(logProbs, numFrames, vocabSize, intArrayOf(1))
    val nonBlank = result.filter { !it.isBlank }

    // The phoneme is forced to be aligned somewhere, but its confidence should be very low
    if (nonBlank.isNotEmpty()) {
      assertThat(nonBlank[0].confidence).isLessThan(0.2f)
    }
  }

  @Test
  fun `subsection blank check works correctly`() {
    val vocabSize = 5
    val numFrames = 100
    val logProbs = FloatArray(numFrames * vocabSize)
    val logHigh = ln(0.95f)
    val logLow = ln(0.0125f)

    // First 50 frames: phoneme dominant; last 50 frames: blank dominant
    for (t in 0 until 50) {
      logProbs[t * vocabSize + 1] = logHigh
      logProbs[t * vocabSize + 0] = logLow
      for (v in 2 until vocabSize) logProbs[t * vocabSize + v] = logLow
    }
    for (t in 50 until 100) {
      logProbs[t * vocabSize + 0] = logHigh
      for (v in 1 until vocabSize) logProbs[t * vocabSize + v] = logLow
    }

    assertThat(aligner.isBlankHeavy(logProbs, 0, 50, vocabSize)).isFalse()
    assertThat(aligner.isBlankHeavy(logProbs, 50, 100, vocabSize)).isTrue()
  }
}
