package com.deeplayer.feature.lyricsaligner.g2p

import com.deeplayer.feature.lyricsaligner.g2p.KoreanJamo.CHOSEONG
import com.deeplayer.feature.lyricsaligner.g2p.KoreanJamo.DOUBLE_JONGSEONG
import com.deeplayer.feature.lyricsaligner.g2p.KoreanJamo.JONGSEONG
import com.deeplayer.feature.lyricsaligner.g2p.KoreanJamo.JONG_TO_CHO
import com.deeplayer.feature.lyricsaligner.g2p.KoreanJamo.Syllable
import javax.inject.Inject

/**
 * Korean grapheme-to-phoneme converter. Applies standard Korean pronunciation rules to convert
 * written Hangul text into its phonemic representation as a jamo sequence.
 *
 * Implements 20+ pronunciation rules including liaison, nasalization, fortition, palatalization,
 * liquidization, aspiration, h-deletion, and more.
 */
class KoreanG2P @Inject constructor() {

  /**
   * Convert Korean text to a phoneme sequence (list of jamo characters).
   *
   * Steps:
   * 1. Normalize numbers and symbols
   * 2. Decompose Hangul syllables
   * 3. Apply inter-syllable pronunciation rules
   * 4. Return jamo sequence
   */
  fun convert(text: String): List<Char> {
    val normalized = normalizeText(text)
    val syllables = mutableListOf<Syllable?>()

    // Decompose all characters; non-Hangul become null placeholders
    for (ch in normalized) {
      if (KoreanJamo.isHangulSyllable(ch)) {
        syllables.add(KoreanJamo.decompose(ch))
      } else {
        syllables.add(null)
      }
    }

    // Apply pronunciation rules between adjacent syllables
    applyPronunciationRules(syllables)

    // Convert to jamo sequence
    val result = mutableListOf<Char>()
    for ((i, syl) in syllables.withIndex()) {
      if (syl != null) {
        result.add(syl.choChar)
        result.add(syl.jungChar)
        if (syl.hasJong) {
          result.add(syl.jongChar)
        }
      } else {
        val ch = normalized[i]
        if (!ch.isWhitespace()) {
          result.add(ch)
        }
      }
    }
    return result
  }

  /** Convert text to a phoneme string representation. */
  fun convertToString(text: String): String {
    return convert(text).joinToString("")
  }

  private fun applyPronunciationRules(syllables: MutableList<Syllable?>) {
    // Process syllable pairs from left to right.
    // Rule application order follows standard Korean phonology:
    // 1. H-deletion / Aspiration (consume ㅎ)
    // 2. Palatalization (ㄷ,ㅌ + 이)
    // 3. Liaison (consonant + ㅇ)
    // 4. Neutralization (remaining unreleased finals)
    // 5. Nasalization
    // 6. Liquidization
    // 7. Fortition
    for (i in 0 until syllables.size - 1) {
      val curr = syllables[i] ?: continue
      val next = syllables[i + 1] ?: continue

      var c = curr
      var n = next

      // Step 1: H-deletion (ㅎ + vowel, ㄶ/ㅀ + vowel)
      val pair1 = applyHDeletion(c, n)
      c = pair1.first
      n = pair1.second

      // Step 2: Aspiration (ㅎ + obstruent, obstruent + ㅎ)
      val pair2 = applyAspiration(c, n)
      c = pair2.first
      n = pair2.second

      // Step 3: Palatalization (ㄷ/ㅌ + 이)
      val pair3 = applyPalatalization(c, n)
      c = pair3.first
      n = pair3.second

      // Step 4: Liaison (final + ㅇ initial)
      val pair4 = applyLiaison(c, n)
      c = pair4.first
      n = pair4.second

      // Step 5: Neutralization (for remaining jongseong before consonant)
      if (c.hasJong) {
        c = applyJongseongNeutralization(c) ?: c
      }

      // Step 6: Nasalization
      val pair5 = applyNasalization(c, n)
      c = pair5.first
      n = pair5.second

      // Step 7: Liquidization
      val pair6 = applyLiquidization(c, n)
      c = pair6.first
      n = pair6.second

      // Step 8: Fortition
      val pair7 = applyFortition(c, n)
      c = pair7.first
      n = pair7.second

      syllables[i] = c
      syllables[i + 1] = n
    }

    // Handle last syllable neutralization (word-final position)
    val last = syllables.lastOrNull()
    if (last != null) {
      val idx = syllables.size - 1
      syllables[idx] = applyJongseongNeutralization(last)
    }
  }

