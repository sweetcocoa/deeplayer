package com.deeplayer.feature.alignmentorchestrator.di

import android.content.Context
import androidx.room.Room
import com.deeplayer.core.contracts.AlignmentOrchestrator
import com.deeplayer.core.contracts.AudioPreprocessor
import com.deeplayer.core.contracts.WhisperTranscriber
import com.deeplayer.feature.alignmentorchestrator.AlignmentOrchestratorImpl
import com.deeplayer.feature.alignmentorchestrator.cache.AlignmentCacheDao
import com.deeplayer.feature.alignmentorchestrator.cache.AlignmentDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AlignmentOrchestratorModule {

  @Provides
  @Singleton
  fun provideAlignmentOrchestrator(
    audioPreprocessor: AudioPreprocessor,
    whisperTranscriber: WhisperTranscriber,
    cacheDao: AlignmentCacheDao,
  ): AlignmentOrchestrator =
    AlignmentOrchestratorImpl(
      audioPreprocessor,
      whisperTranscriber,
      cacheDao,
    )

  @Provides
  @Singleton
  fun provideAlignmentDatabase(@ApplicationContext context: Context): AlignmentDatabase {
    return Room.databaseBuilder(context, AlignmentDatabase::class.java, "deeplayer-alignment.db")
      .fallbackToDestructiveMigration()
      .build()
  }

  @Provides
  @Singleton
  fun provideAlignmentCacheDao(database: AlignmentDatabase): AlignmentCacheDao =
    database.alignmentCacheDao()
}
