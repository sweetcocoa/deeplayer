#include "resampler.h"

#include <stdexcept>

extern "C" {
#include <libavutil/channel_layout.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>
}

namespace deeplayer {

Resampler::Resampler(int src_rate, int dst_rate, int src_channels,
                     int dst_channels)
    : swr_ctx_(nullptr), src_rate_(src_rate), dst_rate_(dst_rate) {
  AVChannelLayout in_ch_layout, out_ch_layout;
  av_channel_layout_default(&in_ch_layout, src_channels);
  av_channel_layout_default(&out_ch_layout, dst_channels);

  int ret = swr_alloc_set_opts2(&swr_ctx_, &out_ch_layout, AV_SAMPLE_FMT_FLT,
                                dst_rate, &in_ch_layout, AV_SAMPLE_FMT_FLT,
                                src_rate, 0, nullptr);

  av_channel_layout_uninit(&in_ch_layout);
  av_channel_layout_uninit(&out_ch_layout);

  if (ret < 0 || !swr_ctx_) {
    throw std::runtime_error("Failed to allocate resampler");
  }

  if (swr_init(swr_ctx_) < 0) {
    swr_free(&swr_ctx_);
    throw std::runtime_error("Failed to initialize resampler");
  }
}

Resampler::~Resampler() {
  if (swr_ctx_) {
    swr_free(&swr_ctx_);
  }
}

std::vector<float> Resampler::resample(const std::vector<float>& input) {
  if (input.empty()) return {};

  int in_samples = static_cast<int>(input.size());
  int max_out_samples = swr_get_out_samples(swr_ctx_, in_samples);
  if (max_out_samples <= 0) return {};

  std::vector<float> output(max_out_samples);
  const uint8_t* in_buf = reinterpret_cast<const uint8_t*>(input.data());
  uint8_t* out_buf = reinterpret_cast<uint8_t*>(output.data());

  int out_samples = swr_convert(swr_ctx_, &out_buf, max_out_samples, &in_buf,
                                in_samples);
  if (out_samples <= 0) return {};

  output.resize(out_samples);
  return output;
}

std::vector<float> Resampler::flush() {
  int flush_samples = swr_get_out_samples(swr_ctx_, 0);
  if (flush_samples <= 0) return {};

  std::vector<float> output(flush_samples);
  uint8_t* out_buf = reinterpret_cast<uint8_t*>(output.data());

  int out_samples =
      swr_convert(swr_ctx_, &out_buf, flush_samples, nullptr, 0);
  if (out_samples <= 0) return {};

  output.resize(out_samples);
  return output;
}

}  // namespace deeplayer
