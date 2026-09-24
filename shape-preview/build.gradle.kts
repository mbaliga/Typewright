import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("typewright.kmp.platform")
}

kotlin {
    // No Compose here, so the common tests (ShaperStubTest, etc.) also run on Wasm under Node.
    // P9 adds a *second* wasmJs runtime, browser via Karma, only for BrowserFontFaceVisualPreview
    // (its own KDoc): that file is real DOM code (FontFace, canvas) that cannot run under Node at
    // all, so it needs the same headless-Chrome route `ui/build.gradle.kts` already established
    // for the identical CMP-4906-adjacent reason (Skiko/DOM needs a real browser). Both runtimes
    // share one wasmJsTest compilation, so BrowserFontFaceVisualPreviewBrowserTest skips itself
    // under wasmJsNodeTest by checking for a real `document` first (its own KDoc) rather than
    // failing wasmJsNodeTest, which must stay green.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
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
        val wasmJsMain by getting {
            dependencies {
                // BrowserFontFaceVisualPreview.kt (P9): typed Canvas/Document bindings, and
                // Promise<T>.await() for the one real async step (FontFace.load()). Both already
                // resolve elsewhere in this build's own dependency graph (gradle/libs.versions.toml
                // comment on these aliases has the full citation) -- pinned and declared directly
                // here for the same reason `skiko-awt` above already is.
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.browser)
            }
        }
    }
}
