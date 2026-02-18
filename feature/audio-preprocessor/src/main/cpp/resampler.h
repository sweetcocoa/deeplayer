#pragma once

#include <cstdint>
#include <vector>

extern "C" {
#include <libswresample/swresample.h>
}

namespace deeplayer {

/**
 * Wrapper around FFmpeg's SwrContext for sample rate conversion.
 */
class Resampler {
 public:
  /**
   * @param src_rate Source sample rate in Hz.
   * @param dst_rate Destination sample rate in Hz.
   * @param src_channels Source channel count.
   * @param dst_channels Destination channel count.
   */
  Resampler(int src_rate, int dst_rate, int src_channels = 1,
            int dst_channels = 1);
  ~Resampler();

  Resampler(const Resampler&) = delete;
  Resampler& operator=(const Resampler&) = delete;

  /**
   * Resample float PCM data.
   * @param input Input samples (interleaved if multi-channel).
   * @return Resampled output samples.
   */
  std::vector<float> resample(const std::vector<float>& input);

  /** Flush any buffered data from the resampler. */
  std::vector<float> flush();

 private:
  SwrContext* swr_ctx_;
  int src_rate_;
  int dst_rate_;
};

}  // namespace deeplayer
