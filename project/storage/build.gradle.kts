// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("typewright.kmp.platform")
}

kotlin {
    // FsaProjectStore needs a real browser (the File System Access API and OPFS), so the Wasm
    // tests run in headless Chrome through Karma, as ui's do.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask {
                enabled = true
                useKarma {
                    useChromeHeadlessNoSandbox()
                }
            }
        }
    }
    sourceSets {
        commonMain {
            dependencies {
                api(project(":project"))
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.activity.compose)
            }
        }
        wasmJsMain {
            dependencies {
                implementation(libs.kotlinx.browser)
            }
        }
    }
}
