package dev.aarso.typewright.core.geometry

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SegmentStraightnessTest {
    @Test
    fun onLineDegenerateControlsAreStraight() {
        // Both control points sit exactly on the chord from (0,0) to (90,0): T/H's own "on-line
        // degenerate" representation of a plain polygon side (see buildCubicContour's KDoc).
        val segment = CurveSegment.Cubic(Vec2(0.0, 0.0), Vec2(30.0, 0.0), Vec2(60.0, 0.0), Vec2(90.0, 0.0))
        assertTrue(segment.isEffectivelyStraight())
    }

    @Test
    fun aRealQuarterArcIsNotStraight() {
        // A cubic approximation of a quarter circle of radius 100, standard kappa handle length.
        val kappa = 0.5522847498
        val segment = CurveSegment.Cubic(Vec2(100.0, 0.0), Vec2(100.0, 100.0 * kappa), Vec2(100.0 * kappa, 100.0), Vec2(0.0, 100.0))
        assertFalse(segment.isEffectivelyStraight())
    }

    @Test
    fun aTinyBulgeWithinToleranceCountsAsStraight() {
        val segment = CurveSegment.Cubic(Vec2(0.0, 0.0), Vec2(30.0, 1.0), Vec2(60.0, 1.0), Vec2(90.0, 0.0))
        assertTrue(segment.isEffectivelyStraight(toleranceUnits = 1.5))
        assertFalse(segment.isEffectivelyStraight(toleranceUnits = 0.5))
    }

    @Test
    fun aZeroLengthChordIsStraightByDefinition() {
        val segment = CurveSegment.Cubic(Vec2(5.0, 5.0), Vec2(6.0, 7.0), Vec2(4.0, 3.0), Vec2(5.0, 5.0))
        assertTrue(segment.isEffectivelyStraight())
    }
}
