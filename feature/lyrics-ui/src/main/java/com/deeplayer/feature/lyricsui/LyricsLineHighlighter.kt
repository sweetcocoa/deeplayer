package com.deeplayer.feature.lyricsui

import com.deeplayer.core.contracts.LineAlignment

/** Maps a playback position to the current line index and calculates word-level progress. */
object LyricsLineHighlighter {

  /**
   * Returns the index of the line that should be highlighted at [positionMs], applying
   * [globalOffsetMs]. Returns -1 if no line matches (before first line or after last).
   */
  fun currentLineIndex(
    lines: List<LineAlignment>,
    positionMs: Long,
    globalOffsetMs: Long = 0,
  ): Int {
    if (lines.isEmpty()) return -1
    val adjusted = positionMs + globalOffsetMs
    // Find the last line whose startMs <= adjusted position
    var result = -1
    for (i in lines.indices) {
      if (lines[i].startMs <= adjusted) {
        result = i
      } else {
        break
      }
    }
    // If we are past the end of the last line, return -1
    if (result >= 0 && adjusted > lines[result].endMs && result == lines.lastIndex) {
      return result // keep highlighting last line until playback ends
    }
    return result
  }

  /**
   * Calculates word-level progress within the given [line] at [positionMs]. Returns a float in [0,
   * wordCount) representing how many words have been "spoken". The integer part is the
   * fully-completed word count; the fractional part is progress through the current word.
   */
  fun wordProgress(line: LineAlignment, positionMs: Long, globalOffsetMs: Long = 0): Float {
    val words = line.wordAlignments
    if (words.isEmpty()) return 0f
    val adjusted = positionMs + globalOffsetMs

    if (adjusted < words.first().startMs) return 0f
    if (adjusted >= words.last().endMs) return words.size.toFloat()

    for (i in words.indices) {
      val w = words[i]
      if (adjusted < w.startMs) {
        // Between previous word end and this word start
        return i.toFloat()
      }
      if (adjusted in w.startMs until w.endMs) {
        val duration = (w.endMs - w.startMs).toFloat()
        val elapsed = (adjusted - w.startMs).toFloat()
        return i + (elapsed / duration)
      }
    }
    return words.size.toFloat()
  }
}
