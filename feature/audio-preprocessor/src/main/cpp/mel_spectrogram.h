#pragma once

#include <cstdint>
#include <vector>

namespace deeplayer {

/**
 * Computes 80-band Log-Mel Spectrogram (Whisper compatible).
 *
 * Parameters match Whisper:
 *   - Sample rate: 16kHz
 *   - Window size: 400 samples (25ms)
 *   - Hop size: 160 samples (10ms)
 *   - FFT size: 400
 *   - Mel bands: 80
 *   - Frequency range: 0 - 8000 Hz
 */
class MelSpectrogram {
 public:
  MelSpectrogram();
  ~MelSpectrogram();

  MelSpectrogram(const MelSpectrogram&) = delete;
  MelSpectrogram& operator=(const MelSpectrogram&) = delete;

  /**
   * Compute log-mel spectrogram from 16kHz mono PCM.
   * @param pcm Input PCM samples (16kHz, mono, float [-1.0, 1.0]).
   * @return Flattened mel spectrogram [num_frames x 80].
   */
  std::vector<float> compute(const std::vector<float>& pcm);

  /** Number of mel bands. */
  static constexpr int kNumMelBands = 80;
  /** FFT window size in samples. */
  static constexpr int kWindowSize = 400;
  /** Hop size in samples. */
  static constexpr int kHopSize = 160;
  /** Sample rate. */
  static constexpr int kSampleRate = 16000;

 private:
  /** FFT size (next power of 2 >= window size). */
  static constexpr int kFftSize = 512;
  /** Number of FFT bins (kFftSize / 2 + 1). */
  static constexpr int kNumFftBins = kFftSize / 2 + 1;
  /** Minimum frequency for mel filterbank. */
  static constexpr float kMinFreq = 0.0f;
  /** Maximum frequency for mel filterbank. */
  static constexpr float kMaxFreq = 8000.0f;

  std::vector<float> hann_window_;
  std::vector<std::vector<float>> mel_filterbank_;

  void init_hann_window();
  void init_mel_filterbank();

  static float hz_to_mel(float hz);
  static float mel_to_hz(float mel);

  /** In-place real FFT using radix-2 Cooley-Tukey. */
  void fft(std::vector<float>& real, std::vector<float>& imag);
};

}  // namespace deeplayer
