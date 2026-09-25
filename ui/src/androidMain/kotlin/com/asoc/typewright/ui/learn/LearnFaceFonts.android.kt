// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import java.nio.ByteBuffer
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily

/**
 * Android is Compose's real Jetpack androidx target here (`gradle/libs.versions.toml`'s
 * `compose-multiplatform = "1.12.1"` resolves Android to the real
 * `androidx.compose.ui:ui-text-android:1.12.1` `.aar`, confirmed by extracting and `javap`-ing
 * its `classes.jar` directly), not a Skiko/Compose-Multiplatform reimplementation — so it does
 * **not** carry `androidx.compose.ui.text.platform.Font(identity, data, weight, style)` at all
 * (verified absent from that jar by listing every `*Font*` class in it; this task's own brief
 * assumed Android "follows the same Skia-backed path as desktop", and that assumption is false,
 * found by checking rather than by reading a changelog). Android's own byte-loading path below is
 * a custom [AndroidFont] subclass plus an [AndroidFont.TypefaceLoader] that hands back a real
 * [Typeface] built from [android.graphics.fonts.Font.Builder]'s direct [ByteBuffer] constructor
 * (API 26+; this repo's own `minSdk` is 31, `docs/OPEN_QUESTIONS.md` item 4, so no version guard
 * is needed) wrapped in [PlatformFontFamily] and [Typeface.CustomFallbackBuilder] (API 29+, also
 * covered by `minSdk` 31). This is the shape Jetpack Compose's own public `AndroidFont`/
 * `TypefaceLoader` API exists for: a font source Compose has no built-in `Font(...)` overload
 * for.
 *
 * Compiles for real — `:ui:compileAndroidMain`, run offline against `/opt/android-sdk`'s
 * `android-37` platform, as part of this task's own verification. **Not** run on a device or
 * emulator: this container has neither (CLAUDE.md law 4). This file is real code, not a stub —
 * its on-device behaviour is simply owner-verified only, exactly like every other Android-only
 * path in this repository.
 */
public actual fun learnFaceFontsAreReal(): Boolean = true

internal actual fun platformLearnFaceFontFamily(
    identity: String,
    bytes: ByteArray,
    weight: FontWeight,
    style: FontStyle,
): FontFamily = FontFamily(LearnFaceAndroidFont(identity = identity, bytes = bytes, weight = weight, style = style))

/** One face's real bytes, as an [AndroidFont] Compose's Android [androidx.compose.ui.text.font.FontFamily.Resolver] can resolve through [LearnFaceTypefaceLoader]. */
private class LearnFaceAndroidFont(
    val identity: String,
    val bytes: ByteArray,
    override val weight: FontWeight,
    override val style: FontStyle,
) : AndroidFont(
        loadingStrategy = FontLoadingStrategy.Blocking,
        typefaceLoader = LearnFaceTypefaceLoader,
        // The 2-arg super constructor is deprecated in this pinned version ("Replaced with
        // fontVariation constructor"); passing empty settings explicitly uses the real one.
        variationSettings = FontVariation.Settings(),
    ) {
    // Compose's typeface cache keys on equals/hashCode (mirrors desktop's PlatformFont, whose own
    // "identity" is its cache key too — see LearnFaceFonts.desktop.kt) so the same face isn't
    // rebuilt from bytes on every recomposition.
    override fun equals(other: Any?): Boolean =
        other is LearnFaceAndroidFont && other.identity == identity && other.weight == weight && other.style == style

    override fun hashCode(): Int = (identity.hashCode() * 31 + weight.hashCode()) * 31 + style.hashCode()

    override fun toString(): String = "LearnFaceAndroidFont($identity, weight=$weight, style=$style, ${bytes.size} bytes)"
}

/** [FontLoadingStrategy.Blocking] above means Compose only ever calls [loadBlocking], never [awaitLoad]; the latter delegates to the former so both paths stay correct if that ever changes. */
private object LearnFaceTypefaceLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(
        context: Context,
        font: AndroidFont,
    ): Typeface {
        val bytes = (font as LearnFaceAndroidFont).bytes
        val buffer =
            ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                rewind()
            }
        val platformFont = PlatformFont.Builder(buffer).build()
        val platformFamily = PlatformFontFamily.Builder(platformFont).build()
        return Typeface.CustomFallbackBuilder(platformFamily).build()
    }

    override suspend fun awaitLoad(
        context: Context,
        font: AndroidFont,
    ): Typeface = loadBlocking(context, font)
}
