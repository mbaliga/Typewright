@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    id("typewright.lint")
}

group = "dev.aarso.typewright"

kotlin {
    wasmJs {
        outputModuleName = "typewright"
        browser {
            commonWebpackConfig {
                outputFileName = "typewright.js"
            }
            // Compose's Wasm runtime (Skiko) loads only in a browser, so this module's tests run
            // in headless Chrome through Karma. Locally, point CHROME_BIN at a Chrome/Chromium.
            testTask {
                useKarma {
                    useChromeHeadlessNoSandbox()
                }
            }
        }
        binaries.executable()
    }
    sourceSets {
        wasmJsMain {
            dependencies {
                implementation(project(":ui"))
            }
        }
        wasmJsTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

// Binaryen's wasm-opt shrinks the production binary. KGP downloads Binaryen from its GitHub
// releases; where github.com is unreachable, pass -Ptypewright.wasmOpt=/path/to/wasm-opt
// (a native build, or bin/wasm-opt from the npm package `binaryen`, which runs on Node).
providers.gradleProperty("typewright.wasmOpt").orNull?.let { wasmOpt ->
    plugins.withType<BinaryenPlugin> {
        the<BinaryenEnvSpec>().apply {
            download.set(false)
            command.set(wasmOpt)
        }
    }
}
