package com.deeplayer.feature.inferenceengine

import com.deeplayer.core.contracts.Backend
import com.deeplayer.core.contracts.InferenceConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FakeInferenceEngineTest {

  @Test
  fun `loadModel succeeds and marks engine as loaded`() {
    val engine = FakeInferenceEngine.simple(10)

    val result = engine.loadModel("/fake/model.tflite")

    assertThat(result).isTrue()
    assertThat(engine.isLoaded()).isTrue()
  }

  @Test
  fun `run returns output matching configured shape`() {
    val shapes = mapOf("logits" to intArrayOf(1, 100, 512))
    val engine = FakeInferenceEngine(outputShapes = shapes)
    engine.loadModel("/fake/model.tflite")

    val output = engine.run(mapOf("input" to FloatArray(16000)))

    assertThat(output).containsKey("logits")
    @Suppress("UNCHECKED_CAST") val logits = output["logits"] as Array<Array<FloatArray>>
    assertThat(logits.size).isEqualTo(1)
    assertThat(logits[0].size).isEqualTo(100)
    assertThat(logits[0][0].size).isEqualTo(512)
  }

  @Test
  fun `run with 2D output shape returns correct dimensions`() {
    val shapes = mapOf("0" to intArrayOf(50, 80))
    val engine = FakeInferenceEngine(outputShapes = shapes)
    engine.loadModel("/fake/model.onnx")

    val output = engine.run(mapOf("0" to FloatArray(1000)))

    @Suppress("UNCHECKED_CAST") val result = output["0"] as Array<FloatArray>
    assertThat(result.size).isEqualTo(50)
    assertThat(result[0].size).isEqualTo(80)
  }

  @Test
  fun `run with 1D output shape returns flat array`() {
    val engine = FakeInferenceEngine.simple(256)
    engine.loadModel("/fake/model.tflite")

    val output = engine.run(mapOf("0" to FloatArray(100)))

    val result = output["0"] as FloatArray
    assertThat(result.size).isEqualTo(256)
  }

  @Test
  fun `close marks engine as closed and not loaded`() {
    val engine = FakeInferenceEngine.simple(10)
    engine.loadModel("/fake/model.tflite")

    engine.close()

    assertThat(engine.isClosed()).isTrue()
    assertThat(engine.isLoaded()).isFalse()
  }

  @Test(expected = IllegalStateException::class)
  fun `run after close throws IllegalStateException`() {
    val engine = FakeInferenceEngine.simple(10)
    engine.loadModel("/fake/model.tflite")
    engine.close()

    engine.run(mapOf("0" to FloatArray(10)))
  }

  @Test(expected = IllegalStateException::class)
  fun `loadModel after close throws IllegalStateException`() {
    val engine = FakeInferenceEngine.simple(10)
    engine.close()

    engine.loadModel("/fake/model.tflite")
  }

  @Test(expected = IllegalStateException::class)
  fun `run without loading model throws IllegalStateException`() {
    val engine = FakeInferenceEngine.simple(10)

    engine.run(mapOf("0" to FloatArray(10)))
  }

  @Test
  fun `getBackendInfo returns Fake delegate`() {
    val engine = FakeInferenceEngine.simple(10)

    val info = engine.getBackendInfo()

    assertThat(info.delegateName).isEqualTo("Fake")
    assertThat(info.backend).isEqualTo(Backend.CPU)
  }

  @Test
  fun `getBackendInfo reflects preferred backend from config`() {
    val engine = FakeInferenceEngine.simple(10)
    engine.loadModel("/fake/model.tflite", InferenceConfig(preferredBackend = Backend.GPU))

    val info = engine.getBackendInfo()

    assertThat(info.backend).isEqualTo(Backend.GPU)
  }

  @Test
  fun `whisperTiny factory creates correctly shaped engine`() {
    val engine = FakeInferenceEngine.whisperTiny()
    engine.loadModel("/fake/whisper.tflite")

    val output = engine.run(mapOf("0" to FloatArray(480000)))

    @Suppress("UNCHECKED_CAST") val logits = output["0"] as Array<Array<FloatArray>>
    assertThat(logits.size).isEqualTo(1)
    assertThat(logits[0].size).isEqualTo(1500)
    assertThat(logits[0][0].size).isEqualTo(51865)
  }

  @Test
  fun `ten consecutive inferences remain stable`() {
    val engine = FakeInferenceEngine.whisperTiny()
    engine.loadModel("/fake/whisper.tflite")

    repeat(10) { i ->
      val output = engine.run(mapOf("0" to FloatArray(480000)))
      assertThat(output).containsKey("0")
      @Suppress("UNCHECKED_CAST") val logits = output["0"] as Array<Array<FloatArray>>
      assertThat(logits.size).isEqualTo(1)
    }

    // Engine should still be operational
    assertThat(engine.isLoaded()).isTrue()
    assertThat(engine.isClosed()).isFalse()
  }

  @Test
  fun `full lifecycle load run close`() {
    val engine = FakeInferenceEngine.simple(100)

    // Load
    assertThat(engine.loadModel("/fake/model.tflite")).isTrue()
    assertThat(engine.isLoaded()).isTrue()

    // Run
    val output = engine.run(mapOf("0" to FloatArray(50)))
    assertThat(output).isNotEmpty()

    // Close
    engine.close()
    assertThat(engine.isClosed()).isTrue()
    assertThat(engine.isLoaded()).isFalse()
  }

  @Test
  fun `deterministic output with same seed`() {
    val engine1 = FakeInferenceEngine(outputShapes = mapOf("0" to intArrayOf(5)))
    engine1.loadModel("/fake/model.tflite")
    val out1 = engine1.run(mapOf("0" to FloatArray(1)))

    val engine2 = FakeInferenceEngine(outputShapes = mapOf("0" to intArrayOf(5)))
    engine2.loadModel("/fake/model.tflite")
    val out2 = engine2.run(mapOf("0" to FloatArray(1)))

    val arr1 = out1["0"] as FloatArray
    val arr2 = out2["0"] as FloatArray
    assertThat(arr1).isEqualTo(arr2)
  }
}
