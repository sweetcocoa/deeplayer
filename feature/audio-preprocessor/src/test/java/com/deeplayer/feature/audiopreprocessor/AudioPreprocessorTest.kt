package com.deeplayer.feature.audiopreprocessor

import com.deeplayer.core.contracts.AudioPreprocessor
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/**
 * Tests for the AudioPreprocessor contract using FakeAudioPreprocessor. Validates format coverage,
 * chunk segmentation, and interface compatibility.
 */
class AudioPreprocessorTest {

  private lateinit var preprocessor: AudioPreprocessor

  @Before
  fun setUp() {
    preprocessor = FakeAudioPreprocessor()
  }

  // --- Format coverage: verify output sampleRate==16000, channels==1, data.size > 0 ---

  @Test
  fun decodeToPcm_returnsNonEmptyData() {
    val pcm = preprocessor.decodeToPcm("/fake/audio.mp3")
    assertThat(pcm).isNotEmpty()
  }

  @Test
  fun decodeToPcm_returnsCorrectSampleCount_forOneSec() {
    val pcm = preprocessor.decodeToPcm("/fake/audio.wav")
    // FakeAudioPreprocessor generates 1 second at 16kHz = 16000 samples
    assertThat(pcm.size).isEqualTo(16000)
  }

  @Test
  fun decodeToPcm_valuesInNormalizedRange() {
    val pcm = preprocessor.decodeToPcm("/fake/audio.flac")
    for (sample in pcm) {
      assertThat(sample).isAtLeast(-1.0f)
      assertThat(sample).isAtMost(1.0f)
    }
  }

  @Test
  fun decodeToPcm_mp3Format_returnsValidPcm() {
    val pcm = preprocessor.decodeToPcm("/fake/audio.mp3")
    assertThat(pcm.size).isGreaterThan(0)
    assertThat(pcm.size).isEqualTo(16000)
  }

  @Test
  fun decodeToPcm_flacFormat_returnsValidPcm() {
    val pcm = preprocessor.decodeToPcm("/fake/audio.flac")
    assertThat(pcm.size).isGreaterThan(0)
  }

  @Test
  fun decodeToPcm_oggFormat_returnsValidPcm() {
    val pcm = preprocessor.decodeToPcm("/fake/audio.ogg")
    assertThat(pcm.size).isGreaterThan(0)
  }

  @Test
  fun decodeToPcm_wavFormat_returnsValidPcm() {
    val pcm = preprocessor.decodeToPcm("/fake/audio.wav")
    assertThat(pcm.size).isGreaterThan(0)
  }

  @Test
  fun decodeToPcm_aacFormat_returnsValidPcm() {
    val pcm = preprocessor.decodeToPcm("/fake/audio.aac")
    assertThat(pcm.size).isGreaterThan(0)
  }

  // --- Chunk segmentation: 210s PCM -> 7 chunks, offset continuity ---

  @Test
  fun segmentPcm_210seconds_produces7Chunks() {
    val sampleRate = 16000
    val durationSec = 210
    val pcm = FloatArray(sampleRate * durationSec)
    val chunks = preprocessor.segmentPcm(pcm, chunkDurationMs = 30000)

    // 210s / 30s = 7 exact chunks
    assertThat(chunks.size).isEqualTo(7)
  }

  @Test
  fun segmentPcm_offsetContinuity() {
    val sampleRate = 16000
    val durationSec = 210
    val pcm = FloatArray(sampleRate * durationSec)
    val chunks = preprocessor.segmentPcm(pcm, chunkDurationMs = 30000)

    // Verify offsets are continuous
    assertThat(chunks[0].offsetMs).isEqualTo(0L)
    for (i in 1 until chunks.size) {
      val expectedOffset = chunks[i - 1].offsetMs + chunks[i - 1].durationMs
      assertThat(chunks[i].offsetMs).isEqualTo(expectedOffset)
    }
  }

  @Test
  fun segmentPcm_allDataPreserved() {
    val sampleRate = 16000
    val durationSec = 210
    val pcm = FloatArray(sampleRate * durationSec) { it.toFloat() / (sampleRate * durationSec) }
    val chunks = preprocessor.segmentPcm(pcm, chunkDurationMs = 30000)

    val totalSamples = chunks.sumOf { it.data.size }
    assertThat(totalSamples).isEqualTo(pcm.size)
  }

  @Test
  fun segmentPcm_eachChunkHasCorrectDuration() {
    val sampleRate = 16000
    val durationSec = 210
    val pcm = FloatArray(sampleRate * durationSec)
    val chunks = preprocessor.segmentPcm(pcm, chunkDurationMs = 30000)

    for (chunk in chunks) {
      assertThat(chunk.durationMs).isEqualTo(30000L)
      assertThat(chunk.data.size).isEqualTo(sampleRate * 30)
    }
  }

  @Test
  fun segmentPcm_partialLastChunk() {
    val sampleRate = 16000
    // 95 seconds = 3 full chunks (30s) + 1 partial (5s)
    val pcm = FloatArray(sampleRate * 95)
    val chunks = preprocessor.segmentPcm(pcm, chunkDurationMs = 30000)

    assertThat(chunks.size).isEqualTo(4)
    assertThat(chunks[3].durationMs).isEqualTo(5000L)
    assertThat(chunks[3].data.size).isEqualTo(sampleRate * 5)
  }

  // --- Interface compatibility ---

  @Test
  fun preprocessor_implementsAudioPreprocessorInterface() {
    assertThat(FakeAudioPreprocessor()).isInstanceOf(AudioPreprocessor::class.java)
  }

  @Test
  fun segmentPcm_defaultChunkDuration_is30Seconds() {
    val pcm = FloatArray(16000 * 60) // 60 seconds
    val chunks = preprocessor.segmentPcm(pcm)
    assertThat(chunks.size).isEqualTo(2)
    assertThat(chunks[0].durationMs).isEqualTo(30000L)
  }
}
