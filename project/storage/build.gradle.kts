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
        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        androidMain {
            dependencies {
                // AndroidFolderPicker's ActivityResultRegistry/ActivityResultContracts come from
                // this artifact's own androidx.activity:activity base (already resolved by it);
                // no Compose dependency of this module's own code on it.
                implementation(libs.androidx.activity.compose)
            }
        }
        androidHostTest {
            dependencies {
                implementation(libs.kotlin.test.junit)
            }
        }
        wasmJsMain {
            dependencies {
                implementation(libs.kotlinx.browser)
            }
        }
    }
}
