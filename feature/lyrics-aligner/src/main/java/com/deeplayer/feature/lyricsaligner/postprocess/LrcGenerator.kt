package com.deeplayer.feature.lyricsaligner.postprocess

import com.deeplayer.core.contracts.AlignmentResult
import com.deeplayer.core.contracts.LineAlignment
import com.deeplayer.core.contracts.WordAlignment

/** Generates Enhanced LRC format output from alignment results. Format: [mm:ss.xx] lyrics text */
class LrcGenerator {

  /**
   * Generate enhanced LRC string from alignment result.
   *
   * @param result alignment result with word and line timestamps
   * @return LRC format string with per-line timestamps
   */
  fun generate(result: AlignmentResult): String {
    return generateFromLines(result.lines)
  }

  /** Generate LRC from line alignments. */
  fun generateFromLines(lines: List<LineAlignment>): String {
    val sb = StringBuilder()
    for (line in lines) {
      val timestamp = formatTimestamp(line.startMs)
      sb.appendLine("[$timestamp] ${line.text}")
    }
    return sb.toString().trimEnd()
  }

  /** Generate enhanced LRC with word-level timestamps. */
  fun generateWordLevel(lines: List<LineAlignment>): String {
    val sb = StringBuilder()
    for (line in lines) {
      val lineTimestamp = formatTimestamp(line.startMs)
      sb.append("[$lineTimestamp] ")
      for (word in line.wordAlignments) {
        val wordTimestamp = formatTimestamp(word.startMs)
        sb.append("<$wordTimestamp>${word.word} ")
      }
      sb.appendLine()
    }
    return sb.toString().trimEnd()
  }

  /**
   * Parse an LRC string back to line alignments.
   *
   * @param lrc LRC format string
   * @return list of line alignments (word alignments will have basic timing)
   */
  fun parse(lrc: String): List<LineAlignment> {
    val lines = mutableListOf<LineAlignment>()
    val lineRegex = Regex("""\[(\d{2}):(\d{2})\.(\d{2})] (.+)""")

    val rawLines = lrc.lines().filter { it.isNotBlank() }
    for ((lineIdx, rawLine) in rawLines.withIndex()) {
      val match = lineRegex.matchEntire(rawLine.trim()) ?: continue
      val min = match.groupValues[1].toLong()
      val sec = match.groupValues[2].toLong()
      val hundredths = match.groupValues[3].toLong()
      val text = match.groupValues[4]

      val startMs = min * 60_000 + sec * 1000 + hundredths * 10

      // Determine endMs from next line or add default duration
      val endMs =
        if (lineIdx < rawLines.size - 1) {
          val nextMatch = lineRegex.matchEntire(rawLines[lineIdx + 1].trim())
          if (nextMatch != null) {
            val nextMin = nextMatch.groupValues[1].toLong()
            val nextSec = nextMatch.groupValues[2].toLong()
            val nextHundredths = nextMatch.groupValues[3].toLong()
            nextMin * 60_000 + nextSec * 1000 + nextHundredths * 10
          } else {
            startMs + 5000 // Default 5 second duration
          }
        } else {
          startMs + 5000
        }

      val words =
        text
          .split(Regex("\\s+"))
          .filter { it.isNotBlank() }
          .mapIndexed { i, word ->
            val wordDuration =
              (endMs - startMs) / text.split(Regex("\\s+")).count { it.isNotBlank() }
            WordAlignment(
              word = word,
              startMs = startMs + i * wordDuration,
              endMs = startMs + (i + 1) * wordDuration,
              confidence = 0.5f,
              lineIndex = lineIdx,
            )
          }

      lines.add(
        LineAlignment(text = text, startMs = startMs, endMs = endMs, wordAlignments = words)
      )
    }
    return lines
  }

  companion object {
    /** Format milliseconds as [mm:ss.xx] timestamp string (without brackets). */
    fun formatTimestamp(ms: Long): String {
      val totalSeconds = ms / 1000
      val minutes = totalSeconds / 60
      val seconds = totalSeconds % 60
      val hundredths = (ms % 1000) / 10
      return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
    }
  }
}
