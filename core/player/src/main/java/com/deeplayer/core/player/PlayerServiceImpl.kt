package com.deeplayer.core.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.deeplayer.core.contracts.PlaybackState
import com.deeplayer.core.contracts.PlaybackStatus
import com.deeplayer.core.contracts.PlayerService
import com.deeplayer.core.contracts.TrackMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class PlayerServiceImpl
@Inject
constructor(@ApplicationContext private val context: Context, private val trackDao: TrackDao) :
  PlayerService {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  private val _playbackState = MutableStateFlow(PlaybackState())
  override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

  private var exoPlayer: ExoPlayer? = null
  private var positionUpdateJob: Job? = null
  private var currentTrack: TrackMetadata? = null

  private fun ensurePlayer(): ExoPlayer {
    return exoPlayer
      ?: ExoPlayer.Builder(context).build().also { player ->
        exoPlayer = player
        player.addListener(
          object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
              updateState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
              updateState()
              if (isPlaying) {
                startPositionUpdates()
              } else {
                stopPositionUpdates()
              }
            }
          }
        )
      }
  }

  override fun play(trackId: String) {
    val player = ensurePlayer()
    scope.launch {
      val track = resolveTrack(trackId)
      currentTrack = track
      if (track != null) {
        val mediaItem = MediaItem.fromUri(track.filePath)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
      }
    }
  }

  override fun pause() {
    exoPlayer?.pause()
  }

  override fun seekTo(positionMs: Long) {
    exoPlayer?.seekTo(positionMs)
    updateState()
  }

  override fun stop() {
    stopPositionUpdates()
    exoPlayer?.stop()
    exoPlayer?.release()
    exoPlayer = null
    currentTrack = null
    _playbackState.value = PlaybackState()
  }

  override fun setPlaybackSpeed(speed: Float) {
    exoPlayer?.setPlaybackParameters(
      androidx.media3.common.PlaybackParameters(speed.coerceIn(0.5f, 2.0f))
    )
  }

  private fun updateState() {
    val player = exoPlayer ?: return
    val status =
      when {
        player.isPlaying -> PlaybackStatus.PLAYING
        player.playbackState == Player.STATE_ENDED -> PlaybackStatus.COMPLETED
        player.playbackState == Player.STATE_READY -> PlaybackStatus.PAUSED
        else -> PlaybackStatus.STOPPED
      }
    _playbackState.value =
      PlaybackState(
        status = status,
        positionMs = player.currentPosition,
        durationMs = player.duration.coerceAtLeast(0L),
        track = currentTrack,
      )
  }

  private fun startPositionUpdates() {
    stopPositionUpdates()
    positionUpdateJob =
      scope.launch {
        while (isActive) {
          val player = exoPlayer ?: break
          _playbackState.value = _playbackState.value.copy(positionMs = player.currentPosition)
          delay(POSITION_UPDATE_INTERVAL_MS)
        }
      }
  }

  private fun stopPositionUpdates() {
    positionUpdateJob?.cancel()
    positionUpdateJob = null
  }

  private suspend fun resolveTrack(trackId: String): TrackMetadata? {
    val entity = trackDao.getTrackById(trackId) ?: return null
    return TrackMetadata(
      id = entity.id,
      title = entity.title,
      artist = entity.artist,
      album = entity.album,
      durationMs = entity.durationMs,
      filePath = entity.filePath,
      albumArtUri = entity.albumArtUri,
    )
  }

  companion object {
    private const val POSITION_UPDATE_INTERVAL_MS = 200L
  }
}
