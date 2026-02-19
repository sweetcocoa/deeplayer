#!/bin/bash
# Build whisper.cpp JNI library for the host platform (macOS) for local testing.
# Output: build/whisper-host/libwhisper_jni.dylib
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
CPP_DIR="$PROJECT_ROOT/feature/inference-engine/src/main/cpp"
WHISPER_DIR="$CPP_DIR/whisper.cpp"
OUT_DIR="$PROJECT_ROOT/build/whisper-host"
OBJ_DIR="$OUT_DIR/obj"

# Find JAVA_HOME for JNI headers
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -x /usr/libexec/java_home ]; then
    JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || true)
  fi
fi
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home"
fi

JNI_INCLUDE="$JAVA_HOME/include"
if [ ! -d "$JNI_INCLUDE" ]; then
  echo "ERROR: JNI headers not found at $JNI_INCLUDE"
  echo "Set JAVA_HOME to a JDK installation."
  exit 1
fi

# Platform-specific JNI headers
if [ "$(uname)" = "Darwin" ]; then
  JNI_PLATFORM_INCLUDE="$JNI_INCLUDE/darwin"
  LIB_NAME="libwhisper_jni.dylib"
else
  JNI_PLATFORM_INCLUDE="$JNI_INCLUDE/linux"
  LIB_NAME="libwhisper_jni.so"
fi

if [ ! -d "$WHISPER_DIR" ]; then
  echo "ERROR: whisper.cpp submodule not found at $WHISPER_DIR"
  echo "Run: git submodule update --init"
  exit 1
fi

mkdir -p "$OBJ_DIR"

echo "=== Building whisper.cpp JNI for host ($(uname -m)) ==="
echo "JAVA_HOME: $JAVA_HOME"

COMMON_DEFS="-DGGML_USE_CPU -DGGML_BUILD=1 -DGGML_VERSION=\"0.0.0\" -DGGML_COMMIT=\"unknown\" -DWHISPER_VERSION=\"0.0.0\""

INCLUDES="-I$WHISPER_DIR/include -I$WHISPER_DIR/ggml/include -I$WHISPER_DIR/ggml/src -I$WHISPER_DIR/ggml/src/ggml-cpu -I$WHISPER_DIR/src -I$JNI_INCLUDE -I$JNI_PLATFORM_INCLUDE"

ARCH=$(uname -m)
ARCH_FLAGS=""
if [ "$ARCH" = "arm64" ]; then
  ARCH_FLAGS="-mcpu=apple-m1"
elif [ "$ARCH" = "x86_64" ]; then
  ARCH_FLAGS="-mavx2 -mfma -mf16c"
fi

compile_c() {
  local src="$1"
  local name="$2"
  clang -c -std=c11 -O2 -fPIC $COMMON_DEFS $INCLUDES $ARCH_FLAGS -w -o "$OBJ_DIR/${name}.o" "$src"
}

compile_cpp() {
  local src="$1"
  local name="$2"
  clang++ -c -std=c++17 -O2 -fPIC $COMMON_DEFS $INCLUDES $ARCH_FLAGS -w -o "$OBJ_DIR/${name}.o" "$src"
}

echo "Compiling ggml core..."
compile_c  "$WHISPER_DIR/ggml/src/ggml.c"              "ggml_c"
compile_cpp "$WHISPER_DIR/ggml/src/ggml.cpp"            "ggml_cpp"
compile_c  "$WHISPER_DIR/ggml/src/ggml-alloc.c"         "ggml-alloc"
compile_cpp "$WHISPER_DIR/ggml/src/ggml-backend.cpp"    "ggml-backend"
compile_cpp "$WHISPER_DIR/ggml/src/ggml-backend-reg.cpp" "ggml-backend-reg"
compile_cpp "$WHISPER_DIR/ggml/src/ggml-backend-dl.cpp"  "ggml-backend-dl"
compile_cpp "$WHISPER_DIR/ggml/src/ggml-opt.cpp"        "ggml-opt"
compile_c  "$WHISPER_DIR/ggml/src/ggml-quants.c"        "ggml-quants"
compile_cpp "$WHISPER_DIR/ggml/src/ggml-threading.cpp"  "ggml-threading"
compile_cpp "$WHISPER_DIR/ggml/src/gguf.cpp"            "gguf"

echo "Compiling ggml-cpu..."
compile_c  "$WHISPER_DIR/ggml/src/ggml-cpu/ggml-cpu.c"    "ggml-cpu_c"
compile_cpp "$WHISPER_DIR/ggml/src/ggml-cpu/ggml-cpu.cpp" "ggml-cpu_cpp"
compile_cpp "$WHISPER_DIR/ggml/src/ggml-cpu/ops.cpp"          "cpu-ops"
compile_c  "$WHISPER_DIR/ggml/src/ggml-cpu/quants.c"         "cpu-quants"
compile_cpp "$WHISPER_DIR/ggml/src/ggml-cpu/binary-ops.cpp"  "cpu-binary-ops"
compile_cpp "$WHISPER_DIR/ggml/src/ggml-cpu/unary-ops.cpp"   "cpu-unary-ops"
compile_cpp "$WHISPER_DIR/ggml/src/ggml-cpu/vec.cpp"         "cpu-vec"
compile_cpp "$WHISPER_DIR/ggml/src/ggml-cpu/traits.cpp"      "cpu-traits"
compile_cpp "$WHISPER_DIR/ggml/src/ggml-cpu/repack.cpp"      "cpu-repack"
compile_cpp "$WHISPER_DIR/ggml/src/ggml-cpu/hbm.cpp"         "cpu-hbm"

echo "Compiling arch-specific ($ARCH)..."
if [ "$ARCH" = "arm64" ] || [ "$ARCH" = "aarch64" ]; then
  compile_c   "$WHISPER_DIR/ggml/src/ggml-cpu/arch/arm/quants.c"    "arch-arm-quants"
  compile_cpp "$WHISPER_DIR/ggml/src/ggml-cpu/arch/arm/repack.cpp"  "arch-arm-repack"
  compile_cpp "$WHISPER_DIR/ggml/src/ggml-cpu/arch/arm/cpu-feats.cpp" "arch-arm-cpu-feats"
elif [ "$ARCH" = "x86_64" ]; then
  compile_c   "$WHISPER_DIR/ggml/src/ggml-cpu/arch/x86/quants.c"    "arch-x86-quants"
  compile_cpp "$WHISPER_DIR/ggml/src/ggml-cpu/arch/x86/repack.cpp"  "arch-x86-repack"
  compile_cpp "$WHISPER_DIR/ggml/src/ggml-cpu/arch/x86/cpu-feats.cpp" "arch-x86-cpu-feats"
fi

echo "Compiling whisper + JNI..."
compile_cpp "$WHISPER_DIR/src/whisper.cpp"     "whisper"
compile_cpp "$CPP_DIR/whisper_jni.cpp"         "whisper_jni"

echo "Linking $LIB_NAME..."
clang++ -shared -o "$OUT_DIR/$LIB_NAME" "$OBJ_DIR"/*.o \
  -framework Accelerate -ldl -lpthread

echo ""
echo "=== Build successful ==="
ls -lh "$OUT_DIR/$LIB_NAME"
