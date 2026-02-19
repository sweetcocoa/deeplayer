plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
}

android {
  namespace = "com.deeplayer.feature.inferenceengine"
  compileSdk = 35

  defaultConfig {
    minSdk = 26

    externalNativeBuild { cmake { arguments("-DANDROID_STL=c++_shared") } }

    ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions { jvmTarget = "17" }

  externalNativeBuild {
    cmake {
      path("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

}

dependencies {
  implementation(project(":core:contracts"))

  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
}
