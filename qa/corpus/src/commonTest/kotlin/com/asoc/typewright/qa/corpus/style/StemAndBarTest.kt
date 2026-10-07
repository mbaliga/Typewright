// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus.style

import com.asoc.typewright.core.geometry.Glyph
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StemAndBarTest {
    /** An H drawn as one outline: stems [stem] wide, a bar [bar] tall centred at [barCentre], 700 tall. */
    private fun hGlyph(
        stem: Int = 90,
        bar: Int = 70,
        barCentre: Int = 350,
        width: Int = 560,
    ): Glyph {
        val top = 700
        val barLo = barCentre - bar / 2
        val barHi = barCentre + bar / 2
        return Glyph(
            "H",
            width + 80,
            listOf(
                polygon(
                    0 to 0,
                    stem to 0,
                    stem to barLo,
                    width - stem to barLo,
                    width - stem to 0,
                    width to 0,
                    width to top,
                    width - stem to top,
                    width - stem to barHi,
                    stem to barHi,
                    stem to top,
                    0 to top,
                ),
            ),
        )
    }

    @Test
    fun stemRatioIsTheStemOverTheHeight() {
        val ratio = assertNotNull(stemToHeightRatio(hGlyph(stem = 90)))
        assertTrue(abs(ratio - 90.0 / 700.0) < 1e-6, "ratio was $ratio")
    }

    @Test
    fun aLowBarDoesNotReadAsOneWideStem() {
        // The bar sits across the 0.18 probe; that probe sees one run and is ignored.
        val ratio = assertNotNull(stemToHeightRatio(hGlyph(stem = 80, barCentre = 126, bar = 60)))
        assertTrue(abs(ratio - 80.0 / 700.0) < 1e-6, "ratio was $ratio")
    }

    @Test
    fun crossbarHeightFollowsTheBar() {
        val middle = assertNotNull(crossbarHeightRatio(hGlyph(barCentre = 350)))
        val high = assertNotNull(crossbarHeightRatio(hGlyph(barCentre = 480)))
        val low = assertNotNull(crossbarHeightRatio(hGlyph(barCentre = 210)))
        assertEquals(0.5, middle, 1e-6)
        assertTrue(high > 0.65 && low < 0.35, "high $high, low $low")
    }

    @Test
    fun aGlyphWithoutTwoStemsHasNoStemRatio() {
        val ring = circleRingGlyph()
        // A ring's horizontal probes do cross two runs, so use a single bar instead.
        val bar = Glyph("I", 200, listOf(polygon(0 to 0, 80 to 0, 80 to 700, 0 to 700)))
        assertNull(stemToHeightRatio(bar))
        assertNotNull(stemToHeightRatio(ring))
    }

    @Test
    fun measureStyleReadsStemAndBarFromH() {
        val m = measureStyle(mapOf('H' to hGlyph(stem = 100, barCentre = 420)), unitsPerEm = 1000)
        assertTrue(abs(assertNotNull(m.stemToCapHeight) - 100.0 / 700.0) < 1e-6)
        assertEquals(0.6, assertNotNull(m.crossbarHeight), 1e-6)
        assertNull(m.contrastRatio)
    }

    @Test
    fun anEmptyMapMeasuresNothing() {
        assertTrue(measureStyle(emptyMap(), unitsPerEm = 1000).isEmpty)
        assertTrue(StyleMeasurement.EMPTY.isEmpty)
    }
}
