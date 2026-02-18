package com.deeplayer.core.player

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "tracks")
data class TrackEntity(
  @PrimaryKey val id: String,
  val title: String,
  val artist: String,
  val album: String,
  val durationMs: Long,
  val filePath: String,
)

@Dao
interface TrackDao {
  @Query("SELECT * FROM tracks ORDER BY title ASC") suspend fun getAllTracks(): List<TrackEntity>

  @Query("SELECT * FROM tracks WHERE id = :trackId")
  suspend fun getTrackById(trackId: String): TrackEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(tracks: List<TrackEntity>)

  @Query("DELETE FROM tracks") suspend fun deleteAll()
}
