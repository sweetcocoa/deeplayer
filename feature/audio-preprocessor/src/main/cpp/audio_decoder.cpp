#include "audio_decoder.h"

#include <android/log.h>

#include <cmath>
#include <memory>
#include <stdexcept>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libswresample/swresample.h>
}

#define LOG_TAG "AudioDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace deeplayer {

// RAII wrappers for FFmpeg resources to guarantee cleanup on exceptions
struct FormatContextDeleter {
  void operator()(AVFormatContext* ctx) const {
    if (ctx) avformat_close_input(&ctx);
  }
};

struct CodecContextDeleter {
  void operator()(AVCodecContext* ctx) const {
    if (ctx) avcodec_free_context(&ctx);
  }
};

struct SwrContextDeleter {
  void operator()(SwrContext* ctx) const {
    if (ctx) swr_free(&ctx);
  }
};

struct PacketDeleter {
  void operator()(AVPacket* pkt) const {
    if (pkt) av_packet_free(&pkt);
  }
};

struct FrameDeleter {
  void operator()(AVFrame* frm) const {
    if (frm) av_frame_free(&frm);
  }
};

AudioDecoder::AudioDecoder() = default;
AudioDecoder::~AudioDecoder() = default;

PcmResult AudioDecoder::decode(const std::string& file_path) {
  AVFormatContext* raw_format_ctx = nullptr;
  if (avformat_open_input(&raw_format_ctx, file_path.c_str(), nullptr, nullptr) <
      0) {
    throw std::runtime_error("Failed to open audio file: " + file_path);
  }
  std::unique_ptr<AVFormatContext, FormatContextDeleter> format_ctx(raw_format_ctx);

  if (avformat_find_stream_info(format_ctx.get(), nullptr) < 0) {
    throw std::runtime_error("Failed to find stream info: " + file_path);
  }

  int audio_stream_index = -1;
  const AVCodec* codec = nullptr;
  for (unsigned int i = 0; i < format_ctx->nb_streams; i++) {
    if (format_ctx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
      audio_stream_index = static_cast<int>(i);
      codec = avcodec_find_decoder(format_ctx->streams[i]->codecpar->codec_id);
      break;
    }
  }

  if (audio_stream_index < 0 || !codec) {
    throw std::runtime_error("No audio stream found in: " + file_path);
  }

  std::unique_ptr<AVCodecContext, CodecContextDeleter> codec_ctx(
      avcodec_alloc_context3(codec));
  if (!codec_ctx) {
    throw std::runtime_error("Failed to allocate codec context");
  }

  if (avcodec_parameters_to_context(
          codec_ctx.get(), format_ctx->streams[audio_stream_index]->codecpar) < 0) {
    throw std::runtime_error("Failed to copy codec parameters");
  }

  if (avcodec_open2(codec_ctx.get(), codec, nullptr) < 0) {
    throw std::runtime_error("Failed to open codec");
  }

  // Set up resampler: source format -> 16kHz mono float
  AVChannelLayout out_ch_layout = AV_CHANNEL_LAYOUT_MONO;
  AVChannelLayout in_ch_layout;
  if (codec_ctx->ch_layout.nb_channels > 0) {
    av_channel_layout_copy(&in_ch_layout, &codec_ctx->ch_layout);
  } else {
    av_channel_layout_default(&in_ch_layout, 1);
  }

  SwrContext* raw_swr_ctx = nullptr;
  int ret = swr_alloc_set_opts2(&raw_swr_ctx, &out_ch_layout, AV_SAMPLE_FMT_FLT,
                                kTargetSampleRate, &in_ch_layout,
                                codec_ctx->sample_fmt, codec_ctx->sample_rate,
                                0, nullptr);
  av_channel_layout_uninit(&in_ch_layout);

  std::unique_ptr<SwrContext, SwrContextDeleter> swr_ctx(raw_swr_ctx);
  if (ret < 0 || !swr_ctx || swr_init(swr_ctx.get()) < 0) {
    throw std::runtime_error("Failed to initialize resampler");
  }

  std::vector<float> pcm_data;
  // Pre-allocate based on estimated duration
  if (format_ctx->duration > 0 && format_ctx->duration < INT64_MAX / kTargetSampleRate) {
    int64_t estimated_samples =
        (format_ctx->duration * kTargetSampleRate) / AV_TIME_BASE;
    pcm_data.reserve(static_cast<size_t>(estimated_samples));
  }

  std::unique_ptr<AVPacket, PacketDeleter> packet(av_packet_alloc());
  std::unique_ptr<AVFrame, FrameDeleter> frame(av_frame_alloc());

  while (av_read_frame(format_ctx.get(), packet.get()) >= 0) {
    if (packet->stream_index == audio_stream_index) {
      ret = avcodec_send_packet(codec_ctx.get(), packet.get());
      if (ret < 0) {
        av_packet_unref(packet.get());
        continue;
      }

      while (avcodec_receive_frame(codec_ctx.get(), frame.get()) == 0) {
        // Resample frame
        int max_out_samples = swr_get_out_samples(swr_ctx.get(), frame->nb_samples);
        if (max_out_samples <= 0) continue;

        std::vector<float> buffer(max_out_samples);
        uint8_t* out_buffers[] = {reinterpret_cast<uint8_t*>(buffer.data())};

        int out_samples =
            swr_convert(swr_ctx.get(), out_buffers, max_out_samples,
                        const_cast<const uint8_t**>(frame->extended_data),
                        frame->nb_samples);

        if (out_samples > 0) {
          pcm_data.insert(pcm_data.end(), buffer.begin(),
                          buffer.begin() + out_samples);
        }
      }
    }
    av_packet_unref(packet.get());
  }

  // Flush resampler
  int flush_samples = swr_get_out_samples(swr_ctx.get(), 0);
  if (flush_samples > 0) {
    std::vector<float> buffer(flush_samples);
    uint8_t* out_buffers[] = {reinterpret_cast<uint8_t*>(buffer.data())};
    int out_samples = swr_convert(swr_ctx.get(), out_buffers, flush_samples, nullptr, 0);
    if (out_samples > 0) {
      pcm_data.insert(pcm_data.end(), buffer.begin(),
                      buffer.begin() + out_samples);
    }
  }

  LOGI("Decoded %zu samples at %dHz mono from %s", pcm_data.size(),
       kTargetSampleRate, file_path.c_str());

  return PcmResult{
      .data = std::move(pcm_data),
      .sample_rate = kTargetSampleRate,
      .channels = kTargetChannels,
  };
}

}  // namespace deeplayer
