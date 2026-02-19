package com.deeplayer.feature.audiopreprocessor

import com.deeplayer.core.contracts.AudioPreprocessor
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Before
import org.junit.Test

/** Tests that FakeAudioPreprocessor returns valid, deterministic data for use in other modules. */
class FakeAudioPreprocessorTest {

  private lateinit var fake: FakeAudioPreprocessor

  @Before
  fun setUp() {
    fake = FakeAudioPreprocessor()
  }

  @Test
  fun implementsAudioPreprocessorInterface() {
    assertThat(fake).isInstanceOf(AudioPreprocessor::class.java)
  }

  @Test
  fun decodeToPcm_returns16kHzMonoPcm() {
    val pcm = fake.decodeToPcm("/any/path.mp3")
    // 1 second at 16kHz
    assertThat(pcm.size).isEqualTo(16000)
  }

  @Test
  fun decodeToPcm_returns440HzSineWave() {
    val pcm = fake.decodeToPcm("/any/path.wav")
    // Verify it's a 440Hz sine wave: check zero crossings
    var zeroCrossings = 0
    for (i in 1 until pcm.size) {
      if ((pcm[i - 1] >= 0 && pcm[i] < 0) || (pcm[i - 1] < 0 && pcm[i] >= 0)) {
        zeroCrossings++
      }
    }
    // 440Hz sine wave has ~880 zero crossings per second (2 per cycle)
    assertThat(zeroCrossings).isAtLeast(860)
    assertThat(zeroCrossings).isAtMost(900)
  }

  @Test
  fun decodeToPcm_isDeterministic() {
    val pcm1 = fake.decodeToPcm("/path/a.mp3")
    val pcm2 = fake.decodeToPcm("/path/b.flac")
    assertThat(pcm1).isEqualTo(pcm2)
  }

  @Test
  fun decodeToPcm_valuesNormalized() {
    val pcm = fake.decodeToPcm("/any/path.ogg")
    val maxAbs = pcm.maxOf { abs(it) }
    assertThat(maxAbs).isAtMost(1.0f)
    assertThat(maxAbs).isAtLeast(0.9f) // Sine wave amplitude should be close to 1.0
  }

  @Test
  fun segmentPcm_preservesAllData() {
    val pcm = FloatArray(16000 * 65) { it.toFloat() }
    val chunks = fake.segmentPcm(pcm, chunkDurationMs = 30000)

    val reconstructed = chunks.flatMap { it.data.toList() }.toFloatArray()
    assertThat(reconstructed.size).isEqualTo(pcm.size)
    assertThat(reconstructed).isEqualTo(pcm)
  }

  @Test
  fun segmentPcm_offsetsAreCorrect() {
    val pcm = FloatArray(16000 * 65)
    val chunks = fake.segmentPcm(pcm, chunkDurationMs = 30000)

    assertThat(chunks.size).isEqualTo(3) // 30s + 30s + 5s
    assertThat(chunks[0].offsetMs).isEqualTo(0L)
    assertThat(chunks[1].offsetMs).isEqualTo(30000L)
    assertThat(chunks[2].offsetMs).isEqualTo(60000L)
    assertThat(chunks[2].durationMs).isEqualTo(5000L)
  }

  @Test
  fun generate440HzSine_customDuration() {
    val pcm = FakeAudioPreprocessor.generate440HzSine(durationSeconds = 2.5f, sampleRate = 16000)
    assertThat(pcm.size).isEqualTo(40000)
  }
}