  // --- Rule 1: 종성 중화 (Final consonant neutralization) ---
  // Representative sounds: ㄱ,ㄴ,ㄷ,ㄹ,ㅁ,ㅂ,ㅇ
  private fun applyJongseongNeutralization(s: Syllable): Syllable? {
    if (!s.hasJong) return s
    val jongChar = s.jongChar
    val neutralized = neutralizeJong(jongChar)
    val newJongIdx = JONGSEONG.indexOf(neutralized)
    return if (newJongIdx >= 0) s.copy(jong = newJongIdx) else s
  }

  private fun neutralizeJong(jong: Char): Char {
    return when (jong) {
      '\u3131',
      '\u3132',
      '\u3133',
      '\u314B' -> '\u3131' // ㄱ,ㄲ,ㄳ,ㅋ → ㄱ
      '\u3134',
      '\u3135',
      '\u3136' -> '\u3134' // ㄴ,ㄵ,ㄶ → ㄴ
      '\u3137',
      '\u3145',
      '\u3146',
      '\u3148',
      '\u314A',
      '\u314E' -> '\u3137' // ㄷ,ㅅ,ㅆ,ㅈ,ㅊ,ㅎ → ㄷ
      '\u313A' -> '\u3131' // ㄺ → ㄱ (e.g., 읽다→[익따])
      '\u313B' -> '\u3141' // ㄻ → ㅁ (e.g., 삶→[삼])
      '\u3139',
      '\u313C',
      '\u313D',
      '\u313E',
      '\u313F',
      '\u3140' -> '\u3139' // ㄹ,ㄼ,ㄽ,ㄾ,ㄿ,ㅀ → ㄹ
      '\u3141' -> '\u3141' // ㅁ
      '\u3142',
      '\u3144',
      '\u314D' -> '\u3142' // ㅂ,ㅄ,ㅍ → ㅂ
      '\u3147' -> '\u3147' // ㅇ
      else -> jong
    }
  }

  // --- Rule 2: 연음 (Liaison) ---
  // When a syllable with a final consonant is followed by one starting with ㅇ,
  // the final consonant moves to become the initial of the next syllable.
  private fun applyLiaison(curr: Syllable, next: Syllable): Pair<Syllable, Syllable> {
    if (!curr.hasJong) return curr to next
    // Next syllable must start with ㅇ (ieung, index 11)
    if (next.cho != 11) return curr to next

    val jongChar = JONGSEONG[curr.jong]

    // Handle double jongseong: first stays, second moves
    val doublePair = DOUBLE_JONGSEONG[jongChar]
    if (doublePair != null) {
      val firstJongIdx = JONGSEONG.indexOf(doublePair.first)
      val secondChoIdx = JONG_TO_CHO[doublePair.second] ?: return curr to next
      return curr.copy(jong = firstJongIdx) to next.copy(cho = secondChoIdx)
    }

    // Single jongseong moves entirely
    val choIdx = JONG_TO_CHO[jongChar] ?: return curr to next
    return curr.copy(jong = 0) to next.copy(cho = choIdx)
  }

