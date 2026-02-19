package com.deeplayer.feature.alignmentorchestrator

import com.deeplayer.core.contracts.Language
import com.deeplayer.core.contracts.TranscribedSegment
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TranscriptionLyricsMatcherTest {

  @Test
  fun `exact match assigns correct timestamps`() {
    val segments =
      listOf(
        TranscribedSegment(text = "hello world", startMs = 0, endMs = 2000),
        TranscribedSegment(text = "goodbye moon", startMs = 2000, endMs = 4000),
      )
    val lyrics = listOf("hello world", "goodbye moon")

    val result = TranscriptionLyricsMatcher.match(segments, lyrics, Language.EN)

    assertThat(result.lines).hasSize(2)
    assertThat(result.lines[0].startMs).isEqualTo(0)
    assertThat(result.lines[0].endMs).isEqualTo(2000)
    assertThat(result.lines[1].startMs).isEqualTo(2000)
    assertThat(result.lines[1].endMs).isEqualTo(4000)
    assertThat(result.overallConfidence).isGreaterThan(0.9f)
  }

  @Test
  fun `partial match with typos still assigns timestamps`() {
    val segments =
      listOf(
        TranscribedSegment(text = "helo world", startMs = 0, endMs = 2000),
        TranscribedSegment(text = "goodby moon", startMs = 2000, endMs = 4000),
      )
    val lyrics = listOf("hello world", "goodbye moon")

    val result = TranscriptionLyricsMatcher.match(segments, lyrics, Language.EN)

    assertThat(result.lines).hasSize(2)
    assertThat(result.lines[0].startMs).isEqualTo(0)
    assertThat(result.lines[1].startMs).isEqualTo(2000)
    assertThat(result.overallConfidence).isGreaterThan(0.5f)
  }

  @Test
  fun `korean whitespace differences are normalised`() {
    val segments = listOf(TranscribedSegment(text = "나는 학생 입니다", startMs = 0, endMs = 3000))
    val lyrics = listOf("나는 학생입니다")

    val result = TranscriptionLyricsMatcher.match(segments, lyrics, Language.KO)

    assertThat(result.lines).hasSize(1)
    assertThat(result.lines[0].startMs).isEqualTo(0)
    assertThat(result.lines[0].endMs).isEqualTo(3000)
  }

  @Test
  fun `unmatched lyrics lines get interpolated timestamps`() {
    val segments =
      listOf(
        TranscribedSegment(text = "first line", startMs = 0, endMs = 2000),
        TranscribedSegment(text = "third line", startMs = 4000, endMs = 6000),
      )
    val lyrics = listOf("first line", "second line", "third line")

    val result = TranscriptionLyricsMatcher.match(segments, lyrics, Language.EN)

    assertThat(result.lines).hasSize(3)
    // First should be matched
    assertThat(result.lines[0].startMs).isEqualTo(0)
    // Second line should be interpolated (timestamp between neighbours)
    assertThat(result.lines[1].startMs).isAtLeast(0)
    assertThat(result.lines[1].endMs).isAtMost(6000)
    // All lines should have non-negative timestamps
    for (line in result.lines) {
      assertThat(line.startMs).isAtLeast(0)
      assertThat(line.endMs).isAtLeast(line.startMs)
    }
  }

  @Test
  fun `empty lyrics returns empty result`() {
    val result =
      TranscriptionLyricsMatcher.match(
        listOf(TranscribedSegment("text", 0, 1000)),
        emptyList(),
        Language.EN,
      )

    assertThat(result.lines).isEmpty()
    assertThat(result.words).isEmpty()
    assertThat(result.overallConfidence).isEqualTo(0f)
  }

  @Test
  fun `empty segments still returns lines with interpolated timestamps`() {
    val lyrics = listOf("line one", "line two")

    val result = TranscriptionLyricsMatcher.match(emptyList(), lyrics, Language.EN)

    assertThat(result.lines).hasSize(2)
    assertThat(result.overallConfidence).isEqualTo(0f)
  }

  @Test
  fun `word distribution within a line is even`() {
    val segments = listOf(TranscribedSegment(text = "one two three", startMs = 0, endMs = 3000))
    val lyrics = listOf("one two three")

    val result = TranscriptionLyricsMatcher.match(segments, lyrics, Language.EN)

    val words = result.lines[0].wordAlignments
    assertThat(words).hasSize(3)
    assertThat(words[0].startMs).isEqualTo(0)
    assertThat(words[0].endMs).isEqualTo(1000)
    assertThat(words[1].startMs).isEqualTo(1000)
    assertThat(words[1].endMs).isEqualTo(2000)
    assertThat(words[2].startMs).isEqualTo(2000)
    assertThat(words[2].endMs).isEqualTo(3000)
  }

  @Test
  fun `normalise strips punctuation and lowercases for english`() {
    val result = TranscriptionLyricsMatcher.normalise("Hello, World!", Language.EN)
    assertThat(result).isEqualTo("hello world")
  }

  @Test
  fun `normalise removes whitespace for korean`() {
    val result = TranscriptionLyricsMatcher.normalise("안녕 하세요", Language.KO)
    assertThat(result).isEqualTo("안녕하세요")
  }

  @Test
  fun `levenshtein similarity is 1 for identical strings`() {
    assertThat(TranscriptionLyricsMatcher.levenshteinSimilarity("hello", "hello")).isEqualTo(1f)
  }

  @Test
  fun `levenshtein similarity is 0 for completely different strings`() {
    assertThat(TranscriptionLyricsMatcher.levenshteinSimilarity("abc", "xyz")).isEqualTo(0f)
  }

  @Test
  fun `enhanced lrc format is correct`() {
    val segments =
      listOf(
        TranscribedSegment(text = "first line", startMs = 0, endMs = 2000),
        TranscribedSegment(text = "second line", startMs = 65000, endMs = 67000),
      )
    val lyrics = listOf("first line", "second line")

    val result = TranscriptionLyricsMatcher.match(segments, lyrics, Language.EN)

    assertThat(result.enhancedLrc).contains("[00:00.00]first line")
    assertThat(result.enhancedLrc).contains("[01:05.00]second line")
  }

  @Test
  fun `multi-segment lyrics line consumes multiple transcription segments`() {
    val segments =
      listOf(
        TranscribedSegment(text = "hello", startMs = 0, endMs = 1000),
        TranscribedSegment(text = "world", startMs = 1000, endMs = 2000),
        TranscribedSegment(text = "goodbye", startMs = 2000, endMs = 3000),
      )
    val lyrics = listOf("hello world", "goodbye")

    val result = TranscriptionLyricsMatcher.match(segments, lyrics, Language.EN)

    assertThat(result.lines).hasSize(2)
    assertThat(result.lines[0].startMs).isEqualTo(0)
    assertThat(result.lines[0].endMs).isEqualTo(2000)
    assertThat(result.lines[1].startMs).isEqualTo(2000)
    assertThat(result.lines[1].endMs).isEqualTo(3000)
  }
}
