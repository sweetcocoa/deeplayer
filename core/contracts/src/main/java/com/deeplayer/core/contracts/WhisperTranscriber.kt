package com.deeplayer.core.contracts

/** Whisper full-model transcriber that produces word-level timestamps. */
interface WhisperTranscriber {
  /** Load a GGML model file. Returns true on success. */
  fun loadModel(modelPath: String): Boolean

  /** Transcribe 16kHz mono PCM and return word-level segments with timestamps. */
  fun transcribe(pcm: FloatArray, language: Language): List<TranscribedSegment>

  /** Release native resources. */
  fun close()
}
