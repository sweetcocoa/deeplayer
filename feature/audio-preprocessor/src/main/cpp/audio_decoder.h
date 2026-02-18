#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace deeplayer {

struct PcmResult {
  std::vector<float> data;
  int sample_rate;
  int channels;
};

/**
 * Decodes audio files (MP3, FLAC, OGG, WAV, AAC) to 16kHz mono PCM
 * using FFmpeg (libavformat, libavcodec, libswresample).
 */
class AudioDecoder {
 public:
  AudioDecoder();
  ~AudioDecoder();

  AudioDecoder(const AudioDecoder&) = delete;
  AudioDecoder& operator=(const AudioDecoder&) = delete;

  /**
   * Decode an audio file to 16kHz mono float PCM.
   * @param file_path Path to the audio file.
   * @return PcmResult with 16kHz mono float samples normalized to [-1.0, 1.0].
   */
  PcmResult decode(const std::string& file_path);

 private:
  static constexpr int kTargetSampleRate = 16000;
  static constexpr int kTargetChannels = 1;
};

}  // namespace deeplayer
