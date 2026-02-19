package com.deeplayer.feature.alignmentorchestrator

import com.deeplayer.core.contracts.AlignmentOrchestrator
import com.deeplayer.core.contracts.AlignmentProgress
import com.deeplayer.core.contracts.AlignmentResult
import com.deeplayer.core.contracts.AudioPreprocessor
import com.deeplayer.core.contracts.Language
import com.deeplayer.core.contracts.TranscribedSegment
import com.deeplayer.core.contracts.WhisperTranscriber
import com.deeplayer.feature.alignmentorchestrator.cache.AlignmentCacheDao
import com.deeplayer.feature.alignmentorchestrator.cache.AlignmentCacheEntity
import com.deeplayer.feature.alignmentorchestrator.cache.UserOffsetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class AlignmentOrchestratorImpl(
  private val audioPreprocessor: AudioPreprocessor,
  private val whisperTranscriber: WhisperTranscriber,
  private val cacheDao: AlignmentCacheDao,
) : AlignmentOrchestrator {

  companion object {
    private const val WHISPER_VERSION = "whisper-tiny-full-v1"
    private const val TIMESTAMP_VERSION = "timestamp-v1"
    internal const val PIPELINE_VERSION = "$WHISPER_VERSION|$TIMESTAMP_VERSION"
  }

  override fun requestAlignment(
    songId: String,
    audioPath: String,
    lyrics: List<String>,
    language: Language,
  ): Flow<AlignmentProgress> =
    flow {
        // 1. Check cache
        val cached = cacheDao.getBySongId(songId)
        if (cached != null) {
          if (cached.modelVersion == PIPELINE_VERSION) {
            val result = AlignmentResultSerializer.deserialize(cached.resultJson)
            emit(AlignmentProgress.Complete(result))
            return@flow
          }
          cacheDao.deleteBySongId(songId)
        }

        // 2. Run alignment pipeline
        try {
          val result = runWhisperPipeline(audioPath, lyrics, language)

          // Cache result
          cacheDao.insert(
            AlignmentCacheEntity(
              songId = songId,
              resultJson = AlignmentResultSerializer.serialize(result),
              modelVersion = PIPELINE_VERSION,
            )
          )

          emit(AlignmentProgress.Complete(result))
        } catch (e: Exception) {
          emit(AlignmentProgress.Failed(e, retriesLeft = 0))
        }
      }
      .flowOn(Dispatchers.Default)

  private suspend fun kotlinx.coroutines.flow.FlowCollector<AlignmentProgress>.runWhisperPipeline(
    audioPath: String,
    lyrics: List<String>,
    language: Language,
  ): AlignmentResult {
    // a. Decode audio to PCM
    val pcm = audioPreprocessor.decodeToPcm(audioPath)

    // b. Segment PCM into 30s chunks
    val chunks = audioPreprocessor.segmentPcm(pcm)

    // c. Transcribe each chunk
    val allSegments = mutableListOf<TranscribedSegment>()
    for ((index, chunk) in chunks.withIndex()) {
      emit(AlignmentProgress.Processing(index, chunks.size))
      val segments = whisperTranscriber.transcribe(chunk.data, language)
      // Apply chunk offset to segment timestamps
      val adjusted =
        segments.map {
          it.copy(startMs = it.startMs + chunk.offsetMs, endMs = it.endMs + chunk.offsetMs)
        }
      allSegments.addAll(adjusted)
    }

    // d. Match transcription to lyrics
    return TranscriptionLyricsMatcher.match(allSegments, lyrics, language)
  }

  override suspend fun getCachedAlignment(songId: String): AlignmentResult? {
    val cached = cacheDao.getBySongId(songId) ?: return null
    return AlignmentResultSerializer.deserialize(cached.resultJson)
  }

  override suspend fun saveUserOffset(songId: String, globalOffsetMs: Long) {
    cacheDao.insertUserOffset(UserOffsetEntity(songId = songId, globalOffsetMs = globalOffsetMs))
  }

  override suspend fun invalidateCache(modelVersion: String) {
    cacheDao.invalidateByModelVersion(modelVersion)
  }
}
