package com.deeplayer.feature.lyricsaligner.g2p

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class CodeSwitchTest {

  private lateinit var detector: CodeSwitchDetector

  @Before
  fun setUp() {
    detector = CodeSwitchDetector()
  }

  @Test
  fun `pure Korean text returns single Korean segment`() {
    val segments = detector.detect("\uB098\uC758 \uC0AC\uB791") // 나의 사랑
    assertThat(segments).hasSize(1)
    assertThat(segments[0].language).isEqualTo(CodeSwitchDetector.Language.KOREAN)
    assertThat(segments[0].text).isEqualTo("\uB098\uC758 \uC0AC\uB791")
  }

  @Test
  fun `pure English text returns single English segment`() {
    val segments = detector.detect("I love you")
    assertThat(segments).hasSize(1)
    assertThat(segments[0].language).isEqualTo(CodeSwitchDetector.Language.ENGLISH)
  }

  @Test
  fun `mixed text - 나의 Love Story`() {
    val segments = detector.detect("\uB098\uC758 Love Story")
    assertThat(segments).hasSize(2)
    assertThat(segments[0].language).isEqualTo(CodeSwitchDetector.Language.KOREAN)
    assertThat(segments[0].text).contains("\uB098\uC758")
    assertThat(segments[1].language).isEqualTo(CodeSwitchDetector.Language.ENGLISH)
    assertThat(segments[1].text).contains("Love")
  }

  @Test
  fun `mixed text with alternation`() {
    // 오늘 night 별이 shine
    val segments = detector.detect("\uC624\uB298 night \uBCC4\uC774 shine")
    assertThat(segments.size).isAtLeast(3)
    assertThat(segments[0].language).isEqualTo(CodeSwitchDetector.Language.KOREAN)
    assertThat(segments[1].language).isEqualTo(CodeSwitchDetector.Language.ENGLISH)
  }

  @Test
  fun `empty string returns empty list`() {
    assertThat(detector.detect("")).isEmpty()
  }

  @Test
  fun `dominant language - Korean majority`() {
    val lang = detector.detectDominantLanguage("\uC548\uB155\uD558\uC138\uC694 hello")
    assertThat(lang).isEqualTo(CodeSwitchDetector.Language.KOREAN)
  }

  @Test
  fun `dominant language - English majority`() {
    val lang = detector.detectDominantLanguage("hello world \uD558\uC774")
    assertThat(lang).isEqualTo(CodeSwitchDetector.Language.ENGLISH)
  }

  @Test
  fun `whitespace only returns empty`() {
    assertThat(detector.detect("   ")).isEmpty()
  }

  @Test
  fun `numbers treated as other and attached to current segment`() {
    val segments = detector.detect("Love 123 \uC0AC\uB791")
    assertThat(segments.size).isAtLeast(2)
  }
}
