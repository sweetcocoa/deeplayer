package com.deeplayer.feature.inferenceengine

import com.deeplayer.core.contracts.Backend
import com.deeplayer.core.contracts.BackendInfo
import com.deeplayer.core.contracts.InferenceConfig
import com.deeplayer.core.contracts.InferenceEngine
import kotlin.random.Random

/**
 * Fake inference engine for testing. Returns deterministic or random probability matrices matching
 * the expected output shape for the loaded model.
 */
class FakeInferenceEngine(
  private val outputShapes: Map<String, IntArray> = DEFAULT_OUTPUT_SHAPES,
  private val random: Random = Random(42),
) : InferenceEngine {

  private var loaded = false
  private var closed = false
  private var modelPath: String? = null
  private var config: InferenceConfig = InferenceConfig()

  override fun loadModel(modelPath: String, config: InferenceConfig): Boolean {
    check(!closed) { "Engine is closed" }
    this.modelPath = modelPath
    this.config = config
    loaded = true
    return true
  }

  override fun run(input: Map<String, Any>): Map<String, Any> {
    check(!closed) { "Engine is closed" }
    check(loaded) { "No model loaded" }

    return outputShapes.entries.associate { (name, shape) -> name to generateOutput(shape) }
  }

  override fun close() {
    loaded = false
    closed = true
    modelPath = null
  }

  override fun getBackendInfo(): BackendInfo {
    return BackendInfo(
      backend = config.preferredBackend.let { if (it == Backend.AUTO) Backend.CPU else it },
      delegateName = "Fake",
      estimatedSpeedup = 1.0f,
    )
  }

  /** Returns whether the engine has been closed. */
  fun isClosed(): Boolean = closed

  /** Returns whether a model is currently loaded. */
  fun isLoaded(): Boolean = loaded

  private fun generateOutput(shape: IntArray): Any {
    return when (shape.size) {
      1 -> FloatArray(shape[0]) { random.nextFloat() }
      2 -> Array(shape[0]) { FloatArray(shape[1]) { random.nextFloat() } }
      3 -> Array(shape[0]) { Array(shape[1]) { FloatArray(shape[2]) { random.nextFloat() } } }
      else -> FloatArray(shape.fold(1) { acc, d -> acc * d }) { random.nextFloat() }
    }
  }

  companion object {
    /** Default output: single tensor "0" with shape [1, 1500, 51865] (Whisper tiny logits). */
    val DEFAULT_OUTPUT_SHAPES = mapOf("0" to intArrayOf(1, 1500, 51865))

    /** Create a Whisper-shaped fake engine. */
    fun whisperTiny(): FakeInferenceEngine =
      FakeInferenceEngine(outputShapes = mapOf("0" to intArrayOf(1, 1500, 51865)))

    /** Create a simple fake engine with a single flat output. */
    fun simple(outputSize: Int): FakeInferenceEngine =
      FakeInferenceEngine(outputShapes = mapOf("0" to intArrayOf(outputSize)))
  }
}
