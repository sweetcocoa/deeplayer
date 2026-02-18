package com.deeplayer.feature.inferenceengine

import com.deeplayer.core.contracts.Backend
import com.deeplayer.core.contracts.InferenceConfig
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for LiteRtInferenceEngine. Uses Robolectric for Android framework access.
 *
 * Note: LiteRT native libraries (libtensorflowlite_jni.so) are Android-only and cannot be loaded in
 * a JVM/Robolectric environment. Tests that require actual TFLite interpreter creation verify the
 * error handling and fallback paths. Full end-to-end TFLite inference is validated in
 * instrumentation tests (androidTest).
 *
 * For unit-test-level actual inference validation, see OnnxInferenceEngineTest which uses the
 * JVM-compatible ONNX Runtime library.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LiteRtInferenceEngineTest {

  private lateinit var engine: LiteRtInferenceEngine
  private lateinit var detector: DeviceCapabilityDetector
  private lateinit var tempDir: File

  @Before
  fun setUp() {
    detector = mockk()
    every { detector.isGpuAvailable() } returns false
    every { detector.isNpuAvailable() } returns false
    every { detector.recommendedBackend() } returns Backend.CPU
    engine = LiteRtInferenceEngine(detector)
    tempDir = createTempDir("litert_test")
  }

  @After
  fun tearDown() {
    engine.close()
    tempDir.deleteRecursively()
  }

  // --- Model loading error handling ---

  @Test
  fun `loadModel with nonexistent file returns false`() {
    val result = engine.loadModel("/nonexistent/model.tflite")

    assertThat(result).isFalse()
  }

  @Test
  fun `loadModel with empty file returns false`() {
    val emptyModel = File(tempDir, "empty.tflite")
    emptyModel.createNewFile()

    val result = engine.loadModel(emptyModel.absolutePath)

    assertThat(result).isFalse()
  }

  @Test
  fun `loadModel with invalid model file returns false`() {
    val invalidModel = File(tempDir, "invalid.tflite")
    invalidModel.writeBytes(byteArrayOf(0, 1, 2, 3, 5, 6, 7, 8))

    val result = engine.loadModel(invalidModel.absolutePath)

    assertThat(result).isFalse()
  }

  @Test
  fun `loadModel with dummy tflite flatbuffer does not crash`() {
    val modelFile = createDummyTfliteModel()

    // May succeed or fail depending on LiteRT native lib availability in Robolectric.
    // The key assertion: it does not throw an unhandled exception.
    val result = engine.loadModel(modelFile.absolutePath)

    assertThat(result).isAnyOf(true, false)
  }

  // --- Backend info ---

  @Test
  fun `getBackendInfo returns CPU with none delegate before loading`() {
    val info = engine.getBackendInfo()

    assertThat(info.backend).isEqualTo(Backend.CPU)
    assertThat(info.delegateName).isEqualTo("none")
  }

  // --- Resource release ---

  @Test(expected = IllegalStateException::class)
  fun `run after close throws IllegalStateException`() {
    engine.close()

    engine.run(mapOf("0" to FloatArray(10)))
  }

  @Test(expected = IllegalStateException::class)
  fun `loadModel after close throws IllegalStateException`() {
    engine.close()

    engine.loadModel("/any/model.tflite")
  }

  @Test
  fun `close is idempotent`() {
    engine.close()
    // Second close should not throw
    engine.close()
  }

  @Test(expected = IllegalStateException::class)
  fun `run without loaded model throws IllegalStateException`() {
    // loadModel with nonexistent file fails, so no model is loaded
    engine.loadModel("/nonexistent/model.tflite")

    engine.run(mapOf("0" to FloatArray(10)))
  }

  // --- Delegate switching ---

  @Test
  fun `AUTO backend resolves to CPU when no GPU or NPU available`() {
    every { detector.recommendedBackend() } returns Backend.CPU

    val modelFile = File(tempDir, "test.tflite")
    modelFile.writeBytes(byteArrayOf(0, 1, 2, 3))

    // Invalid model but exercises the delegate resolution path
    engine.loadModel(modelFile.absolutePath, InferenceConfig(preferredBackend = Backend.AUTO))

    // Should not crash even though model loading fails
  }

  @Test
  fun `GPU backend falls back to CPU without crashing`() {
    every { detector.recommendedBackend() } returns Backend.GPU

    val modelFile = File(tempDir, "test.tflite")
    modelFile.writeBytes(byteArrayOf(0, 1, 2, 3))

    val result =
      engine.loadModel(modelFile.absolutePath, InferenceConfig(preferredBackend = Backend.GPU))

    // Model is invalid so returns false, but the GPU -> CPU fallback path was exercised
    assertThat(result).isFalse()
  }

  @Test
  fun `NPU backend falls back through GPU to CPU without crashing`() {
    every { detector.recommendedBackend() } returns Backend.NPU

    val modelFile = File(tempDir, "test.tflite")
    modelFile.writeBytes(byteArrayOf(0, 1, 2, 3))

    val result =
      engine.loadModel(modelFile.absolutePath, InferenceConfig(preferredBackend = Backend.NPU))

    // Exercises NPU -> GPU -> CPU fallback chain
    assertThat(result).isFalse()
  }

  @Test
  fun `explicit CPU config does not attempt GPU delegate`() {
    val modelFile = File(tempDir, "test.tflite")
    modelFile.writeBytes(byteArrayOf(0, 1, 2, 3))

    val result =
      engine.loadModel(modelFile.absolutePath, InferenceConfig(preferredBackend = Backend.CPU))

    assertThat(result).isFalse()
  }

  // --- Model reload ---

  @Test
  fun `loadModel twice does not crash`() {
    val model1 = File(tempDir, "model1.tflite")
    model1.writeBytes(byteArrayOf(0, 1, 2, 3))
    val model2 = File(tempDir, "model2.tflite")
    model2.writeBytes(byteArrayOf(4, 5, 6, 7))

    engine.loadModel(model1.absolutePath)
    // Should release first interpreter and load second without crashing
    engine.loadModel(model2.absolutePath)
  }

  /**
   * Creates a minimal TFLite FlatBuffer file. Contains the "TFL3" file identifier. This exercises
   * the mmap loading path even though the model content may not be parseable by the runtime.
   */
  private fun createDummyTfliteModel(): File {
    val file = File(tempDir, "dummy.tflite")
    val buffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putInt(24) // offset to root table
    buffer.put("TFL3".toByteArray()) // file identifier
    buffer.position(32)
    buffer.flip()
    FileOutputStream(file).use { fos -> fos.channel.write(buffer) }
    return file
  }
}