  // --- Rule 3: 비음화 (Nasalization) ---
  // ㄱ(ㄲ,ㅋ,ㄳ,ㄺ) + ㄴ,ㅁ → ㅇ + ㄴ,ㅁ
  // ㄷ(ㅅ,ㅆ,ㅈ,ㅊ,ㅌ,ㅎ) + ㄴ,ㅁ → ㄴ + ㄴ,ㅁ
  // ㅂ(ㅍ,ㄼ,ㅄ) + ㄴ,ㅁ → ㅁ + ㄴ,ㅁ
  // Also: ㅁ,ㅇ + ㄹ → ㅁ,ㅇ + ㄴ
  private fun applyNasalization(curr: Syllable, next: Syllable): Pair<Syllable, Syllable> {
    if (!curr.hasJong) return curr to next
    val jongChar = JONGSEONG[curr.jong]
    val choChar = CHOSEONG[next.cho]

    // Pattern 1: Obstruent + nasal → nasal + nasal
    if (choChar == '\u3134' || choChar == '\u3141') { // ㄴ or ㅁ
      val nasalJong = nasalizeObstruent(jongChar)
      if (nasalJong != null && nasalJong != jongChar) {
        val newJongIdx = JONGSEONG.indexOf(nasalJong)
        if (newJongIdx >= 0) {
          return curr.copy(jong = newJongIdx) to next
        }
      }
    }

    // Pattern 2: ㅁ,ㅇ + ㄹ → ㅁ,ㅇ + ㄴ
    if (choChar == '\u3139') { // ㄹ
      if (jongChar == '\u3141' || jongChar == '\u3147') { // ㅁ or ㅇ
        val newChoIdx = CHOSEONG.indexOf('\u3134') // ㄴ
        if (newChoIdx >= 0) {
          return curr to next.copy(cho = newChoIdx)
        }
      }
    }

    // Pattern 3: ㄱ,ㄷ,ㅂ + ㄹ → nasalize + ㄴ
    if (choChar == '\u3139') { // ㄹ
      val nasalJong = nasalizeObstruent(jongChar)
      if (nasalJong != null && nasalJong != jongChar) {
        val newJongIdx = JONGSEONG.indexOf(nasalJong)
        val newChoIdx = CHOSEONG.indexOf('\u3134')
        if (newJongIdx >= 0 && newChoIdx >= 0) {
          return curr.copy(jong = newJongIdx) to next.copy(cho = newChoIdx)
        }
      }
    }

    return curr to next
  }

  private fun nasalizeObstruent(jong: Char): Char? {
    return when (jong) {
      '\u3131',
      '\u3132',
      '\u3133',
      '\u314B',
      '\u313A' -> '\u3147' // ㄱ,ㄲ,ㄳ,ㅋ,ㄺ → ㅇ
      '\u3137',
      '\u3145',
      '\u3146',
      '\u3148',
      '\u314A',
      '\u314C',
      '\u314E' -> '\u3134' // ㄷ,ㅅ,ㅆ,ㅈ,ㅊ,ㅌ,ㅎ → ㄴ
      '\u3142',
      '\u314D',
      '\u313C',
      '\u3144' -> '\u3141' // ㅂ,ㅍ,ㄼ,ㅄ → ㅁ
      else -> null
    }
  }

  // --- Rule 4: 경음화 (Fortition/Tensification) ---
  // Obstruent final + lax initial → obstruent final + tense initial
  // ㄱ + ㄱ,ㄷ,ㅂ,ㅅ,ㅈ → ㄱ + ㄲ,ㄸ,ㅃ,ㅆ,ㅉ
  private fun applyFortition(curr: Syllable, next: Syllable): Pair<Syllable, Syllable> {
    if (!curr.hasJong) return curr to next
    val jongChar = JONGSEONG[curr.jong]

    // Check if jongseong is an obstruent (ㄱ,ㄷ,ㅂ group after neutralization)
    if (!isObstruent(jongChar)) return curr to next

    val tensified = tensifyConsonant(CHOSEONG[next.cho])
    if (tensified != null) {
      val newChoIdx = CHOSEONG.indexOf(tensified)
      if (newChoIdx >= 0) {
        return curr to next.copy(cho = newChoIdx)
      }
    }
    return curr to next
  }

