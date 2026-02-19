package com.deeplayer.core.player

import android.content.ContentResolver
import android.database.MatrixCursor
import android.provider.MediaStore
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaStoreScannerTest {

  private val contentResolver: ContentResolver = mockk()
  private val scanner = MediaStoreScanner(contentResolver)

  @Test
  fun `scanAudioFiles returns tracks from MediaStore`() {
    val columns =
      arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.MIME_TYPE,
      )
    val cursor = MatrixCursor(columns)
    cursor.addRow(
      arrayOf(1L, "Song A", "Artist A", "Album A", 100L, 210000L, "/music/song_a.mp3", "audio/mpeg")
    )
    cursor.addRow(
      arrayOf(
        2L,
        "Song B",
        "Artist B",
        "Album B",
        101L,
        180000L,
        "/music/song_b.flac",
        "audio/flac",
      )
    )

    every {
      contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        any(),
        any<String>(),
        isNull(),
        any<String>(),
      )
    } returns cursor

    val tracks = scanner.scanAudioFiles()

    assertThat(tracks).hasSize(2)
    assertThat(tracks[0].id).isEqualTo("1")
    assertThat(tracks[0].title).isEqualTo("Song A")
    assertThat(tracks[0].artist).isEqualTo("Artist A")
    assertThat(tracks[0].durationMs).isEqualTo(210000L)
    assertThat(tracks[0].filePath).isEqualTo("/music/song_a.mp3")
    assertThat(tracks[1].id).isEqualTo("2")
    assertThat(tracks[1].title).isEqualTo("Song B")
  }

  @Test
  fun `scanAudioFiles returns empty list when no tracks found`() {
    every {
      contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        any(),
        any<String>(),
        isNull(),
        any<String>(),
      )
    } returns null

    val tracks = scanner.scanAudioFiles()

    assertThat(tracks).isEmpty()
  }

  @Test
  fun `scanAudioFiles with folder filter returns only matching tracks`() {
    val columns =
      arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.MIME_TYPE,
      )
    val cursor = MatrixCursor(columns)
    cursor.addRow(
      arrayOf(
        1L,
        "Song A",
        "Artist A",
        "Album A",
        100L,
        210000L,
        "/storage/Music/pop/song_a.mp3",
        "audio/mpeg",
      )
    )
    cursor.addRow(
      arrayOf(
        2L,
        "Song B",
        "Artist B",
        "Album B",
        101L,
        180000L,
        "/storage/Music/rock/song_b.flac",
        "audio/flac",
      )
    )
    cursor.addRow(
      arrayOf(
        3L,
        "Song C",
        "Artist C",
        "Album C",
        102L,
        200000L,
        "/storage/Downloads/song_c.mp3",
        "audio/mpeg",
      )
    )

    every {
      contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        any(),
        any<String>(),
        isNull(),
        any<String>(),
      )
    } returns cursor

    val tracks = scanner.scanAudioFiles(setOf("/storage/Music"))

    assertThat(tracks).hasSize(2)
    assertThat(tracks[0].filePath).startsWith("/storage/Music")
    assertThat(tracks[1].filePath).startsWith("/storage/Music")
  }

  @Test
  fun `scanAudioFiles with empty folder set returns all tracks`() {
    val columns =
      arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.MIME_TYPE,
      )
    val cursor = MatrixCursor(columns)
    cursor.addRow(
      arrayOf(
        1L,
        "Song A",
        "Artist A",
        "Album A",
        100L,
        210000L,
        "/storage/Music/song_a.mp3",
        "audio/mpeg",
      )
    )
    cursor.addRow(
      arrayOf(
        2L,
        "Song B",
        "Artist B",
        "Album B",
        101L,
        180000L,
        "/storage/Downloads/song_b.flac",
        "audio/flac",
      )
    )

    every {
      contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        any(),
        any<String>(),
        isNull(),
        any<String>(),
      )
    } returns cursor

    val tracks = scanner.scanAudioFiles(emptySet())

    assertThat(tracks).hasSize(2)
  }

  @Test
  fun `scanAudioFiles filters include subfolders`() {
    val columns =
      arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.MIME_TYPE,
      )
    val cursor = MatrixCursor(columns)
    cursor.addRow(
      arrayOf(
        1L,
        "Song A",
        "Artist A",
        "Album A",
        100L,
        210000L,
        "/storage/Music/kpop/2024/song_a.mp3",
        "audio/mpeg",
      )
    )
    cursor.addRow(
      arrayOf(
        2L,
        "Song B",
        "Artist B",
        "Album B",
        101L,
        180000L,
        "/storage/Other/song_b.flac",
        "audio/flac",
      )
    )

    every {
      contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        any(),
        any<String>(),
        isNull(),
        any<String>(),
      )
    } returns cursor

    val tracks = scanner.scanAudioFiles(setOf("/storage/Music"))

    assertThat(tracks).hasSize(1)
    assertThat(tracks[0].title).isEqualTo("Song A")
  }

  @Test
  fun `scanAudioFiles handles empty cursor`() {
    val columns =
      arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.MIME_TYPE,
      )
    val cursor = MatrixCursor(columns)

    every {
      contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        any(),
        any<String>(),
        isNull(),
        any<String>(),
      )
    } returns cursor

    val tracks = scanner.scanAudioFiles()

    assertThat(tracks).isEmpty()
  }
}
