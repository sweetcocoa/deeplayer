package com.deeplayer.feature.alignmentorchestrator

import com.deeplayer.core.contracts.AlignmentResult
import com.deeplayer.core.contracts.LineAlignment
import com.deeplayer.core.contracts.WordAlignment

/**
 * Simple serializer for [AlignmentResult] to avoid adding a JSON library dependency. Uses a
 * line-based text format.
 */
internal object AlignmentResultSerializer {

  private const val HEADER = "AR:v1"
  private const val LINE_SEP = "\n"
  private const val FIELD_SEP = "\t"

  fun serialize(result: AlignmentResult): String = buildString {
    append(HEADER)
    append(LINE_SEP)
    // overall confidence + enhancedLrc length
    append(result.overallConfidence)
    append(FIELD_SEP)
    append(result.enhancedLrc.length)
    append(LINE_SEP)
    // enhancedLrc (base64 to avoid delimiter collisions)
    append(
      android.util.Base64.encodeToString(
        result.enhancedLrc.toByteArray(Charsets.UTF_8),
        android.util.Base64.NO_WRAP,
      )
    )
    append(LINE_SEP)
    // lines
    append(result.lines.size)
    append(LINE_SEP)
    for (line in result.lines) {
      // line text (base64)
      append(
        android.util.Base64.encodeToString(
          line.text.toByteArray(Charsets.UTF_8),
          android.util.Base64.NO_WRAP,
        )
      )
      append(FIELD_SEP)
      append(line.startMs)
      append(FIELD_SEP)
      append(line.endMs)
      append(FIELD_SEP)
      append(line.wordAlignments.size)
      append(LINE_SEP)
      for (w in line.wordAlignments) {
        append(
          android.util.Base64.encodeToString(
            w.word.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP,
          )
        )
        append(FIELD_SEP)
        append(w.startMs)
        append(FIELD_SEP)
        append(w.endMs)
        append(FIELD_SEP)
        append(w.confidence)
        append(FIELD_SEP)
        append(w.lineIndex)
        append(LINE_SEP)
      }
    }
  }

  fun deserialize(data: String): AlignmentResult {
    val lines = data.split(LINE_SEP).toMutableList()
    var idx = 0
    require(lines[idx++] == HEADER) { "Invalid format header" }

    val headerFields = lines[idx++].split(FIELD_SEP)
    val overallConfidence = headerFields[0].toFloat()

    val enhancedLrc =
      String(android.util.Base64.decode(lines[idx++], android.util.Base64.NO_WRAP), Charsets.UTF_8)

    val lineCount = lines[idx++].toInt()
    val allWords = mutableListOf<WordAlignment>()
    val lineAlignments = mutableListOf<LineAlignment>()

    for (l in 0 until lineCount) {
      val lineFields = lines[idx++].split(FIELD_SEP)
      val text =
        String(
          android.util.Base64.decode(lineFields[0], android.util.Base64.NO_WRAP),
          Charsets.UTF_8,
        )
      val startMs = lineFields[1].toLong()
      val endMs = lineFields[2].toLong()
      val wordCount = lineFields[3].toInt()

      val words = mutableListOf<WordAlignment>()
      for (w in 0 until wordCount) {
        val wFields = lines[idx++].split(FIELD_SEP)
        val word =
          String(
            android.util.Base64.decode(wFields[0], android.util.Base64.NO_WRAP),
            Charsets.UTF_8,
          )
        val wa =
          WordAlignment(
            word = word,
            startMs = wFields[1].toLong(),
            endMs = wFields[2].toLong(),
            confidence = wFields[3].toFloat(),
            lineIndex = wFields[4].toInt(),
          )
        words.add(wa)
        allWords.add(wa)
      }

      lineAlignments.add(
        LineAlignment(text = text, startMs = startMs, endMs = endMs, wordAlignments = words)
      )
    }

    return AlignmentResult(
      words = allWords,
      lines = lineAlignments,
      overallConfidence = overallConfidence,
      enhancedLrc = enhancedLrc,
    )
  }
}
