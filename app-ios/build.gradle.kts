// SPDX-License-Identifier: FSL-1.1-ALv2

// The iPad app's Kotlin half: one static framework, TypewrightKit, that the SwiftUI shell in
// iosApp/ embeds and hosts through ComposeUIViewController. Present only when Gradle runs with
// -Ptypewright.ios=true (and the Android SDK, because :ui still has an Android target); see
// settings.gradle.kts and docs/IOS_PORT.md. Linking the framework needs macOS and Xcode.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    id("typewright.lint")
}

group = "com.asoc"

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "TypewrightKit"
            isStatic = true
        }
    }
    sourceSets {
        iosMain {
            dependencies {
                implementation(project(":ui"))
                implementation(project(":project"))
                implementation(project(":project:storage"))
                implementation(libs.compose.runtime)
                implementation(libs.compose.ui)
            }
        }
    }
}
