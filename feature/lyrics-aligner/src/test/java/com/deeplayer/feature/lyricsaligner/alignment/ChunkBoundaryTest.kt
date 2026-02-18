package com.deeplayer.feature.lyricsaligner.alignment

import com.deeplayer.core.contracts.LineAlignment
import com.deeplayer.core.contracts.WordAlignment
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class ChunkBoundaryTest {

  private lateinit var connector: ChunkBoundaryConnector

  @Before
  fun setUp() {
    connector = ChunkBoundaryConnector()
  }

  private fun makeChunk(
    words: List<Triple<String, Long, Long>>,
    offsetMs: Long,
    durationMs: Long,
    lineText: String = "test line",
    lineIndex: Int = 0,
  ): ChunkBoundaryConnector.ChunkAlignment {
    val wordAlignments =
      words.map { (word, start, end) ->
        WordAlignment(
          word = word,
          startMs = start,
          endMs = end,
          confidence = 0.8f,
          lineIndex = lineIndex,
        )
      }
    val line =
      LineAlignment(
        text = lineText,
        startMs = wordAlignments.firstOrNull()?.startMs ?: 0,
        endMs = wordAlignments.lastOrNull()?.endMs ?: 0,
        wordAlignments = wordAlignments,
      )
    return ChunkBoundaryConnector.ChunkAlignment(
      words = wordAlignments,
      lines = listOf(line),
      chunkOffsetMs = offsetMs,
      chunkDurationMs = durationMs,
    )
  }

  @Test
  fun `two chunks with no gap connect seamlessly`() {
    val chunk1 =
      makeChunk(
        words = listOf(Triple("hello", 0L, 15000L), Triple("world", 15000L, 30000L)),
        offsetMs = 0,
        durationMs = 30000,
      )

    val chunk2 =
      makeChunk(
        words = listOf(Triple("good", 0L, 1000L), Triple("bye", 1000L, 2000L)),
        offsetMs = 30000,
        durationMs = 30000,
        lineIndex = 1,
      )

    val (words, _) = connector.connect(listOf(chunk1, chunk2))
    assertThat(words).hasSize(4)

    // Check timestamps are monotonically increasing
    for (i in 1 until words.size) {
      assertThat(words[i].startMs).isAtLeast(words[i - 1].startMs)
    }

    // Second chunk words should have 30000ms offset
    assertThat(words[2].startMs).isEqualTo(30000L)
    assertThat(words[3].endMs).isEqualTo(32000L)
  }

  @Test
  fun `two chunks with large gap are interpolated below 500ms`() {
    val chunk1 =
      makeChunk(words = listOf(Triple("word1", 0L, 28000L)), offsetMs = 0, durationMs = 30000)

    val chunk2 =
      makeChunk(
        words =
          listOf(
            Triple("word2", 2000L, 4000L) // starts at 2000ms into chunk → absolute 32000ms
          ),
        offsetMs = 30000,
        durationMs = 30000,
        lineIndex = 1,
      )

    val (words, _) = connector.connect(listOf(chunk1, chunk2))
    assertThat(words).hasSize(2)

    // Gap between word1.end (28000) and word2.start (32000) = 4000ms > 500ms
    // Should be interpolated
    val gap = words[1].startMs - words[0].endMs
    assertThat(gap).isAtMost(500)
  }

  @Test
  fun `three chunks maintain continuity`() {
    val chunk1 =
      makeChunk(words = listOf(Triple("a", 0L, 10000L)), offsetMs = 0, durationMs = 30000)
    val chunk2 =
      makeChunk(
        words = listOf(Triple("b", 0L, 10000L)),
        offsetMs = 30000,
        durationMs = 30000,
        lineIndex = 1,
      )
    val chunk3 =
      makeChunk(
        words = listOf(Triple("c", 0L, 10000L)),
        offsetMs = 60000,
        durationMs = 30000,
        lineIndex = 2,
      )

    val (words, _) = connector.connect(listOf(chunk1, chunk2, chunk3))
    assertThat(words).hasSize(3)

    // All words should have monotonically increasing timestamps
    for (i in 1 until words.size) {
      assertThat(words[i].startMs).isAtLeast(words[i - 1].endMs)
    }

    // No gap should exceed 500ms at boundaries
    for (i in 1 until words.size) {
      val gap = words[i].startMs - words[i - 1].endMs
      assertThat(gap).isAtMost(500)
    }
  }

  @Test
  fun `single chunk returns unchanged`() {
    val chunk =
      makeChunk(words = listOf(Triple("hello", 100L, 500L)), offsetMs = 0, durationMs = 30000)
    val (words, lines) = connector.connect(listOf(chunk))
    assertThat(words).hasSize(1)
    assertThat(lines).hasSize(1)
  }

  @Test
  fun `empty chunks list returns empty`() {
    val (words, lines) = connector.connect(emptyList())
    assertThat(words).isEmpty()
    assertThat(lines).isEmpty()
  }
}
