package com.deeplayer.feature.lyricsaligner.alignment

import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import kotlin.math.ln
import org.junit.Before
import org.junit.Test

class SyntheticAlignmentTest {

  private lateinit var aligner: CtcForcedAligner

  private val vocabSize = 5 // blank(0), phoneme A(1), B(2), C(3), D(4)
  @Suppress("unused") private val frameDurationMs = 20f

  @Before
  fun setUp() {
    aligner = CtcForcedAligner()
    aligner.blankIndex = 0
  }

  /**
   * Build a synthetic log-prob matrix where specific phonemes are dominant at known frames. This
   * creates a clear alignment target for testing.
   */
  private fun buildSyntheticLogProbs(
    numFrames: Int,
    vocabSize: Int,
    assignments: List<Pair<IntRange, Int>>, // frame range → dominant vocab index
    dominantProb: Float = 0.9f,
  ): FloatArray {
    val logProbs = FloatArray(numFrames * vocabSize)
    val bgProb = (1f - dominantProb) / (vocabSize - 1)
    val logDominant = ln(dominantProb)
    val logBg = ln(bgProb)

    // Fill with background
    for (i in logProbs.indices) {
      logProbs[i] = logBg
    }

    // Set dominant phonemes at assigned frames
    for ((range, vocabIdx) in assignments) {
      for (t in range) {
        if (t < numFrames) {
          // Reset all to background for this frame
          for (v in 0 until vocabSize) {
            logProbs[t * vocabSize + v] = logBg
          }
          logProbs[t * vocabSize + vocabIdx] = logDominant
        }
      }
    }

    return logProbs
  }

  @Test
  fun `single phoneme aligned to correct frames`() {
    // 50 frames, phoneme A(1) dominant at frames 10-39, blank elsewhere
    val numFrames = 50
    val logProbs =
      buildSyntheticLogProbs(
        numFrames,
        vocabSize,
        listOf(
          0..9 to 0, // blank
          10..39 to 1, // phoneme A
          40..49 to 0, // blank
        ),
      )

    val result = aligner.align(logProbs, numFrames, vocabSize, intArrayOf(1))
    val nonBlank = result.filter { !it.isBlank }
    assertThat(nonBlank).hasSize(1)
    assertThat(nonBlank[0].phonemeLabel).isEqualTo(1)

    // Check alignment is within 1 frame (20ms) of expected
    val expectedStart = 10
    val expectedEnd = 39
    assertThat(abs(nonBlank[0].startFrame - expectedStart)).isAtMost(1)
    assertThat(abs(nonBlank[0].endFrame - expectedEnd)).isAtMost(1)
  }

  @Test
  fun `two phonemes aligned to correct frames`() {
    // 100 frames: blank(0-9), A(10-49), blank(50-54), B(55-89), blank(90-99)
    val numFrames = 100
    val logProbs =
      buildSyntheticLogProbs(
        numFrames,
        vocabSize,
        listOf(0..9 to 0, 10..49 to 1, 50..54 to 0, 55..89 to 2, 90..99 to 0),
      )

    val result = aligner.align(logProbs, numFrames, vocabSize, intArrayOf(1, 2))
    val nonBlank = result.filter { !it.isBlank }
    assertThat(nonBlank).hasSize(2)

    // Phoneme A should be around frames 10-49
    assertThat(abs(nonBlank[0].startFrame - 10)).isAtMost(1)
    // Phoneme B should be around frames 55-89
    assertThat(abs(nonBlank[1].startFrame - 55)).isAtMost(2)
  }

  @Test
  fun `three phonemes sequence ABC`() {
    val numFrames = 150
    val logProbs =
      buildSyntheticLogProbs(
        numFrames,
        vocabSize,
        listOf(
          0..4 to 0, // blank
          5..44 to 1, // A
          45..49 to 0, // blank
          50..99 to 2, // B
          100..104 to 0, // blank
          105..144 to 3, // C
          145..149 to 0, // blank
        ),
      )

    val result = aligner.align(logProbs, numFrames, vocabSize, intArrayOf(1, 2, 3))
    val nonBlank = result.filter { !it.isBlank }
    assertThat(nonBlank).hasSize(3)
    assertThat(nonBlank[0].phonemeLabel).isEqualTo(1)
    assertThat(nonBlank[1].phonemeLabel).isEqualTo(2)
    assertThat(nonBlank[2].phonemeLabel).isEqualTo(3)

    // Verify ordering
    assertThat(nonBlank[0].endFrame).isLessThan(nonBlank[1].startFrame)
    assertThat(nonBlank[1].endFrame).isLessThan(nonBlank[2].startFrame)

    // MAE should be < 1 frame (20ms)
    val mae0 = abs(nonBlank[0].startFrame - 5)
    val mae1 = abs(nonBlank[1].startFrame - 50)
    val mae2 = abs(nonBlank[2].startFrame - 105)
    assertThat(mae0).isAtMost(1)
    assertThat(mae1).isAtMost(1)
    assertThat(mae2).isAtMost(1)
  }

  @Test
  fun `repeated phonemes AA aligned correctly`() {
    // Same phoneme appearing twice must be separated by blank in CTC
    val numFrames = 100
    val logProbs =
      buildSyntheticLogProbs(
        numFrames,
        vocabSize,
        listOf(
          0..4 to 0, // blank
          5..39 to 1, // first A
          40..54 to 0, // blank (required between repeated phonemes)
          55..89 to 1, // second A
          90..99 to 0, // blank
        ),
      )

    val result = aligner.align(logProbs, numFrames, vocabSize, intArrayOf(1, 1))
    val nonBlank = result.filter { !it.isBlank }
    assertThat(nonBlank).hasSize(2)
    assertThat(nonBlank[0].phonemeLabel).isEqualTo(1)
    assertThat(nonBlank[1].phonemeLabel).isEqualTo(1)
    // Must have a blank separating them
    assertThat(nonBlank[0].endFrame).isLessThan(nonBlank[1].startFrame)
  }

  @Test
  fun `alignment with four phonemes ABCD has MAE below 1 frame`() {
    val numFrames = 200
    val expectedStarts = listOf(5, 55, 105, 155)
    @Suppress("unused") val expectedEnds = listOf(44, 94, 144, 194)

    val logProbs =
      buildSyntheticLogProbs(
        numFrames,
        vocabSize,
        listOf(
          0..4 to 0,
          5..44 to 1,
          45..54 to 0,
          55..94 to 2,
          95..104 to 0,
          105..144 to 3,
          145..154 to 0,
          155..194 to 4,
          195..199 to 0,
        ),
      )

    val result = aligner.align(logProbs, numFrames, vocabSize, intArrayOf(1, 2, 3, 4))
    val nonBlank = result.filter { !it.isBlank }
    assertThat(nonBlank).hasSize(4)

    // Calculate MAE in frames
    var totalError = 0.0
    for (i in nonBlank.indices) {
      totalError += abs(nonBlank[i].startFrame - expectedStarts[i])
    }
    val mae = totalError / nonBlank.size
    assertThat(mae).isLessThan(1.0) // MAE < 1 frame = 20ms
  }

  @Test
  fun `empty phoneme sequence returns empty`() {
    val logProbs = FloatArray(50 * vocabSize) { ln(0.2f) }
    val result = aligner.align(logProbs, 50, vocabSize, intArrayOf())
    assertThat(result).isEmpty()
  }
}
