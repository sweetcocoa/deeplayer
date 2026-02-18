package com.deeplayer.feature.lyricsaligner.g2p

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class EnglishG2PTest {

  private lateinit var g2p: EnglishG2P

  @Before
  fun setUp() {
    g2p = EnglishG2P()
  }

  // --- CMU Dictionary lookups ---

  @Test
  fun `cmu lookup - LOVE`() {
    val result = g2p.convert("love")
    assertThat(result).containsExactly("L", "AH", "V").inOrder()
  }

  @Test
  fun `cmu lookup - HEART`() {
    val result = g2p.convert("heart")
    assertThat(result).containsExactly("HH", "AA", "R", "T").inOrder()
  }

  @Test
  fun `cmu lookup - BABY`() {
    val result = g2p.convert("baby")
    assertThat(result).containsExactly("B", "EY", "B", "IY").inOrder()
  }

  @Test
  fun `cmu lookup - KNOW`() {
    val result = g2p.convert("know")
    assertThat(result).containsExactly("N", "OW").inOrder()
  }

  @Test
  fun `cmu lookup - NIGHT`() {
    val result = g2p.convert("night")
    assertThat(result).containsExactly("N", "AY", "T").inOrder()
  }

  @Test
  fun `cmu lookup - DREAM`() {
    val result = g2p.convert("dream")
    assertThat(result).containsExactly("D", "R", "IY", "M").inOrder()
  }

  @Test
  fun `cmu lookup - BEAUTIFUL`() {
    val result = g2p.convert("beautiful")
    assertThat(result).containsExactly("B", "Y", "UW", "T", "AH", "F", "AH", "L").inOrder()
  }

  @Test
  fun `cmu lookup - FOREVER`() {
    val result = g2p.convert("forever")
    assertThat(result).containsExactly("F", "ER", "EH", "V", "ER").inOrder()
  }

  @Test
  fun `cmu lookup - WORLD`() {
    val result = g2p.convert("world")
    assertThat(result).containsExactly("W", "ER", "L", "D").inOrder()
  }

  @Test
  fun `cmu lookup - TOGETHER`() {
    val result = g2p.convert("together")
    assertThat(result).containsExactly("T", "AH", "G", "EH", "DH", "ER").inOrder()
  }

  // --- Rule-based fallback (OOV words) ---

  @Test
  fun `oov - simple CVC word cat`() {
    val result = g2p.convert("cat")
    assertThat(result).isNotEmpty()
    assertThat(result).contains("K") // c before a → K
    assertThat(result).contains("T")
  }

  @Test
  fun `oov - sh digraph`() {
    val result = g2p.convert("shout")
    assertThat(result).isNotEmpty()
    assertThat(result.first()).isEqualTo("SH")
  }

  @Test
  fun `oov - th digraph`() {
    val result = g2p.convert("thud")
    assertThat(result).isNotEmpty()
    assertThat(result.first()).isEqualTo("TH")
  }

  // --- Edge cases ---

  @Test
  fun `empty string returns empty`() {
    assertThat(g2p.convert("")).isEmpty()
  }

  @Test
  fun `case insensitive`() {
    assertThat(g2p.convert("Love")).isEqualTo(g2p.convert("LOVE"))
    assertThat(g2p.convert("love")).isEqualTo(g2p.convert("LOVE"))
  }

  @Test
  fun `sentence conversion`() {
    val result = g2p.convertSentence("I love you")
    assertThat(result).isNotEmpty()
    assertThat(result).contains("AY") // I
    assertThat(result).contains("AH") // love
  }
}
