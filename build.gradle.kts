plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.room) apply false
  alias(libs.plugins.ktfmt) apply false
  alias(libs.plugins.detekt) apply false
}

subprojects {
  apply(plugin = "com.ncorti.ktfmt.gradle")
  apply(plugin = "io.gitlab.arturbosch.detekt")

  configure<com.ncorti.ktfmt.gradle.KtfmtExtension> {
    googleStyle()
  }

  configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
    config.setFrom(rootProject.files("gradle/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
  }
}
