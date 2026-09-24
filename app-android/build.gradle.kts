import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("typewright.lint")
}

android {
    namespace = "dev.aarso.typewright.app.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.aarso.typewright"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.0.1"
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
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.kotlin.test.junit)
}
