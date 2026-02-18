package com.deeplayer.feature.inferenceengine

import com.deeplayer.core.contracts.InferenceEngine

/** Creates the optimal inference engine based on model format and device capabilities. */
class InferenceEngineFactory(
  private val capabilityDetector: DeviceCapabilityDetector = DeviceCapabilityDetector()
) {

  /** Create an engine appropriate for the given model file extension. */
  fun create(modelPath: String): InferenceEngine {
    return when {
      modelPath.endsWith(".tflite") -> LiteRtInferenceEngine(capabilityDetector)
      modelPath.endsWith(".onnx") -> OnnxInferenceEngine()
      else ->
        throw IllegalArgumentException(
          "Unsupported model format: $modelPath. Supported: .tflite, .onnx"
        )
    }
  }
}
