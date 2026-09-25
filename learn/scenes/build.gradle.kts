// SPDX-License-Identifier: FSL-1.1-ALv2

plugins {
    id("typewright.kmp.pure")
    alias(libs.plugins.kotlin.serialization)
}

// Copies data/learn-faces/manifest.json, the font files it names and each family's licence file
// (OFL.txt, or LICENSE.txt for Roboto Slab) into generated resources under
// typewright/learn-faces/, so jvm and wasmJs read them with a plain classpath or fetch lookup
// (LearnFaceResources.kt). The '[' ']' in variable-font file names are literal; none of the
// include patterns contains one.
val syncLearnFaceData =
    tasks.register<Sync>("syncLearnFaceData") {
        description = "Copies data/learn-faces/manifest.json and its real font and licence files into this module's generated resources."
        from(isolated.rootProject.projectDirectory.dir("data/learn-faces")) {
            include("manifest.json")
            include("*/*.ttf")
            include("*/OFL.txt")
            include("*/LICENSE.txt")
            into("typewright/learn-faces")
        }
        into(layout.buildDirectory.dir("generated/learnFaceData"))
    }

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":learn"))
                implementation(libs.kotlinx.serialization.json)
            }
            resources.srcDir(syncLearnFaceData)
        }
    }
}
