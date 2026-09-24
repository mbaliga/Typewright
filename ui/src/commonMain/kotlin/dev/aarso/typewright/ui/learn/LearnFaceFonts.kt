package dev.aarso.typewright.ui.learn

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import dev.aarso.typewright.learn.scenes.LearnFaceResources

/**
 * Real font rendering for the Learn screen's Lineages tab (`ui/typewright-explorer.html`'s
 * `#s-learn` / `#ln-lin`; `docs/LESSONS_SCAFFOLD.md` section 1: "every example is a real font
 * loaded at build time"). This is the platform *text*-rendering path, not
 * `GeometryInterop.kt`'s outline-to-Compose-path bridge — that bridge draws this app's own
 * traced/constructed geometry (the Draw sheet's ink, and the Overlay tab's colour+line-pattern
 * comparison layers), where the caller needs the raw contour. Here the caller wants the finished,
 * already-hinted, already-shaped rendering of a real OFL/Apache typeface exactly the way its own
 * designer built it — [FontFamily] is the correct, simpler tool for that, and this file's whole
 * job is getting a real face's own bytes into one.
 *
 * [learnFaceFontFamily] takes a face key the same way [dev.aarso.typewright.learn.scenes.Scene]'s
 * own `FaceRef.key` and `data/learn-faces/manifest.json`'s top-level keys do (`"garalde"`,
 * `"transitional"`, …) rather than a family name, so a Lineages scene's [FaceRef] can be passed
 * straight through without a lookup step of its own.
 *
 * **Platform status**, confirmed for real against the exact Compose Multiplatform version pinned
 * in `gradle/libs.versions.toml` (`compose-multiplatform = "1.12.1"`), not assumed:
 * - **desktop (jvm)**: real. `androidx.compose.ui.text.platform.Font(identity, data, weight,
 *   style)` — verified against the `ui-text-desktop-1.12.1.jar` classfiles directly (its
 *   `PlatformFont.skiko.kt`/`LoadedFont` class) — loads the actual bytes; see
 *   `ui/src/desktopTest/kotlin/dev/aarso/typewright/ui/learn/LearnFaceFontsScreenshotTest.kt`
 *   for a real screenshot of `BasicText` set in three of the seventeen real faces.
 * - **android**: real, but by a genuinely different mechanism than desktop, not "the same
 *   Skia-backed path" (an assumption this task's own brief made and this file's own investigation
 *   found false: `androidx.compose.ui.text.platform.Font` does not exist in the real
 *   `androidx.compose.ui:ui-text-android:1.12.1` `.aar` at all — verified by extracting and
 *   `javap`-ing its `classes.jar`, not by reading changelogs). Android's Compose target uses the
 *   platform's own `android.graphics.Typeface`/`android.graphics.fonts.Font`, not Skiko directly,
 *   so loading arbitrary bytes there needs a custom `AndroidFont` +
 *   `AndroidFont.TypefaceLoader` — see `LearnFaceFonts.android.kt`'s own KDoc for the real
 *   implementation and its citation. Compiles for real (`:ui:compileAndroidMain`); unverified
 *   on-device, per CLAUDE.md law 4 — this container has no phone and no emulator.
 * - **wasmJs (browser)**: the underlying Compose text-rendering API genuinely does support this
 *   — `LearnFaceFonts.wasmJs.kt` calls the identical `androidx.compose.ui.text.platform.Font`
 *   call desktop does, and `ui/src/wasmJsTest/kotlin/.../LearnFaceFontsWasmJsBrowserTest.kt`
 *   proves it at runtime in headless Chrome (real text measured with a real embedded font,
 *   through `:ui:wasmJsBrowserTest`, this module's own established CMP-4906 workaround). What is
 *   **not** built here is fetching the `.ttf` bytes under `data/learn-faces/` into a browser at runtime:
 *   [LearnFaceResources]'s wasmJs actual reads via Node's `fs`
 *   (`learn:scenes`'s own `KmpPureConventionPlugin` target, `wasmJs { nodejs() }`), which does
 *   not exist in a browser (`ReferenceError: process is not defined`) — the same fork in the road
 *   `docs/OPEN_QUESTIONS.md` items 8 and 33 already disclosed for corpus data and left for "the
 *   P6 UI task" to decide. [learnFaceFontFamily] therefore catches that failure and falls back to
 *   [FontFamily.Default] on wasmJs today, rather than crashing — see [learnFaceFontsAreReal] for
 *   a flag a caller can use to show a small, honest note instead of silently substituting a
 *   different typeface. A new `docs/OPEN_QUESTIONS.md` entry records the follow-up (fetching the
 *   bytes over HTTP in a browser, or bundling them through Compose's own resources mechanism).
 */
public fun learnFaceFontFamily(
    key: String,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
): FontFamily {
    // LineagesTab's own P6 task found this the hard way: LearnFaceResources.entry(key) reads
    // `data/learn-faces/manifest.json` through the exact same readSceneResourceText Node-`fs`
    // path fontBytes()'s own read below already needs runCatching for (this file's own
    // "Platform status" KDoc, wasmJs/browser) -- unguarded, this line threw uncaught on that
    // target the first time a real composable (not just platformLearnFaceFontFamily directly,
    // which every existing test here calls) reached it, contradicting this function's own
    // documented "falls back to FontFamily.Default ... rather than crashing" contract above.
    // One-line fix, same fallback the very next line already uses.
    val entry = runCatching { LearnFaceResources.entry(key) }.getOrNull() ?: return FontFamily.Default
    val bytes = runCatching { LearnFaceResources.fontBytes(key) }.getOrNull() ?: return FontFamily.Default
    return runCatching {
        platformLearnFaceFontFamily(identity = "typewright-learn-face:$key", bytes = bytes, weight = weight, style = style)
    }.getOrDefault(FontFamily.Default)
}

/**
 * True when [learnFaceFontFamily] on *this* platform actually loads a face's own real bytes;
 * false when every call falls back to [FontFamily.Default]. Today: `true` on desktop and
 * android, `false` on wasmJs (browser) — see [learnFaceFontFamily]'s own KDoc, "Platform status",
 * for the full, verified-not-assumed reasoning behind each. A caller that wants to show the
 * Lineages tab's "not yet supported on this target" note (rather than silently rendering the
 * wrong face) reads this once, rather than trying to infer it from whether a particular
 * [learnFaceFontFamily] call happened to fall back.
 */
public expect fun learnFaceFontsAreReal(): Boolean

/**
 * The actual per-platform font-loading call [learnFaceFontFamily] delegates to once it has real
 * bytes in hand. `internal` because callers only ever need [learnFaceFontFamily]; this is exposed
 * as its own `expect`/`actual` function (rather than inlined into [learnFaceFontFamily] with a
 * `when`) so each platform's implementation — and its own citation of the real API it calls — is
 * one small, independently readable file, the same shape `readSceneResourceText`/
 * `readLearnFaceResourceBytes` already use in `learn:scenes`.
 */
internal expect fun platformLearnFaceFontFamily(
    identity: String,
    bytes: ByteArray,
    weight: FontWeight,
    style: FontStyle,
): FontFamily
