plugins {
    id("typewright.kmp.platform")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.compose.runtime)
                api(libs.compose.foundation)
                api(libs.compose.ui)
                implementation(project(":core-geometry"))
                implementation(project(":core-font"))
                implementation(project(":engine-trace"))
                implementation(project(":engine-construct"))
                implementation(project(":qa"))
                implementation(project(":learn:scenes"))
                implementation(project(":campaign"))
                implementation(project(":scripts"))
                implementation(project(":compile"))
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

// ui runs no Wasm tests: Skiko's Wasm runtime cannot load under Node, and browser test runs are
// off (see KmpPlatformConventionPlugin). Compose's guard for Wasm UI tests hangs off every
// KotlinJsTest task, disabled ones included, so switch the guard off here (it is registered
// after this script runs, hence `matching`). app-web runs its Wasm test in headless Chrome.
tasks.matching { it.name == "checkComposeUiTestConfigurationForWasmJs" }.configureEach {
    enabled = false
}
