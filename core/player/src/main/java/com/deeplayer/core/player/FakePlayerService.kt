package com.deeplayer.core.player

import com.deeplayer.core.contracts.PlaybackState
import com.deeplayer.core.contracts.PlaybackStatus
import com.deeplayer.core.contracts.PlayerService
import com.deeplayer.core.contracts.TrackMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePlayerService : PlayerService {

  private val _playbackState = MutableStateFlow(PlaybackState())
  override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

  val playedTrackIds = mutableListOf<String>()

  private var fakeTrack: TrackMetadata? = null

  fun setFakeTrack(track: TrackMetadata) {
    fakeTrack = track
  }

  fun setPosition(positionMs: Long) {
    _playbackState.value = _playbackState.value.copy(positionMs = positionMs)
  }

  override fun play(trackId: String) {
    playedTrackIds.add(trackId)
    val track =
      fakeTrack
        ?: TrackMetadata(
          id = trackId,
          title = "Track $trackId",
          artist = "Artist",
          album = "Album",
          durationMs = 180_000L,
          filePath = "/music/$trackId.mp3",
        )
    _playbackState.value =
      PlaybackState(
        status = PlaybackStatus.PLAYING,
        positionMs = 0L,
        durationMs = track.durationMs,
        track = track,
      )
  }

  override fun pause() {
    _playbackState.value = _playbackState.value.copy(status = PlaybackStatus.PAUSED)
  }

  override fun seekTo(positionMs: Long) {
    _playbackState.value = _playbackState.value.copy(positionMs = positionMs)
  }

  override fun stop() {
    _playbackState.value = PlaybackState()
  }

  var lastSpeed: Float = 1.0f
    private set

  override fun setPlaybackSpeed(speed: Float) {
    lastSpeed = speed
  }
}
