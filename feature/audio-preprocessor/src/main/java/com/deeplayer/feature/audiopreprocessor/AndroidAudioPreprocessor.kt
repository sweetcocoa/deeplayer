package com.deeplayer.feature.audiopreprocessor

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.deeplayer.core.contracts.AudioPreprocessor
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * [AudioPreprocessor] using Android's [MediaExtractor] + [MediaCodec] APIs. No native libraries
 * required — works with any audio format supported by the platform (MP3, AAC, FLAC, OGG, WAV,
 * etc.).
 *
 * Output: 16 kHz mono Float32 PCM (what Whisper expects).
 */
class AndroidAudioPreprocessor : AudioPreprocessor {

  override fun decodeToPcm(filePath: String): FloatArray {
    val extractor = MediaExtractor()
    try {
      extractor.setDataSource(filePath)
      val audioTrackIndex = selectAudioTrack(extractor)
      check(audioTrackIndex >= 0) { "No audio track found in: $filePath" }
      extractor.selectTrack(audioTrackIndex)

      val format = extractor.getTrackFormat(audioTrackIndex)
      val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      val mime = format.getString(MediaFormat.KEY_MIME)!!

      val codec = MediaCodec.createDecoderByType(mime)
      codec.configure(format, null, null, 0)
      codec.start()

      val pcmSamples = decodeAllSamples(extractor, codec, sampleRate, channelCount)

      codec.stop()
      codec.release()

      return pcmSamples
    } finally {
      extractor.release()
    }
  }

  // ---------------------------------------------------------------------------
  // Internal helpers
  // ---------------------------------------------------------------------------

  private fun selectAudioTrack(extractor: MediaExtractor): Int {
    for (i in 0 until extractor.trackCount) {
      val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
      if (mime.startsWith("audio/")) return i
    }
    return -1
  }

  /**
   * Drive the MediaCodec decode loop, resample to 16 kHz mono on the fly, and return the final
   * float PCM array.
   */
  private fun decodeAllSamples(
    extractor: MediaExtractor,
    codec: MediaCodec,
    sourceSampleRate: Int,
    sourceChannelCount: Int,
  ): FloatArray {
    val info = MediaCodec.BufferInfo()
    var inputEos = false
    // Estimate capacity: duration × 16000 samples/s
    val outputSamples = ArrayList<Float>(TARGET_SAMPLE_RATE * 300) // ~5 min initial capacity

    // Simple linear resampler state
    var resampleAccumulator = 0.0

    while (true) {
      // --- Feed input ---
      if (!inputEos) {
        val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
        if (inIdx >= 0) {
          val buf = codec.getInputBuffer(inIdx)!!
          val read = extractor.readSampleData(buf, 0)
          if (read < 0) {
            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputEos = true
          } else {
            codec.queueInputBuffer(inIdx, 0, read, extractor.sampleTime, 0)
            extractor.advance()
          }
        }
      }

      // --- Drain output ---
      val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
      if (outIdx >= 0) {
        if (info.size > 0) {
          val outBuf = codec.getOutputBuffer(outIdx)!!
          outBuf.position(info.offset)
          outBuf.limit(info.offset + info.size)
          // Check if codec output is float PCM
          val outputFormat = codec.outputFormat
          val pcmEncoding =
            if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
              outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
              AudioFormat.ENCODING_PCM_16BIT
            }
          val actualSampleRate =
            if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
              outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
              sourceSampleRate
            }
          val actualChannels =
            if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
              outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
              sourceChannelCount
            }

          convertAndResample(
            outBuf,
            pcmEncoding,
            actualSampleRate,
            actualChannels,
            outputSamples,
          ) { resampleAccumulator }
            .also { resampleAccumulator = it }
        }
        codec.releaseOutputBuffer(outIdx, false)
        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
      } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
        if (inputEos) break // nothing more to do
      }
      // INFO_OUTPUT_FORMAT_CHANGED / INFO_OUTPUT_BUFFERS_CHANGED → loop continues
    }

    return outputSamples.toFloatArray()
  }

  /**
   * Convert decoded PCM buffer (16-bit or float, multi-channel) to 16 kHz mono float samples.
   * Returns updated resample accumulator.
   */
  private fun convertAndResample(
    buf: ByteBuffer,
    pcmEncoding: Int,
    sampleRate: Int,
    channelCount: Int,
    output: ArrayList<Float>,
    accumulatorProvider: () -> Double,
  ): Double {
    var accumulator = accumulatorProvider()
    val ratio = sampleRate.toDouble() / TARGET_SAMPLE_RATE
    val ordered = buf.order(ByteOrder.LITTLE_ENDIAN)

    when (pcmEncoding) {
      AudioFormat.ENCODING_PCM_16BIT -> {
        val shortBuf = ordered.asShortBuffer()
        val totalFrames = shortBuf.remaining() / channelCount
        while (accumulator < totalFrames) {
          val frameIdx = accumulator.toInt()
          // Mono mix
          var sample = 0f
          for (ch in 0 until channelCount) {
            sample += shortBuf.get(frameIdx * channelCount + ch).toFloat() / 32768f
          }
          sample /= channelCount
          output.add(sample)
          accumulator += ratio
        }
        accumulator -= totalFrames
      }
      AudioFormat.ENCODING_PCM_FLOAT -> {
        val floatBuf = ordered.asFloatBuffer()
        val totalFrames = floatBuf.remaining() / channelCount
        while (accumulator < totalFrames) {
          val frameIdx = accumulator.toInt()
          var sample = 0f
          for (ch in 0 until channelCount) {
            sample += floatBuf.get(frameIdx * channelCount + ch)
          }
          sample /= channelCount
          output.add(sample)
          accumulator += ratio
        }
        accumulator -= totalFrames
      }
      else -> {
        // Fallback: treat as 16-bit
        val shortBuf = ordered.asShortBuffer()
        val totalFrames = shortBuf.remaining() / channelCount
        while (accumulator < totalFrames) {
          val frameIdx = accumulator.toInt()
          var sample = 0f
          for (ch in 0 until channelCount) {
            sample += shortBuf.get(frameIdx * channelCount + ch).toFloat() / 32768f
          }
          sample /= channelCount
          output.add(sample)
          accumulator += ratio
        }
        accumulator -= totalFrames
      }
    }
    return accumulator
  }

  companion object {
    private const val TARGET_SAMPLE_RATE = 16000
    private const val TIMEOUT_US = 10_000L
  }
}
