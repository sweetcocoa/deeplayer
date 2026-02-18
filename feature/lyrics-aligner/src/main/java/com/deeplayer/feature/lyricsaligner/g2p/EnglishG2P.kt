package com.deeplayer.feature.lyricsaligner.g2p

/**
 * English grapheme-to-phoneme converter using a built-in CMU Pronouncing Dictionary subset and
 * rule-based fallback for out-of-vocabulary words.
 */
class EnglishG2P @javax.inject.Inject constructor() {

  /** CMU dictionary subset: word → phoneme list (ARPAbet without stress markers). */
  private val cmuDict: Map<String, List<String>> by lazy { loadCmuDict() }

  /** Convert an English word to a phoneme sequence. */
  fun convert(word: String): List<String> {
    val normalized = word.uppercase().replace(Regex("[^A-Z']"), "")
    if (normalized.isEmpty()) return emptyList()

    // Try CMU dictionary first
    val cmuResult = cmuDict[normalized]
    if (cmuResult != null) return cmuResult

    // Rule-based fallback for OOV
    return ruleBasedG2P(normalized.lowercase())
  }

  /** Convert a sentence to phonemes. */
  fun convertSentence(sentence: String): List<String> {
    return sentence
      .split(Regex("\\s+"))
      .filter { it.isNotBlank() }
      .flatMap { word -> convert(word) + listOf(" ") }
      .dropLast(1) // Remove trailing space
  }

  /**
   * Rule-based fallback for words not in CMU dictionary. Simple letter-to-phoneme mapping with
   * basic context rules.
   */
  private fun ruleBasedG2P(word: String): List<String> {
    val phonemes = mutableListOf<String>()
    var i = 0
    while (i < word.length) {
      val remaining = word.substring(i)
      val (phoneme, consumed) = matchRule(remaining, i, word)
      if (phoneme.isNotEmpty()) {
        phonemes.addAll(phoneme)
      }
      i += consumed
    }
    return phonemes
  }

  @Suppress("ReturnCount")
  private fun matchRule(
    remaining: String,
    pos: Int,
    @Suppress("UnusedParameter") word: String,
  ): Pair<List<String>, Int> {
    // Multi-character rules first
    if (remaining.startsWith("th")) return listOf("TH") to 2
    if (remaining.startsWith("sh")) return listOf("SH") to 2
    if (remaining.startsWith("ch")) return listOf("CH") to 2
    if (remaining.startsWith("ph")) return listOf("F") to 2
    if (remaining.startsWith("wh")) return listOf("W") to 2
    if (remaining.startsWith("ck")) return listOf("K") to 2
    if (remaining.startsWith("ng")) return listOf("NG") to 2
    if (remaining.startsWith("ght")) return listOf("T") to 3
    if (remaining.startsWith("igh")) return listOf("AY") to 3
    if (remaining.startsWith("tion")) return listOf("SH", "AH", "N") to 4
    if (remaining.startsWith("sion")) return listOf("ZH", "AH", "N") to 4
    if (remaining.startsWith("ous")) return listOf("AH", "S") to 3
    if (remaining.startsWith("ee")) return listOf("IY") to 2
    if (remaining.startsWith("ea")) return listOf("IY") to 2
    if (remaining.startsWith("oo")) return listOf("UW") to 2
    if (remaining.startsWith("ou")) return listOf("AW") to 2
    if (remaining.startsWith("ow")) return listOf("OW") to 2
    if (remaining.startsWith("ai")) return listOf("EY") to 2
    if (remaining.startsWith("ay")) return listOf("EY") to 2
    if (remaining.startsWith("oi")) return listOf("OY") to 2
    if (remaining.startsWith("oy")) return listOf("OY") to 2

    // Silent e at end
    if (remaining == "e" && pos > 0) return emptyList<String>() to 1

    // Single character rules
    val phoneme =
      when (remaining[0]) {
        'a' -> listOf("AE")
        'b' -> listOf("B")
        'c' -> if (remaining.length > 1 && remaining[1] in "eiy") listOf("S") else listOf("K")
        'd' -> listOf("D")
        'e' -> listOf("EH")
        'f' -> listOf("F")
        'g' -> if (remaining.length > 1 && remaining[1] in "eiy") listOf("JH") else listOf("G")
        'h' -> listOf("HH")
        'i' -> listOf("IH")
        'j' -> listOf("JH")
        'k' -> listOf("K")
        'l' -> listOf("L")
        'm' -> listOf("M")
        'n' -> listOf("N")
        'o' -> listOf("AA")
        'p' -> listOf("P")
        'q' -> listOf("K")
        'r' -> listOf("R")
        's' -> listOf("S")
        't' -> listOf("T")
        'u' -> listOf("AH")
        'v' -> listOf("V")
        'w' -> listOf("W")
        'x' -> listOf("K", "S")
        'y' -> if (pos == 0) listOf("Y") else listOf("IY")
        'z' -> listOf("Z")
        '\'' -> emptyList()
        else -> emptyList()
      }
    return phoneme to 1
  }

