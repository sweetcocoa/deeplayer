plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
}

android {
  namespace = "com.deeplayer.feature.inferenceengine"
  compileSdk = 35

  defaultConfig { minSdk = 26 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions { jvmTarget = "17" }

  testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
  implementation(project(":core:contracts"))

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.litert)
  implementation(libs.litert.gpu)
  implementation(libs.onnxruntime.android)

  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  // JVM ONNX Runtime for unit tests (Android variant won't load in JVM)
  testImplementation(libs.onnxruntime)
}