  private fun isObstruent(jong: Char): Boolean {
    return when (jong) {
      '\u3131',
      '\u3132',
      '\u3133',
      '\u314B', // ㄱ,ㄲ,ㄳ,ㅋ
      '\u3137',
      '\u3145',
      '\u3146',
      '\u3148',
      '\u314A',
      '\u314C', // ㄷ,ㅅ,ㅆ,ㅈ,ㅊ,ㅌ
      '\u3142',
      '\u3144',
      '\u314D' // ㅂ,ㅄ,ㅍ
      -> true
      else -> false
    }
  }

  private fun tensifyConsonant(cho: Char): Char? {
    return when (cho) {
      '\u3131' -> '\u3132' // ㄱ → ㄲ
      '\u3137' -> '\u3138' // ㄷ → ㄸ
      '\u3142' -> '\u3143' // ㅂ → ㅃ
      '\u3145' -> '\u3146' // ㅅ → ㅆ
      '\u3148' -> '\u3149' // ㅈ → ㅉ
      else -> null
    }
  }

  // --- Rule 5: 구개음화 (Palatalization) ---
  // ㄷ + 이 → ㅈ + 이, ㅌ + 이 → ㅊ + 이
  private fun applyPalatalization(curr: Syllable, next: Syllable): Pair<Syllable, Syllable> {
    if (!curr.hasJong) return curr to next
    val jongChar = JONGSEONG[curr.jong]

    // Only applies when next vowel is ㅣ (index 20)
    if (next.jung != 20) return curr to next

    // ㄷ(종성) + 이 → ㅈ(초성)
    if (jongChar == '\u3137' && next.cho == 11) { // ㄷ + ㅇ이
      val newChoIdx = CHOSEONG.indexOf('\u3148') // ㅈ
      if (newChoIdx >= 0) {
        return curr.copy(jong = 0) to next.copy(cho = newChoIdx)
      }
    }

    // ㅌ(종성) + 이 → ㅊ(초성)
    if (jongChar == '\u314C' && next.cho == 11) { // ㅌ + ㅇ이
      val newChoIdx = CHOSEONG.indexOf('\u314A') // ㅊ
      if (newChoIdx >= 0) {
        return curr.copy(jong = 0) to next.copy(cho = newChoIdx)
      }
    }

    return curr to next
  }

  // --- Rule 6: 유음화 (Liquidization) ---
  // ㄴ + ㄹ → ㄹ + ㄹ, ㄹ + ㄴ → ㄹ + ㄹ
  private fun applyLiquidization(curr: Syllable, next: Syllable): Pair<Syllable, Syllable> {
    if (!curr.hasJong) return curr to next
    val jongChar = JONGSEONG[curr.jong]
    val choChar = CHOSEONG[next.cho]

    // ㄴ(종성) + ㄹ(초성) → ㄹ + ㄹ
    if (jongChar == '\u3134' && choChar == '\u3139') {
      val newJongIdx = JONGSEONG.indexOf('\u3139')
      if (newJongIdx >= 0) {
        return curr.copy(jong = newJongIdx) to next
      }
    }

    // ㄹ(종성) + ㄴ(초성) → ㄹ + ㄹ
    if (jongChar == '\u3139' && choChar == '\u3134') {
      val newChoIdx = CHOSEONG.indexOf('\u3139')
      if (newChoIdx >= 0) {
        return curr to next.copy(cho = newChoIdx)
      }
    }

    return curr to next
  }

