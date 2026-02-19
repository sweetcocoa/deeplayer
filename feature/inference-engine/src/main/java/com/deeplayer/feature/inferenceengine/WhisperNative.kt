package com.deeplayer.feature.inferenceengine

/**
 * JNI bindings for whisper.cpp. Each method maps to a native function in whisper_jni.cpp.
 *
 * Thread safety: a single [whisper_context] must not be used concurrently. Callers are responsible
 * for serialising access.
 */
internal class WhisperNative {
  companion object {
    init {
      System.loadLibrary("whisper_jni")
    }
  }

  /**
   * Initialise a whisper context from a GGML model file.
   *
   * @return opaque native pointer (0 on failure).
   */
  external fun init(modelPath: String): Long

  /**
   * Run full transcription on 16 kHz mono PCM samples.
   *
   * @return array of `[text, startMs, endMs]` string triples, or null on error.
   */
  external fun transcribe(ctx: Long, pcm: FloatArray, language: String): Array<Array<String>>?

  /** Free the native whisper context. */
  external fun free(ctx: Long)
}
