// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * P6 (Learn UI half), task item 3: "research and test this directly" whether Compose
 * Multiplatform's wasmJs (browser) target supports loading a font from raw bytes the same way
 * desktop does, in the exact pinned `compose-multiplatform = "1.12.1"`. Runs for real under
 * headless Chrome via `:ui:wasmJsBrowserTest` (this module's own established CMP-4906
 * workaround, `ui/build.gradle.kts`'s own doc comment: Skiko's Wasm runtime loads in a real
 * browser, not under Node, so this module's wasmJs tests run through Karma rather than
 * `wasmJs { nodejs() }`).
 *
 * This test calls [platformLearnFaceFontFamily] directly with [embeddedUnifrakturMaguntiaBytes]
 * (a real face's real bytes, embedded so this test does not also depend on solving the separate,
 * still-open "fetch `data/learn-faces/` into a browser at runtime" problem --
 * [LearnFaceFonts.kt]'s own "Platform status" KDoc covers that split), then *measures real text*
 * with it through [Paragraph]/[createFontFamilyResolver] -- both part of Compose Multiplatform's
 * shared Skiko intermediate source set (`FontFamilyResolver.skiko.kt`, `Paragraph.skiko.kt`),
 * compiled into the wasmJs target the same as the desktop one. Measuring intrinsic width, not
 * merely constructing the [FontFamily] object, is what actually exercises Skia's own font
 * parsing at runtime: a [FontFamily] wrapper can be constructed from garbage bytes without
 * throwing, so only a real layout pass proves the bytes were genuinely usable.
 */
class LearnFaceFontsWasmJsBrowserTest {
    private val sampleWord = "Hamburgefonstiv"
    private val fontSize = 40.sp

    @Test
    fun realFontBytesLoadAndMeasureDifferentlyFromTheDefaultFamily() {
        val resolver = createFontFamilyResolver()
        val density = Density(density = 1f, fontScale = 1f)

        val unifrakturFamily =
            platformLearnFaceFontFamily(
                identity = "wasmjs-browser-test:unifrakturmaguntia",
                bytes = embeddedUnifrakturMaguntiaBytes,
                weight = FontWeight.Normal,
                style = FontStyle.Normal,
            )

        val unifrakturWidth = measureIntrinsicWidth(unifrakturFamily, resolver, density)
        val defaultWidth = measureIntrinsicWidth(FontFamily.Default, resolver, density)

        assertTrue(unifrakturWidth > 0f, "real-bytes font measured zero width -- looks like it failed to load")
        assertTrue(defaultWidth > 0f, "sanity: the platform default font measured zero width")
        assertNotEquals(
            defaultWidth,
            unifrakturWidth,
            "UnifrakturMaguntia's real bytes measured the exact same width as FontFamily.Default -- " +
                "looks like Font(identity, bytes, weight, style) silently fell back rather than loading the real face",
        )
    }

    private fun measureIntrinsicWidth(
        fontFamily: FontFamily,
        resolver: androidx.compose.ui.text.font.FontFamily.Resolver,
        density: Density,
    ): Float =
        Paragraph(
            text = sampleWord,
            style = TextStyle(fontFamily = fontFamily, fontSize = fontSize),
            constraints = Constraints(),
            density = density,
            fontFamilyResolver = resolver,
            spanStyles = emptyList(),
            placeholders = emptyList(),
        ).maxIntrinsicWidth
}
