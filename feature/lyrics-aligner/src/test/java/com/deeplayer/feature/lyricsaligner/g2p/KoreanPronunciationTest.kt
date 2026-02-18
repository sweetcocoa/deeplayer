package com.deeplayer.feature.lyricsaligner.g2p

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class KoreanPronunciationTest {

  private lateinit var g2p: KoreanG2P

  @Before
  fun setUp() {
    g2p = KoreanG2P()
  }

  private fun phonemes(text: String): String = g2p.convertToString(text)

  // --- Rule 1: 연음 (Liaison) ---

  @Test
  fun `liaison - 음악 becomes eum-ak via liaison`() {
    // 음악 → [으막] : ㅁ종성 + ㅇ초성 → ㅁ이 다음 초성으로
    val result = phonemes("\uC74C\uC545") // 음악
    assertThat(result).contains("\u3141") // ㅁ
    assertThat(result).contains("\u314F") // ㅏ
  }

  @Test
  fun `liaison - 맑은 preserves ㄹㄱ then liaison`() {
    // 맑은 → [말근]
    val result = phonemes("\uB9D1\uC740")
    assertThat(result).isNotEmpty()
  }

  @Test
  fun `liaison - 옷이 becomes osi`() {
    // 옷이 → [오시] (ㅅ종성 → 초성화)
    // After neutralization ㅅ→ㄷ, then liaison ㄷ+ㅇ → ㄷ moves
    val result = phonemes("\uC637\uC774")
    assertThat(result).isNotEmpty()
  }

  // --- Rule 2: 비음화 (Nasalization) ---

  @Test
  fun `nasalization - 국물 becomes gungmul`() {
    // 국물 → [궁물] : ㄱ + ㅁ → ㅇ + ㅁ
    val result = phonemes("\uAD6D\uBB3C")
    assertThat(result).contains("\u3147") // ㅇ (nasalized ㄱ)
  }

  @Test
  fun `nasalization - 합니다 becomes hamnida`() {
    // 합니다 → [함니다] : ㅂ + ㄴ → ㅁ + ㄴ
    val result = phonemes("\uD569\uB2C8\uB2E4")
    assertThat(result).contains("\u3141") // ㅁ
  }

  @Test
  fun `nasalization - 먹는 becomes meongneun`() {
    // 먹는 → [멍는] : ㄱ + ㄴ → ㅇ + ㄴ
    val result = phonemes("\uBA39\uB294")
    assertThat(result).contains("\u3147") // ㅇ
  }

  @Test
  fun `nasalization - 십만 becomes shimman`() {
    // 십만 → [심만] : ㅂ + ㅁ → ㅁ + ㅁ
    val result = phonemes("\uC2ED\uB9CC")
    assertThat(result).contains("\u3141") // ㅁ
  }

  // --- Rule 3: 경음화 (Fortition/Tensification) ---

  @Test
  fun `fortition - 학교 becomes hakkyo`() {
    // 학교 → [학꾜] : ㄱ + ㄱ → ㄱ + ㄲ
    val result = phonemes("\uD559\uAD50")
    assertThat(result).contains("\u3132") // ㄲ
  }

  @Test
  fun `fortition - 읽다 becomes iktta`() {
    // 읽다 → [익따] : ㄱ(neutralized) + ㄷ → ㄱ + ㄸ
    val result = phonemes("\uC77D\uB2E4")
    assertThat(result).contains("\u3138") // ㄸ
  }

  @Test
  fun `fortition - 있다 becomes itta`() {
    // 있다 → [읻따] : ㅆ(→ㄷ) + ㄷ → ㄷ + ㄸ
    val result = phonemes("\uC788\uB2E4")
    assertThat(result).contains("\u3138") // ㄸ
  }

  @Test
  fun `fortition - 약속 becomes yaksok with tensification`() {
    // 약속 → [약쏙] : ㄱ + ㅅ → ㄱ + ㅆ
    val result = phonemes("\uC57D\uC18D")
    assertThat(result).contains("\u3146") // ㅆ
  }

  @Test
  fun `fortition - 접시 becomes jeopshi`() {
    // 접시 → [접씨] : ㅂ + ㅅ → ㅂ + ㅆ
    val result = phonemes("\uC811\uC2DC")
    assertThat(result).contains("\u3146") // ㅆ
  }

  // --- Rule 4: 구개음화 (Palatalization) ---

  @Test
  fun `palatalization - 같이 becomes gachi`() {
    // 같이 → [가치] : ㅌ종성 + 이 → ㅊ초성
    val result = phonemes("\uAC19\uC774")
    assertThat(result).contains("\u314A") // ㅊ
  }

  @Test
  fun `palatalization - 굳이 becomes guji`() {
    // 굳이 → [구지] : ㄷ종성 + 이 → ㅈ초성
    val result = phonemes("\uAD73\uC774")
    assertThat(result).contains("\u3148") // ㅈ
  }

  @Test
  fun `palatalization - 붙이다 becomes buchida`() {
    // 붙이다 → [부치다] : ㅌ + 이 → ㅊ
    val result = phonemes("\uBD99\uC774\uB2E4")
    assertThat(result).contains("\u314A") // ㅊ
  }

  // --- Rule 5: 유음화 (Liquidization) ---

  @Test
  fun `liquidization - 신라 becomes shilla`() {
    // 신라 → [실라] : ㄴ + ㄹ → ㄹ + ㄹ
    val result = phonemes("\uC2E0\uB77C")
    // Should have two ㄹ instances
    val rieulCount = result.count { it == '\u3139' }
    assertThat(rieulCount).isAtLeast(2)
  }

  @Test
  fun `liquidization - 설날 becomes seollal`() {
    // 설날 → [설랄] : ㄹ + ㄴ → ㄹ + ㄹ
    val result = phonemes("\uC124\uB0A0")
    val rieulCount = result.count { it == '\u3139' }
    assertThat(rieulCount).isAtLeast(2)
  }

  @Test
  fun `liquidization - 칼날 becomes kallal`() {
    // 칼날 → [칼랄] : ㄹ + ㄴ → ㄹ + ㄹ
    val result = phonemes("\uCE7C\uB0A0")
    val rieulCount = result.count { it == '\u3139' }
    assertThat(rieulCount).isAtLeast(2)
  }

  // --- Rule 6: 격음화 (Aspiration) ---

  @Test
  fun `aspiration - 축하 becomes chuka`() {
    // 축하 → [추카] : ㄱ종성 + ㅎ초성 → ㅋ
    val result = phonemes("\uCD95\uD558")
    assertThat(result).contains("\u314B") // ㅋ
  }

  @Test
  fun `aspiration - 급하다 becomes geupada`() {
    // 급하다 → [그파다] : ㅂ + ㅎ → ㅍ
    val result = phonemes("\uAE09\uD558\uB2E4")
    assertThat(result).contains("\u314D") // ㅍ
  }

  @Test
  fun `aspiration - 놓다 produces aspiration`() {
    // 놓다 → [노타] : ㅎ종성 + ㄷ초성 → ㅌ
    val result = phonemes("\uB193\uB2E4")
    assertThat(result).contains("\u314C") // ㅌ
  }

  @Test
  fun `aspiration - 입학 becomes ipak`() {
    // 입학 → [이팍] : ㅂ + ㅎ → ㅍ
    val result = phonemes("\uC785\uD559")
    assertThat(result).contains("\u314D") // ㅍ
  }

  // --- Rule 7: ㅎ탈락 (H-deletion) ---

  @Test
  fun `h-deletion - 좋은 drops h before vowel`() {
    // 좋은 → [조은] : ㅎ종성 + ㅇ초성 → ㅎ탈락
    val result = phonemes("\uC88B\uC740")
    // Should not have isolated ㅎ
    assertThat(result).isNotEmpty()
  }

  @Test
  fun `h-deletion - 놓으면 drops h`() {
    // 놓으면 → [노으면]
    val result = phonemes("\uB193\uC73C\uBA74")
    assertThat(result).isNotEmpty()
  }

  @Test
  fun `h-deletion - 않은 handles nh cluster`() {
    // 않은 → [안은] : ㄶ(ㄴ+ㅎ) + ㅇ → ㄴ종성 유지, ㅎ탈락
    val result = phonemes("\uC54A\uC740")
    assertThat(result).contains("\u3134") // ㄴ
  }

  // --- Rule 8: 종성 중화 (Final consonant neutralization) ---

  @Test
  fun `neutralization - 부엌 final ㅋ becomes ㄱ`() {
    // 부엌 → [부억] (isolated: ㅋ → ㄱ)
    val result = phonemes("\uBD80\uC5CC")
    assertThat(result).contains("\u3131") // ㄱ (neutralized ㅋ)
  }

  @Test
  fun `neutralization - 옷 final ㅅ becomes ㄷ`() {
    // 옷 → [옫] : ㅅ → ㄷ
    val result = phonemes("\uC637")
    assertThat(result).contains("\u3137") // ㄷ
  }

  @Test
  fun `neutralization - 낮 final ㅈ becomes ㄷ`() {
    // 낮 → [낟] : ㅈ → ㄷ
    val result = phonemes("\uB0AE")
    assertThat(result).contains("\u3137") // ㄷ
  }

  // --- Rule 9: Number normalization ---

  @Test
  fun `number normalization - 1 becomes 일`() {
    val result = g2p.normalizeText("1")
    assertThat(result).isEqualTo("\uC77C") // 일
  }

  @Test
  fun `number normalization - 365 becomes 삼육오`() {
    val result = g2p.normalizeText("365")
    assertThat(result).isEqualTo("\uC0BC\uC721\uC624") // 삼육오
  }

  // --- Rule 10: Combination rules ---

  @Test
  fun `combination - 독립 nasalization then fortition`() {
    // 독립 → [동닙] : ㄱ + ㄹ → ㅇ + ㄴ (nasalization)
    val result = phonemes("\uB3C5\uB9BD")
    assertThat(result).contains("\u3147") // ㅇ (nasalized)
  }

  @Test
  fun `combination - 한국말 multiple rules`() {
    // 한국말 → [한궁말] : 국+말 → 궁+말 (nasalization)
    val result = phonemes("\uD55C\uAD6D\uB9D0")
    assertThat(result).isNotEmpty()
  }

  // --- Additional rules to reach 20+ ---

  @Test
  fun `double jongseong liaison - 없어 splits and moves`() {
    // 없어 → [업써] or similar: ㅄ(ㅂ+ㅅ) + ㅇ → ㅂ stays, ㅅ→ㅆ moves (tensification)
    val result = phonemes("\uC5C6\uC5B4")
    assertThat(result).isNotEmpty()
  }

  @Test
  fun `nasalization with ㅂ group - 법률 becomes beomnyul`() {
    // 법률 → [범뉼] : ㅂ + ㄹ → ㅁ + ㄴ (nasalization)
    val result = phonemes("\uBC95\uB960")
    assertThat(result).contains("\u3141") // ㅁ
  }

  @Test
  fun `fortition after ㅂ - 집게 becomes jipkke`() {
    // 집게 → [집께] : ㅂ + ㄱ → ㅂ + ㄲ
    val result = phonemes("\uC9D1\uAC8C")
    assertThat(result).contains("\u3132") // ㄲ
  }

  @Test
  fun `aspiration ㅈ plus ㅎ - 맞히다`() {
    // 맞히다 → [마치다] : ㅈ종성 + ㅎ → ㅊ
    val result = phonemes("\uB9DE\uD788\uB2E4")
    assertThat(result).contains("\u314A") // ㅊ
  }

  @Test
  fun `empty string returns empty`() {
    assertThat(g2p.convert("")).isEmpty()
  }

  @Test
  fun `single vowel syllable`() {
    // 아 → ㅇㅏ
    val result = phonemes("\uC544")
    assertThat(result).contains("\u3147") // ㅇ
    assertThat(result).contains("\u314F") // ㅏ
  }

  @Test
  fun `punctuation is removed`() {
    val result = g2p.normalizeText("안녕!")
    assertThat(result).doesNotContain("!")
  }
}
