// SPDX-License-Identifier: FSL-1.1-ALv2

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("typewright.kmp.platform")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    // P6 (Learn UI half, Scrapbook tab): ScrapbookManifest's own JSON codec needs
    // kotlinx.serialization's @Serializable classes, the same plugin qa/corpus's and
    // learn/scenes's own build.gradle.kts files already apply for their own data packs.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // P4a's sheet camera, room layout and puck gesture machine (com.asoc.typewright.ui.sheet /
    // .puck / .tokens) are plain Kotlin with zero Compose dependency, and expose core-geometry's
    // Vec2 in their own public API, so unlike every other project dependency below, core-geometry
    // is `api`, not `implementation` (matching how engine-construct and core-font already depend
    // on it, for the same reason: their own public API surfaces expose its types too).
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
                api(libs.compose.runtime)
                api(libs.compose.foundation)
                api(libs.compose.ui)
                api(project(":core-geometry"))
                implementation(project(":core-font"))
                implementation(project(":engine-trace"))
                implementation(project(":engine-construct"))
                implementation(project(":qa"))
                implementation(project(":learn:scenes"))
                implementation(project(":campaign"))
                implementation(project(":scripts"))
                implementation(project(":compile"))
                // P11 WP5: the scrapbook model and ProjectSession/ProjectWorkspace (docs/PROJECT_MODEL.md
                // §10, §13). `api`, not `implementation`: app-desktop's and app-web's own `main()`
                // (docs/PROJECT_MODEL.md §13's lifecycle-flush wiring) construct and hold a
                // com.asoc.typewright.project.ProjectWorkspace themselves, outside Compose, so it must be
                // on their own compile classpath too, not just `ui`'s.
                api(project(":project"))
                implementation(libs.kotlinx.serialization.json)
                implementation(project(":shape-preview"))
            }
        }
        named("desktopTest") {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

// The screenshot harness (PlaceholderScreenshotTest and the ones P4+ add) writes PNGs here.
val screenshotDir = layout.buildDirectory.dir("screenshots")
tasks.named<Test>("desktopTest") {
    systemProperty("typewright.screenshotDir", screenshotDir.get().asFile.absolutePath)
    outputs.dir(screenshotDir)
}

// P4a tried `wasmJs { nodejs() }` first, the same way :compile and :shape-preview run their own
// Compose-free tests under Node -- it does not work here, even for a commonTest file (e.g.
// PuckGestureMachineTest) that itself never touches Compose. ui's commonMain already depends on
// Compose (compose-runtime/foundation/ui above), so the wasmJs *test* binary links that in too
// regardless of which test file is actually running, and Skiko boots eagerly at module load:
// under Node this failed with "failed to asynchronously prepare wasm: both async and sync
// fetching of the wasm failed" (reproduced by running `:ui:wasmJsNodeTest`, task P4a) -- exactly
// CMP-4906 (KmpPlatformConventionPlugin's own doc comment), and a module-wide constraint, not one
// P4a's own Compose-free files could route around by writing themselves any differently. Skiko's
// Wasm runtime does load in a real browser, so this module's wasmJs test instead runs the same
// way app-web's already does -- headless Chrome through Karma (`useChromeHeadlessNoSandbox()`,
// reading `CHROME_BIN`) -- confirmed working here: `:ui:wasmJsBrowserTest` runs all 74 commonTest
// cases (P4a's new ones plus the pre-existing PaperTokensTest) for real in Chrome, 0 failures.
tasks.matching { it.name == "checkComposeUiTestConfigurationForWasmJs" }.configureEach {
    enabled = false
}
