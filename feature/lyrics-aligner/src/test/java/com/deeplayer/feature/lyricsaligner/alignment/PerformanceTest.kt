package com.deeplayer.feature.lyricsaligner.alignment

import com.google.common.truth.Truth.assertThat
import kotlin.math.ln
import org.junit.Before
import org.junit.Test

/** Performance tests for CTC forced alignment. */
class PerformanceTest {

  private lateinit var aligner: CtcForcedAligner

  @Before
  fun setUp() {
    aligner = CtcForcedAligner()
    aligner.blankIndex = 0
  }

  @Test
  fun `alignment of 10500 frames and 100 phonemes completes under 500ms`() {
    val numFrames = 10500 // ~3.5 minutes at 20ms/frame
    val vocabSize = 80 // Realistic vocab size
    val numPhonemes = 100

    // Build a realistic-ish log-prob matrix
    val logProbs = FloatArray(numFrames * vocabSize)
    val logBg = ln(1.0f / vocabSize)

    // Fill with background
    for (i in logProbs.indices) {
      logProbs[i] = logBg
    }

    // Create phoneme sequence and assign dominant frames
    val phonemeSequence = IntArray(numPhonemes) { (it % (vocabSize - 1)) + 1 }
    val framesPerPhoneme = numFrames / numPhonemes

    for (p in 0 until numPhonemes) {
      val startFrame = p * framesPerPhoneme
      val endFrame = minOf(startFrame + framesPerPhoneme - 1, numFrames - 1)
      val vocabIdx = phonemeSequence[p]
      for (t in startFrame..endFrame) {
        logProbs[t * vocabSize + vocabIdx] = ln(0.8f)
        logProbs[t * vocabSize + 0] = ln(0.05f)
      }
    }

    // Warm-up run
    aligner.align(logProbs, numFrames, vocabSize, phonemeSequence)

    // Timed run
    val startTime = System.nanoTime()
    val result = aligner.align(logProbs, numFrames, vocabSize, phonemeSequence)
    val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

    assertThat(result).isNotEmpty()
    assertThat(elapsedMs).isLessThan(500)
  }

  @Test
  fun `alignment of 5000 frames and 200 phonemes completes under 500ms`() {
    val numFrames = 5000
    val vocabSize = 80
    val numPhonemes = 200

    val logProbs = FloatArray(numFrames * vocabSize)
    val logBg = ln(1.0f / vocabSize)
    for (i in logProbs.indices) logProbs[i] = logBg

    val phonemeSequence = IntArray(numPhonemes) { (it % (vocabSize - 1)) + 1 }
    val framesPerPhoneme = numFrames / numPhonemes
    for (p in 0 until numPhonemes) {
      val startFrame = p * framesPerPhoneme
      val endFrame = minOf(startFrame + framesPerPhoneme - 1, numFrames - 1)
      for (t in startFrame..endFrame) {
        logProbs[t * vocabSize + phonemeSequence[p]] = ln(0.8f)
      }
    }

    // Warm-up
    aligner.align(logProbs, numFrames, vocabSize, phonemeSequence)

    val startTime = System.nanoTime()
    val result = aligner.align(logProbs, numFrames, vocabSize, phonemeSequence)
    val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

    assertThat(result).isNotEmpty()
    assertThat(elapsedMs).isLessThan(500)
  }
}
