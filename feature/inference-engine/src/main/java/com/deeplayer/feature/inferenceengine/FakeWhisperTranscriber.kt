package com.deeplayer.feature.inferenceengine

import com.deeplayer.core.contracts.Language
import com.deeplayer.core.contracts.TranscribedSegment
import com.deeplayer.core.contracts.WhisperTranscriber

/**
 * Deterministic fake for testing. Returns pre-configured segments or generates evenly-spaced
 * segments from the PCM duration.
 */
class FakeWhisperTranscriber(private val segments: List<TranscribedSegment>? = null) :
  WhisperTranscriber {

  private var loaded = false

  override fun loadModel(modelPath: String): Boolean {
    loaded = true
    return true
  }

  override fun transcribe(pcm: FloatArray, language: Language): List<TranscribedSegment> {
    check(loaded) { "Model not loaded" }
    if (segments != null) return segments

    // Generate deterministic segments: one word per second
    val durationMs = (pcm.size.toLong() * 1000L) / 16000L
    val numSegments = (durationMs / 1000L).toInt().coerceAtLeast(1)
    return (0 until numSegments).map { i ->
      TranscribedSegment(text = "word$i", startMs = i * 1000L, endMs = (i + 1) * 1000L)
    }
  }

  override fun close() {
    loaded = false
  }
}
