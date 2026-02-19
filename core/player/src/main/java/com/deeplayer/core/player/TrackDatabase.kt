package com.deeplayer.core.player

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TrackEntity::class], version = 2, exportSchema = false)
abstract class TrackDatabase : RoomDatabase() {
  abstract fun trackDao(): TrackDao
}
