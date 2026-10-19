// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.project.Ethos
import com.asoc.typewright.qa.corpus.style.StyleMeasurement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DriftTest {
    private val model =
        resolveBrief(Ethos(levels = mapOf("contrast" to "even", "serifs" to "none", "waist" to "middle")), FIXTURE_ATLAS)

    private fun drawing(
        contrast: Double?,
        serif: Boolean?,
    ) = Drawing(FontView.EMPTY, StyleMeasurement.EMPTY.copy(contrastRatio = contrast, hasSerif = serif))

    private fun item(
        d: Drawing,
        feature: Feature,
    ) = drift(model, d).single { it.feature == feature }

    @Test
    fun insideTheRangeIsOnTarget() {
        val c = item(drawing(1.1, false), Feature.CONTRAST)
        assertEquals(DriftStatus.ON, c.status)
        assertEquals("1.1 : 1", c.measured)
        assertNull(c.advice)
    }

    @Test
    fun aLittleOutsideIsCloseAndFarOutsideIsOff() {
        // The even band ends at 1.19; the corpus's middle half spans ln(2.4 / 1.1) = 0.78.
        assertEquals(DriftStatus.NEAR, item(drawing(1.6, false), Feature.CONTRAST).status)
        val off = item(drawing(3.0, false), Feature.CONTRAST)
        assertEquals(DriftStatus.OFF, off.status)
        assertTrue(assertNotNull(off.advice).startsWith("Thicken the thin parts of o"), off.advice)
    }

    @Test
    fun aCategoricalMismatchIsOffWithItsReason() {
        val serif = item(drawing(1.1, true), Feature.SERIF)
        assertEquals(DriftStatus.OFF, serif.status)
        assertEquals("Your T has serifs; the brief is sans-serif.", serif.advice)
    }

    @Test
    fun nothingIsJudgedBeforeItIsDrawn() {
        val waist = item(drawing(null, null), Feature.WAIST)
        assertEquals(DriftStatus.UNMEASURED, waist.status)
        assertEquals("Draw H to measure it.", waist.advice)
        assertEquals(
            "Draw c, e and s to measure it.",
            drift(resolveBrief(Ethos(levels = mapOf("aperture" to "open")), FIXTURE_ATLAS), Drawing.EMPTY).single().advice,
        )
    }

    @Test
    fun widthWaitsForMostOfTheStyleLetters() {
        val wide = resolveBrief(Ethos(levels = mapOf("width" to "wide")), FIXTURE_ATLAS)
        val measured = Drawing(FontView(1000, mapOf('o' to ringGlyph("o", 900, 800, 60))), StyleMeasurement.EMPTY.copy(widthClass = 0.9))
        assertEquals(DriftStatus.UNMEASURED, drift(wide, measured).single().status)
    }
}
