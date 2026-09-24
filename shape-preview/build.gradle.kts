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
        val desktopMain by getting {
            dependencies {
                // The real desktop shaper (SkikoShaper.kt, P8): org.jetbrains.skia.shaper.Shaper.
                // API jar only -- app-desktop supplies the matching native runtime at the app
                // level via `compose.desktop.currentOs` (ui/app-desktop already depend on this
                // module and resolve to the same 0.150.1), so main does not need it duplicated.
                implementation(libs.skiko.awt)
            }
        }
        val desktopTest by getting {
            dependencies {
                // This module applies no Compose plugin, so unlike `ui`'s desktopTest
                // (`compose.desktop.currentOs`) there is no automatic native runtime here --
                // add it directly so SkikoShaperTest can actually load and shape a real font.
                runtimeOnly(libs.skiko.awt.runtime.linux.x64)
            }
        }
    }
}
