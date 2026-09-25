// SPDX-License-Identifier: Apache-2.0

rootProject.name = "typewright"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

// Pure modules (CLAUDE.md law 2): JVM + Kotlin/Wasm, no Android SDK needed to build or test.
include(
    ":core-geometry",
    ":core-font",
    ":engine-trace",
    ":engine-construct",
    ":qa",
    ":qa:corpus",
    ":learn",
    ":learn:scenes",
    ":campaign",
    ":scripts",
    ":project",
)

// Platform modules and apps: these have an Android target, so they need the Android SDK.
// `-Ptypewright.android=false` leaves them out; CI's SDK-free `core` job uses it.
val withAndroid = providers.gradleProperty("typewright.android").orNull?.toBoolean() ?: true
if (withAndroid) {
    include(
        ":compile",
        ":project:storage",
        ":shape-preview",
        ":ui",
        ":app-android",
        ":app-desktop",
        ":app-web",
        // golden-path is a plain-JVM test harness with no Android target of its own, but it
        // depends on :compile (a platform module), so it lives in this block, not the pure one
        // above -- `-Ptypewright.android=false` leaves it out along with :compile.
        ":golden-path",
    )
}
