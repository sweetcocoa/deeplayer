package com.deeplayer.feature.alignmentorchestrator

import app.cash.turbine.test
import com.deeplayer.core.contracts.AlignmentProgress
import com.deeplayer.core.contracts.AlignmentResult
import com.deeplayer.core.contracts.AudioPreprocessor
import com.deeplayer.core.contracts.Language
import com.deeplayer.core.contracts.LineAlignment
import com.deeplayer.core.contracts.PcmChunk
import com.deeplayer.core.contracts.TranscribedSegment
import com.deeplayer.core.contracts.WhisperTranscriber
import com.deeplayer.core.contracts.WordAlignment
import com.deeplayer.feature.alignmentorchestrator.cache.AlignmentCacheDao
import com.deeplayer.feature.alignmentorchestrator.cache.AlignmentCacheEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlignmentOrchestratorImplTest {

  private val audioPreprocessor = mockk<AudioPreprocessor>()
  private val whisperTranscriber = mockk<WhisperTranscriber>()
  private val cacheDao = mockk<AlignmentCacheDao>(relaxUnitFun = true)

  private lateinit var orchestrator: AlignmentOrchestratorImpl

  private val dummyResult =
    AlignmentResult(
      words =
        listOf(
          WordAlignment(word = "hello", startMs = 0, endMs = 500, confidence = 0.9f, lineIndex = 0)
        ),
      lines =
        listOf(
          LineAlignment(
            text = "hello",
            startMs = 0,
            endMs = 500,
            wordAlignments =
              listOf(
                WordAlignment(
                  word = "hello",
                  startMs = 0,
                  endMs = 500,
                  confidence = 0.9f,
                  lineIndex = 0,
                )
              ),
          )
        ),
      overallConfidence = 0.9f,
      enhancedLrc = "[00:00.00]hello",
    )

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    orchestrator =
      AlignmentOrchestratorImpl(audioPreprocessor, whisperTranscriber, cacheDao)
    mockkObject(AlignmentResultSerializer)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkObject(AlignmentResultSerializer)
  }

  // --- Cache tests ---

  @Test
  fun `cache hit with matching pipeline version returns cached result`() = runTest {
    every { AlignmentResultSerializer.deserialize("cached-json") } returns dummyResult

    coEvery { cacheDao.getBySongId("song1") } returns
      AlignmentCacheEntity(
        songId = "song1",
        resultJson = "cached-json",
        modelVersion = AlignmentOrchestratorImpl.PIPELINE_VERSION,
      )

    orchestrator.requestAlignment("song1", "/audio.mp3", listOf("hello"), Language.EN).test {
      val progress = awaitItem()
      assertThat(progress).isInstanceOf(AlignmentProgress.Complete::class.java)
      val complete = progress as AlignmentProgress.Complete
      assertThat(complete.result.overallConfidence).isEqualTo(0.9f)
      awaitComplete()
    }

    coVerify(exactly = 0) { cacheDao.deleteBySongId(any()) }
    coVerify(exactly = 0) { audioPreprocessor.decodeToPcm(any()) }
  }

  @Test
  fun `cache hit with mismatched version deletes cache and re-runs alignment`() = runTest {
    coEvery { cacheDao.getBySongId("song1") } returns
      AlignmentCacheEntity(songId = "song1", resultJson = "stale", modelVersion = "old-version")

    every { audioPreprocessor.decodeToPcm(any()) } returns FloatArray(16000)
    every { audioPreprocessor.segmentPcm(any()) } returns
      listOf(PcmChunk(data = FloatArray(16000), offsetMs = 0, durationMs = 1000))
    every { whisperTranscriber.transcribe(any(), any()) } returns
      listOf(TranscribedSegment(text = "hello", startMs = 0, endMs = 500))
    every { AlignmentResultSerializer.serialize(any()) } returns "serialized"

    orchestrator.requestAlignment("song1", "/audio.mp3", listOf("hello"), Language.EN).test {
      awaitItem() // Processing
      val complete = awaitItem() as AlignmentProgress.Complete
      assertThat(complete.result.lines).hasSize(1)
      awaitComplete()
    }

    coVerify { cacheDao.deleteBySongId("song1") }
  }

  @Test
  fun `cache insert uses pipeline version`() = runTest {
    coEvery { cacheDao.getBySongId(any()) } returns null
    every { audioPreprocessor.decodeToPcm(any()) } returns FloatArray(16000)
    every { audioPreprocessor.segmentPcm(any()) } returns
      listOf(PcmChunk(data = FloatArray(16000), offsetMs = 0, durationMs = 1000))
    every { whisperTranscriber.transcribe(any(), any()) } returns
      listOf(TranscribedSegment(text = "hello", startMs = 0, endMs = 500))
    every { AlignmentResultSerializer.serialize(any()) } returns "serialized"

    val entitySlot = slot<AlignmentCacheEntity>()
    coEvery { cacheDao.insert(capture(entitySlot)) } returns Unit

    orchestrator.requestAlignment("song1", "/audio.mp3", listOf("hello"), Language.EN).test {
      awaitItem() // Processing
      awaitItem() // Complete
      awaitComplete()
    }

    assertThat(entitySlot.captured.modelVersion)
      .isEqualTo(AlignmentOrchestratorImpl.PIPELINE_VERSION)
  }

  // --- Whisper pipeline tests ---

  @Test
  fun `whisper pipeline uses transcriber and matcher`() = runTest {
    coEvery { cacheDao.getBySongId(any()) } returns null
    every { audioPreprocessor.decodeToPcm(any()) } returns FloatArray(16000)
    every { audioPreprocessor.segmentPcm(any()) } returns
      listOf(PcmChunk(data = FloatArray(16000), offsetMs = 0, durationMs = 1000))
    every { whisperTranscriber.transcribe(any(), any()) } returns
      listOf(TranscribedSegment(text = "hello", startMs = 0, endMs = 500))
    every { AlignmentResultSerializer.serialize(any()) } returns "serialized"

    val entitySlot = slot<AlignmentCacheEntity>()
    coEvery { cacheDao.insert(capture(entitySlot)) } returns Unit

    orchestrator.requestAlignment("song1", "/audio.mp3", listOf("hello"), Language.EN).test {
      awaitItem() // Processing
      val complete = awaitItem() as AlignmentProgress.Complete
      assertThat(complete.result.lines).hasSize(1)
      assertThat(complete.result.lines[0].startMs).isEqualTo(0)
      awaitComplete()
    }

    assertThat(entitySlot.captured.modelVersion)
      .isEqualTo(AlignmentOrchestratorImpl.PIPELINE_VERSION)
  }

  @Test
  fun `whisper pipeline applies chunk offset to segment timestamps`() = runTest {
    coEvery { cacheDao.getBySongId(any()) } returns null
    every { audioPreprocessor.decodeToPcm(any()) } returns FloatArray(32000) // 2 seconds
    every { audioPreprocessor.segmentPcm(any()) } returns
      listOf(
        PcmChunk(data = FloatArray(16000), offsetMs = 0, durationMs = 1000),
        PcmChunk(data = FloatArray(16000), offsetMs = 1000, durationMs = 1000),
      )
    // Chunk 1: segment at 0-500ms (relative)
    // Chunk 2: segment at 0-500ms (relative) → should become 1000-1500ms (absolute)
    every { whisperTranscriber.transcribe(any(), any()) } returnsMany
      listOf(
        listOf(TranscribedSegment(text = "hello", startMs = 0, endMs = 500)),
        listOf(TranscribedSegment(text = "world", startMs = 0, endMs = 500)),
      )
    every { AlignmentResultSerializer.serialize(any()) } returns "serialized"

    orchestrator
      .requestAlignment("song1", "/audio.mp3", listOf("hello world"), Language.EN)
      .test {
        awaitItem() // Processing chunk 0
        awaitItem() // Processing chunk 1
        val complete = awaitItem() as AlignmentProgress.Complete
        // The combined segments should span 0-500ms and 1000-1500ms
        assertThat(complete.result.lines[0].startMs).isEqualTo(0)
        assertThat(complete.result.lines[0].endMs).isEqualTo(1500)
        awaitComplete()
      }
  }
}
