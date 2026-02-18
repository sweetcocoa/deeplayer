package com.deeplayer.core.player

import com.deeplayer.core.contracts.PlaybackState
import com.deeplayer.core.contracts.TrackMetadata
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tests playback lifecycle using FakePlayerService. */
class PlaybackLifecycleTest {

  private val fakePlayer = FakePlayerService()

  private val testTrack =
    TrackMetadata(
      id = "1",
      title = "Test Song",
      artist = "Test Artist",
      album = "Test Album",
      durationMs = 200_000L,
      filePath = "/music/test.mp3",
    )

  @Test
  fun `initial state is idle`() {
    val state = fakePlayer.playbackState.value
    assertThat(state.isPlaying).isFalse()
    assertThat(state.isPaused).isFalse()
    assertThat(state.track).isNull()
    assertThat(state.positionMs).isEqualTo(0L)
  }

  @Test
  fun `play transitions to playing state`() {
    fakePlayer.setFakeTrack(testTrack)
    fakePlayer.play("1")

    val state = fakePlayer.playbackState.value
    assertThat(state.isPlaying).isTrue()
    assertThat(state.isPaused).isFalse()
    assertThat(state.track).isEqualTo(testTrack)
    assertThat(state.durationMs).isEqualTo(200_000L)
  }

  @Test
  fun `pause transitions from playing to paused`() {
    fakePlayer.setFakeTrack(testTrack)
    fakePlayer.play("1")
    fakePlayer.pause()

    val state = fakePlayer.playbackState.value
    assertThat(state.isPlaying).isFalse()
    assertThat(state.isPaused).isTrue()
    assertThat(state.track).isEqualTo(testTrack)
  }

  @Test
  fun `seekTo updates position`() {
    fakePlayer.setFakeTrack(testTrack)
    fakePlayer.play("1")
    fakePlayer.seekTo(60_000L)

    val state = fakePlayer.playbackState.value
    assertThat(state.positionMs).isEqualTo(60_000L)
  }

  @Test
  fun `stop resets all state`() {
    fakePlayer.setFakeTrack(testTrack)
    fakePlayer.play("1")
    fakePlayer.seekTo(30_000L)
    fakePlayer.stop()

    val state = fakePlayer.playbackState.value
    assertThat(state.isPlaying).isFalse()
    assertThat(state.isPaused).isFalse()
    assertThat(state.positionMs).isEqualTo(0L)
    assertThat(state.track).isNull()
  }

  @Test
  fun `play pause seek stop full lifecycle`() {
    fakePlayer.setFakeTrack(testTrack)

    fakePlayer.play("1")
    assertThat(fakePlayer.playbackState.value.isPlaying).isTrue()

    fakePlayer.pause()
    assertThat(fakePlayer.playbackState.value.isPaused).isTrue()

    fakePlayer.seekTo(90_000L)
    assertThat(fakePlayer.playbackState.value.positionMs).isEqualTo(90_000L)

    fakePlayer.stop()
    assertThat(fakePlayer.playbackState.value).isEqualTo(PlaybackState())
  }

  @Test
  fun `play records track ids`() {
    fakePlayer.play("1")
    fakePlayer.play("2")
    fakePlayer.play("3")

    assertThat(fakePlayer.playedTrackIds).containsExactly("1", "2", "3").inOrder()
  }
}
