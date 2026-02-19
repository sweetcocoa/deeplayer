package com.deeplayer.feature.alignmentorchestrator.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AlignmentCacheDao {

  @Query("SELECT * FROM alignment_cache WHERE songId = :songId LIMIT 1")
  suspend fun getBySongId(songId: String): AlignmentCacheEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: AlignmentCacheEntity)

  @Query("DELETE FROM alignment_cache WHERE songId = :songId")
  suspend fun deleteBySongId(songId: String)

  @Query("DELETE FROM alignment_cache WHERE modelVersion != :currentVersion")
  suspend fun invalidateByModelVersion(currentVersion: String)

  @Query("SELECT * FROM user_offset WHERE songId = :songId LIMIT 1")
  suspend fun getUserOffset(songId: String): UserOffsetEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertUserOffset(entity: UserOffsetEntity)
}
