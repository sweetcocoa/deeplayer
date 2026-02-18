package com.deeplayer.feature.inferenceengine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import com.deeplayer.core.contracts.Backend
import com.deeplayer.core.contracts.BackendInfo
import com.deeplayer.core.contracts.InferenceConfig
import com.deeplayer.core.contracts.InferenceEngine
import java.nio.FloatBuffer

/** ONNX Runtime backed inference engine. */
class OnnxInferenceEngine : InferenceEngine {

  private var environment: OrtEnvironment? = null
  private var session: OrtSession? = null
  private var activeBackend: BackendInfo =
    BackendInfo(backend = Backend.CPU, delegateName = "none", estimatedSpeedup = 1.0f)
  private var closed = false

  override fun loadModel(modelPath: String, config: InferenceConfig): Boolean {
    check(!closed) { "Engine is closed" }
    // Release any previously loaded session without marking as closed
    session?.close()
    session = null

    return try {
      val env = OrtEnvironment.getEnvironment()
      environment = env

      val sessionOptions =
        OrtSession.SessionOptions().apply {
          setIntraOpNumThreads(config.numThreads)
          setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
          if (config.enableFp16) {
            addConfigEntry("session.graph_optimization_level", "ORT_ENABLE_ALL")
          }
        }

      // GPU (NNAPI) support: try to add if requested and available
      val backend = resolveBackend(config.preferredBackend)
      val usedBackend = tryAddExecutionProvider(sessionOptions, backend)

      session = env.createSession(modelPath, sessionOptions)
      activeBackend = usedBackend
      Log.i(TAG, "ONNX model loaded with backend: ${usedBackend.delegateName}")
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load ONNX model: $modelPath: ${e.message}")
      false
    }
  }

  override fun run(input: Map<String, Any>): Map<String, Any> {
    check(!closed) { "Engine is closed" }
    val sess = session ?: error("No model loaded")
    val env = environment ?: error("No environment")

    // Convert input map to OnnxTensor map
    val onnxInputs =
      input.entries.associate { (name, value) -> name to createOnnxTensor(env, value) }

    val results = sess.run(onnxInputs)

    // Convert outputs back to standard types
    val outputMap = mutableMapOf<String, Any>()
    for (entry in results) {
      val name = entry.key
      val onnxValue = entry.value
      if (onnxValue is OnnxTensor) {
        outputMap[name] = extractTensorValue(onnxValue)
      }
    }

    results.close()
    onnxInputs.values.forEach { it.close() }

    return outputMap
  }

  override fun close() {
    session?.close()
    session = null
    // OrtEnvironment is shared singleton, do not close it
    environment = null
    closed = true
  }

  override fun getBackendInfo(): BackendInfo = activeBackend

  private fun resolveBackend(preferred: Backend): Backend {
    return if (preferred == Backend.AUTO) Backend.CPU else preferred
  }

  private fun tryAddExecutionProvider(
    options: OrtSession.SessionOptions,
    backend: Backend,
  ): BackendInfo {
    if (backend == Backend.GPU || backend == Backend.NPU) {
      try {
        options.addNnapi()
        return BackendInfo(backend = Backend.GPU, delegateName = "NNAPI", estimatedSpeedup = 2.5f)
      } catch (e: Exception) {
        Log.w(TAG, "NNAPI not available, falling back to CPU: ${e.message}")
      }
    }
    return BackendInfo(backend = Backend.CPU, delegateName = "CPU", estimatedSpeedup = 1.0f)
  }

  private fun createOnnxTensor(env: OrtEnvironment, value: Any): OnnxTensor {
    return when (value) {
      is FloatArray -> {
        OnnxTensor.createTensor(env, FloatBuffer.wrap(value), longArrayOf(value.size.toLong()))
      }
      is Array<*> -> {
        val first = value.firstOrNull() ?: throw IllegalArgumentException("Empty array input")
        require(first is FloatArray) {
          "Expected Array<FloatArray> but got Array<${first::class.simpleName}>"
        }
        @Suppress("UNCHECKED_CAST") OnnxTensor.createTensor(env, value as Array<FloatArray>)
      }
      else -> throw IllegalArgumentException("Unsupported input type: ${value::class}")
    }
  }

  private fun extractTensorValue(tensor: OnnxTensor): Any {
    val shape = tensor.info.shape
    return when (shape.size) {
      1 -> tensor.floatBuffer.let { buf -> FloatArray(buf.remaining()).also { buf.get(it) } }
      2 -> {
        val rows = shape[0].toInt()
        val cols = shape[1].toInt()
        val buf = tensor.floatBuffer
        Array(rows) { FloatArray(cols).also { row -> buf.get(row) } }
      }
      3 -> {
        val d0 = shape[0].toInt()
        val d1 = shape[1].toInt()
        val d2 = shape[2].toInt()
        val buf = tensor.floatBuffer
        Array(d0) { Array(d1) { FloatArray(d2).also { row -> buf.get(row) } } }
      }
      else -> {
        val buf = tensor.floatBuffer
        FloatArray(buf.remaining()).also { buf.get(it) }
      }
    }
  }

  companion object {
    private const val TAG = "OnnxInferenceEngine"
  }
}
