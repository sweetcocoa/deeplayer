#include <jni.h>
#include <cstring>
#include <string>
#include <vector>
#include "whisper.h"

#ifdef __ANDROID__
#include <android/log.h>
#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) do { fprintf(stderr, "[WhisperJNI INFO] "); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); } while(0)
#define LOGE(...) do { fprintf(stderr, "[WhisperJNI ERROR] "); fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); } while(0)
#endif

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_deeplayer_feature_inferenceengine_WhisperNative_init(
    JNIEnv *env, jobject /* this */, jstring modelPath) {
  const char *path = env->GetStringUTFChars(modelPath, nullptr);
  if (!path) {
    LOGE("Failed to get model path string");
    return 0;
  }

  struct whisper_context_params cparams = whisper_context_default_params();
  struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
  env->ReleaseStringUTFChars(modelPath, path);

  if (!ctx) {
    LOGE("Failed to initialize whisper context from: %s", path);
    return 0;
  }

  LOGI("Whisper model loaded successfully");
  return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jobjectArray JNICALL
Java_com_deeplayer_feature_inferenceengine_WhisperNative_transcribe(
    JNIEnv *env, jobject /* this */, jlong ctxPtr, jfloatArray pcmArray,
    jstring langStr) {
  auto *ctx = reinterpret_cast<struct whisper_context *>(ctxPtr);
  if (!ctx) {
    LOGE("Null whisper context");
    return nullptr;
  }

  // Get PCM data
  jfloat *pcmData = env->GetFloatArrayElements(pcmArray, nullptr);
  jsize pcmLen = env->GetArrayLength(pcmArray);

  // Get language
  const char *lang = env->GetStringUTFChars(langStr, nullptr);

  // Configure whisper parameters
  struct whisper_full_params params =
      whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
  params.language = lang;
  params.token_timestamps = true;
  // max_len controls segment splitting by character count (UTF-8 chars).
  // English: max_len=1 gives word-level segments (split_on_word=true).
  // CJK: max_len=1 produces single-character segments (useless).
  //       Use a larger value for phrase-level segmentation.
  bool is_cjk = (strcmp(lang, "ko") == 0 || strcmp(lang, "ja") == 0 ||
                 strcmp(lang, "zh") == 0);
  params.max_len = is_cjk ? 20 : 1;
  params.split_on_word = true;
  params.print_progress = false;
  params.print_realtime = false;
  params.print_special = false;
  params.print_timestamps = false;
  params.n_threads = 4;
  params.no_context = true;

  // Run inference
  int ret = whisper_full(ctx, params, pcmData, pcmLen);
  env->ReleaseStringUTFChars(langStr, lang);
  env->ReleaseFloatArrayElements(pcmArray, pcmData, JNI_ABORT);

  if (ret != 0) {
    LOGE("whisper_full failed with code %d", ret);
    return nullptr;
  }

  // Collect segments
  int n_segments = whisper_full_n_segments(ctx);
  LOGI("Transcription produced %d segments", n_segments);

  // Result: array of String[] where each element is [text, startMs, endMs]
  jclass stringClass = env->FindClass("java/lang/String");
  // Create outer array: n_segments x 3
  jclass stringArrayClass = env->FindClass("[Ljava/lang/String;");
  jobjectArray result =
      env->NewObjectArray(n_segments, stringArrayClass, nullptr);

  for (int i = 0; i < n_segments; i++) {
    const char *text = whisper_full_get_segment_text(ctx, i);
    int64_t t0 = whisper_full_get_segment_t0(ctx, i); // in centiseconds
    int64_t t1 = whisper_full_get_segment_t1(ctx, i);

    long startMs = t0 * 10; // centiseconds to milliseconds
    long endMs = t1 * 10;

    jobjectArray segArray = env->NewObjectArray(3, stringClass, nullptr);
    env->SetObjectArrayElement(segArray, 0, env->NewStringUTF(text));
    env->SetObjectArrayElement(
        segArray, 1,
        env->NewStringUTF(std::to_string(startMs).c_str()));
    env->SetObjectArrayElement(
        segArray, 2,
        env->NewStringUTF(std::to_string(endMs).c_str()));

    env->SetObjectArrayElement(result, i, segArray);
    env->DeleteLocalRef(segArray);
  }

  return result;
}

JNIEXPORT void JNICALL
Java_com_deeplayer_feature_inferenceengine_WhisperNative_free(
    JNIEnv * /* env */, jobject /* this */, jlong ctxPtr) {
  auto *ctx = reinterpret_cast<struct whisper_context *>(ctxPtr);
  if (ctx) {
    whisper_free(ctx);
    LOGI("Whisper context freed");
  }
}

} // extern "C"
