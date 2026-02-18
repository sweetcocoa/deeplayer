package com.deeplayer.core.player

import com.deeplayer.core.contracts.PlaybackState
import com.deeplayer.core.contracts.PlayerService
import com.deeplayer.core.contracts.TrackMetadata
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FakePlayerServiceTest {

  private val fake = FakePlayerService()

  @Test
  fun `implements PlayerService interface`() {
    val service: PlayerService = fake
    assertThat(service).isNotNull()
  }

  @Test
  fun `initial state is idle`() {
    assertThat(fake.playbackState.value).isEqualTo(PlaybackState())
  }

  @Test
  fun `play creates default track when no fake track set`() {
    fake.play("42")

    val state = fake.playbackState.value
    assertThat(state.isPlaying).isTrue()
    assertThat(state.track).isNotNull()
    assertThat(state.track!!.id).isEqualTo("42")
    assertThat(state.track!!.title).isEqualTo("Track 42")
  }

  @Test
  fun `play uses fake track when set`() {
    val track =
      TrackMetadata(
        id = "99",
        title = "Custom Song",
        artist = "Custom Artist",
        album = "Custom Album",
        durationMs = 300_000L,
        filePath = "/music/custom.flac",
      )
    fake.setFakeTrack(track)
    fake.play("99")

    assertThat(fake.playbackState.value.track).isEqualTo(track)
    assertThat(fake.playbackState.value.durationMs).isEqualTo(300_000L)
  }

  @Test
  fun `setPosition updates playback state`() {
    fake.play("1")
    fake.setPosition(45_000L)

    assertThat(fake.playbackState.value.positionMs).isEqualTo(45_000L)
  }

  @Test
  fun `pause after play sets correct state`() {
    fake.play("1")
    fake.pause()

    assertThat(fake.playbackState.value.isPlaying).isFalse()
    assertThat(fake.playbackState.value.isPaused).isTrue()
    assertThat(fake.playbackState.value.track).isNotNull()
  }

  @Test
  fun `stop clears everything`() {
    fake.play("1")
    fake.seekTo(50_000L)
    fake.stop()

    assertThat(fake.playbackState.value.isPlaying).isFalse()
    assertThat(fake.playbackState.value.isPaused).isFalse()
    assertThat(fake.playbackState.value.track).isNull()
    assertThat(fake.playbackState.value.positionMs).isEqualTo(0L)
  }

  @Test
  fun `playedTrackIds tracks all play calls`() {
    fake.play("a")
    fake.play("b")
    fake.stop()
    fake.play("c")

    assertThat(fake.playedTrackIds).containsExactly("a", "b", "c").inOrder()
  }
}
