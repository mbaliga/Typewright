// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.time.Duration

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

// These tests take seconds. A test that waits on a dialog nobody answers (the folder picker under
// xvfb-run did, for the whole job timeout) fails here with Gradle's own "timeout has been exceeded".
tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(10))
}
