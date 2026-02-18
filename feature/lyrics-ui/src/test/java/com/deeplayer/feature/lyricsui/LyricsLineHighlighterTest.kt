package com.deeplayer.feature.lyricsui

import com.deeplayer.core.contracts.LineAlignment
import com.deeplayer.core.contracts.WordAlignment
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LyricsLineHighlighterTest {

  private val lines =
    listOf(
      LineAlignment(
        text = "Hello world",
        startMs = 1000,
        endMs = 3000,
        wordAlignments =
          listOf(
            WordAlignment("Hello", 1000, 2000, 0.9f, 0),
            WordAlignment("world", 2000, 3000, 0.9f, 0),
          ),
      ),
      LineAlignment(
        text = "Second line",
        startMs = 3000,
        endMs = 5000,
        wordAlignments =
          listOf(
            WordAlignment("Second", 3000, 4000, 0.9f, 1),
            WordAlignment("line", 4000, 5000, 0.9f, 1),
          ),
      ),
      LineAlignment(
        text = "Third line",
        startMs = 5000,
        endMs = 7000,
        wordAlignments =
          listOf(
            WordAlignment("Third", 5000, 6000, 0.9f, 2),
            WordAlignment("line", 6000, 7000, 0.9f, 2),
          ),
      ),
    )

  // --- currentLineIndex tests ---

  @Test
  fun `currentLineIndex returns -1 for empty list`() {
    assertThat(LyricsLineHighlighter.currentLineIndex(emptyList(), 1000)).isEqualTo(-1)
  }

  @Test
  fun `currentLineIndex returns -1 before first line`() {
    assertThat(LyricsLineHighlighter.currentLineIndex(lines, 500)).isEqualTo(-1)
  }

  @Test
  fun `currentLineIndex returns 0 at exact start of first line`() {
    assertThat(LyricsLineHighlighter.currentLineIndex(lines, 1000)).isEqualTo(0)
  }

  @Test
  fun `currentLineIndex returns 0 during first line`() {
    assertThat(LyricsLineHighlighter.currentLineIndex(lines, 2500)).isEqualTo(0)
  }

  @Test
  fun `currentLineIndex returns 1 at boundary between first and second line`() {
    assertThat(LyricsLineHighlighter.currentLineIndex(lines, 3000)).isEqualTo(1)
  }

  @Test
  fun `currentLineIndex returns 2 during third line`() {
    assertThat(LyricsLineHighlighter.currentLineIndex(lines, 6000)).isEqualTo(2)
  }

  @Test
  fun `currentLineIndex returns last index after all lines end`() {
    assertThat(LyricsLineHighlighter.currentLineIndex(lines, 8000)).isEqualTo(2)
  }

  @Test
  fun `currentLineIndex applies global offset`() {
    // position 500 + offset 600 = 1100 => should be in first line
    assertThat(LyricsLineHighlighter.currentLineIndex(lines, 500, globalOffsetMs = 600))
      .isEqualTo(0)
  }

  @Test
  fun `currentLineIndex with negative offset pushes back`() {
    // position 3500 + offset -1000 = 2500 => still in first line
    assertThat(LyricsLineHighlighter.currentLineIndex(lines, 3500, globalOffsetMs = -1000))
      .isEqualTo(0)
  }

  // --- wordProgress tests ---

  @Test
  fun `wordProgress returns 0 before line starts`() {
    assertThat(LyricsLineHighlighter.wordProgress(lines[0], 500)).isEqualTo(0f)
  }

  @Test
  fun `wordProgress returns word count after line ends`() {
    assertThat(LyricsLineHighlighter.wordProgress(lines[0], 4000)).isEqualTo(2f)
  }

  @Test
  fun `wordProgress returns 0 for line with no word alignments`() {
    val emptyLine = LineAlignment("test", 0, 1000, emptyList())
    assertThat(LyricsLineHighlighter.wordProgress(emptyLine, 500)).isEqualTo(0f)
  }

  @Test
  fun `wordProgress halfway through first word`() {
    // first word: 1000-2000, position 1500 => 0 + 500/1000 = 0.5
    assertThat(LyricsLineHighlighter.wordProgress(lines[0], 1500)).isWithin(0.01f).of(0.5f)
  }

  @Test
  fun `wordProgress at start of second word`() {
    // second word: 2000-3000, position 2000 => 1 + 0/1000 = 1.0
    assertThat(LyricsLineHighlighter.wordProgress(lines[0], 2000)).isWithin(0.01f).of(1.0f)
  }

  @Test
  fun `wordProgress halfway through second word`() {
    // second word: 2000-3000, position 2500 => 1 + 500/1000 = 1.5
    assertThat(LyricsLineHighlighter.wordProgress(lines[0], 2500)).isWithin(0.01f).of(1.5f)
  }

  @Test
  fun `wordProgress applies global offset`() {
    // position 500 + offset 1000 = 1500 => halfway through first word => 0.5
    assertThat(LyricsLineHighlighter.wordProgress(lines[0], 500, globalOffsetMs = 1000))
      .isWithin(0.01f)
      .of(0.5f)
  }
}
