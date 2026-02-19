package com.deeplayer.core.player

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/** Reads embedded lyrics (ID3v2 USLT frame) from audio files. */
object EmbeddedLyricsReader {

  /**
   * Extracts lyrics text from the ID3v2 USLT frame of an audio file. Returns null if no lyrics are
   * found or the file is not a valid ID3v2 file.
   */
  fun readLyrics(filePath: String): String? {
    val file = File(filePath)
    if (!file.exists() || file.length() < 10) return null
    return try {
      RandomAccessFile(file, "r").use { raf -> readId3v2Lyrics(raf) }
    } catch (_: Exception) {
      null
    }
  }

  private fun readId3v2Lyrics(raf: RandomAccessFile): String? {
    val header = ByteArray(10)
    raf.readFully(header)

    // Verify ID3v2 header: "ID3"
    if (
      header[0] != 'I'.code.toByte() ||
        header[1] != 'D'.code.toByte() ||
        header[2] != '3'.code.toByte()
    ) {
      return null
    }

    val majorVersion = header[3].toInt() and 0xFF
    if (majorVersion !in 2..4) return null

    // Syncsafe integer for tag size
    val tagSize = decodeSyncsafe(header, 6)
    val tagEnd = 10L + tagSize

    var pos = 10L
    // Skip extended header if present (bit 6 of flags)
    if (header[5].toInt() and 0x40 != 0 && majorVersion >= 3) {
      raf.seek(pos)
      val extSize = raf.readInt()
      pos += 4 + extSize
    }

    val frameHeaderSize = if (majorVersion == 2) 6 else 10

    while (pos + frameHeaderSize < tagEnd) {
      raf.seek(pos)
      val frameHeader = ByteArray(frameHeaderSize)
      if (raf.read(frameHeader) < frameHeaderSize) break

      val frameId: String
      val frameSize: Int

      if (majorVersion == 2) {
        frameId = String(frameHeader, 0, 3, Charsets.US_ASCII)
        frameSize =
          ((frameHeader[3].toInt() and 0xFF) shl 16) or
            ((frameHeader[4].toInt() and 0xFF) shl 8) or
            (frameHeader[5].toInt() and 0xFF)
      } else {
        frameId = String(frameHeader, 0, 4, Charsets.US_ASCII)
        frameSize =
          if (majorVersion == 4) {
            decodeSyncsafe(frameHeader, 4)
          } else {
            ((frameHeader[4].toInt() and 0xFF) shl 24) or
              ((frameHeader[5].toInt() and 0xFF) shl 16) or
              ((frameHeader[6].toInt() and 0xFF) shl 8) or
              (frameHeader[7].toInt() and 0xFF)
          }
      }

      if (frameSize <= 0 || frameId[0] == '\u0000') break

      val isUslt =
        (majorVersion == 2 && frameId == "ULT") || (majorVersion >= 3 && frameId == "USLT")

      if (isUslt) {
        val data = ByteArray(frameSize)
        raf.seek(pos + frameHeaderSize)
        raf.readFully(data)
        return parseUsltPayload(data)
      }

      pos += frameHeaderSize + frameSize
    }
    return null
  }

  private fun parseUsltPayload(data: ByteArray): String? {
    if (data.isEmpty()) return null
    val encoding = data[0].toInt() and 0xFF
    // Skip language (3 bytes)
    val charset = charsetForEncoding(encoding)
    val terminator = if (encoding == 1 || encoding == 2) byteArrayOf(0, 0) else byteArrayOf(0)

    // Find end of content descriptor (null-terminated)
    var descEnd = 4 // after encoding(1) + language(3)
    descEnd = findTerminator(data, descEnd, terminator)
    if (descEnd < 0) return null

    val lyricsStart = descEnd + terminator.size
    if (lyricsStart >= data.size) return null

    return String(data, lyricsStart, data.size - lyricsStart, charset).trim()
  }

  private fun findTerminator(data: ByteArray, start: Int, terminator: ByteArray): Int {
    val step = terminator.size
    var i = start
    while (i <= data.size - step) {
      if (step == 1 && data[i] == 0.toByte()) return i
      if (step == 2 && data[i] == 0.toByte() && data[i + 1] == 0.toByte()) return i
      i += if (step == 2) 2 else 1
    }
    return -1
  }

  private fun charsetForEncoding(encoding: Int): Charset =
    when (encoding) {
      0 -> Charsets.ISO_8859_1
      1 -> Charsets.UTF_16
      2 -> Charsets.UTF_16BE
      3 -> Charsets.UTF_8
      else -> Charsets.ISO_8859_1
    }

  private fun decodeSyncsafe(data: ByteArray, offset: Int): Int =
    ((data[offset].toInt() and 0x7F) shl 21) or
      ((data[offset + 1].toInt() and 0x7F) shl 14) or
      ((data[offset + 2].toInt() and 0x7F) shl 7) or
      (data[offset + 3].toInt() and 0x7F)
}
