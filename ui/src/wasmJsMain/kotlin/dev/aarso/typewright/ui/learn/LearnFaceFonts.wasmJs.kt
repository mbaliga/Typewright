package dev.aarso.typewright.ui.learn

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/**
 * The Font(bytes) API itself works here (see this function's own actual, and
 * `LearnFaceFontsWasmJsBrowserTest`) — `false` because [LearnFaceFonts.kt]'s
 * [learnFaceFontFamily] cannot get real bytes to hand it yet on this target, not because this
 * platform can't render them. See [LearnFaceFonts.kt]'s "Platform status" KDoc.
 */
public actual fun learnFaceFontsAreReal(): Boolean = false

/**
 * The identical call desktop makes (`LearnFaceFonts.desktop.kt`'s own KDoc has the full
 * citation) — `androidx.compose.ui.text.platform.Font(identity, data, weight, style)` is part of
 * Compose Multiplatform's shared Skiko source set (`PlatformFont.skiko.kt`), compiled separately
 * into `ui-text-desktop-1.12.1.jar` *and* `ui-text-wasm-js-1.12.1.klib` — confirmed present in
 * the wasm klib too by inspecting its `package_androidx.compose.ui.text.platform` linkdata
 * directly (the `LoadedFont`/`PlatformFont` symbols, `identity`/`getData` fields, are there) —
 * before writing this file, not assumed from the desktop actual compiling. This function is
 * exercised for real (not just compiled) by
 * `ui/src/wasmJsTest/kotlin/dev/aarso/typewright/ui/learn/LearnFaceFontsWasmJsBrowserTest.kt`
 * under headless Chrome via `:ui:wasmJsBrowserTest`.
 */
internal actual fun platformLearnFaceFontFamily(
    identity: String,
    bytes: ByteArray,
    weight: FontWeight,
    style: FontStyle,
): FontFamily = FontFamily(Font(identity, bytes, weight, style))
