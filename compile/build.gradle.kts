import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("typewright.kmp.platform")
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
            }
        }
    }
}
