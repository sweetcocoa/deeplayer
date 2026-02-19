package com.deeplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deeplayer.core.contracts.AlignmentOrchestrator
import com.deeplayer.core.contracts.AlignmentProgress
import com.deeplayer.core.contracts.LineAlignment
import com.deeplayer.core.contracts.PlaybackState
import com.deeplayer.core.contracts.PlaybackStatus
import com.deeplayer.core.contracts.PlayerService
import com.deeplayer.core.contracts.TrackMetadata
import com.deeplayer.core.player.EmbeddedLyricsReader
import com.deeplayer.core.player.FolderPreferences
import com.deeplayer.core.player.MediaStoreScanner
import com.deeplayer.core.player.TrackDao
import com.deeplayer.core.player.TrackEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class RepeatMode {
  OFF,
  ALL,
  ONE,
}

@HiltViewModel
class MainViewModel
@Inject
constructor(
  private val playerService: PlayerService,
  private val scanner: MediaStoreScanner,
  private val trackDao: TrackDao,
  private val alignmentOrchestrator: AlignmentOrchestrator,
  private val folderPreferences: FolderPreferences,
) : ViewModel() {

  private val _tracks = MutableStateFlow<List<TrackMetadata>>(emptyList())
  val tracks: StateFlow<List<TrackMetadata>> = _tracks

  val playbackState: StateFlow<PlaybackState> = playerService.playbackState

  private val _shuffleEnabled = MutableStateFlow(false)
  val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled

  private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
  val repeatMode: StateFlow<RepeatMode> = _repeatMode

  private val _currentLyrics = MutableStateFlow<List<LineAlignment>>(emptyList())
  val currentLyrics: StateFlow<List<LineAlignment>> = _currentLyrics

  private val _playbackSpeed = MutableStateFlow(1.0f)
  val playbackSpeed: StateFlow<Float> = _playbackSpeed

  private val _sleepTimerMinutes = MutableStateFlow(0)
  val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes

  private val _favorites = MutableStateFlow<Set<String>>(emptySet())
  val favorites: StateFlow<Set<String>> = _favorites

  private val _queue = MutableStateFlow<List<TrackMetadata>>(emptyList())
  val queue: StateFlow<List<TrackMetadata>> = _queue

  private val _alignmentProgress = MutableStateFlow<AlignmentProgress?>(null)
  val alignmentProgress: StateFlow<AlignmentProgress?> = _alignmentProgress

  private var _lastLoadedTrackId: TrackMetadata? = null
  private var alignmentJob: Job? = null

  init {
    // Observe track changes to load lyrics
    viewModelScope.launch {
      playbackState.collect { state ->
        if (state.track != null && state.track != _lastLoadedTrackId) {
          _lastLoadedTrackId = state.track
          _alignmentProgress.value = null
          alignmentJob?.cancel()
          loadLyricsForTrack(state.track!!.id)
        }
      }
    }
    // Auto-advance: when a track completes, play next based on repeat/shuffle
    viewModelScope.launch {
      playbackState.collect { state ->
        if (state.status == PlaybackStatus.COMPLETED && state.track != null) {
          onTrackCompleted(state.track!!)
        }
      }
    }
  }

  private fun onTrackCompleted(completedTrack: TrackMetadata) {
    val trackList = _tracks.value
    if (trackList.isEmpty()) return

    when (_repeatMode.value) {
      RepeatMode.ONE -> {
        // Replay the same track
        playerService.play(completedTrack.id)
      }
      RepeatMode.ALL -> {
        if (_shuffleEnabled.value) {
          val randomTrack = trackList.random()
          play(randomTrack.id)
        } else {
          val currentIndex = trackList.indexOfFirst { it.id == completedTrack.id }
          val nextIndex = (currentIndex + 1) % trackList.size
          play(trackList[nextIndex].id)
        }
      }
      RepeatMode.OFF -> {
        if (_shuffleEnabled.value) {
          val currentIndex = trackList.indexOfFirst { it.id == completedTrack.id }
          if (trackList.size > 1) {
            val remaining = trackList.filterIndexed { i, _ -> i != currentIndex }
            play(remaining.random().id)
          }
        } else {
          val currentIndex = trackList.indexOfFirst { it.id == completedTrack.id }
          if (currentIndex >= 0 && currentIndex < trackList.size - 1) {
            play(trackList[currentIndex + 1].id)
          }
          // If last track and no repeat, playback stops naturally
        }
      }
    }
  }

  private val _selectedFolders = MutableStateFlow<Set<String>>(emptySet())
  val selectedFolders: StateFlow<Set<String>> = _selectedFolders

  private val _availableFolders = MutableStateFlow<List<String>>(emptyList())
  val availableFolders: StateFlow<List<String>> = _availableFolders

  fun loadTracks() {
    viewModelScope.launch(Dispatchers.IO) {
      val folders = folderPreferences.getSelectedFolders()
      _selectedFolders.value = folders
      val scanned = scanner.scanAudioFiles(folders)
      trackDao.deleteAll()
      trackDao.insertAll(
        scanned.map {
          TrackEntity(
            id = it.id,
            title = it.title,
            artist = it.artist,
            album = it.album,
            durationMs = it.durationMs,
            filePath = it.filePath,
            albumArtUri = it.albumArtUri,
          )
        }
      )
      _tracks.value = scanned
      // Compute available folders from all tracks (unfiltered scan)
      val allTracks = scanner.scanAudioFiles()
      _availableFolders.value =
        allTracks.map { it.filePath.substringBeforeLast('/') }.distinct().sorted()
    }
  }

  fun updateSelectedFolders(folders: Set<String>) {
    folderPreferences.setSelectedFolders(folders)
    _selectedFolders.value = folders
    loadTracks()
  }

  fun play(trackId: String) {
    playerService.play(trackId)
    // Auto-build queue from current position in track list
    val trackList = _tracks.value
    val index = trackList.indexOfFirst { it.id == trackId }
    if (index >= 0) {
      _queue.value = trackList.subList(index + 1, trackList.size)
    }
  }

  fun pause() {
    playerService.pause()
  }

  fun resume() {
    val track = playbackState.value.track ?: return
    playerService.play(track.id)
  }

  fun next() {
    val currentTrack = playbackState.value.track ?: return
    val trackList = _tracks.value
    if (trackList.isEmpty()) return
    if (_shuffleEnabled.value) {
      val remaining = trackList.filter { it.id != currentTrack.id }
      if (remaining.isNotEmpty()) play(remaining.random().id)
      return
    }
    val currentIndex = trackList.indexOfFirst { it.id == currentTrack.id }
    if (currentIndex >= 0) {
      val nextIndex =
        if (_repeatMode.value == RepeatMode.ALL) {
          (currentIndex + 1) % trackList.size
        } else {
          currentIndex + 1
        }
      if (nextIndex in trackList.indices) {
        play(trackList[nextIndex].id)
      }
    }
  }

  fun previous() {
    val currentTrack = playbackState.value.track ?: return
    val trackList = _tracks.value
    if (trackList.isEmpty()) return
    if (_shuffleEnabled.value) {
      val remaining = trackList.filter { it.id != currentTrack.id }
      if (remaining.isNotEmpty()) play(remaining.random().id)
      return
    }
    val currentIndex = trackList.indexOfFirst { it.id == currentTrack.id }
    if (currentIndex >= 0) {
      val prevIndex =
        if (_repeatMode.value == RepeatMode.ALL) {
          (currentIndex - 1 + trackList.size) % trackList.size
        } else {
          currentIndex - 1
        }
      if (prevIndex in trackList.indices) {
        play(trackList[prevIndex].id)
      }
    }
  }

  fun seekTo(positionMs: Long) {
    playerService.seekTo(positionMs)
  }

  fun toggleShuffle() {
    _shuffleEnabled.value = !_shuffleEnabled.value
  }

  fun cycleRepeatMode() {
    _repeatMode.value =
      when (_repeatMode.value) {
        RepeatMode.OFF -> RepeatMode.ALL
        RepeatMode.ALL -> RepeatMode.ONE
        RepeatMode.ONE -> RepeatMode.OFF
      }
  }

  fun setPlaybackSpeed(speed: Float) {
    _playbackSpeed.value = speed
    playerService.setPlaybackSpeed(speed)
  }

  fun setSleepTimer(minutes: Int) {
    _sleepTimerMinutes.value = minutes
    if (minutes > 0) {
      viewModelScope.launch {
        delay(minutes * 60_000L)
        if (_sleepTimerMinutes.value > 0) {
          pause()
          _sleepTimerMinutes.value = 0
        }
      }
    }
  }

  fun toggleFavorite(trackId: String) {
    val current = _favorites.value.toMutableSet()
    if (current.contains(trackId)) {
      current.remove(trackId)
    } else {
      current.add(trackId)
    }
    _favorites.value = current
  }

  fun addToQueue(track: TrackMetadata) {
    _queue.value = _queue.value + track
  }

  fun removeFromQueue(index: Int) {
    val current = _queue.value.toMutableList()
    if (index in current.indices) {
      current.removeAt(index)
      _queue.value = current
    }
  }

  fun clearQueue() {
    _queue.value = emptyList()
  }

  private fun loadLyricsForTrack(trackId: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val track = trackDao.getTrackById(trackId) ?: return@launch
      // 1) Try external .lrc sidecar file
      val lrcPath = track.filePath.replaceAfterLast('.', "lrc")
      val lrcFile = java.io.File(lrcPath)
      if (lrcFile.exists()) {
        _currentLyrics.value = parseLrcFile(lrcFile)
        return@launch
      }
      // 2) Try embedded lyrics (ID3 USLT tag)
      val embedded = EmbeddedLyricsReader.readLyrics(track.filePath)
      if (!embedded.isNullOrBlank()) {
        _currentLyrics.value = parseEmbeddedLyrics(embedded)
        return@launch
      }
      _currentLyrics.value = emptyList()
    }
  }

  private fun parseEmbeddedLyrics(text: String): List<LineAlignment> {
    // Check if the embedded text is LRC-formatted (has timestamps)
    val lrcPattern = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")
    val hasTimestamps = text.lines().any { lrcPattern.containsMatchIn(it) }
    if (hasTimestamps) {
      return parseLrcText(text)
    }
    // Plain unsynced lyrics: one LineAlignment per non-empty line with no timing
    return text
      .lines()
      .filter { it.isNotBlank() }
      .mapIndexed { index, line ->
        LineAlignment(text = line.trim(), startMs = 0L, endMs = 0L, wordAlignments = emptyList())
      }
  }

  private fun parseLrcFile(file: java.io.File): List<LineAlignment> = parseLrcText(file.readText())

  private fun parseLrcText(text: String): List<LineAlignment> {
    val pattern = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")
    return text
      .lines()
      .mapNotNull { line ->
        pattern.find(line)?.let { match ->
          val min = match.groupValues[1].toLongOrNull() ?: return@let null
          val sec = match.groupValues[2].toLongOrNull() ?: return@let null
          val msStr = match.groupValues[3]
          val ms =
            if (msStr.length == 2) (msStr.toLongOrNull() ?: 0) * 10 else msStr.toLongOrNull() ?: 0
          val text = match.groupValues[4].trim()
          if (text.isEmpty()) return@let null
          val startMs = min * 60_000 + sec * 1_000 + ms
          LineAlignment(
            text = text,
            startMs = startMs,
            endMs = startMs + 5000L,
            wordAlignments = emptyList(),
          )
        }
      }
      .let { lines ->
        lines.mapIndexed { index, line ->
          if (index < lines.size - 1) {
            line.copy(endMs = lines[index + 1].startMs)
          } else {
            line
          }
        }
      }
  }

  fun startAlignment() {
    val track = playbackState.value.track ?: return
    val lyrics = _currentLyrics.value
    // Extract plain text lines for alignment; use existing lyrics or skip if none
    val lyricsText = lyrics.map { it.text }.filter { it.isNotBlank() }
    if (lyricsText.isEmpty()) return

    alignmentJob?.cancel()
    alignmentJob =
      viewModelScope.launch {
        alignmentOrchestrator
          .requestAlignment(songId = track.id, audioPath = track.filePath, lyrics = lyricsText)
          .collect { progress ->
            _alignmentProgress.value = progress
            if (progress is AlignmentProgress.Complete) {
              _currentLyrics.value = progress.result.lines
            }
          }
      }
  }
}
