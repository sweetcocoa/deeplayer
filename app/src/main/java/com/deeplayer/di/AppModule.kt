package com.deeplayer.di

import android.content.Context
import com.deeplayer.core.contracts.AudioPreprocessor
import com.deeplayer.core.contracts.WhisperTranscriber
import com.deeplayer.feature.audiopreprocessor.AndroidAudioPreprocessor
import com.deeplayer.feature.inferenceengine.WhisperCppTranscriber
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

  private const val WHISPER_MODEL_ASSET = "ggml-tiny.bin"

  @Provides
  @Singleton
  fun provideAudioPreprocessor(): AudioPreprocessor = AndroidAudioPreprocessor()

  @Provides
  @Singleton
  fun provideWhisperTranscriber(@ApplicationContext context: Context): WhisperTranscriber {
    val modelFile = File(context.filesDir, WHISPER_MODEL_ASSET)
    if (!modelFile.exists()) {
      context.assets.open(WHISPER_MODEL_ASSET).use { input ->
        modelFile.outputStream().use { output -> input.copyTo(output) }
      }
    }
    val transcriber = WhisperCppTranscriber()
    check(transcriber.loadModel(modelFile.absolutePath)) {
      "Failed to load Whisper model: ${modelFile.absolutePath}"
    }
    return transcriber
  }
}
