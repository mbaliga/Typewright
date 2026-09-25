// SPDX-License-Identifier: FSL-1.1-ALv2

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    id("typewright.lint")
}

group = "dev.aarso.typewright"

java {
    val java = JavaVersion.toVersion(libs.versions.jvm.target.get())
    sourceCompatibility = java
    targetCompatibility = java
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(libs.versions.jvm.target.get())
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
    testImplementation(libs.kotlin.test)
}

compose.desktop {
    application {
        mainClass = "dev.aarso.typewright.app.desktop.MainKt"
        nativeDistributions {
            // Linux is the v1 desktop; Windows and macOS formats join later on the same target.
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.AppImage)
            packageName = "typewright"
            packageVersion = "0.0.1"
            description = "Hand-made letterforms to fonts of Google Fonts quality"
            vendor = "A System of Cells"
        }
    }
}
