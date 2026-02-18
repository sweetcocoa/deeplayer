package com.deeplayer.feature.inferenceengine

import ai.onnxruntime.OrtEnvironment
import com.deeplayer.core.contracts.Backend
import com.deeplayer.core.contracts.InferenceConfig
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for OnnxInferenceEngine. Uses programmatically generated minimal ONNX models to run actual
 * inference through ONNX Runtime in the JVM test environment.
 *
 * These tests require the ONNX Runtime native library to be available. They are automatically
 * skipped when running in environments where the native library cannot be loaded (e.g., Android
 * unit test classpath conflicts).
 */
class OnnxInferenceEngineTest {

  private lateinit var engine: OnnxInferenceEngine
  private lateinit var tempDir: File

  @Before
  fun setUp() {
    assumeTrue("ONNX Runtime native library not available", isOnnxRuntimeAvailable())
    engine = OnnxInferenceEngine()
    tempDir = createTempDir("onnx_test")
  }

  private fun isOnnxRuntimeAvailable(): Boolean {
    return try {
      val env = OrtEnvironment.getEnvironment()
      val tmpDir = createTempDir("onnx_check")
      val modelFile = File(tmpDir, "check.onnx")
      OnnxModelBuilder.createIdentityModel(modelFile, inputSize = 2)
      val session = env.createSession(modelFile.absolutePath)
      session.close()
      tmpDir.deleteRecursively()
      true
    } catch (e: Throwable) {
      false
    }
  }

  @After
  fun tearDown() {
    if (::engine.isInitialized) engine.close()
    if (::tempDir.isInitialized) tempDir.deleteRecursively()
  }

  // --- Model loading tests ---

  @Test
  fun `loadModel with nonexistent file returns false`() {
    val result = engine.loadModel("/nonexistent/model.onnx")

    assertThat(result).isFalse()
  }

  @Test
  fun `loadModel with invalid file returns false`() {
    val tempFile = File(tempDir, "invalid.onnx")
    tempFile.writeBytes(byteArrayOf(0, 1, 2, 3))

    val result = engine.loadModel(tempFile.absolutePath)

    assertThat(result).isFalse()
  }

  @Test
  fun `loadModel with valid Identity model succeeds`() {
    val modelFile = File(tempDir, "identity.onnx")
    OnnxModelBuilder.createIdentityModel(modelFile, inputSize = 4)

    val result = engine.loadModel(modelFile.absolutePath)

    assertThat(result).isTrue()
  }

  @Test
  fun `loadModel with valid Add model succeeds`() {
    val modelFile = File(tempDir, "add.onnx")
    OnnxModelBuilder.createAddModel(modelFile, inputSize = 8)

    val result = engine.loadModel(modelFile.absolutePath)

    assertThat(result).isTrue()
  }

  @Test
  fun `getBackendInfo returns CPU after loading model`() {
    val modelFile = File(tempDir, "identity.onnx")
    OnnxModelBuilder.createIdentityModel(modelFile)
    engine.loadModel(modelFile.absolutePath)

    val info = engine.getBackendInfo()

    assertThat(info.backend).isEqualTo(Backend.CPU)
    assertThat(info.delegateName).isEqualTo("CPU")
    assertThat(info.estimatedSpeedup).isEqualTo(1.0f)
  }

  // --- Inference execution tests ---

  @Test
  fun `run Identity model returns output matching input shape`() {
    val modelFile = File(tempDir, "identity.onnx")
    OnnxModelBuilder.createIdentityModel(modelFile, inputSize = 4)
    engine.loadModel(modelFile.absolutePath)

    val input = Array(1) { floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f) }
    val output = engine.run(mapOf("X" to input))

