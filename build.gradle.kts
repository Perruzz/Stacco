// Top-level build file. AGP 9 compiles Kotlin natively ("built-in Kotlin"):
// the Kotlin plugin is only declared here to pin the Kotlin Gradle Plugin version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
