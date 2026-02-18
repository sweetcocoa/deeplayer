#include <jni.h>

#include <climits>
#include <string>

#include "audio_decoder.h"
#include "mel_spectrogram.h"

#define LOG_TAG "AudioPreprocessorJNI"

struct NativeContext {
  deeplayer::AudioDecoder decoder;
  deeplayer::MelSpectrogram mel;
};

// RAII guard for JNI string resources
struct JniStringGuard {
  JNIEnv* env;
  jstring jstr;
  const char* cstr;
  ~JniStringGuard() {
    if (cstr) env->ReleaseStringUTFChars(jstr, cstr);
  }
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_deeplayer_feature_audiopreprocessor_NativeAudioPreprocessor_nativeCreate(
    JNIEnv* /* env */, jobject /* thiz */) {
  auto* ctx = new NativeContext();
  return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_deeplayer_feature_audiopreprocessor_NativeAudioPreprocessor_nativeDestroy(
    JNIEnv* /* env */, jobject /* thiz */, jlong handle) {
  auto* ctx = reinterpret_cast<NativeContext*>(handle);
  delete ctx;
}

JNIEXPORT jfloatArray JNICALL
Java_com_deeplayer_feature_audiopreprocessor_NativeAudioPreprocessor_nativeDecodeToPcm(
    JNIEnv* env, jobject /* thiz */, jlong handle, jstring filePath) {
  auto* ctx = reinterpret_cast<NativeContext*>(handle);
  const char* path = env->GetStringUTFChars(filePath, nullptr);
  if (!path) {
    env->ThrowNew(env->FindClass("java/lang/RuntimeException"),
                  "Failed to get file path string");
    return nullptr;
  }
  JniStringGuard path_guard{env, filePath, path};

  std::string file_path(path);

  try {
    auto result = ctx->decoder.decode(file_path);
    if (result.data.size() > static_cast<size_t>(INT_MAX)) {
      env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                    "PCM data too large for JNI array");
      return nullptr;
    }
    jfloatArray output = env->NewFloatArray(static_cast<jsize>(result.data.size()));
    if (!output) {
      env->ThrowNew(env->FindClass("java/lang/OutOfMemoryError"),
                    "Failed to allocate PCM output array");
      return nullptr;
    }
    env->SetFloatArrayRegion(output, 0, static_cast<jsize>(result.data.size()),
                             result.data.data());
    return output;
  } catch (const std::exception& e) {
    env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
    return nullptr;
  }
}

JNIEXPORT jfloatArray JNICALL
Java_com_deeplayer_feature_audiopreprocessor_NativeAudioPreprocessor_nativeExtractMelSpectrogram(
    JNIEnv* env, jobject /* thiz */, jlong handle, jfloatArray pcmArray) {
  auto* ctx = reinterpret_cast<NativeContext*>(handle);

  jsize pcm_len = env->GetArrayLength(pcmArray);
  std::vector<float> pcm(pcm_len);
  env->GetFloatArrayRegion(pcmArray, 0, pcm_len, pcm.data());

  try {
    auto mel = ctx->mel.compute(pcm);
    if (mel.size() > static_cast<size_t>(INT_MAX)) {
      env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                    "Mel spectrogram data too large for JNI array");
      return nullptr;
    }
    jfloatArray output = env->NewFloatArray(static_cast<jsize>(mel.size()));
    if (!output) {
      env->ThrowNew(env->FindClass("java/lang/OutOfMemoryError"),
                    "Failed to allocate mel spectrogram output array");
      return nullptr;
    }
    env->SetFloatArrayRegion(output, 0, static_cast<jsize>(mel.size()), mel.data());
    return output;
  } catch (const std::exception& e) {
    env->ThrowNew(env->FindClass("java/lang/RuntimeException"), e.what());
    return nullptr;
  }
}

}  // extern "C"
