package com.deeplayer.core.contracts

import kotlinx.coroutines.flow.StateFlow

interface PlayerService {
  /** Observable playback state (includes position, duration, track info). */
  val playbackState: StateFlow<PlaybackState>

  /** Start playback for the given track. */
  fun play(trackId: String)

  /** Pause playback. */
  fun pause()

  /** Seek to a position in milliseconds. */
  fun seekTo(positionMs: Long)

  /** Stop playback and release resources. */
  fun stop()
}
