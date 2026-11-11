// SPDX-License-Identifier: FSL-1.1-ALv2

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("typewright.lint")
}

// The one version file (version.properties at the repository root) feeds versionName and versionCode here,
// the desktop package version, and the constant About shows (tools/sync_version.py).
val versionFile =
    Properties().apply {
        load(providers.fileContents(isolated.rootProject.projectDirectory.file("version.properties")).asText.get().reader())
    }

// Which store the build is for: -Ptypewright.channel=play for the Google Play bundle (no support link in About),
// github (the default) for the APK that is downloaded directly. Anything else is refused.
val channel = providers.gradleProperty("typewright.channel").orElse("github").get()
require(channel == "play" || channel == "github") { "typewright.channel must be play or github, not '$channel'" }

// Release signing comes from the environment (CI secrets), never from a file in the repository.
// With none of it set the release APK is built unsigned, and says so; it cannot be installed until signed.
fun signingEnv(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
val releaseKeystore = signingEnv("TYPEWRIGHT_KEYSTORE_FILE")
val releaseKeystorePassword = signingEnv("TYPEWRIGHT_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingEnv("TYPEWRIGHT_KEY_ALIAS")
val releaseKeyPassword = signingEnv("TYPEWRIGHT_KEY_PASSWORD") ?: releaseKeystorePassword
val canSignRelease = releaseKeystore != null && releaseKeystorePassword != null && releaseKeyAlias != null

android {
    namespace = "com.asoc.typewright.app.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.asoc.typewright"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = versionFile.getProperty("VERSION_CODE").toInt()
        versionName = versionFile.getProperty("VERSION_NAME")
        buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"$channel\"")

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

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // R8 with the keep rules in proguard-rules.pro; tools/release/android_smoke.py checks the result.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "app-android: no TYPEWRIGHT_KEYSTORE_FILE / TYPEWRIGHT_KEYSTORE_PASSWORD / TYPEWRIGHT_KEY_ALIAS in the " +
                        "environment, so the release build is UNSIGNED and cannot be installed or uploaded. See docs/release/RELEASE_CHECKLIST.md.",
                )
            }
        }
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

// The release smoke test (tools/release/android_smoke.py): assembles the release APK and checks what it
// shipped. Run it with `./gradlew :app-android:releaseSmokeTest`. It never signs and never installs.
tasks.register<Exec>("releaseSmokeTest") {
    group = "verification"
    description = "Assembles the release APK and checks its manifest, icon, size and that it is not debuggable"
    dependsOn("assembleRelease")
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")
    val script = isolated.rootProject.projectDirectory.file("tools/release/android_smoke.py").asFile.absolutePath
    val versionFilePath = isolated.rootProject.projectDirectory.file("version.properties").asFile.absolutePath
    commandLine("python3", "-I", script, apkDir.get().asFile.absolutePath, "--version-file", versionFilePath)
}

dependencies {
    implementation(project(":ui"))
    // A pure module (jvm + wasmJs, no Android target) consumed through its JVM variant.
    implementation(project(":core-geometry"))
    // The Camera hands the capture engine's grey rasters to the shared code (CameraFeed.analyse), so its type is on this classpath too.
    implementation(project(":engine-trace"))
    // P11 WP5's Android bridge: TypewrightApplication builds a real ProjectWorkspace over SAF
    // storage, and MainActivity's picker is AndroidFolderPicker.
    implementation(project(":project"))
    implementation(project(":project:storage"))
    implementation(libs.androidx.activity.compose)
    // Capture's Camera (S09): CameraX preview, analysis and still capture. Compile-verified only: no phone here (CLAUDE.md law 4).
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

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
