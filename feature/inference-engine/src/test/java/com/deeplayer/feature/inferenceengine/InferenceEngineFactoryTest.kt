package com.deeplayer.feature.inferenceengine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InferenceEngineFactoryTest {

  private val factory = InferenceEngineFactory()

  @Test
  fun `tflite extension creates LiteRtInferenceEngine`() {
    val engine = factory.create("/models/whisper.tflite")

    assertThat(engine).isInstanceOf(LiteRtInferenceEngine::class.java)
  }

  @Test
  fun `onnx extension creates OnnxInferenceEngine`() {
    val engine = factory.create("/models/whisper.onnx")

    assertThat(engine).isInstanceOf(OnnxInferenceEngine::class.java)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `unsupported extension throws IllegalArgumentException`() {
    factory.create("/models/whisper.pt")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `no extension throws IllegalArgumentException`() {
    factory.create("/models/whisper")
  }
}
