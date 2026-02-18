package com.deeplayer.feature.inferenceengine

import android.util.Log
import com.deeplayer.core.contracts.Backend
import com.deeplayer.core.contracts.BackendInfo
import com.deeplayer.core.contracts.InferenceConfig
import com.deeplayer.core.contracts.InferenceEngine
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime

/** LiteRT (TFLite) backed inference engine with automatic delegate selection. */
class LiteRtInferenceEngine(
  private val capabilityDetector: DeviceCapabilityDetector = DeviceCapabilityDetector()
) : InferenceEngine {

  private var interpreter: InterpreterApi? = null
  private var activeBackend: BackendInfo =
    BackendInfo(backend = Backend.CPU, delegateName = "none", estimatedSpeedup = 1.0f)
  private var closed = false

  override fun loadModel(modelPath: String, config: InferenceConfig): Boolean {
    check(!closed) { "Engine is closed" }
    // Release any previously loaded interpreter without marking as closed
    interpreter?.close()
    interpreter = null

    val modelBuffer = mmapModel(modelPath) ?: return false
    val backend = resolveBackend(config.preferredBackend)

    val options =
      InterpreterApi.Options().setRuntime(TfLiteRuntime.FROM_APPLICATION_ONLY).apply {
        setNumThreads(config.numThreads)
      }

    // Try delegates in preference order with fallback
    val (createdInterpreter, usedBackend) =
      tryCreateInterpreter(modelBuffer, options, backend, config)

    if (createdInterpreter == null) {
      Log.e(TAG, "Failed to create interpreter for all backends")
      return false
    }

    interpreter = createdInterpreter
    activeBackend = usedBackend
    Log.i(TAG, "Model loaded with backend: ${usedBackend.delegateName}")
    return true
  }

  override fun run(input: Map<String, Any>): Map<String, Any> {
    check(!closed) { "Engine is closed" }
    val interp = interpreter ?: error("No model loaded")

    val inputCount = interp.getInputTensorCount()
    val outputCount = interp.getOutputTensorCount()

    // Prepare input arrays from the map. Keys are expected to be "0", "1", ...
    require(input.size >= inputCount) {
      "Expected at least $inputCount inputs but got ${input.size}"
    }
    val inputValues = input.values.toList()
    val inputs = Array<Any>(inputCount) { index -> input[index.toString()] ?: inputValues[index] }

    // Allocate output buffers based on output tensor shapes
    val outputs = mutableMapOf<Int, Any>()
    for (i in 0 until outputCount) {
      val outputTensor = interp.getOutputTensor(i)
      val shape = outputTensor.shape()
      val totalSize = shape.fold(1) { acc, dim -> acc * dim }
      outputs[i] = createOutputBuffer(shape, totalSize)
    }

    interp.runForMultipleInputsOutputs(inputs, outputs)

    return outputs.entries.associate { (key, value) -> key.toString() to value }
  }

  override fun close() {
    interpreter?.close()
    interpreter = null
    closed = true
  }

  override fun getBackendInfo(): BackendInfo = activeBackend

  private fun mmapModel(modelPath: String): MappedByteBuffer? {
    return try {
      val file = File(modelPath)
      FileInputStream(file).use { fis ->
        fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to mmap model file: $modelPath", e)
      null
    }
  }

  private fun resolveBackend(preferred: Backend): Backend {
    if (preferred != Backend.AUTO) return preferred
    return capabilityDetector.recommendedBackend()
  }

  private fun tryCreateInterpreter(
    modelBuffer: MappedByteBuffer,
    baseOptions: InterpreterApi.Options,
    targetBackend: Backend,
    config: InferenceConfig,
  ): Pair<InterpreterApi?, BackendInfo> {
    // Build a prioritized list of backends to try
    val backends = buildBackendPriority(targetBackend)

    for (backend in backends) {
      try {
        val options = applyDelegate(baseOptions, backend, config)
        val interp = InterpreterApi.create(modelBuffer, options)
        val info = backendInfo(backend)
        return interp to info
      } catch (e: Exception) {
        Log.w(TAG, "Failed to create interpreter with $backend, trying next", e)
      }
    }
    return null to BackendInfo(Backend.CPU, "none", 1.0f)
  }

  private fun buildBackendPriority(target: Backend): List<Backend> {
    val list = mutableListOf(target)
    // Always fall back through GPU then CPU
    if (target == Backend.NPU && !list.contains(Backend.GPU)) list.add(Backend.GPU)
    if (!list.contains(Backend.CPU)) list.add(Backend.CPU)
    return list
  }

  private fun applyDelegate(
    options: InterpreterApi.Options,
    backend: Backend,
    @Suppress("UnusedParameter") config: InferenceConfig,
  ): InterpreterApi.Options {
    return when (backend) {
      Backend.GPU -> {
        try {
          val gpuDelegateFactoryClass = Class.forName("org.tensorflow.lite.gpu.GpuDelegateFactory")
          val delegateFactory = gpuDelegateFactoryClass.getDeclaredConstructor().newInstance()
          val addMethod =
            InterpreterApi.Options::class
              .java
              .getMethod("addDelegateFactory", Class.forName("org.tensorflow.lite.DelegateFactory"))
          addMethod.invoke(options, delegateFactory)
        } catch (e: Exception) {
          Log.w(TAG, "GPU delegate not available, falling back to CPU", e)
        }
        options
      }
      Backend.NPU,
      Backend.CPU,
      Backend.AUTO -> options
    }
  }

  private fun backendInfo(backend: Backend): BackendInfo {
    return when (backend) {
      Backend.GPU -> BackendInfo(Backend.GPU, "GPU (OpenCL)", 3.0f)
      Backend.NPU -> BackendInfo(Backend.NPU, "NPU (QNN)", 5.0f)
      Backend.CPU -> BackendInfo(Backend.CPU, "CPU (XNNPack)", 1.0f)
      Backend.AUTO -> BackendInfo(Backend.CPU, "CPU (XNNPack)", 1.0f)
    }
  }

  private fun createOutputBuffer(shape: IntArray, totalSize: Int): Any {
    return when (shape.size) {
      1 -> FloatArray(shape[0])
      2 -> Array(shape[0]) { FloatArray(shape[1]) }
      3 -> Array(shape[0]) { Array(shape[1]) { FloatArray(shape[2]) } }
      else -> FloatArray(totalSize)
    }
  }

  companion object {
    private const val TAG = "LiteRtInferenceEngine"
  }
}
