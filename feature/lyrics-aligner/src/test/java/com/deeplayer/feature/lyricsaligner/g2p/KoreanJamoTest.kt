package com.deeplayer.feature.lyricsaligner.g2p

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KoreanJamoTest {

  @Test
  fun `isHangulSyllable returns true for Hangul syllables`() {
    assertThat(KoreanJamo.isHangulSyllable('\uAC00')).isTrue() // 가
    assertThat(KoreanJamo.isHangulSyllable('\uD7A3')).isTrue() // 힣
    assertThat(KoreanJamo.isHangulSyllable('\uB9D0')).isTrue() // 말
  }

  @Test
  fun `isHangulSyllable returns false for non-Hangul`() {
    assertThat(KoreanJamo.isHangulSyllable('A')).isFalse()
    assertThat(KoreanJamo.isHangulSyllable('1')).isFalse()
    assertThat(KoreanJamo.isHangulSyllable('\u3131')).isFalse() // ㄱ is jamo, not syllable
  }

  @Test
  fun `decompose ga returns cho=0 jung=0 jong=0`() {
    // 가 = ㄱ + ㅏ
    val result = KoreanJamo.decompose('\uAC00')!!
    assertThat(result.cho).isEqualTo(0) // ㄱ
    assertThat(result.jung).isEqualTo(0) // ㅏ
    assertThat(result.jong).isEqualTo(0)
    assertThat(result.hasJong).isFalse()
  }

  @Test
  fun `decompose han returns correct jamo`() {
    // 한 = ㅎ + ㅏ + ㄴ
    val result = KoreanJamo.decompose('\uD55C')!!
    assertThat(result.choChar).isEqualTo('\u314E') // ㅎ
    assertThat(result.jungChar).isEqualTo('\u314F') // ㅏ
    assertThat(result.jongChar).isEqualTo('\u3134') // ㄴ
    assertThat(result.hasJong).isTrue()
  }

  @Test
  fun `decompose gul returns correct jamo`() {
    // 글 = ㄱ + ㅡ + ㄹ
    val result = KoreanJamo.decompose('\uAE00')!!
    assertThat(result.choChar).isEqualTo('\u3131') // ㄱ
    assertThat(result.jungChar).isEqualTo('\u3161') // ㅡ
    assertThat(result.jongChar).isEqualTo('\u3139') // ㄹ
  }

  @Test
  fun `decompose returns null for non-Hangul`() {
    assertThat(KoreanJamo.decompose('A')).isNull()
    assertThat(KoreanJamo.decompose('3')).isNull()
  }

  @Test
  fun `compose produces correct syllable`() {
    // ㄱ(0) + ㅏ(0) = 가
    assertThat(KoreanJamo.compose(0, 0, 0)).isEqualTo('\uAC00')
    // ㅎ(18) + ㅏ(0) + ㄴ(4) = 한
    assertThat(KoreanJamo.compose(18, 0, 4)).isEqualTo('\uD55C')
  }

  @Test
  fun `decomposeToJamo handles simple word`() {
    // 가나 → ㄱㅏㄴㅏ
    val jamo = KoreanJamo.decomposeToJamo("\uAC00\uB098")
    assertThat(jamo).containsExactly('\u3131', '\u314F', '\u3134', '\u314F').inOrder()
  }

  @Test
  fun `decomposeToJamo handles word with jongseong`() {
    // 한글 → ㅎㅏㄴㄱㅡㄹ
    val jamo = KoreanJamo.decomposeToJamo("\uD55C\uAE00")
    assertThat(jamo)
      .containsExactly('\u314E', '\u314F', '\u3134', '\u3131', '\u3161', '\u3139')
      .inOrder()
  }

  @Test
  fun `decomposeToJamo preserves non-Hangul characters`() {
    // A1 → A, 1
    val jamo = KoreanJamo.decomposeToJamo("A1")
    assertThat(jamo).containsExactly('A', '1').inOrder()
  }

  @Test
  fun `decomposeToJamo handles mixed text`() {
    // 가A → ㄱㅏA
    val jamo = KoreanJamo.decomposeToJamo("\uAC00A")
    assertThat(jamo).containsExactly('\u3131', '\u314F', 'A').inOrder()
  }

  @Test
  fun `double jongseong map contains expected entries`() {
    // ㄳ → ㄱ + ㅅ
    assertThat(KoreanJamo.DOUBLE_JONGSEONG['\u3133']).isEqualTo('\u3131' to '\u3145')
    // ㄵ → ㄴ + ㅈ
    assertThat(KoreanJamo.DOUBLE_JONGSEONG['\u3135']).isEqualTo('\u3134' to '\u3148')
    // ㄺ → ㄹ + ㄱ
    assertThat(KoreanJamo.DOUBLE_JONGSEONG['\u313A']).isEqualTo('\u3139' to '\u3131')
  }

  @Test
  fun `decompose syllable with double jongseong`() {
    // 닭 = ㄷ + ㅏ + ㄺ
    val result = KoreanJamo.decompose('\uB2ED')!!
    assertThat(result.choChar).isEqualTo('\u3137') // ㄷ
    assertThat(result.jungChar).isEqualTo('\u314F') // ㅏ
    assertThat(result.hasJong).isTrue()
    // ㄺ is at index 10 in JONGSEONG
    assertThat(result.jongChar).isEqualTo('\u313A') // ㄺ
  }
}
