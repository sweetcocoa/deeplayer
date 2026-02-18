package com.deeplayer.core.contracts

import kotlinx.coroutines.flow.Flow

interface AlignmentOrchestrator {
  /** Start lyrics alignment in the background. Returns cached result immediately if available. */
  fun requestAlignment(
    songId: String,
    audioPath: String,
    lyrics: List<String>,
    language: Language = Language.KO,
  ): Flow<AlignmentProgress>

  /** Retrieve a cached alignment result. */
  suspend fun getCachedAlignment(songId: String): AlignmentResult?

  /** Save a user-adjusted global offset. */
  suspend fun saveUserOffset(songId: String, globalOffsetMs: Long)

  /** Invalidate cached results when the model is updated. */
  suspend fun invalidateCache(modelVersion: String)
}
