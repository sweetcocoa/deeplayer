package com.deeplayer.core.contracts

// --- Audio Preprocessing ---

data class PcmChunk(val data: FloatArray, val offsetMs: Long, val durationMs: Long) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PcmChunk) return false
    return data.contentEquals(other.data) &&
      offsetMs == other.offsetMs &&
      durationMs == other.durationMs
  }

  override fun hashCode(): Int {
    var result = data.contentHashCode()
    result = 31 * result + offsetMs.hashCode()
    result = 31 * result + durationMs.hashCode()
    return result
  }
}

// --- Alignment ---

data class AlignmentResult(
  val words: List<WordAlignment>,
  val lines: List<LineAlignment>,
  val overallConfidence: Float,
  val enhancedLrc: String,
)

data class WordAlignment(
  val word: String,
  val startMs: Long,
  val endMs: Long,
  val confidence: Float,
  val lineIndex: Int,
)

data class LineAlignment(
  val text: String,
  val startMs: Long,
  val endMs: Long,
  val wordAlignments: List<WordAlignment>,
)

sealed class AlignmentProgress {
  data class Processing(val chunkIndex: Int, val totalChunks: Int) : AlignmentProgress()

  data class PartialResult(val lines: List<LineAlignment>, val upToMs: Long) : AlignmentProgress()

  data class Complete(val result: AlignmentResult) : AlignmentProgress()

  data class Failed(val error: Throwable, val retriesLeft: Int) : AlignmentProgress()
}

// --- Playback ---

enum class PlaybackStatus {
  STOPPED,
  PLAYING,
  PAUSED,
  COMPLETED,
}

data class PlaybackState(
  val status: PlaybackStatus = PlaybackStatus.STOPPED,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val track: TrackMetadata? = null,
) {
  val isPlaying: Boolean
    get() = status == PlaybackStatus.PLAYING

  val isPaused: Boolean
    get() = status == PlaybackStatus.PAUSED
}

data class TrackMetadata(
  val id: String,
  val title: String,
  val artist: String,
  val album: String,
  val durationMs: Long,
  val filePath: String,
  val albumArtUri: String? = null,
)

// --- Whisper Transcription ---

data class TranscribedSegment(val text: String, val startMs: Long, val endMs: Long)

// --- Language ---

enum class Language {
  KO,
  EN,
  MIXED,
}

