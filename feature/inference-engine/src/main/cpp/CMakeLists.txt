cmake_minimum_required(VERSION 3.22)
project(whisper_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

# whisper.cpp source directory (added as git submodule)
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

# Core GGML sources
set(GGML_SOURCES
    ${WHISPER_DIR}/ggml/src/ggml.c
    ${WHISPER_DIR}/ggml/src/ggml.cpp
    ${WHISPER_DIR}/ggml/src/ggml-alloc.c
    ${WHISPER_DIR}/ggml/src/ggml-backend.cpp
    ${WHISPER_DIR}/ggml/src/ggml-backend-reg.cpp
    ${WHISPER_DIR}/ggml/src/ggml-backend-dl.cpp
    ${WHISPER_DIR}/ggml/src/ggml-opt.cpp
    ${WHISPER_DIR}/ggml/src/ggml-quants.c
    ${WHISPER_DIR}/ggml/src/ggml-threading.cpp
    ${WHISPER_DIR}/ggml/src/gguf.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/ggml-cpu.c
    ${WHISPER_DIR}/ggml/src/ggml-cpu/ggml-cpu.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/ops.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/quants.c
    ${WHISPER_DIR}/ggml/src/ggml-cpu/binary-ops.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/unary-ops.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/vec.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/traits.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/repack.cpp
    ${WHISPER_DIR}/ggml/src/ggml-cpu/hbm.cpp
)

# Architecture-specific sources for Android ARM
if(${ANDROID_ABI} STREQUAL "arm64-v8a" OR ${ANDROID_ABI} STREQUAL "armeabi-v7a")
    list(APPEND GGML_SOURCES
        ${WHISPER_DIR}/ggml/src/ggml-cpu/arch/arm/quants.c
        ${WHISPER_DIR}/ggml/src/ggml-cpu/arch/arm/repack.cpp
        ${WHISPER_DIR}/ggml/src/ggml-cpu/arch/arm/cpu-feats.cpp
    )
endif()

# Whisper sources
set(WHISPER_SOURCES
    ${WHISPER_DIR}/src/whisper.cpp
)

# JNI bridge
set(JNI_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper_jni.cpp
)

add_library(whisper_jni SHARED
    ${GGML_SOURCES}
    ${WHISPER_SOURCES}
    ${JNI_SOURCES}
)

target_include_directories(whisper_jni PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
    ${WHISPER_DIR}/ggml/src/ggml-cpu
    ${WHISPER_DIR}/src
)

target_compile_definitions(whisper_jni PRIVATE
    GGML_USE_CPU
    GGML_BUILD=1
    GGML_VERSION="0.0.0"
    GGML_COMMIT="unknown"
    WHISPER_VERSION="0.0.0"
)

# ARM NEON optimization for Android
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(whisper_jni PRIVATE -mcpu=generic+fp+simd)
endif()

target_link_libraries(whisper_jni
    android
    log
    m
)
