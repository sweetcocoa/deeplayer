package com.deeplayer.core.player

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.deeplayer.core.contracts.PlayerService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerBindsModule {
  @Binds @Singleton abstract fun bindPlayerService(impl: PlayerServiceImpl): PlayerService
}

@Module
@InstallIn(SingletonComponent::class)
object PlayerProvidesModule {

  @Provides
  @Singleton
  fun provideContentResolver(@ApplicationContext context: Context): ContentResolver {
    return context.contentResolver
  }

  @Provides
  @Singleton
  fun provideTrackDatabase(@ApplicationContext context: Context): TrackDatabase {
    return Room.databaseBuilder(context, TrackDatabase::class.java, "deeplayer-tracks.db")
      .fallbackToDestructiveMigration()
      .build()
  }

  @Provides @Singleton fun provideTrackDao(database: TrackDatabase): TrackDao = database.trackDao()

  @Provides
  @Singleton
  fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
    context.getSharedPreferences("deeplayer_prefs", Context.MODE_PRIVATE)
}
