package com.deeplayer.feature.alignmentorchestrator.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_offset")
data class UserOffsetEntity(@PrimaryKey val songId: String, val globalOffsetMs: Long)
