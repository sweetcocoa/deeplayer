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

  companion object {
    const val SAMPLE_RATE = 16000

    fun generate440HzSine(durationSeconds: Float, sampleRate: Int = SAMPLE_RATE): FloatArray {
      val numSamples = (sampleRate * durationSeconds).toInt()
      return FloatArray(numSamples) { i -> sin(2.0 * PI * 440.0 * i / sampleRate).toFloat() }
    }
  }
}
