package com.deeplayer.feature.audiopreprocessor

import com.deeplayer.core.contracts.AudioPreprocessor
import java.io.Closeable

class NativeAudioPreprocessor : AudioPreprocessor, Closeable {

  private var nativeHandle: Long = 0L

  init {
    nativeHandle = nativeCreate()
  }

  override fun decodeToPcm(filePath: String): FloatArray {
    check(nativeHandle != 0L) { "NativeAudioPreprocessor has been closed" }
    return nativeDecodeToPcm(nativeHandle, filePath)
  }

  override fun extractMelSpectrogram(pcm: FloatArray): FloatArray {
    check(nativeHandle != 0L) { "NativeAudioPreprocessor has been closed" }
    return nativeExtractMelSpectrogram(nativeHandle, pcm)
  }

  override fun close() {
    if (nativeHandle != 0L) {
      nativeDestroy(nativeHandle)
      nativeHandle = 0L
    }
  }

  private external fun nativeCreate(): Long

  private external fun nativeDestroy(handle: Long)

  private external fun nativeDecodeToPcm(handle: Long, filePath: String): FloatArray

  private external fun nativeExtractMelSpectrogram(handle: Long, pcm: FloatArray): FloatArray

  companion object {
    init {
      System.loadLibrary("audio_preprocessor")
    }
  }
}
