// SPDX-License-Identifier: FSL-1.1-ALv2

plugins {
    id("typewright.kmp.pure")
    alias(libs.plugins.kotlin.serialization)
}

// P6 (Learn UI half): copies data/learn-faces/manifest.json and the seventeen real font files it
// names into this module's generated resources under typewright/learn-faces/, so both jvm and
// wasmJs can read them back via a plain classpath/fetch lookup (LearnFaceResources.kt) -- the
// exact same pattern qa/corpus/build.gradle.kts's own syncCorpusData task already established for
// data/node-economy-*.json, per this task's own brief ("apply the exact same pattern"). Unlike
// syncCorpusData's include("node-economy-*.json") (flat filenames), the font files sit one
// directory per slug (data/learn-faces/<slug>/<file>.ttf), so the include patterns below are
// twofold: the manifest at the root and every *.ttf two levels down. Several filenames contain
// literal '[' ']' (variable-font tags, e.g. "EBGaramond[wght].ttf") -- Gradle's Ant-style glob
// syntax gives '[' special meaning only *inside a pattern string*, and neither pattern below has
// one, so the brackets in the matched *filenames* are just literal characters and cause no
// surprises here.
val syncLearnFaceData =
    tasks.register<Sync>("syncLearnFaceData") {
        description = "Copies data/learn-faces/manifest.json and its real font files into this module's generated resources."
        from(isolated.rootProject.projectDirectory.dir("data/learn-faces")) {
            include("manifest.json")
            include("*/*.ttf")
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
