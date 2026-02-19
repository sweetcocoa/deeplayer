package com.deeplayer.feature.alignmentorchestrator.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alignment_cache")
data class AlignmentCacheEntity(
  @PrimaryKey val songId: String,
  val resultJson: String,
  /** Composite pipeline version string (model|aligner|g2p) for cache invalidation. */
  val modelVersion: String,
  val createdAt: Long = System.currentTimeMillis(),
)