  // --- Rule 7: 격음화 (Aspiration) ---
  // ㅎ + ㄱ,ㄷ,ㅂ,ㅈ → ㅋ,ㅌ,ㅍ,ㅊ
  // ㄱ,ㄷ,ㅂ,ㅈ + ㅎ → ㅋ,ㅌ,ㅍ,ㅊ
  private fun applyAspiration(curr: Syllable, next: Syllable): Pair<Syllable, Syllable> {
    if (!curr.hasJong) return curr to next
    val jongChar = JONGSEONG[curr.jong]
    val choChar = CHOSEONG[next.cho]

    // Pattern 1: 종성 ㅎ + 초성 ㄱ,ㄷ,ㅂ,ㅈ → aspirated
    if (jongChar == '\u314E') { // ㅎ
      val aspirated = aspirateConsonant(choChar)
      if (aspirated != null) {
        val newChoIdx = CHOSEONG.indexOf(aspirated)
        if (newChoIdx >= 0) {
          return curr.copy(jong = 0) to next.copy(cho = newChoIdx)
        }
      }
    }

    // Pattern 2: 종성 ㄱ,ㄷ,ㅂ,ㅈ + 초성 ㅎ → aspirated
    if (choChar == '\u314E') { // ㅎ
      val aspirated = aspirateFromJong(jongChar)
      if (aspirated != null) {
        val newChoIdx = CHOSEONG.indexOf(aspirated)
        if (newChoIdx >= 0) {
          return curr.copy(jong = 0) to next.copy(cho = newChoIdx)
        }
      }
    }

    return curr to next
  }

  private fun aspirateConsonant(cho: Char): Char? {
    return when (cho) {
      '\u3131' -> '\u314B' // ㄱ → ㅋ
      '\u3137' -> '\u314C' // ㄷ → ㅌ
      '\u3142' -> '\u314D' // ㅂ → ㅍ
      '\u3148' -> '\u314A' // ㅈ → ㅊ
      else -> null
    }
  }

  private fun aspirateFromJong(jong: Char): Char? {
    return when (jong) {
      '\u3131' -> '\u314B' // ㄱ → ㅋ
      '\u3137' -> '\u314C' // ㄷ → ㅌ
      '\u3142' -> '\u314D' // ㅂ → ㅍ
      '\u3148' -> '\u314A' // ㅈ → ㅊ
      else -> null
    }
  }

  // --- Rule 8: ㅎ 탈락 (H-deletion) ---
  // ㅎ(종성) + ㅇ(초성) → ㅎ disappears, liaison doesn't apply
  // The ㅎ is silenced before a vowel.
  private fun applyHDeletion(curr: Syllable, next: Syllable): Pair<Syllable, Syllable> {
    if (!curr.hasJong) return curr to next
    val jongChar = JONGSEONG[curr.jong]

    // ㅎ(종성) + vowel (ㅇ초성) → drop ㅎ
    if (jongChar == '\u314E' && next.cho == 11) {
      return curr.copy(jong = 0) to next
    }

    // Double jongseong ending in ㅎ: e.g. ㄶ(ㄴ+ㅎ) + vowel → ㄴ liaison
    val doublePair = DOUBLE_JONGSEONG[jongChar]
    if (doublePair != null && doublePair.second == '\u314E' && next.cho == 11) {
      val firstJongIdx = JONGSEONG.indexOf(doublePair.first)
      if (firstJongIdx >= 0) {
        return curr.copy(jong = firstJongIdx) to next
      }
    }

    return curr to next
  }

  // --- Text normalization ---

  fun normalizeText(text: String): String {
    val sb = StringBuilder()
    for (ch in text) {
      when {
        ch.isDigit() -> sb.append(numberToKorean(ch))
        ch == ',' || ch == '.' || ch == '!' || ch == '?' || ch == '~' -> {} // Remove punctuation
        ch == '-' || ch == '_' -> sb.append(' ')
        else -> sb.append(ch)
      }
    }
    return sb.toString().trim()
  }

  private fun numberToKorean(ch: Char): String {
    return when (ch) {
      '0' -> "\uC601" // 영
      '1' -> "\uC77C" // 일
      '2' -> "\uC774" // 이
      '3' -> "\uC0BC" // 삼
      '4' -> "\uC0AC" // 사
      '5' -> "\uC624" // 오
      '6' -> "\uC721" // 육
      '7' -> "\uCE60" // 칠
      '8' -> "\uD314" // 팔
      '9' -> "\uAD6C" // 구
      else -> ch.toString()
    }
  }
}
