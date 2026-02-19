plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt)
}

android {
  namespace = "com.deeplayer"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.deeplayer"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "0.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions { jvmTarget = "17" }

  buildFeatures { compose = true }

  androidResources { noCompress += listOf("bin") }
}

dependencies {
  implementation(project(":core:contracts"))
  implementation(project(":core:player"))
  implementation(project(":feature:audio-preprocessor"))
  implementation(project(":feature:inference-engine"))
  implementation(project(":feature:alignment-orchestrator"))
  implementation(project(":feature:lyrics-ui"))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.material3)
  implementation(libs.compose.material.icons.extended)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.animation)
  implementation(libs.compose.foundation)
  implementation(libs.navigation.compose)
  implementation(libs.coil.compose)

  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.hilt.navigation.compose)

  testImplementation(libs.junit)
  androidTestImplementation(libs.junit.ext)
  debugImplementation(libs.compose.ui.tooling)
  debugImplementation(libs.compose.ui.test.manifest)
}
