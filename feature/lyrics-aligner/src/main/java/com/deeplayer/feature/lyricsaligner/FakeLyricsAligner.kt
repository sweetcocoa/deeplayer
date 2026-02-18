package com.deeplayer.feature.lyricsaligner

import com.deeplayer.core.contracts.AlignmentResult
import com.deeplayer.core.contracts.Language
import com.deeplayer.core.contracts.LineAlignment
import com.deeplayer.core.contracts.LyricsAligner
import com.deeplayer.core.contracts.WordAlignment
import com.deeplayer.feature.lyricsaligner.postprocess.LrcGenerator

/**
 * Fake implementation of [LyricsAligner] that produces uniform distribution timestamps. Useful for
 * testing other modules before the real alignment is available.
 */
class FakeLyricsAligner : LyricsAligner {

  companion object {
    /**
     * Must match LyricsAlignerImpl.buildPhonemeVocab() size: blank + 19 cho + 21 jung + 39 arpa +
     * space.
     */
    const val VOCAB_SIZE = 81
  }

  override fun align(
    lyrics: List<String>,
    phonemeProbabilities: FloatArray,
    frameDurationMs: Float,
    language: Language,
  ): AlignmentResult {
    if (lyrics.isEmpty()) {
      return AlignmentResult(
        words = emptyList(),
        lines = emptyList(),
        overallConfidence = 0f,
        enhancedLrc = "",
      )
    }

    // Calculate total duration from the probability matrix
    val vocabSize = VOCAB_SIZE
    val numFrames = phonemeProbabilities.size / vocabSize
    val totalDurationMs = (numFrames * frameDurationMs).toLong()

    // Distribute time uniformly across lines and words
    val allWords = mutableListOf<WordAlignment>()
    val lineAlignments = mutableListOf<LineAlignment>()

    val msPerLine = totalDurationMs / lyrics.size.coerceAtLeast(1)

    for ((lineIdx, line) in lyrics.withIndex()) {
      val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
      val lineStartMs = lineIdx * msPerLine
      val lineEndMs = lineStartMs + msPerLine

      val lineWords = mutableListOf<WordAlignment>()
      if (words.isNotEmpty()) {
        val msPerWord = msPerLine / words.size
        for ((wordIdx, word) in words.withIndex()) {
          val wordAlignment =
            WordAlignment(
              word = word,
              startMs = lineStartMs + wordIdx * msPerWord,
              endMs = lineStartMs + (wordIdx + 1) * msPerWord,
              confidence = 0.5f,
              lineIndex = lineIdx,
            )
          lineWords.add(wordAlignment)
          allWords.add(wordAlignment)
        }
      }

      lineAlignments.add(
        LineAlignment(
          text = line,
          startMs = lineStartMs,
          endMs = lineEndMs,
          wordAlignments = lineWords,
        )
      )
    }

    val lrcGenerator = LrcGenerator()
    val lrc = lrcGenerator.generateFromLines(lineAlignments)

    return AlignmentResult(
      words = allWords,
      lines = lineAlignments,
      overallConfidence = 0.5f,
      enhancedLrc = lrc,
    )
  }
}
