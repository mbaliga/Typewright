// SPDX-License-Identifier: Apache-2.0

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("typewright.kmp.platform")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // No Compose here, so the common tests also run on Wasm under Node.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }
    sourceSets {
        commonMain {
            dependencies {
                api(project(":core-font"))
                // ProjectDirectory is now a typealias of com.asoc.typewright.project.ProjectFiles (docs/PROJECT_MODEL.md §2).
                api(project(":project"))
                // HostedBuildProtocol's request/response wire encoding (docs/HOSTED_BUILD_ENDPOINT.md).
                // Already a build-wide dependency (qa:corpus's node-economy loader; see its own
                // build.gradle.kts comment); same version, so nothing new resolves.
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
