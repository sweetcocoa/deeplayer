package com.deeplayer.feature.alignmentorchestrator

import com.deeplayer.core.contracts.Language
import com.deeplayer.core.contracts.TranscribedSegment
import com.deeplayer.feature.inferenceengine.WhisperCppTranscriber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Integration test that runs the full Whisper transcription + lyrics matching pipeline on the host
 * machine. Requires native library and model to be pre-built/downloaded.
 *
 * Run via: ./scripts/test_alignment.sh <audio_file> <lyrics_file> [language]
 *
 * System properties (forwarded by build.gradle.kts):
 * - whisper.native.lib: path to libwhisper_jni.dylib
 * - whisper.model.path: path to ggml-tiny.bin
 * - whisper.pcm.path: path to 16kHz mono f32le PCM file
 * - whisper.lyrics.path: path to lyrics text file (one line per line)
 * - whisper.language: ko, en, or mixed (default: ko)
 */
class PipelineIntegrationTest {

  @Test
  fun `run full pipeline`() {
    val nativeLib = System.getProperty("whisper.native.lib")
    val modelPath = System.getProperty("whisper.model.path")
    val pcmPath = System.getProperty("whisper.pcm.path")
    val lyricsPath = System.getProperty("whisper.lyrics.path")
    val language = System.getProperty("whisper.language") ?: "ko"

    // Skip gracefully if not configured (normal ./gradlew test)
    assumeTrue(
      "Skipping: whisper.native.lib not set (run via scripts/test_alignment.sh)",
      !nativeLib.isNullOrBlank(),
    )
    assumeTrue("Skipping: whisper.model.path not set", !modelPath.isNullOrBlank())
    assumeTrue("Skipping: whisper.pcm.path not set", !pcmPath.isNullOrBlank())
    assumeTrue("Skipping: whisper.lyrics.path not set", !lyricsPath.isNullOrBlank())

    val libFile = File(nativeLib!!)
    val modelFile = File(modelPath!!)
    val pcmFile = File(pcmPath!!)
    val lyricsFile = File(lyricsPath!!)

    assumeTrue("Native library not found: $nativeLib", libFile.exists())
    assumeTrue("Model file not found: $modelPath", modelFile.exists())
    assumeTrue("PCM file not found: $pcmPath", pcmFile.exists())
    assumeTrue("Lyrics file not found: $lyricsPath", lyricsFile.exists())

    // Read PCM data (f32le format)
    val pcmData = readPcmFile(pcmFile)
    println("=== PCM loaded: ${pcmData.size} samples (${pcmData.size / 16000}s) ===")

    // Read lyrics
    val lyrics = lyricsFile.readLines().filter { it.isNotBlank() }
    println("=== Lyrics: ${lyrics.size} lines ===")
    lyrics.forEachIndexed { i, line -> println("  [$i] $line") }
    println()

    // Parse language
    val lang =
      when (language.lowercase()) {
        "en" -> Language.EN
        "mixed" -> Language.MIXED
        else -> Language.KO
      }

    // Transcribe using WhisperCppTranscriber
    // WhisperNative companion init calls System.loadLibrary("whisper_jni") which finds the lib
    // via java.library.path set in build.gradle.kts
    val transcriber = WhisperCppTranscriber()
    check(transcriber.loadModel(modelFile.absolutePath)) {
      "Failed to load whisper model: $modelPath"
    }
    println("=== Model loaded ===")

    try {
      // Chunk into 30-second segments for Whisper
      val samplesPerChunk = 30 * 16000 // 30 seconds at 16kHz
      val allSegments = mutableListOf<TranscribedSegment>()

      val totalChunks = (pcmData.size + samplesPerChunk - 1) / samplesPerChunk
      for (chunkIdx in 0 until totalChunks) {
        val start = chunkIdx * samplesPerChunk
        val end = minOf(start + samplesPerChunk, pcmData.size)
        val chunk = pcmData.copyOfRange(start, end)
        val offsetMs = (start.toLong() * 1000) / 16000

        println("  Transcribing chunk ${chunkIdx + 1}/$totalChunks (offset=${offsetMs}ms)...")

        val segments = transcriber.transcribe(chunk, lang)
        for (seg in segments) {
          allSegments.add(
            TranscribedSegment(
              text = seg.text,
              startMs = seg.startMs + offsetMs,
              endMs = seg.endMs + offsetMs,
            )
          )
        }
      }

      println("=== Transcription: ${allSegments.size} segments ===")
      for (seg in allSegments) {
        println("  [${formatTime(seg.startMs)} -> ${formatTime(seg.endMs)}] ${seg.text}")
      }
      println()

      // Match transcription to lyrics
      val result = TranscriptionLyricsMatcher.match(allSegments, lyrics, lang)

      // Print results
      val output = buildString {
        appendLine("=== Transcription (${allSegments.size} segments) ===")
        for (seg in allSegments) {
          appendLine(
            "  [${formatTime(seg.startMs)} -> ${formatTime(seg.endMs)}] ${seg.text}"
          )
        }
        appendLine()
        appendLine("=== Alignment Result ===")
        appendLine("Overall confidence: ${"%.2f".format(result.overallConfidence)}")
        appendLine("Lines: ${result.lines.size}")
        appendLine()

        for ((i, line) in result.lines.withIndex()) {
          appendLine(
            "  Line $i: [${formatTime(line.startMs)} -> ${formatTime(line.endMs)}] ${line.text}"
          )
          for (word in line.wordAlignments) {
            appendLine(
              "    Word: [${formatTime(word.startMs)} -> ${formatTime(word.endMs)}] ${word.word}"
            )
          }
        }

        appendLine()
        appendLine("=== Enhanced LRC ===")
        appendLine(result.enhancedLrc)
      }

      println(output)

      // Write to result file
      val resultFile = File(libFile.parentFile, "alignment_result.txt")
      resultFile.writeText(output)
      println("Result written to: ${resultFile.absolutePath}")
    } finally {
      transcriber.close()
      println("=== Model freed ===")
    }
  }

  /** Read raw f32le PCM file into FloatArray. */
  private fun readPcmFile(file: File): FloatArray {
    val bytes = file.readBytes()
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val floats = FloatArray(bytes.size / 4)
    buffer.asFloatBuffer().get(floats)
    return floats
  }

  private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val millis = ms % 1000
    return "%02d:%02d.%03d".format(min, sec, millis)
  }
}
