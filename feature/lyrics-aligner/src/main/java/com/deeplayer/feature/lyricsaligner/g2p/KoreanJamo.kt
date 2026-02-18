package com.deeplayer.feature.lyricsaligner.g2p

/**
 * Hangul Unicode decomposition utilities. Decomposes syllables into individual jamo (consonants and
 * vowels) using Unicode math without external libraries.
 */
object KoreanJamo {
  private const val HANGUL_BASE = 0xAC00
  private const val HANGUL_END = 0xD7A3
  private const val JONGSUNG_COUNT = 28
  private const val JUNGSUNG_COUNT = 21

  /** Initial consonants (초성) in Unicode order. */
  val CHOSEONG =
    charArrayOf(
      '\u3131',
      '\u3132',
      '\u3134',
      '\u3137',
      '\u3138',
      '\u3139',
      '\u3141',
      '\u3142',
      '\u3143',
      '\u3145',
      '\u3146',
      '\u3147',
      '\u3148',
      '\u3149',
      '\u314A',
      '\u314B',
      '\u314C',
      '\u314D',
      '\u314E',
    )

  /** Medial vowels (중성) in Unicode order. */
  val JUNGSEONG =
    charArrayOf(
      '\u314F',
      '\u3150',
      '\u3151',
      '\u3152',
      '\u3153',
      '\u3154',
      '\u3155',
      '\u3156',
      '\u3157',
      '\u3158',
      '\u3159',
      '\u315A',
      '\u315B',
      '\u315C',
      '\u315D',
      '\u315E',
      '\u315F',
      '\u3160',
      '\u3161',
      '\u3162',
      '\u3163',
    )

  /** Final consonants (종성). Index 0 means no final consonant. 28 values total. */
  val JONGSEONG =
    charArrayOf(
      '\u0000', // 0: no jongseong
      '\u3131', // 1: ㄱ
      '\u3132', // 2: ㄲ
      '\u3133', // 3: ㄳ
      '\u3134', // 4: ㄴ
      '\u3135', // 5: ㄵ
      '\u3136', // 6: ㄶ
      '\u3137', // 7: ㄷ
      '\u3139', // 8: ㄹ
      '\u313A', // 9: ㄺ
      '\u313B', // 10: ㄻ
      '\u313C', // 11: ㄼ
      '\u313D', // 12: ㄽ
      '\u313E', // 13: ㄾ
      '\u313F', // 14: ㄿ
      '\u3140', // 15: ㅀ
      '\u3141', // 16: ㅁ
      '\u3142', // 17: ㅂ
      '\u3144', // 18: ㅄ
      '\u3145', // 19: ㅅ
      '\u3146', // 20: ㅆ
      '\u3147', // 21: ㅇ
      '\u3148', // 22: ㅈ
      '\u314A', // 23: ㅊ
      '\u314B', // 24: ㅋ
      '\u314C', // 25: ㅌ
      '\u314D', // 26: ㅍ
      '\u314E', // 27: ㅎ
    )

  /** Double final consonants that can be split into two separate consonants. */
  val DOUBLE_JONGSEONG: Map<Char, Pair<Char, Char>> =
    mapOf(
      '\u3133' to ('\u3131' to '\u3145'), // ㄳ → ㄱ+ㅅ
      '\u3135' to ('\u3134' to '\u3148'), // ㄵ → ㄴ+ㅈ
      '\u3136' to ('\u3134' to '\u314E'), // ㄶ → ㄴ+ㅎ
      '\u313A' to ('\u3139' to '\u3131'), // ㄺ → ㄹ+ㄱ
      '\u313B' to ('\u3139' to '\u3141'), // ㄻ → ㄹ+ㅁ
      '\u313C' to ('\u3139' to '\u3142'), // ㄼ → ㄹ+ㅂ
      '\u313D' to ('\u3139' to '\u3145'), // ㄽ → ㄹ+ㅅ
      '\u313E' to ('\u3139' to '\u314C'), // ㄾ → ㄹ+ㅌ
      '\u313F' to ('\u3139' to '\u314D'), // ㄿ → ㄹ+ㅍ
      '\u3140' to ('\u3139' to '\u314E'), // ㅀ → ㄹ+ㅎ
      '\u3144' to ('\u3142' to '\u3145'), // ㅄ → ㅂ+ㅅ
    )

  /** Map from jongseong jamo to its equivalent choseong index (for liaison). */
  val JONG_TO_CHO: Map<Char, Int> =
    mapOf(
      '\u3131' to 0, // ㄱ
      '\u3132' to 1, // ㄲ
      '\u3134' to 2, // ㄴ
      '\u3137' to 3, // ㄷ
      '\u3138' to 4, // ㄸ
      '\u3139' to 5, // ㄹ
      '\u3141' to 6, // ㅁ
      '\u3142' to 7, // ㅂ
      '\u3143' to 8, // ㅃ
      '\u3145' to 9, // ㅅ
      '\u3146' to 10, // ㅆ
      '\u3147' to 11, // ㅇ
      '\u3148' to 12, // ㅈ
      '\u3149' to 13, // ㅉ
      '\u314A' to 14, // ㅊ
      '\u314B' to 15, // ㅋ
      '\u314C' to 16, // ㅌ
      '\u314D' to 17, // ㅍ
      '\u314E' to 18, // ㅎ
    )

  fun isHangulSyllable(ch: Char): Boolean = ch.code in HANGUL_BASE..HANGUL_END

  fun isHangulJamo(ch: Char): Boolean = ch.code in 0x3131..0x3163

  data class Syllable(val cho: Int, val jung: Int, val jong: Int) {
    val choChar: Char
      get() = CHOSEONG[cho]

    val jungChar: Char
      get() = JUNGSEONG[jung]

    val jongChar: Char
      get() = JONGSEONG[jong]

    val hasJong: Boolean
      get() = jong != 0
  }

  /** Decompose a Hangul syllable character into its cho/jung/jong indices. */
  fun decompose(ch: Char): Syllable? {
    if (!isHangulSyllable(ch)) return null
    val code = ch.code - HANGUL_BASE
    val cho = code / (JUNGSUNG_COUNT * JONGSUNG_COUNT)
    val jung = (code % (JUNGSUNG_COUNT * JONGSUNG_COUNT)) / JONGSUNG_COUNT
    val jong = code % JONGSUNG_COUNT
    return Syllable(cho, jung, jong)
  }

  /** Compose a Hangul syllable from cho/jung/jong indices. */
  fun compose(cho: Int, jung: Int, jong: Int = 0): Char {
    return (HANGUL_BASE + cho * JUNGSUNG_COUNT * JONGSUNG_COUNT + jung * JONGSUNG_COUNT + jong)
      .toChar()
  }

  /** Decompose a string of Hangul syllables into a list of jamo characters. */
  fun decomposeToJamo(text: String): List<Char> {
    val result = mutableListOf<Char>()
    for (ch in text) {
      val syllable = decompose(ch)
      if (syllable != null) {
        result.add(syllable.choChar)
        result.add(syllable.jungChar)
        if (syllable.hasJong) {
          result.add(syllable.jongChar)
        }
      } else {
        result.add(ch)
      }
    }
    return result
  }
}
