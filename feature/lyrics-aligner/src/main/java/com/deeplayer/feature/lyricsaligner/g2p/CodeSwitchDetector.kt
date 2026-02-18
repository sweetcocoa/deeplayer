package com.deeplayer.feature.lyricsaligner.g2p

/** Detects language boundaries in mixed Korean-English text. */
class CodeSwitchDetector @javax.inject.Inject constructor() {

  enum class Language {
    KOREAN,
    ENGLISH,
    OTHER,
  }

  data class Segment(val text: String, val language: Language)

  /**
   * Detect language segments in mixed text. Groups consecutive characters of the same language into
   * segments.
   */
  fun detect(text: String): List<Segment> {
    if (text.isEmpty()) return emptyList()

    val segments = mutableListOf<Segment>()
    var currentLang: Language? = null
    val currentText = StringBuilder()

    for (ch in text) {
      val lang = detectCharLanguage(ch)

      if (lang == Language.OTHER && currentLang != null) {
        // Whitespace and punctuation attach to the current segment
        currentText.append(ch)
        continue
      }

      if (lang != currentLang && lang != Language.OTHER) {
        if (currentText.isNotEmpty() && currentLang != null) {
          segments.add(Segment(currentText.toString().trim(), currentLang))
          currentText.clear()
        }
        currentLang = lang
      }

      currentText.append(ch)
    }

    if (currentText.isNotEmpty() && currentLang != null) {
      val trimmed = currentText.toString().trim()
      if (trimmed.isNotEmpty()) {
        segments.add(Segment(trimmed, currentLang))
      }
    }

    return segments
  }

  /** Detect the dominant language of a text string. */
  fun detectDominantLanguage(text: String): Language {
    var korean = 0
    var english = 0
    for (ch in text) {
      when (detectCharLanguage(ch)) {
        Language.KOREAN -> korean++
        Language.ENGLISH -> english++
        Language.OTHER -> {}
      }
    }
    return when {
      korean > english -> Language.KOREAN
      english > korean -> Language.ENGLISH
      korean > 0 -> Language.KOREAN
      else -> Language.OTHER
    }
  }

  private fun detectCharLanguage(ch: Char): Language {
    return when {
      // Hangul syllables: AC00-D7A3
      ch.code in 0xAC00..0xD7A3 -> Language.KOREAN
      // Hangul jamo: 3131-3163
      ch.code in 0x3131..0x3163 -> Language.KOREAN
      // Hangul compatibility jamo: 3130-318F
      ch.code in 0x3130..0x318F -> Language.KOREAN
      // Latin letters
      ch in 'A'..'Z' || ch in 'a'..'z' -> Language.ENGLISH
      else -> Language.OTHER
    }
  }
}
