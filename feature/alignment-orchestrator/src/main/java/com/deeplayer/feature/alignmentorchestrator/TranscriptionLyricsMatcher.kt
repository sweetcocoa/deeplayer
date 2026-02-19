package com.deeplayer.feature.alignmentorchestrator

import com.deeplayer.core.contracts.AlignmentResult
import com.deeplayer.core.contracts.Language
import com.deeplayer.core.contracts.LineAlignment
import com.deeplayer.core.contracts.TranscribedSegment
import com.deeplayer.core.contracts.WordAlignment

/**
 * Matches Whisper transcription segments to user-provided lyrics lines to transfer timestamps.
 *
 * Algorithm:
 * 1. Normalise both transcribed text and lyrics (strip whitespace for Korean, lowercase for
 *    English).
 * 2. Monotonic sequential matching: greedily assign contiguous transcription segments to each
 *    lyrics line, choosing the assignment that maximises Levenshtein similarity.
 * 3. Un-matched lyrics lines receive interpolated timestamps from their neighbours.
 * 4. Per-word timestamps within a line are distributed evenly across the line duration.
 */
internal object TranscriptionLyricsMatcher {

  fun match(
    segments: List<TranscribedSegment>,
    lyrics: List<String>,
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

    // 1. Normalise lyrics lines
    val normLyrics = lyrics.map { normalise(it, language) }

    // 2. Monotonic matching – assign segments to lyrics lines
    val lineSegments = assignSegmentsToLines(segments, normLyrics, language)

    // 3. Build LineAlignment list
    val lineAlignments = mutableListOf<LineAlignment>()
    val allWords = mutableListOf<WordAlignment>()
    var totalSimilarity = 0f
    var matchedLines = 0

    for ((lineIdx, lyricText) in lyrics.withIndex()) {
      val assigned = lineSegments[lineIdx]
      val startMs: Long
      val endMs: Long

      if (assigned.isNotEmpty()) {
        startMs = assigned.first().startMs
        endMs = assigned.last().endMs
        val joined = assigned.joinToString(" ") { it.text.trim() }
        totalSimilarity += levenshteinSimilarity(normalise(joined, language), normLyrics[lineIdx])
        matchedLines++
      } else {
        // Will be interpolated below
        startMs = -1L
        endMs = -1L
      }

      val words = distributeWords(lyricText, startMs, endMs, lineIdx)
      allWords.addAll(words)
      lineAlignments.add(
        LineAlignment(text = lyricText, startMs = startMs, endMs = endMs, wordAlignments = words)
      )
    }

    // 4. Interpolate un-matched lines
    interpolateGaps(lineAlignments)

    // Rebuild words after interpolation
    val finalWords = mutableListOf<WordAlignment>()
    val finalLines = mutableListOf<LineAlignment>()
    for ((lineIdx, line) in lineAlignments.withIndex()) {
      val words = distributeWords(line.text, line.startMs, line.endMs, lineIdx)
      finalWords.addAll(words)
      finalLines.add(line.copy(wordAlignments = words))
    }

    val confidence = if (matchedLines > 0) totalSimilarity / matchedLines else 0f
    val enhancedLrc = buildEnhancedLrc(finalLines)

    return AlignmentResult(
      words = finalWords,
      lines = finalLines,
      overallConfidence = confidence,
      enhancedLrc = enhancedLrc,
    )
  }

  // --- Internal helpers ---

  /**
   * Greedily assign transcription segments to lyrics lines in monotonic order. For each lyrics
   * line, consume segments whose normalised text best matches the lyrics line text.
   */
  private fun assignSegmentsToLines(
    segments: List<TranscribedSegment>,
    normLyrics: List<String>,
    language: Language,
  ): List<List<TranscribedSegment>> {
    val result = MutableList<MutableList<TranscribedSegment>>(normLyrics.size) { mutableListOf() }
    if (segments.isEmpty()) return result

    var segIdx = 0
    for (lineIdx in normLyrics.indices) {
      if (segIdx >= segments.size) break
      val target = normLyrics[lineIdx]
      if (target.isBlank()) continue

      // Try consuming 1..maxSegments segments and pick the best match
      var bestSim = -1f
      var bestCount = 0
      val maxLookahead = minOf(segments.size - segIdx, 20) // cap lookahead

      val builder = StringBuilder()
      for (count in 1..maxLookahead) {
        val seg = segments[segIdx + count - 1]
        if (builder.isNotEmpty()) builder.append(' ')
        builder.append(seg.text.trim())
        val sim = levenshteinSimilarity(normalise(builder.toString(), language), target)
        if (sim > bestSim) {
          bestSim = sim
          bestCount = count
        }
        // Early exit if perfect match
        if (sim >= 1f) break
        // If similarity starts dropping significantly after a good match, stop
        if (bestSim > 0.8f && sim < bestSim - 0.2f) break
      }

      if (bestSim >= 0.3f) {
        for (i in 0 until bestCount) {
          result[lineIdx].add(segments[segIdx + i])
        }
        segIdx += bestCount
      }
    }

    return result
  }

