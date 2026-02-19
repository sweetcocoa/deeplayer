package com.deeplayer.feature.alignmentorchestrator.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
  entities = [AlignmentCacheEntity::class, UserOffsetEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class AlignmentDatabase : RoomDatabase() {
  abstract fun alignmentCacheDao(): AlignmentCacheDao
}
