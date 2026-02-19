package com.deeplayer.feature.alignmentorchestrator

import com.deeplayer.core.contracts.AlignmentOrchestrator
import com.deeplayer.core.contracts.AlignmentProgress
import com.deeplayer.core.contracts.AlignmentResult
import com.deeplayer.core.contracts.Language
import com.deeplayer.core.contracts.LineAlignment
import com.deeplayer.core.contracts.WordAlignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fake implementation of [AlignmentOrchestrator] that produces uniform distribution timestamps
 * without requiring real audio processing or inference. Useful for UI testing and integration
 * tests.
 */
class FakeAlignmentOrchestrator : AlignmentOrchestrator {

  private val cache = mutableMapOf<String, AlignmentResult>()
  private val offsets = mutableMapOf<String, Long>()

  override fun requestAlignment(
    songId: String,
    audioPath: String,
    lyrics: List<String>,
    language: Language,
  ): Flow<AlignmentProgress> = flow {
    // Return cached if available
    cache[songId]?.let {
      emit(AlignmentProgress.Complete(it))
      return@flow
    }

    // Simulate processing with small delays
    val totalChunks = 3
    for (i in 0 until totalChunks) {
      emit(AlignmentProgress.Processing(i, totalChunks))
      delay(100)
    }

    // Generate uniform alignment
    val result = generateFakeResult(lyrics)
    cache[songId] = result
    emit(AlignmentProgress.Complete(result))
  }

  override suspend fun getCachedAlignment(songId: String): AlignmentResult? = cache[songId]

  override suspend fun saveUserOffset(songId: String, globalOffsetMs: Long) {
    offsets[songId] = globalOffsetMs
  }

  override suspend fun invalidateCache(modelVersion: String) {
    cache.clear()
  }

  private fun generateFakeResult(lyrics: List<String>): AlignmentResult {
    if (lyrics.isEmpty()) {
      return AlignmentResult(
        words = emptyList(),
        lines = emptyList(),
        overallConfidence = 0f,
        enhancedLrc = "",
      )
    }

    val msPerLine = 5000L
    val allWords = mutableListOf<WordAlignment>()
    val lineAlignments =
      lyrics.mapIndexed { lineIdx, line ->
        val lineStart = lineIdx * msPerLine
        val lineEnd = lineStart + msPerLine
        val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        val lineWords =
          if (words.isNotEmpty()) {
            val msPerWord = msPerLine / words.size
            words.mapIndexed { wordIdx, word ->
              WordAlignment(
                  word = word,
                  startMs = lineStart + wordIdx * msPerWord,
                  endMs = lineStart + (wordIdx + 1) * msPerWord,
                  confidence = 0.8f,
                  lineIndex = lineIdx,
                )
                .also { allWords.add(it) }
            }
          } else {
            emptyList()
          }
        LineAlignment(text = line, startMs = lineStart, endMs = lineEnd, wordAlignments = lineWords)
      }

    val lrc =
      lineAlignments.joinToString("\n") { line ->
        val min = line.startMs / 60000
        val sec = (line.startMs % 60000) / 1000
        val ms = (line.startMs % 1000) / 10
        "[%02d:%02d.%02d]%s".format(min, sec, ms, line.text)
      }

    return AlignmentResult(
      words = allWords,
      lines = lineAlignments,
      overallConfidence = 0.8f,
      enhancedLrc = lrc,
    )
  }
}
