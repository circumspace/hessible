// AGP 9 ships with built-in Kotlin support, so no org.jetbrains.kotlin.* plugin is applied here.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
