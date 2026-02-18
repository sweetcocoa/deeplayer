package com.deeplayer.feature.lyricsaligner.di

import com.deeplayer.core.contracts.LyricsAligner
import com.deeplayer.feature.lyricsaligner.LyricsAlignerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LyricsAlignerModule {

  @Binds @Singleton abstract fun bindLyricsAligner(impl: LyricsAlignerImpl): LyricsAligner
}
