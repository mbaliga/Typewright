// SPDX-License-Identifier: FSL-1.1-ALv2

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("typewright.lint")
}

android {
    namespace = "com.asoc.typewright.app.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.asoc.typewright"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.0.1"

        // P11 WP3's instrumented tests (docs/PROJECT_MODEL.md §13, owner-run: CLAUDE.md law 4).
        // AndroidJUnitRunner from androidx.test:runner, not the legacy platform one. Gradle's own
        // ANDROIDX_TEST_ORCHESTRATOR execution mode is deliberately not turned on here: it clears
        // app data between test classes by default, which would erase ProjectSaveDeviceTest's own
        // state before ProjectRestoreDeviceTest -- run as a second, separate `adb shell am
        // instrument` by tools/device/p11-save-kill-restore.sh -- gets to read it back. The
        // orchestrator dependency (androidTestUtil below) is still on the classpath, so the owner
        // can opt into it by hand for a run that doesn't need that state to survive.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        val java = JavaVersion.toVersion(libs.versions.jvm.target.get())
        sourceCompatibility = java
        targetCompatibility = java
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(libs.versions.jvm.target.get())
    }
}

dependencies {
    implementation(project(":ui"))
    // A pure module (jvm + wasmJs, no Android target) consumed through its JVM variant.
    implementation(project(":core-geometry"))
    // P11 WP5's Android bridge: TypewrightApplication builds a real ProjectWorkspace over SAF
    // storage, and MainActivity's picker is AndroidFolderPicker.
    implementation(project(":project"))
    implementation(project(":project:storage"))
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.kotlin.test.junit)

    // P11 WP3's instrumented tests (docs/PROJECT_MODEL.md §13): ProjectSaveDeviceTest and
    // ProjectRestoreDeviceTest drive ProjectSession/ProjectWorkspace and the SAF storage actuals
    // for real, on device.
    androidTestImplementation(project(":project"))
    androidTestImplementation(project(":project:storage"))
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestUtil(libs.androidx.test.orchestrator)
}
