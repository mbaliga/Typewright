// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.qa.corpus.style

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SerifBracketTest {
    @Test
    fun sansStemHasNoSerif() {
        val metrics = assertNotNull(serifMetrics(sansTGlyph()))
        assertFalse(metrics.hasSerif)
        assertTrue(metrics.flareRatio in 0.95..1.05, "flareRatio was ${metrics.flareRatio}")
        assertNull(metrics.bracketScore)
    }

    @Test
    fun gradualFootReadsAsBracketed() {
        val t = serifTGlyph(gradualSerifProfile(overhang = 70, rise = 120))
        val metrics = assertNotNull(serifMetrics(t))
        assertTrue(metrics.hasSerif)
        val bracket = assertNotNull(metrics.bracketScore)
        assertTrue(bracket > 0.5, "bracketScore was $bracket, expected a smooth, well-bracketed foot")
    }

    @Test
    fun abruptFootReadsAsUnbracketed() {
        val t = serifTGlyph(abruptSerifProfile(overhang = 70, rise = 120))
        val metrics = assertNotNull(serifMetrics(t))
        assertTrue(metrics.hasSerif)
        val bracket = assertNotNull(metrics.bracketScore)
        assertTrue(bracket < 0.3, "bracketScore was $bracket, expected a near-step, unbracketed foot")
    }

    @Test
    fun gradualFootIsMoreBracketedThanAnAbruptOne() {
        val gradual = assertNotNull(serifMetrics(serifTGlyph(gradualSerifProfile(70, 120)))?.bracketScore)
        val abrupt = assertNotNull(serifMetrics(serifTGlyph(abruptSerifProfile(70, 120)))?.bracketScore)
        assertTrue(gradual > abrupt, "gradual ($gradual) should score higher than abrupt ($abrupt)")
    }

    @Test
    fun serifMetricsIsNullForAContourlessGlyph() {
        val empty =
            dev.aarso.typewright.core.geometry
                .Glyph("T", 500, emptyList())
        assertNull(serifMetrics(empty))
    }
}
