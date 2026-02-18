package com.deeplayer.feature.inferenceengine

import com.deeplayer.core.contracts.Backend
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeviceCapabilityDetectorTest {

  private val detector = DeviceCapabilityDetector()

  @Test
  fun `recommendedBackend returns CPU on unknown hardware`() {
    ShadowBuild.setHardware("unknown_device")
    ShadowBuild.setBoard("unknown_board")

    val backend = detector.recommendedBackend()

    assertThat(backend).isEqualTo(Backend.CPU)
  }

  @Test
  fun `isGpuAvailable returns true for Qualcomm hardware`() {
    ShadowBuild.setHardware("qcom")
    ShadowBuild.setBoard("taro")

    assertThat(detector.isGpuAvailable()).isTrue()
  }

  @Test
  fun `isGpuAvailable returns false for unknown hardware`() {
    ShadowBuild.setHardware("unknown")
    ShadowBuild.setBoard("unknown")

    assertThat(detector.isGpuAvailable()).isFalse()
  }

  @Test
  fun `isGpuAvailable returns true for Samsung Exynos`() {
    ShadowBuild.setHardware("exynos2200")
    ShadowBuild.setBoard("universal")

    assertThat(detector.isGpuAvailable()).isTrue()
  }

  @Test
  fun `isGpuAvailable returns true for MediaTek`() {
    ShadowBuild.setHardware("mt6893")
    ShadowBuild.setBoard("mt6893")

    assertThat(detector.isGpuAvailable()).isTrue()
  }
}
