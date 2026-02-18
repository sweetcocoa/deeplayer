package com.deeplayer.feature.lyricsaligner.alignment

/**
 * CTC Forced Alignment using Viterbi-style dynamic programming.
 *
 * Given a frame-level phoneme probability matrix and an expected phoneme sequence, this algorithm
 * finds the optimal alignment path that maps each phoneme to specific frames.
 *
 * The CTC topology allows:
 * - Blank tokens between any phoneme transitions
 * - Self-loops (staying on the same phoneme for multiple frames)
 * - Advancing to the next phoneme (or its blank)
 *
 * The extended label sequence is: blank, p0, blank, p1, blank, ..., pN, blank where each phoneme
 * can optionally be preceded/followed by blank frames.
 */
class CtcForcedAligner {

  /** Index of the CTC blank token in the vocabulary. */
  var blankIndex: Int = 0

  data class AlignedPhoneme(
    val phonemeIndex: Int,
    val phonemeLabel: Int,
    val startFrame: Int,
    val endFrame: Int,
    val confidence: Float,
    val isBlank: Boolean,
  )

  /**
   * Perform CTC forced alignment.
   *
   * @param logProbs log-probability matrix [numFrames x vocabSize] (row-major)
   * @param numFrames number of frames (time steps)
   * @param vocabSize size of the phoneme vocabulary (including blank)
   * @param phonemeSequence expected phoneme indices (from G2P, indices into vocab)
   * @return list of aligned phonemes with frame ranges and confidence
   */
  fun align(
    logProbs: FloatArray,
    numFrames: Int,
    vocabSize: Int,
    phonemeSequence: IntArray,
  ): List<AlignedPhoneme> {
    if (phonemeSequence.isEmpty() || numFrames == 0) return emptyList()

    // Build extended label sequence with blanks interleaved
    // extended = [blank, phoneme0, blank, phoneme1, blank, ..., phonemeN, blank]
    val extLen = 2 * phonemeSequence.size + 1
    val extLabels = IntArray(extLen)
    for (i in 0 until extLen) {
      extLabels[i] = if (i % 2 == 0) blankIndex else phonemeSequence[i / 2]
    }

    // Viterbi DP in log space
    // dp[t][s] = log probability of best path ending at frame t, extended label s
    val negInf = Float.NEGATIVE_INFINITY

    // Use two rolling arrays to save memory
    var prev = FloatArray(extLen) { negInf }
    var curr = FloatArray(extLen) { negInf }

    // Backpointer for traceback: backptr[t][s] = previous extended label index at t-1
    val backptr = Array(numFrames) { IntArray(extLen) { -1 } }

    // Initialize: at frame 0, can start with blank (ext[0]) or first phoneme (ext[1])
    prev[0] = logProb(logProbs, 0, extLabels[0], vocabSize)
    if (extLen > 1) {
      prev[1] = logProb(logProbs, 0, extLabels[1], vocabSize)
    }

    // Fill DP
    for (t in 1 until numFrames) {
      curr.fill(negInf)

      for (s in 0 until extLen) {
        val logProbTs = logProb(logProbs, t, extLabels[s], vocabSize)

        // Option 1: Stay on same label (self-loop)
        var bestPrev = prev[s]
        var bestPrevIdx = s

        // Option 2: Come from previous extended label
        if (s > 0 && prev[s - 1] > bestPrev) {
          bestPrev = prev[s - 1]
          bestPrevIdx = s - 1
        }

        // Option 3: Skip blank (only if current is not blank and prev-prev is different label)
        if (s > 1 && extLabels[s] != blankIndex && extLabels[s] != extLabels[s - 2]) {
          if (prev[s - 2] > bestPrev) {
            bestPrev = prev[s - 2]
            bestPrevIdx = s - 2
          }
        }

        if (bestPrev.isFinite()) {
          curr[s] = bestPrev + logProbTs
          backptr[t][s] = bestPrevIdx
        }
      }

      // Swap arrays
      val tmp = prev
      prev = curr
      curr = tmp
    }

    // Find best final state: must end at last blank (extLen-1) or last phoneme (extLen-2)
    var bestFinalState = extLen - 1
    if (extLen >= 2 && prev[extLen - 2] > prev[extLen - 1]) {
      bestFinalState = extLen - 2
    }

    // Traceback
    val path = IntArray(numFrames)
    path[numFrames - 1] = bestFinalState
    for (t in numFrames - 2 downTo 0) {
      path[t] = backptr[t + 1][path[t + 1]]
    }

    // Convert path to aligned phonemes
    return extractAlignments(path, extLabels, logProbs, vocabSize, phonemeSequence)
  }

