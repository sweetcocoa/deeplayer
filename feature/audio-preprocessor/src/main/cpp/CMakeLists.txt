cmake_minimum_required(VERSION 3.22.1)
project("audio_preprocessor")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ASan build option for memory safety testing
option(ENABLE_ASAN "Enable AddressSanitizer" OFF)
if(ENABLE_ASAN)
  add_compile_options(-fsanitize=address -fno-omit-frame-pointer)
  add_link_options(-fsanitize=address)
endif()

add_library(audio_preprocessor SHARED
    audio_preprocessor_jni.cpp
    audio_decoder.cpp
    mel_spectrogram.cpp
    resampler.cpp
)

target_include_directories(audio_preprocessor PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

find_library(log-lib log)

# FFmpeg libraries (pre-built for Android NDK) - optional
find_library(avformat-lib avformat)
find_library(avcodec-lib avcodec)
find_library(avutil-lib avutil)
find_library(swresample-lib swresample)

set(LINK_LIBS ${log-lib})

if(avformat-lib AND avcodec-lib AND avutil-lib AND swresample-lib)
  list(APPEND LINK_LIBS ${avformat-lib} ${avcodec-lib} ${avutil-lib} ${swresample-lib})
  target_compile_definitions(audio_preprocessor PRIVATE HAS_FFMPEG=1)
else()
  message(WARNING "FFmpeg libraries not found. Building stub-only native library.")
  target_compile_definitions(audio_preprocessor PRIVATE HAS_FFMPEG=0)
endif()

target_link_libraries(audio_preprocessor ${LINK_LIBS})
