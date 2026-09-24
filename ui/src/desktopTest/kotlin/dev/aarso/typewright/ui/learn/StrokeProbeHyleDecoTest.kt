package dev.aarso.typewright.ui.learn

import dev.aarso.typewright.core.font.sfnt.readSfntFont
import dev.aarso.typewright.qa.corpus.style.contrastRatio
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * [strokeProbe] against `fonts/HyleDeco-Regular.ttf` (this build's own real project-font
 * fixture, the same file [dev.aarso.typewright.ui.learn.AnatomyLensDataHyleDecoTest] already
 * reads this same way -- JVM-only (`desktopTest`) for the same reason that file is: reading an
 * arbitrary file off disk during a Gradle test run). Every number below was independently
 * cross-checked against the font's own raw `glyf` points with fontTools while building this
 * file (not derived from, or copied out of, this file's own or `StrokeProbe.kt`'s own code) --
 * see [StrokeProbe.kt]'s own top-level KDoc for the full method.
 */
class StrokeProbeHyleDecoTest {
    private val font by lazy { readSfntFont(File("../fonts/HyleDeco-Regular.ttf").readBytes()) }

    private val b get() = font.glyphForCodePoint('b'.code)
    private val h get() = font.glyphForCodePoint('H'.code)
    private val o get() = font.glyphForCodePoint('o'.code)
    private val n get() = font.glyphForCodePoint('n'.code)
    private val m get() = font.glyphForCodePoint('m'.code)
    private val u get() = font.glyphForCodePoint('u'.code)
    private val r get() = font.glyphForCodePoint('r'.code)

    @Test
    fun bsOwnLeftStemIsExactly44Units() {
        val stem = stemWidth(assertNotNull(b))
        assertEquals(44.0, stem)
    }

    @Test
    fun everyOtherStemBearingLowercaseLetterInHamburgAgreesAt44Units() {
        // Real quirk of this fixture worth pinning: every one of n/m/u/r's own left stem also
        // measures exactly 44.0 -- Hyle Deco is a monoline-stemmed design (matches
        // ui/typewright-explorer.html's own Lens-tab illustration, "Its width, 44 units here").
        for (glyph in listOf(n, m, u, r)) {
            assertEquals(44.0, stemWidth(assertNotNull(glyph)), "stem width for '${glyph!!.name}'")
        }
    }

    @Test
    fun hsOwnCrossbarIsExactly43Units() {
        val bar = barWidth(assertNotNull(h))
        assertEquals(43.0, bar)
    }

    @Test
    fun strokeProbeMatchesTheIndividualFunctionsAndContrastRatioDirectly() {
        val reading = strokeProbe(b, h, o)
        assertEqualsNotNull(44.0, reading.stemUnits)
        assertEqualsNotNull(43.0, reading.barUnits)
        assertEquals(contrastRatio(assertNotNull(o)), reading.contrast)
        // Pinned from AnatomyLensDataHyleDecoTest's own already-established real value.
        assertEquals(1.2513158653716447, reading.contrast!!, 1e-9)
    }

    @Test
    fun strokeProbeReturnsNullFieldsForMissingGlyphsRatherThanGuessing() {
        val reading = strokeProbe(stemGlyph = null, barGlyph = null, oGlyph = null)
        assertEquals(null, reading.stemUnits)
        assertEquals(null, reading.barUnits)
        assertEquals(null, reading.contrast)
    }
}

private fun assertEqualsNotNull(
    expected: Double,
    actual: Double?,
) {
    assertNotNull(actual)
    assertEquals(expected, actual, 1e-9)
}
