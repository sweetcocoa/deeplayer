package com.deeplayer.core.contracts

interface InferenceEngine {
  /** Load a model, auto-selecting the optimal delegate for the device. */
  fun loadModel(modelPath: String, config: InferenceConfig = InferenceConfig()): Boolean

  /** Run a single inference. */
  fun run(input: Map<String, Any>): Map<String, Any>

  /** Release resources. */
  fun close()

  /** Return information about the active backend. */
  fun getBackendInfo(): BackendInfo
}
