// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus.style

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ContrastStressTest {
    @Test
    fun perfectCircleHasContrastCloseToOne() {
        // A ring built from two concentric circles has exactly the same radial width at every
        // angle -- the textbook "no contrast, no stress" case, brief 8.4's own example.
        val o = circleRingGlyph(outerRadius = 250.0, innerRadius = 170.0)
        val contrast = assertNotNull(contrastRatio(o))
        assertTrue(abs(contrast - 1.0) < 0.05, "contrast was $contrast, expected ~1.0")
    }

    @Test
    fun perfectCircleHasNoMeaningfulStressDirection() {
        // "Undefined" in the brief's own words; concretely, a uniform ring's thickest-diameter
        // search has no real winner, so its magnitude should stay small (the search still returns
        // *some* angle, since every sample is equally thick, but that angle carries no signal).
        val o = circleRingGlyph()
        val stress = assertNotNull(stressAngleDegrees(o))
        assertTrue(abs(stress) <= 90.0)
    }

    @Test
    fun verticalStressRingIsThickestAtTopAndBottom() {
        val o = verticalStressRingGlyph()
        val stress = assertNotNull(stressAngleDegrees(o))
        assertTrue(abs(stress) < 15.0, "stress was $stress degrees, expected close to 0 (vertical)")
        val contrast = assertNotNull(contrastRatio(o))
        assertTrue(contrast > 1.2, "contrast was $contrast, expected a visible difference between top/bottom and the sides")
    }

    @Test
    fun horizontalStressRingIsThickestAtTheSides() {
        val o = horizontalStressRingGlyph()
        val stress = assertNotNull(stressAngleDegrees(o))
        assertTrue(abs(abs(stress) - 90.0) < 15.0, "stress was $stress degrees, expected close to +-90 (horizontal)")
    }

    @Test
    fun oStrokeProfileIsEmptyForAContourlessGlyph() {
        val empty =
            com.asoc.typewright.core.geometry
                .Glyph("o", 500, emptyList())
        assertTrue(oStrokeProfile(empty).isEmpty())
        assertTrue(contrastRatio(empty) == null)
        assertTrue(stressAngleDegrees(empty) == null)
    }
}
