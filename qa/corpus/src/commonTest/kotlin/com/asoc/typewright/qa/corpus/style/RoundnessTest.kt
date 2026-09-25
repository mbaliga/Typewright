// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus.style

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoundnessTest {
    @Test
    fun perfectCircleFitsCloseToExponentTwo() {
        // Brief 8.4's own example: "a hand-built perfect circle 'o' should score... superellipse
        // exponent ~2.0".
        val o = circleRingGlyph()
        val n = assertNotNull(superellipseExponent(o))
        assertTrue(n in 1.6..2.6, "exponent was $n, expected close to 2.0")
    }

    @Test
    fun squarishORequiresAHigherExponentThanACircle() {
        // "a hand-built square-ish 'o' should score a higher exponent" (brief 8.4).
        val circleN = assertNotNull(superellipseExponent(circleRingGlyph()))
        val squarishN = assertNotNull(superellipseExponent(squarishRingGlyph()))
        assertTrue(squarishN > circleN + 0.8, "squarish exponent ($squarishN) should be well above the circle's ($circleN)")
    }

    @Test
    fun contourlessGlyphIsNull() {
        val empty =
            com.asoc.typewright.core.geometry
                .Glyph("o", 500, emptyList())
        assertNull(superellipseExponent(empty))
    }
}
