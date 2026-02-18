package com.deeplayer.feature.lyricsaligner

import com.deeplayer.core.contracts.AlignmentResult
import com.deeplayer.core.contracts.Language
import com.deeplayer.core.contracts.LineAlignment
import com.deeplayer.core.contracts.LyricsAligner
import com.deeplayer.core.contracts.WordAlignment
import com.deeplayer.feature.lyricsaligner.alignment.CtcForcedAligner
import com.deeplayer.feature.lyricsaligner.g2p.CodeSwitchDetector
import com.deeplayer.feature.lyricsaligner.g2p.EnglishG2P
import com.deeplayer.feature.lyricsaligner.g2p.KoreanG2P
import com.deeplayer.feature.lyricsaligner.postprocess.ConfidenceCalculator
import com.deeplayer.feature.lyricsaligner.postprocess.LowConfidenceInterpolator
import com.deeplayer.feature.lyricsaligner.postprocess.LrcGenerator
import com.deeplayer.feature.lyricsaligner.postprocess.TimestampConverter
import javax.inject.Inject

/**
 * Full implementation of [LyricsAligner] that orchestrates G2P preprocessing, CTC forced alignment,
 * and post-processing to produce word-level timestamps.
 */
class LyricsAlignerImpl
@Inject
constructor(
  private val koreanG2P: KoreanG2P,
  private val englishG2P: EnglishG2P,
  private val codeSwitchDetector: CodeSwitchDetector,
) : LyricsAligner {

  private val ctcAligner = CtcForcedAligner()
  private val timestampConverter = TimestampConverter()
  private val confidenceCalculator = ConfidenceCalculator()
  private val lowConfidenceInterpolator = LowConfidenceInterpolator()
  private val lrcGenerator = LrcGenerator()

  /**
   * Phoneme vocabulary used by the model. Maps phoneme symbols to vocab indices. Index 0 is always
   * blank.
   */
  private val phonemeVocab: Map<String, Int> by lazy { buildPhonemeVocab() }

  override fun align(
    lyrics: List<String>,
    phonemeProbabilities: FloatArray,
    frameDurationMs: Float,
    language: Language,
  ): AlignmentResult {
    if (lyrics.isEmpty()) {
      return AlignmentResult(
        words = emptyList(),
        lines = emptyList(),
        overallConfidence = 0f,
        enhancedLrc = "",
      )
    }

    val vocabSize = phonemeVocab.size
    val numFrames = phonemeProbabilities.size / vocabSize
    if (numFrames == 0) {
      return AlignmentResult(
        words = emptyList(),
        lines = emptyList(),
        overallConfidence = 0f,
        enhancedLrc = "",
      )
    }

    // Step 1: G2P - convert lyrics to phoneme sequences
    val wordInfos = mutableListOf<WordInfo>()
    for ((lineIdx, line) in lyrics.withIndex()) {
      val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
      for (word in words) {
        val phonemes = convertWordToPhonemes(word, language)
        val phonemeIndices = phonemes.mapNotNull { phonemeVocab[it] }.toIntArray()
        if (phonemeIndices.isNotEmpty()) {
          wordInfos.add(WordInfo(word, lineIdx, phonemeIndices))
        }
      }
    }

    if (wordInfos.isEmpty()) {
      return AlignmentResult(
        words = emptyList(),
        lines = emptyList(),
        overallConfidence = 0f,
        enhancedLrc = "",
      )
    }

    // Step 2: Build full phoneme sequence
    val allPhonemeIndices = wordInfos.flatMap { it.phonemeIndices.toList() }.toIntArray()

    // Step 3: CTC Forced Alignment
    val alignedPhonemes =
      ctcAligner.align(phonemeProbabilities, numFrames, vocabSize, allPhonemeIndices)

    // Step 4: Convert frames to timestamps
    val timestamped = timestampConverter.convert(alignedPhonemes, frameDurationMs)

    // Step 5: Map phoneme alignments back to words
    val wordAlignments = mapPhonemesToWords(wordInfos, timestamped)

    // Step 6: Interpolate low-confidence sections
    val interpolated = lowConfidenceInterpolator.interpolate(wordAlignments)

    // Step 7: Build line alignments
    val lineAlignments = buildLineAlignments(lyrics, interpolated)

    // Step 8: Calculate overall confidence
    val overallConfidence = confidenceCalculator.calculateOverall(timestamped)

    // Step 9: Generate LRC
    val lrc = lrcGenerator.generateFromLines(lineAlignments)

    return AlignmentResult(
      words = interpolated,
      lines = lineAlignments,
      overallConfidence = overallConfidence,
      enhancedLrc = lrc,
    )
  }

  private fun convertWordToPhonemes(word: String, language: Language): List<String> {
    return when (language) {
      Language.KO -> koreanG2P.convert(word).map { it.toString() }
      Language.EN -> englishG2P.convert(word)
      Language.MIXED -> {
        val segments = codeSwitchDetector.detect(word)
        segments.flatMap { segment ->
          when (segment.language) {
            CodeSwitchDetector.Language.KOREAN ->
              koreanG2P.convert(segment.text).map { it.toString() }
            CodeSwitchDetector.Language.ENGLISH -> englishG2P.convert(segment.text)
            else -> emptyList()
          }
        }
      }
    }
  }

  private fun mapPhonemesToWords(
    wordInfos: List<WordInfo>,
    timestamped: List<TimestampConverter.TimestampedPhoneme>,
  ): List<WordAlignment> {
    // Filter to non-blank phonemes
    val nonBlank = timestamped.filter { !it.isBlank }

    val wordAlignments = mutableListOf<WordAlignment>()
    var phonemeOffset = 0

    for (info in wordInfos) {
      val numPhonemes = info.phonemeIndices.size
      val endOffset = phonemeOffset + numPhonemes

      if (phonemeOffset < nonBlank.size) {
        val wordPhonemes = nonBlank.subList(phonemeOffset, minOf(endOffset, nonBlank.size))
        if (wordPhonemes.isNotEmpty()) {
          val startMs = wordPhonemes.first().startMs
          val endMs = wordPhonemes.last().endMs
          val avgConf = wordPhonemes.map { it.confidence }.average().toFloat()

          wordAlignments.add(
            WordAlignment(
              word = info.word,
              startMs = startMs,
              endMs = endMs,
              confidence = avgConf,
              lineIndex = info.lineIndex,
            )
          )
        }
      }
      phonemeOffset = endOffset
    }

    return wordAlignments
  }

  private fun buildLineAlignments(
    lyrics: List<String>,
    words: List<WordAlignment>,
  ): List<LineAlignment> {
    return lyrics.mapIndexed { lineIdx, lineText ->
      val lineWords = words.filter { it.lineIndex == lineIdx }
      val startMs = lineWords.minOfOrNull { it.startMs } ?: 0L
      val endMs = lineWords.maxOfOrNull { it.endMs } ?: 0L
      LineAlignment(text = lineText, startMs = startMs, endMs = endMs, wordAlignments = lineWords)
    }
  }

  private fun buildPhonemeVocab(): Map<String, Int> {
    val vocab = mutableMapOf<String, Int>()
    var idx = 0

    // Index 0: CTC blank
    vocab["<blank>"] = idx++

    // Korean jamo (consonants)
    val consonants =
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
    for (c in consonants) vocab[c.toString()] = idx++

    // Korean jamo (vowels)
    val vowels =
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
    for (v in vowels) vocab[v.toString()] = idx++

    // English ARPAbet phonemes
    val arpa =
      listOf(
        "AA",
        "AE",
        "AH",
        "AO",
        "AW",
        "AY",
        "B",
        "CH",
        "D",
        "DH",
        "EH",
        "ER",
        "EY",
        "F",
        "G",
        "HH",
        "IH",
        "IY",
        "JH",
        "K",
        "L",
        "M",
        "N",
        "NG",
        "OW",
        "OY",
        "P",
        "R",
        "S",
        "SH",
        "T",
        "TH",
        "UH",
        "UW",
        "V",
        "W",
        "Y",
        "Z",
        "ZH",
      )
    for (p in arpa) vocab[p] = idx++

    // Space separator
    vocab[" "] = idx++

    return vocab
  }

  private data class WordInfo(val word: String, val lineIndex: Int, val phonemeIndices: IntArray) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is WordInfo) return false
      return word == other.word &&
        lineIndex == other.lineIndex &&
        phonemeIndices.contentEquals(other.phonemeIndices)
    }

    override fun hashCode(): Int {
      var result = word.hashCode()
      result = 31 * result + lineIndex
      result = 31 * result + phonemeIndices.contentHashCode()
      return result
    }
  }
}
