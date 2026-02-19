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
  val albumArtUri: String? = null,
)

@Dao
interface TrackDao {
  @Query("SELECT * FROM tracks ORDER BY title ASC") suspend fun getAllTracks(): List<TrackEntity>

  @Query("SELECT * FROM tracks WHERE id = :trackId")
  suspend fun getTrackById(trackId: String): TrackEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(tracks: List<TrackEntity>)

  @Query("DELETE FROM tracks") suspend fun deleteAll()

  @Query("SELECT DISTINCT album FROM tracks WHERE album != 'Unknown' ORDER BY album ASC")
  suspend fun getDistinctAlbums(): List<String>

  @Query("SELECT DISTINCT artist FROM tracks WHERE artist != 'Unknown' ORDER BY artist ASC")
  suspend fun getDistinctArtists(): List<String>

  @Query("SELECT * FROM tracks WHERE album = :album ORDER BY title ASC")
  suspend fun getTracksByAlbum(album: String): List<TrackEntity>

  @Query("SELECT * FROM tracks WHERE artist = :artist ORDER BY title ASC")
  suspend fun getTracksByArtist(artist: String): List<TrackEntity>
}
