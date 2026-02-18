package com.deeplayer.feature.inferenceengine

import android.os.Build
import com.deeplayer.core.contracts.Backend

/** Detects hardware capabilities to recommend the optimal inference backend. */
class DeviceCapabilityDetector {

  /** Returns true if the device likely has a usable GPU for inference. */
  fun isGpuAvailable(): Boolean {
    // OpenCL-based GPU delegate is generally available on devices with Adreno, Mali, or
    // PowerVR GPUs. We use a heuristic based on known SoC families.
    val hardware = Build.HARDWARE.lowercase()
    val board = Build.BOARD.lowercase()
    val soc = "$hardware $board"
    return GPU_SOC_PATTERNS.any { soc.contains(it) }
  }

  /** Returns true if the device supports Qualcomm QNN (NPU/HTP). */
  fun isNpuAvailable(): Boolean {
    val hardware = Build.HARDWARE.lowercase()
    // Build.SOC_MODEL requires API 31+
    val soc =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Build.SOC_MODEL.lowercase()
      } else {
        ""
      }
    // QNN delegate works on Snapdragon 8 Gen 1+ with HTP (Hexagon Tensor Processor)
    return QUALCOMM_NPU_PATTERNS.any { hardware.contains(it) || soc.contains(it) }
  }

  /** Returns the recommended backend based on device capabilities. */
  fun recommendedBackend(): Backend {
    return when {
      isNpuAvailable() -> Backend.NPU
      isGpuAvailable() -> Backend.GPU
      else -> Backend.CPU
    }
  }

  companion object {
    private val GPU_SOC_PATTERNS =
      listOf("qcom", "qualcomm", "exynos", "samsung", "mediatek", "mt", "kirin", "tensor")

    private val QUALCOMM_NPU_PATTERNS =
      listOf("sm8450", "sm8475", "sm8550", "sm8650", "qcom", "kalama", "taro", "waipio")
  }
}
