package com.deeplayer.feature.lyricsui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LyricsEditorTest {

  @Test
  fun `splitting text into lines filters blank lines`() {
    val text = "Line one\n\nLine two\n  \nLine three\n"
    val result = text.split("\n").filter { it.isNotBlank() }
    assertThat(result).containsExactly("Line one", "Line two", "Line three")
  }

  @Test
  fun `single line returns list with one element`() {
    val text = "Only line"
    val result = text.split("\n").filter { it.isNotBlank() }
    assertThat(result).containsExactly("Only line")
  }

  @Test
  fun `empty text returns empty list`() {
    val text = ""
    val result = text.split("\n").filter { it.isNotBlank() }
    assertThat(result).isEmpty()
  }

  @Test
  fun `whitespace only text returns empty list`() {
    val text = "  \n  \n  "
    val result = text.split("\n").filter { it.isNotBlank() }
    assertThat(result).isEmpty()
  }

  @Test
  fun `korean lyrics split correctly`() {
    val text = "첫 번째 줄\n두 번째 줄\n세 번째 줄"
    val result = text.split("\n").filter { it.isNotBlank() }
    assertThat(result).containsExactly("첫 번째 줄", "두 번째 줄", "세 번째 줄")
  }
}
