// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.sheet

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CubicBezierEasingTest {
    @Test
    fun endpointsAreExact() {
        val easing = CubicBezierEasing.ROOM_PAN
        assertEquals(0.0, easing.transform(0.0))
        assertEquals(1.0, easing.transform(1.0))
    }

    @Test
    fun outOfRangeInputIsClamped() {
        val easing = CubicBezierEasing.ROOM_PAN
        assertEquals(0.0, easing.transform(-5.0))
        assertEquals(1.0, easing.transform(5.0))
    }

    @Test
    fun linearControlPointsReproduceTheIdentity() {
        val linear = CubicBezierEasing(0.0, 0.0, 1.0, 1.0)
        for (t in listOf(0.0, 0.1, 0.3, 0.5, 0.7, 0.9, 1.0)) {
            assertNear(t, linear.transform(t), tolerance = 1e-6)
        }
    }

    // Reference values independently computed (Python, same bisection algorithm) for
    // cubic-bezier(.2, .8, .2, 1) -- UI_SPEC §5 / brief §6's room-pan curve.
    @Test
    fun roomPanMatchesTheCssCubicBezierCurve() {
        val easing = CubicBezierEasing.ROOM_PAN
        assertNear(0.7672843, easing.transform(0.25), tolerance = 1e-5)
        assertNear(0.9460795, easing.transform(0.5), tolerance = 1e-5)
        assertNear(0.9911082, easing.transform(0.75), tolerance = 1e-5)
    }

    @Test
    fun roomPanIsMonotonicallyIncreasing() {
        val easing = CubicBezierEasing.ROOM_PAN
        var previous = easing.transform(0.0)
        var t = 0.0
        while (t <= 1.0) {
            val value = easing.transform(t)
            assertTrue(value >= previous - 1e-9, "eased progress must not go backwards at t=$t")
            previous = value
            t += 0.01
        }
    }

    @Test
    fun roomPanOvershootsPastLinearEarly() {
        // y1 = 0.8 pulls the curve up fast: by the midpoint the eased progress is already well
        // past the halfway point (a strong ease-out), which is the whole point of this curve
        // over a linear pan.
        assertTrue(CubicBezierEasing.ROOM_PAN.transform(0.5) > 0.9)
    }

    private fun assertNear(
        expected: Double,
        actual: Double,
        tolerance: Double,
    ) {
        assertTrue(abs(expected - actual) < tolerance, "expected $expected, was $actual")
    }
}
