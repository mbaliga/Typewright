plugins {
    // Every plugin the modules use, loaded once here so all modules share one classloader.
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.spotless) apply false
    // ktlint for the root build scripts and build-logic.
    id("typewright.lint")
}

spotless {
    kotlin {
        target("build-logic/convention/src/**/*.kt")
    }
    kotlinGradle {
        target("*.gradle.kts", "build-logic/*.gradle.kts", "build-logic/convention/*.gradle.kts")
    }
}

// The committed npm lock (kotlin-js-store/) describes the full build. A reduced build
// (-Ptypewright.android=false) has fewer npm workspaces, so it must neither fail on the lock
// nor overwrite it.
if (providers.gradleProperty("typewright.android").orNull?.toBoolean() == false) {
    tasks.matching { it.name == "kotlinWasmStorePackageLock" }.configureEach {
        enabled = false
    }
}
