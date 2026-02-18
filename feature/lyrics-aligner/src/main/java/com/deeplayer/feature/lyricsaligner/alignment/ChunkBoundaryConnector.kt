package com.deeplayer.feature.lyricsaligner.alignment

import com.deeplayer.core.contracts.LineAlignment
import com.deeplayer.core.contracts.WordAlignment

/**
 * Connects alignments across 30-second audio chunks, ensuring timestamp continuity at boundaries
 * with no gaps exceeding 500ms.
 */
class ChunkBoundaryConnector {

  data class ChunkAlignment(
    val words: List<WordAlignment>,
    val lines: List<LineAlignment>,
    val chunkOffsetMs: Long,
    val chunkDurationMs: Long,
  )

  /**
   * Merge alignments from multiple chunks into a single continuous alignment.
   *
   * @param chunks aligned chunks with their time offsets
   * @param maxGapMs maximum allowed gap at boundaries (default 500ms)
   * @return merged word and line alignments with continuous timestamps
   */
  fun connect(
    chunks: List<ChunkAlignment>,
    maxGapMs: Long = 500,
  ): Pair<List<WordAlignment>, List<LineAlignment>> {
    if (chunks.isEmpty()) return emptyList<WordAlignment>() to emptyList()
    if (chunks.size == 1) return chunks[0].words to chunks[0].lines

    val allWords = mutableListOf<WordAlignment>()
    val allLines = mutableListOf<LineAlignment>()
    var globalLineIndex = 0

    for ((chunkIdx, chunk) in chunks.withIndex()) {
      // Offset all timestamps by chunk's position in the original audio
      val offsetWords = chunk.words.map { it.offset(chunk.chunkOffsetMs, globalLineIndex) }

      val offsetLines =
        chunk.lines.map { l ->
          l.copy(
            startMs = l.startMs + chunk.chunkOffsetMs,
            endMs = l.endMs + chunk.chunkOffsetMs,
            wordAlignments =
              l.wordAlignments.map { it.offset(chunk.chunkOffsetMs, globalLineIndex) },
          )
        }

      // Fix boundary gaps with previous chunk
      if (chunkIdx > 0 && allWords.isNotEmpty() && offsetWords.isNotEmpty()) {
        val prevEnd = allWords.last().endMs
        val nextStart = offsetWords.first().startMs
        val gap = nextStart - prevEnd

        if (gap > maxGapMs) {
          // Interpolate: extend previous word's end and current word's start
          val midpoint = prevEnd + gap / 2
          val lastIdx = allWords.size - 1
          allWords[lastIdx] = allWords[lastIdx].copy(endMs = midpoint)

          val fixedFirst = offsetWords.first().copy(startMs = midpoint)
          val fixedWords = mutableListOf(fixedFirst).apply { addAll(offsetWords.drop(1)) }
          allWords.addAll(fixedWords)
        } else if (gap > 0) {
          // Small gap: extend previous word to close it
          val lastIdx = allWords.size - 1
          allWords[lastIdx] = allWords[lastIdx].copy(endMs = nextStart)
          allWords.addAll(offsetWords)
        } else {
          allWords.addAll(offsetWords)
        }

        // Fix line boundaries similarly
        if (allLines.isNotEmpty() && offsetLines.isNotEmpty()) {
          val prevLineEnd = allLines.last().endMs
          val nextLineStart = offsetLines.first().startMs
          val lineGap = nextLineStart - prevLineEnd
          if (lineGap > maxGapMs) {
            val midpoint = prevLineEnd + lineGap / 2
            val lastLineIdx = allLines.size - 1
            allLines[lastLineIdx] = allLines[lastLineIdx].copy(endMs = midpoint)
            val fixedFirstLine = offsetLines.first().copy(startMs = midpoint)
            allLines.add(fixedFirstLine)
            allLines.addAll(offsetLines.drop(1))
          } else {
            allLines.addAll(offsetLines)
          }
        } else {
          allLines.addAll(offsetLines)
        }
      } else {
        allWords.addAll(offsetWords)
        allLines.addAll(offsetLines)
      }

      globalLineIndex += chunk.lines.size
    }

    return allWords to allLines
  }

  private fun WordAlignment.offset(timeOffsetMs: Long, lineOffset: Int): WordAlignment =
    copy(
      startMs = startMs + timeOffsetMs,
      endMs = endMs + timeOffsetMs,
      lineIndex = lineIndex + lineOffset,
    )
}