  /**
   * Check if a region of frames is blank-heavy, indicating instrumental/non-vocal section.
   *
   * @param logProbs log-probability matrix
   * @param startFrame start of the region
   * @param endFrame end of the region (exclusive)
   * @param vocabSize vocabulary size
   * @param threshold minimum fraction of frames that must have blank as top prediction
   */
  fun isBlankHeavy(
    logProbs: FloatArray,
    startFrame: Int,
    endFrame: Int,
    vocabSize: Int,
    threshold: Float = 0.8f,
  ): Boolean {
    if (startFrame >= endFrame) return true
    var blankCount = 0
    for (t in startFrame until endFrame) {
      var maxIdx = 0
      var maxVal = logProb(logProbs, t, 0, vocabSize)
      for (v in 1 until vocabSize) {
        val p = logProb(logProbs, t, v, vocabSize)
        if (p > maxVal) {
          maxVal = p
          maxIdx = v
        }
      }
      if (maxIdx == blankIndex) blankCount++
    }
    return blankCount.toFloat() / (endFrame - startFrame) >= threshold
  }

  private fun extractAlignments(
    path: IntArray,
    extLabels: IntArray,
    logProbs: FloatArray,
    vocabSize: Int,
    @Suppress("UnusedParameter") phonemeSequence: IntArray,
  ): List<AlignedPhoneme> {
    val result = mutableListOf<AlignedPhoneme>()
    var currentExtLabel = path[0]
    var startFrame = 0
    var sumLogProb = logProb(logProbs, 0, extLabels[path[0]], vocabSize)
    var frameCount = 1

    for (t in 1 until path.size) {
      if (path[t] != currentExtLabel) {
        // Emit segment
        val label = extLabels[currentExtLabel]
        val isBlank = currentExtLabel % 2 == 0
        val phonemeIdx = if (isBlank) -1 else currentExtLabel / 2
        val avgConf = computeConfidence(sumLogProb, frameCount, vocabSize)

        result.add(
          AlignedPhoneme(
            phonemeIndex = phonemeIdx,
            phonemeLabel = label,
            startFrame = startFrame,
            endFrame = t - 1,
            confidence = avgConf,
            isBlank = isBlank,
          )
        )

        currentExtLabel = path[t]
        startFrame = t
        sumLogProb = 0f
        frameCount = 0
      }
      sumLogProb += logProb(logProbs, t, extLabels[path[t]], vocabSize)
      frameCount++
    }

    // Emit last segment
    val label = extLabels[currentExtLabel]
    val isBlank = currentExtLabel % 2 == 0
    val phonemeIdx = if (isBlank) -1 else currentExtLabel / 2
    val avgConf = computeConfidence(sumLogProb, frameCount, vocabSize)
    result.add(
      AlignedPhoneme(
        phonemeIndex = phonemeIdx,
        phonemeLabel = label,
        startFrame = startFrame,
        endFrame = path.size - 1,
        confidence = avgConf,
        isBlank = isBlank,
      )
    )

    return result
  }

  /**
   * Compute confidence from accumulated log-probabilities.
   *
   * Raw exp(avgLogProb) is near 0 for large vocabularies since uniform probability = 1/vocabSize.
   * We normalize relative to the uniform baseline so that:
   * - confidence ~1.0 = model is very certain about this phoneme
   * - confidence ~0.0 = model assigns probability no better than uniform random
   */
  private fun computeConfidence(sumLogProb: Float, frameCount: Int, vocabSize: Int): Float {
    if (frameCount == 0) return 0f
    val avgLogProb = sumLogProb / frameCount
    // Uniform log-prob baseline: ln(1/vocabSize)
    val uniformLogProb = -kotlin.math.ln(vocabSize.toFloat())
    // Normalize: 0 at uniform, 1 at perfect (logProb=0)
    val normalized = 1f - (avgLogProb / uniformLogProb)
    return normalized.coerceIn(0f, 1f)
  }

  private fun logProb(logProbs: FloatArray, frame: Int, vocab: Int, vocabSize: Int): Float {
    return logProbs[frame * vocabSize + vocab]
  }
}