  /** Distribute words evenly across the line's time span. */
  private fun distributeWords(
    text: String,
    startMs: Long,
    endMs: Long,
    lineIndex: Int,
  ): List<WordAlignment> {
    val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    if (startMs < 0 || endMs < 0) {
      // Placeholder for un-matched lines
      return words.map {
        WordAlignment(word = it, startMs = 0, endMs = 0, confidence = 0f, lineIndex = lineIndex)
      }
    }

    val duration = endMs - startMs
    val wordDuration = if (words.size > 1) duration / words.size else duration

    return words.mapIndexed { i, word ->
      WordAlignment(
        word = word,
        startMs = startMs + i * wordDuration,
        endMs = startMs + (i + 1) * wordDuration,
        confidence = 1f,
        lineIndex = lineIndex,
      )
    }
  }

  /**
   * Fill gaps for lyrics lines that had no matching segments. Uses a two-pass approach:
   * 1. Collect indices of matched "anchor" lines.
   * 2. For each gap between anchors, linearly distribute timestamps.
   */
  private fun interpolateGaps(lines: MutableList<LineAlignment>) {
    if (lines.isEmpty()) return

    // Collect anchor indices (lines that have a valid match)
    val anchors = lines.indices.filter { lines[it].startMs >= 0 }

    if (anchors.isEmpty()) {
      // Nothing matched at all — spread evenly with 2s per line starting at 0
      val gap = 2000L
      for (i in lines.indices) {
        lines[i] = lines[i].copy(startMs = i * gap, endMs = (i + 1) * gap)
      }
      return
    }

    // Fill gap before the first anchor (lines 0 until anchors[0])
    val firstAnchor = anchors.first()
    if (firstAnchor > 0) {
      val anchorStart = lines[firstAnchor].startMs
      val gap = if (firstAnchor > 0) anchorStart / firstAnchor else anchorStart
      for (i in 0 until firstAnchor) {
        lines[i] = lines[i].copy(startMs = i * gap, endMs = (i + 1) * gap)
      }
    }

    // Fill gap after the last anchor
    val lastAnchor = anchors.last()
    if (lastAnchor < lines.size - 1) {
      val anchorEnd = lines[lastAnchor].endMs
      val gap = 2000L
      for (i in (lastAnchor + 1) until lines.size) {
        val offset = i - lastAnchor
        lines[i] =
          lines[i].copy(startMs = anchorEnd + (offset - 1) * gap, endMs = anchorEnd + offset * gap)
      }
    }

    // Fill interior gaps between consecutive anchors
    for (a in 0 until anchors.size - 1) {
      val left = anchors[a]
      val right = anchors[a + 1]
      if (right - left <= 1) continue // no gap

      val leftEnd = lines[left].endMs
      val rightStart = lines[right].startMs
      val gapLines = right - left - 1 // number of lines to fill
      val totalDuration = rightStart - leftEnd
      val perLine = if (gapLines > 0) totalDuration / gapLines else totalDuration

      for (g in 1..gapLines) {
        val idx = left + g
        lines[idx] =
          lines[idx].copy(startMs = leftEnd + (g - 1) * perLine, endMs = leftEnd + g * perLine)
      }
    }
  }

  internal fun normalise(text: String, language: Language): String {
    val stripped = text.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()
    return when (language) {
      Language.KO -> stripped.replace(Regex("\\s+"), "") // Korean: remove all whitespace
      Language.EN -> stripped.lowercase()
      Language.MIXED -> stripped.replace(Regex("\\s+"), "").lowercase()
    }
  }

  internal fun levenshteinSimilarity(a: String, b: String): Float {
    if (a == b) return 1f
    val maxLen = maxOf(a.length, b.length)
    if (maxLen == 0) return 1f
    return 1f - levenshteinDistance(a, b).toFloat() / maxLen
  }

  private fun levenshteinDistance(a: String, b: String): Int {
    val m = a.length
    val n = b.length
    val dp = Array(m + 1) { IntArray(n + 1) }
    for (i in 0..m) dp[i][0] = i
    for (j in 0..n) dp[0][j] = j
    for (i in 1..m) {
      for (j in 1..n) {
        val cost = if (a[i - 1] == b[j - 1]) 0 else 1
        dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
      }
    }
    return dp[m][n]
  }

  private fun buildEnhancedLrc(lines: List<LineAlignment>): String = buildString {
    for (line in lines) {
      val mins = line.startMs / 60000
      val secs = (line.startMs % 60000) / 1000
      val centis = (line.startMs % 1000) / 10
      append("[%02d:%02d.%02d]%s\n".format(mins, secs, centis, line.text))
    }
  }
}
