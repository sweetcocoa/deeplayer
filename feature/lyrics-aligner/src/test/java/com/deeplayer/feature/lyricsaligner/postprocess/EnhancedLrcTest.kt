package com.deeplayer.feature.lyricsaligner.postprocess

import com.deeplayer.core.contracts.AlignmentResult
import com.deeplayer.core.contracts.LineAlignment
import com.deeplayer.core.contracts.WordAlignment
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class EnhancedLrcTest {

  private lateinit var generator: LrcGenerator

  @Before
  fun setUp() {
    generator = LrcGenerator()
  }

  @Test
  fun `format timestamp correctly at 0ms`() {
    assertThat(LrcGenerator.formatTimestamp(0)).isEqualTo("00:00.00")
  }

  @Test
  fun `format timestamp at 1 minute 30 seconds`() {
    assertThat(LrcGenerator.formatTimestamp(90000)).isEqualTo("01:30.00")
  }

  @Test
  fun `format timestamp with hundredths`() {
    assertThat(LrcGenerator.formatTimestamp(65320)).isEqualTo("01:05.32")
  }

  @Test
  fun `format timestamp at 3 minutes 45 seconds 67 hundredths`() {
    // 3*60*1000 + 45*1000 + 670 = 225670
    assertThat(LrcGenerator.formatTimestamp(225670)).isEqualTo("03:45.67")
  }

  @Test
  fun `generate single line LRC`() {
    val lines =
      listOf(
        LineAlignment(
          text = "Hello world",
          startMs = 1000,
          endMs = 3000,
          wordAlignments =
            listOf(
              WordAlignment("Hello", 1000, 2000, 0.9f, 0),
              WordAlignment("world", 2000, 3000, 0.8f, 0),
            ),
        )
      )

    val lrc = generator.generateFromLines(lines)
    assertThat(lrc).isEqualTo("[00:01.00] Hello world")
  }

  @Test
  fun `generate multi-line LRC`() {
    val lines =
      listOf(
        LineAlignment(
          text = "First line",
          startMs = 0,
          endMs = 2000,
          wordAlignments =
            listOf(
              WordAlignment("First", 0, 1000, 0.9f, 0),
              WordAlignment("line", 1000, 2000, 0.8f, 0),
            ),
        ),
        LineAlignment(
          text = "Second line",
          startMs = 3000,
          endMs = 5000,
          wordAlignments =
            listOf(
              WordAlignment("Second", 3000, 4000, 0.9f, 1),
              WordAlignment("line", 4000, 5000, 0.8f, 1),
            ),
        ),
      )

    val lrc = generator.generateFromLines(lines)
    val lrcLines = lrc.lines()
    assertThat(lrcLines).hasSize(2)
    assertThat(lrcLines[0]).isEqualTo("[00:00.00] First line")
    assertThat(lrcLines[1]).isEqualTo("[00:03.00] Second line")
  }

  @Test
  fun `parse LRC back to line alignments`() {
    val lrc =
      """
      [00:01.00] Hello world
      [00:03.00] Good bye
      """
        .trimIndent()

    val lines = generator.parse(lrc)
    assertThat(lines).hasSize(2)
    assertThat(lines[0].text).isEqualTo("Hello world")
    assertThat(lines[0].startMs).isEqualTo(1000)
    assertThat(lines[1].text).isEqualTo("Good bye")
    assertThat(lines[1].startMs).isEqualTo(3000)
  }

  @Test
  fun `roundtrip generate then parse preserves timestamps`() {
    val original =
      listOf(
        LineAlignment(
          text = "Test line one",
          startMs = 5000,
          endMs = 10000,
          wordAlignments =
            listOf(
              WordAlignment("Test", 5000, 6500, 0.9f, 0),
              WordAlignment("line", 6500, 8000, 0.85f, 0),
              WordAlignment("one", 8000, 10000, 0.8f, 0),
            ),
        ),
        LineAlignment(
          text = "Test line two",
          startMs = 12000,
          endMs = 17000,
          wordAlignments =
            listOf(
              WordAlignment("Test", 12000, 13500, 0.9f, 1),
              WordAlignment("line", 13500, 15000, 0.85f, 1),
              WordAlignment("two", 15000, 17000, 0.8f, 1),
            ),
        ),
      )

    val lrc = generator.generateFromLines(original)
    val parsed = generator.parse(lrc)

    assertThat(parsed).hasSize(2)
    assertThat(parsed[0].text).isEqualTo("Test line one")
    assertThat(parsed[1].text).isEqualTo("Test line two")

    // Timestamps should match within rounding (10ms granularity in LRC)
    assertThat(parsed[0].startMs).isEqualTo(5000)
    assertThat(parsed[1].startMs).isEqualTo(12000)
  }

  @Test
  fun `LRC format matches mm_ss_xx pattern`() {
    val lines =
      listOf(
        LineAlignment(
          text = "Test",
          startMs = 125670,
          endMs = 130000,
          wordAlignments = listOf(WordAlignment("Test", 125670, 130000, 0.9f, 0)),
        )
      )

    val lrc = generator.generateFromLines(lines)
    val pattern = Regex("""\[\d{2}:\d{2}\.\d{2}] .+""")
    assertThat(lrc).matches(pattern.pattern)
  }

  @Test
  fun `generate from AlignmentResult`() {
    val result =
      AlignmentResult(
        words =
          listOf(
            WordAlignment("hello", 0, 1000, 0.9f, 0),
            WordAlignment("world", 1000, 2000, 0.8f, 0),
          ),
        lines =
          listOf(
            LineAlignment(
              text = "hello world",
              startMs = 0,
              endMs = 2000,
              wordAlignments =
                listOf(
                  WordAlignment("hello", 0, 1000, 0.9f, 0),
                  WordAlignment("world", 1000, 2000, 0.8f, 0),
                ),
            )
          ),
        overallConfidence = 0.85f,
        enhancedLrc = "",
      )

    val lrc = generator.generate(result)
    assertThat(lrc).contains("[00:00.00] hello world")
  }

  @Test
  fun `word level LRC includes word timestamps`() {
    val lines =
      listOf(
        LineAlignment(
          text = "hello world",
          startMs = 0,
          endMs = 2000,
          wordAlignments =
            listOf(
              WordAlignment("hello", 0, 1000, 0.9f, 0),
              WordAlignment("world", 1000, 2000, 0.8f, 0),
            ),
        )
      )

    val lrc = generator.generateWordLevel(lines)
    assertThat(lrc).contains("<00:00.00>hello")
    assertThat(lrc).contains("<00:01.00>world")
  }
}
