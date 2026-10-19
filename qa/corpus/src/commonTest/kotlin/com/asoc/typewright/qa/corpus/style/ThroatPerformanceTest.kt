// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus.style

import com.asoc.typewright.core.geometry.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class ThroatPerformanceTest {
    @Test
    fun flattenedInkAgreesWithTheRayCastAwayFromTheOutline() {
        // Points well inside the stroke, well inside the counter and well outside the glyph. The
        // flattened test and Glyph.isInkAt must agree on every one of them.
        val ring = circleRingGlyph(outerRadius = 250.0, innerRadius = 170.0)
        val ink = FlattenedInk(ring)
        val probes =
            listOf(
                Vec2(210.0, 0.0),
                Vec2(0.0, -210.0),
                Vec2(-150.0, 150.0),
                Vec2(0.0, 0.0),
                Vec2(60.0, 60.0),
                Vec2(400.0, 0.0),
                Vec2(-300.0, -300.0),
            )
        for (p in probes) assertEquals(ring.isInkAt(p), ink.contains(p), "disagreement at $p")
    }

    @Test
    fun aDensePolygonalCStillFindsItsApertureQuickly() {
        // A traced outline: every arc broken into hundreds of short straight segments, the shape a
        // polygonised font (or a raw trace) hands the detector. The throat search must stay
        // bounded and still read the gap.
        val dense = straightCutCGlyph(gapHalfAngleDegrees = 20.0, arcSteps = 400)
        val start = TimeSource.Monotonic.markNow()
        val openness = assertNotNull(apertureOpenness(dense, 500.0))
        val elapsed = start.elapsedNow()
        assertTrue(elapsed.inWholeMilliseconds < 5_000, "aperture on a dense outline took $elapsed")
        val coarse = assertNotNull(apertureOpenness(straightCutCGlyph(gapHalfAngleDegrees = 20.0), 500.0))
        assertTrue(kotlin.math.abs(openness - coarse) < 0.05, "dense ($openness) and coarse ($coarse) readings of the same c differ")
    }
}
