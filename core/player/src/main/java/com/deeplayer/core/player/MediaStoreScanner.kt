package com.deeplayer.core.player

import android.content.ContentResolver
import android.database.Cursor
import android.provider.MediaStore
import com.deeplayer.core.contracts.TrackMetadata
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreScanner @Inject constructor(private val contentResolver: ContentResolver) {

  fun scanAudioFiles(): List<TrackMetadata> {
    val tracks = mutableListOf<TrackMetadata>()
    val projection =
      arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.MIME_TYPE,
      )

    val selection =
      "${MediaStore.Audio.Media.MIME_TYPE} IN (${SUPPORTED_MIME_TYPES.joinToString { "'$it'" }})"

    val cursor: Cursor? =
      contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        null,
        "${MediaStore.Audio.Media.TITLE} ASC",
      )

    cursor?.use {
      val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
      val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
      val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
      val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
      val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
      val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

      while (it.moveToNext()) {
        tracks.add(
          TrackMetadata(
            id = it.getLong(idCol).toString(),
            title = it.getString(titleCol) ?: "Unknown",
            artist = it.getString(artistCol) ?: "Unknown",
            album = it.getString(albumCol) ?: "Unknown",
            durationMs = it.getLong(durationCol),
            filePath = it.getString(dataCol) ?: "",
          )
        )
      }
    }
    return tracks
  }

  companion object {
    val SUPPORTED_MIME_TYPES =
      listOf("audio/mpeg", "audio/flac", "audio/ogg", "audio/x-wav", "audio/aac")
  }
}
