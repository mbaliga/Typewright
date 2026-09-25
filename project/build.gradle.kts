// SPDX-License-Identifier: Apache-2.0

plugins {
    id("typewright.kmp.pure")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":core-font"))
                api(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

// UfoLibValidationSamplesTest (docs/PROJECT_MODEL.md §11) writes sample projects here, and CI's
// tools/validate_ufo.py reads them with fontTools' ufoLib.
tasks.named<Test>("jvmTest") {
    systemProperty("typewright.repoRoot", isolated.rootProject.projectDirectory.asFile.absolutePath)
    systemProperty("typewright.ufoValidationDir", layout.buildDirectory.dir("ufo-validation").get().asFile.absolutePath)
}
