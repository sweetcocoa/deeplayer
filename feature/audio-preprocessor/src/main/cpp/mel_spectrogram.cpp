#include "mel_spectrogram.h"

#include <algorithm>
#include <cmath>
#include <stdexcept>

namespace deeplayer {

MelSpectrogram::MelSpectrogram() {
  init_hann_window();
  init_mel_filterbank();
}

MelSpectrogram::~MelSpectrogram() = default;

void MelSpectrogram::init_hann_window() {
  hann_window_.resize(kWindowSize);
  for (int i = 0; i < kWindowSize; i++) {
    hann_window_[i] =
        0.5f * (1.0f - std::cos(2.0f * static_cast<float>(M_PI) * i /
                                 kWindowSize));
  }
}

float MelSpectrogram::hz_to_mel(float hz) {
  return 2595.0f * std::log10(1.0f + hz / 700.0f);
}

float MelSpectrogram::mel_to_hz(float mel) {
  return 700.0f * (std::pow(10.0f, mel / 2595.0f) - 1.0f);
}

void MelSpectrogram::init_mel_filterbank() {
  float mel_min = hz_to_mel(kMinFreq);
  float mel_max = hz_to_mel(kMaxFreq);

  // kNumMelBands + 2 points for the triangular filters
  std::vector<float> mel_points(kNumMelBands + 2);
  for (int i = 0; i < kNumMelBands + 2; i++) {
    mel_points[i] =
        mel_min + (mel_max - mel_min) * i / (kNumMelBands + 1);
  }

  std::vector<float> hz_points(kNumMelBands + 2);
  for (int i = 0; i < kNumMelBands + 2; i++) {
    hz_points[i] = mel_to_hz(mel_points[i]);
  }

  // Convert Hz to FFT bin indices
  std::vector<int> bin_points(kNumMelBands + 2);
  for (int i = 0; i < kNumMelBands + 2; i++) {
    bin_points[i] = static_cast<int>(
        std::floor((kFftSize + 1) * hz_points[i] / kSampleRate));
  }

  mel_filterbank_.resize(kNumMelBands);
  for (int m = 0; m < kNumMelBands; m++) {
    mel_filterbank_[m].resize(kNumFftBins, 0.0f);
    int left = bin_points[m];
    int center = bin_points[m + 1];
    int right = bin_points[m + 2];

    for (int k = left; k < center && k < kNumFftBins; k++) {
      if (center != left) {
        mel_filterbank_[m][k] =
            static_cast<float>(k - left) / (center - left);
      }
    }
    for (int k = center; k < right && k < kNumFftBins; k++) {
      if (right != center) {
        mel_filterbank_[m][k] =
            static_cast<float>(right - k) / (right - center);
      }
    }
  }
}

void MelSpectrogram::fft(std::vector<float>& real, std::vector<float>& imag) {
  int n = static_cast<int>(real.size());

  // Bit-reversal permutation
  for (int i = 1, j = 0; i < n; i++) {
    int bit = n >> 1;
    for (; j & bit; bit >>= 1) {
      j ^= bit;
    }
    j ^= bit;
    if (i < j) {
      std::swap(real[i], real[j]);
      std::swap(imag[i], imag[j]);
    }
  }

  // Cooley-Tukey iterative FFT
  for (int len = 2; len <= n; len <<= 1) {
    float ang = 2.0f * static_cast<float>(M_PI) / len;
    float w_real = std::cos(ang);
    float w_imag = -std::sin(ang);
    for (int i = 0; i < n; i += len) {
      float cur_real = 1.0f;
      float cur_imag = 0.0f;
      for (int j = 0; j < len / 2; j++) {
        float t_real =
            cur_real * real[i + j + len / 2] - cur_imag * imag[i + j + len / 2];
        float t_imag =
            cur_real * imag[i + j + len / 2] + cur_imag * real[i + j + len / 2];
        real[i + j + len / 2] = real[i + j] - t_real;
        imag[i + j + len / 2] = imag[i + j] - t_imag;
        real[i + j] += t_real;
        imag[i + j] += t_imag;
        float new_real = cur_real * w_real - cur_imag * w_imag;
        float new_imag = cur_real * w_imag + cur_imag * w_real;
        cur_real = new_real;
        cur_imag = new_imag;
      }
    }
  }
}

std::vector<float> MelSpectrogram::compute(const std::vector<float>& pcm) {
  if (pcm.size() < static_cast<size_t>(kWindowSize)) {
    return {};
  }

  int num_frames =
      (static_cast<int>(pcm.size()) - kWindowSize) / kHopSize + 1;
  std::vector<float> mel_output(num_frames * kNumMelBands);

  std::vector<float> fft_real(kFftSize);
  std::vector<float> fft_imag(kFftSize);
  std::vector<float> power_spectrum(kNumFftBins);

  for (int frame = 0; frame < num_frames; frame++) {
    int start = frame * kHopSize;

    // Apply Hann window and zero-pad to FFT size
    std::fill(fft_real.begin(), fft_real.end(), 0.0f);
    std::fill(fft_imag.begin(), fft_imag.end(), 0.0f);
    for (int i = 0; i < kWindowSize; i++) {
      fft_real[i] = pcm[start + i] * hann_window_[i];
    }

    // FFT
    fft(fft_real, fft_imag);

    // Power spectrum
    for (int k = 0; k < kNumFftBins; k++) {
      power_spectrum[k] =
          fft_real[k] * fft_real[k] + fft_imag[k] * fft_imag[k];
    }

    // Apply mel filterbank and log
    for (int m = 0; m < kNumMelBands; m++) {
      float energy = 0.0f;
      for (int k = 0; k < kNumFftBins; k++) {
        energy += mel_filterbank_[m][k] * power_spectrum[k];
      }
      // Log-mel with floor to avoid log(0)
      mel_output[frame * kNumMelBands + m] =
          std::log(std::max(energy, 1e-10f));
    }
  }

  return mel_output;
}

}  // namespace deeplayer
