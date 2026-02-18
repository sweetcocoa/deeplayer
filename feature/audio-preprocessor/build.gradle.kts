plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
}

android {
  namespace = "com.deeplayer.feature.audiopreprocessor"
  compileSdk = 35

  defaultConfig {
    minSdk = 26

    ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
  }

  // Native build requires FFmpeg pre-built libraries.
  // Uncomment when FFmpeg libs are available under src/main/jniLibs/.
  // externalNativeBuild {
  //   cmake {
  //     path = file("src/main/cpp/CMakeLists.txt")
  //     version = "3.22.1"
  //   }
  // }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions { jvmTarget = "17" }
}

dependencies {
  implementation(project(":core:contracts"))

  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
}