  private fun loadCmuDict(): Map<String, List<String>> {
    // Embedded subset of CMU Pronouncing Dictionary (most common words in lyrics)
    return mapOf(
      "THE" to listOf("DH", "AH"),
      "A" to listOf("AH"),
      "I" to listOf("AY"),
      "YOU" to listOf("Y", "UW"),
      "IT" to listOf("IH", "T"),
      "IS" to listOf("IH", "Z"),
      "IN" to listOf("IH", "N"),
      "TO" to listOf("T", "UW"),
      "AND" to listOf("AE", "N", "D"),
      "OF" to listOf("AH", "V"),
      "MY" to listOf("M", "AY"),
      "ME" to listOf("M", "IY"),
      "YOUR" to listOf("Y", "AO", "R"),
      "LOVE" to listOf("L", "AH", "V"),
      "BABY" to listOf("B", "EY", "B", "IY"),
      "HEART" to listOf("HH", "AA", "R", "T"),
      "KNOW" to listOf("N", "OW"),
      "LIKE" to listOf("L", "AY", "K"),
      "JUST" to listOf("JH", "AH", "S", "T"),
      "WANT" to listOf("W", "AA", "N", "T"),
      "CAN" to listOf("K", "AE", "N"),
      "ALL" to listOf("AO", "L"),
      "TIME" to listOf("T", "AY", "M"),
      "NEVER" to listOf("N", "EH", "V", "ER"),
      "FEEL" to listOf("F", "IY", "L"),
      "WORLD" to listOf("W", "ER", "L", "D"),
      "NIGHT" to listOf("N", "AY", "T"),
      "DAY" to listOf("D", "EY"),
      "LIFE" to listOf("L", "AY", "F"),
      "COME" to listOf("K", "AH", "M"),
      "BACK" to listOf("B", "AE", "K"),
      "DOWN" to listOf("D", "AW", "N"),
      "WAY" to listOf("W", "EY"),
      "MAKE" to listOf("M", "EY", "K"),
      "SAY" to listOf("S", "EY"),
      "GO" to listOf("G", "OW"),
      "NO" to listOf("N", "OW"),
      "SO" to listOf("S", "OW"),
      "BE" to listOf("B", "IY"),
      "DO" to listOf("D", "UW"),
      "ARE" to listOf("AA", "R"),
      "WE" to listOf("W", "IY"),
      "ONE" to listOf("W", "AH", "N"),
      "ON" to listOf("AA", "N"),
      "WITH" to listOf("W", "IH", "DH"),
      "NOT" to listOf("N", "AA", "T"),
      "BUT" to listOf("B", "AH", "T"),
      "WHAT" to listOf("W", "AH", "T"),
      "UP" to listOf("AH", "P"),
      "OUT" to listOf("AW", "T"),
      "THAT" to listOf("DH", "AE", "T"),
      "THIS" to listOf("DH", "IH", "S"),
      "WHEN" to listOf("W", "EH", "N"),
      "DON'T" to listOf("D", "OW", "N", "T"),
      "STORY" to listOf("S", "T", "AO", "R", "IY"),
      "HAPPY" to listOf("HH", "AE", "P", "IY"),
      "HELLO" to listOf("HH", "AH", "L", "OW"),
      "DREAM" to listOf("D", "R", "IY", "M"),
      "FIRE" to listOf("F", "AY", "ER"),
      "FOREVER" to listOf("F", "ER", "EH", "V", "ER"),
      "BEAUTIFUL" to listOf("B", "Y", "UW", "T", "AH", "F", "AH", "L"),
      "TOGETHER" to listOf("T", "AH", "G", "EH", "DH", "ER"),
    )
  }
}
