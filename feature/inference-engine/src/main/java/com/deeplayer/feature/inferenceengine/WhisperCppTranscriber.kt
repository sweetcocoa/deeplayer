package com.deeplayer.feature.inferenceengine

import com.deeplayer.core.contracts.Language
import com.deeplayer.core.contracts.TranscribedSegment
import com.deeplayer.core.contracts.WhisperTranscriber

/** [WhisperTranscriber] backed by whisper.cpp via JNI. */
class WhisperCppTranscriber : WhisperTranscriber {

  private val native = WhisperNative()
  private var ctx: Long = 0L

  override fun loadModel(modelPath: String): Boolean {
    ctx = native.init(modelPath)
    return ctx != 0L
  }

  override fun transcribe(pcm: FloatArray, language: Language): List<TranscribedSegment> {
    check(ctx != 0L) { "Model not loaded" }
    val lang =
      when (language) {
        Language.KO -> "ko"
        Language.EN -> "en"
        Language.MIXED -> "ko" // default to Korean for mixed content
      }
    val raw = native.transcribe(ctx, pcm, lang) ?: return emptyList()
    return raw.mapNotNull { seg ->
      if (seg.size < 3) return@mapNotNull null
      val text = sanitizeText(seg[0])
      if (text.isBlank()) return@mapNotNull null
      val startMs = seg[1].toLongOrNull() ?: return@mapNotNull null
      val endMs = seg[2].toLongOrNull() ?: return@mapNotNull null
      if (startMs < 0 || endMs < startMs) return@mapNotNull null
      TranscribedSegment(text = text, startMs = startMs, endMs = endMs)
    }
  }

  /**
   * Strip control characters, isolated surrogate bytes, and punctuation-only text that Whisper
   * sometimes emits for non-English languages (broken BPE token boundaries).
   */
  private fun sanitizeText(raw: String): String {
    // Remove control characters and isolated surrogates
    val cleaned =
      raw
        .filter { c -> !c.isISOControl() && !c.isSurrogate() }
        .trim()
    // Skip segments that are only punctuation/brackets/whitespace
    if (cleaned.all { !it.isLetterOrDigit() }) return ""
    return cleaned
  }

  override fun close() {
    if (ctx != 0L) {
      native.free(ctx)
      ctx = 0L
    }
  }
}