    assertThat(output).isNotEmpty()
    // Identity op should preserve the values
    val outputTensor = output.values.first()
    assertThat(outputTensor).isInstanceOf(Array::class.java)
    @Suppress("UNCHECKED_CAST") val result = outputTensor as Array<FloatArray>
    assertThat(result.size).isEqualTo(1)
    assertThat(result[0].size).isEqualTo(4)
    assertThat(result[0][0]).isEqualTo(1.0f)
    assertThat(result[0][1]).isEqualTo(2.0f)
    assertThat(result[0][2]).isEqualTo(3.0f)
    assertThat(result[0][3]).isEqualTo(4.0f)
  }

  @Test
  fun `run Add model returns doubled values`() {
    val modelFile = File(tempDir, "add.onnx")
    OnnxModelBuilder.createAddModel(modelFile, inputSize = 4)
    engine.loadModel(modelFile.absolutePath)

    val input = Array(1) { floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f) }
    val output = engine.run(mapOf("X" to input))

    assertThat(output).isNotEmpty()
    @Suppress("UNCHECKED_CAST") val result = output.values.first() as Array<FloatArray>
    // Add(X, X) should return 2*X
    assertThat(result[0][0]).isEqualTo(2.0f)
    assertThat(result[0][1]).isEqualTo(4.0f)
    assertThat(result[0][2]).isEqualTo(6.0f)
    assertThat(result[0][3]).isEqualTo(8.0f)
  }

  // --- Full lifecycle test ---

  @Test
  fun `full lifecycle loadModel then run then close`() {
    val modelFile = File(tempDir, "identity.onnx")
    OnnxModelBuilder.createIdentityModel(modelFile, inputSize = 2)

    // Load
    assertThat(engine.loadModel(modelFile.absolutePath)).isTrue()

    // Run
    val input = Array(1) { floatArrayOf(5.0f, 10.0f) }
    val output = engine.run(mapOf("X" to input))
    assertThat(output).isNotEmpty()
    @Suppress("UNCHECKED_CAST") val result = output.values.first() as Array<FloatArray>
    assertThat(result[0][0]).isEqualTo(5.0f)

    // Close
    engine.close()
  }

  // --- Resource release tests ---

  @Test(expected = IllegalStateException::class)
  fun `run after close throws IllegalStateException`() {
    val modelFile = File(tempDir, "identity.onnx")
    OnnxModelBuilder.createIdentityModel(modelFile)
    engine.loadModel(modelFile.absolutePath)
    engine.close()

    engine.run(mapOf("X" to Array(1) { FloatArray(4) }))
  }

  @Test(expected = IllegalStateException::class)
  fun `loadModel after close throws IllegalStateException`() {
    engine.close()

    engine.loadModel("/any/model.onnx")
  }

  @Test
  fun `close is idempotent`() {
    engine.close()
    engine.close()
  }

  // --- Stability tests ---

  @Test
  fun `ten consecutive inferences remain stable`() {
    val modelFile = File(tempDir, "identity.onnx")
    OnnxModelBuilder.createIdentityModel(modelFile, inputSize = 4)
    engine.loadModel(modelFile.absolutePath)

    repeat(10) { i ->
      val input = Array(1) { floatArrayOf(i.toFloat(), i + 1f, i + 2f, i + 3f) }
      val output = engine.run(mapOf("X" to input))
      assertThat(output).isNotEmpty()
      @Suppress("UNCHECKED_CAST") val result = output.values.first() as Array<FloatArray>
      assertThat(result[0][0]).isEqualTo(i.toFloat())
    }
  }

  // --- Delegate fallback tests ---

  @Test
  fun `CPU backend config loads model successfully`() {
    val modelFile = File(tempDir, "identity.onnx")
    OnnxModelBuilder.createIdentityModel(modelFile)
    val config = InferenceConfig(preferredBackend = Backend.CPU)

    val result = engine.loadModel(modelFile.absolutePath, config)

    assertThat(result).isTrue()
  }

  @Test
  fun `GPU backend config falls back to CPU gracefully`() {
    val modelFile = File(tempDir, "identity.onnx")
    OnnxModelBuilder.createIdentityModel(modelFile)
    val config = InferenceConfig(preferredBackend = Backend.GPU)

    // Should succeed by falling back to CPU (NNAPI not available in JVM test)
    val result = engine.loadModel(modelFile.absolutePath, config)

    assertThat(result).isTrue()
    // After fallback, should still be able to run inference
    val input = Array(1) { floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f) }
    val output = engine.run(mapOf("X" to input))
    assertThat(output).isNotEmpty()
  }

  // --- Model reload test ---

  @Test
  fun `loadModel twice replaces previous model`() {
    val model4 = File(tempDir, "identity4.onnx")
    OnnxModelBuilder.createIdentityModel(model4, inputSize = 4)
    val model8 = File(tempDir, "identity8.onnx")
    OnnxModelBuilder.createIdentityModel(model8, inputSize = 8)

    engine.loadModel(model4.absolutePath)
    engine.loadModel(model8.absolutePath)

    // Should use the second model (inputSize=8)
    val input = Array(1) { FloatArray(8) { it.toFloat() } }
    val output = engine.run(mapOf("X" to input))
    @Suppress("UNCHECKED_CAST") val result = output.values.first() as Array<FloatArray>
    assertThat(result[0].size).isEqualTo(8)
  }
}
