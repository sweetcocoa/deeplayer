package com.deeplayer.core.contracts

interface AudioPreprocessor {
  /** Decode an audio file to 16kHz mono PCM. */
  fun decodeToPcm(filePath: String): FloatArray

  /** Split PCM into chunks with time offsets. Default implementation assumes 16kHz sample rate. */
  fun segmentPcm(
    pcm: FloatArray,
    chunkDurationMs: Int = 30000,
    sampleRate: Int = 16000,
  ): List<PcmChunk> {
    val samplesPerChunk = (sampleRate * chunkDurationMs) / 1000
    val chunks = mutableListOf<PcmChunk>()
    var offset = 0
    while (offset < pcm.size) {
      val end = minOf(offset + samplesPerChunk, pcm.size)
      val chunkData = pcm.copyOfRange(offset, end)
      val offsetMs = (offset.toLong() * 1000L) / sampleRate
      val durationMs = (chunkData.size.toLong() * 1000L) / sampleRate
      chunks.add(PcmChunk(data = chunkData, offsetMs = offsetMs, durationMs = durationMs))
      offset = end
    }
    return chunks
  }

  /** Convert PCM to 80-band Log-Mel Spectrogram (Whisper compatible). */
  fun extractMelSpectrogram(pcm: FloatArray): FloatArray
}
