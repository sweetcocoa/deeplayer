package com.deeplayer.feature.audiopreprocessor

import com.deeplayer.core.contracts.AudioPreprocessor
import kotlin.math.PI
import kotlin.math.sin

/**
 * Test double for AudioPreprocessor. Returns deterministic synthetic data so other modules can test
 * against it without native code.
 */
class FakeAudioPreprocessor : AudioPreprocessor {

  override fun decodeToPcm(filePath: String): FloatArray {
    return generate440HzSine(durationSeconds = 1.0f, sampleRate = SAMPLE_RATE)
  }

  override fun extractMelSpectrogram(pcm: FloatArray): FloatArray {
    val numFrames = (pcm.size - WINDOW_SIZE) / HOP_SIZE + 1
    if (numFrames <= 0) return FloatArray(0)
    val mel = FloatArray(numFrames * NUM_MEL_BANDS)
    for (frame in 0 until numFrames) {
      for (band in 0 until NUM_MEL_BANDS) {
        // Deterministic fake values: higher bands get lower energy
        mel[frame * NUM_MEL_BANDS + band] = -4.0f + (band.toFloat() / NUM_MEL_BANDS) * -6.0f
      }
    }
    return mel
  }

  companion object {
    const val SAMPLE_RATE = 16000
    const val WINDOW_SIZE = 400
    const val HOP_SIZE = 160
    const val NUM_MEL_BANDS = 80

    fun generate440HzSine(durationSeconds: Float, sampleRate: Int = SAMPLE_RATE): FloatArray {
      val numSamples = (sampleRate * durationSeconds).toInt()
      return FloatArray(numSamples) { i -> sin(2.0 * PI * 440.0 * i / sampleRate).toFloat() }
    }
  }
}
