// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The measure tool's readout and its three-tap state, with no glyph and no session in sight. */
class DrawMeasureTest {
    @Test
    fun aThreeFourFiveTriangleIsFiveUnitsAtItsOwnAngle() {
        val m = Measurement(Vec2(100.0, 200.0), Vec2(130.0, 240.0))
        assertEquals(30.0, m.dx, 1e-9)
        assertEquals(40.0, m.dy, 1e-9)
        assertEquals(50.0, m.distance, 1e-9)
        assertEquals(53.13, m.angleDegrees, 0.01)
        assertEquals("Δx 30 · Δy 40 · 50 units · 53.1°", m.text)
    }

    @Test
    fun anglesRunCounterClockwiseFromTheBaselineAndSignedBelowIt() {
        assertEquals(0.0, Measurement(Vec2(0.0, 0.0), Vec2(10.0, 0.0)).angleDegrees, 1e-9)
        assertEquals(90.0, Measurement(Vec2(0.0, 0.0), Vec2(0.0, 10.0)).angleDegrees, 1e-9)
        assertEquals(-45.0, Measurement(Vec2(0.0, 0.0), Vec2(10.0, -10.0)).angleDegrees, 1e-9)
        assertEquals(180.0, Measurement(Vec2(0.0, 0.0), Vec2(-10.0, 0.0)).angleDegrees, 1e-9)
        assertEquals("Δx 10 · Δy −10 · 14.1 units · −45°", Measurement(Vec2(0.0, 0.0), Vec2(10.0, -10.0)).text)
    }

    @Test
    fun twoPlacesOnOneSpotHaveNoDirectionAndZeroLength() {
        val m = Measurement(Vec2(5.0, 5.0), Vec2(5.0, 5.0))
        assertEquals(0.0, m.distance, 0.0)
        assertEquals(0.0, m.angleDegrees, 0.0)
        assertEquals("Δx 0 · Δy 0 · 0 units · 0°", m.text)
    }

    @Test
    fun tenthsRoundsHalfUpWithoutLocaleAndKeepsTheSignOnlyWhenNonZero() {
        assertEquals("126.5", Measurement.tenths(126.46))
        assertEquals("126.5", Measurement.tenths(126.45))
        assertEquals("120", Measurement.tenths(120.04))
        assertEquals("−0.1", Measurement.tenths(-0.06))
        assertEquals("0", Measurement.tenths(-0.04), "a value that rounds to nothing carries no minus sign")
    }

    @Test
    fun theFirstTapIsOneEndTheSecondIsTheOtherAndTheThirdStartsAgain() {
        val a = Vec2(1.0, 2.0)
        val b = Vec2(11.0, 2.0)
        val c = Vec2(50.0, 60.0)
        val one = MeasureState().tapped(a)
        assertEquals(a, one.first)
        assertNull(one.measurement, "one end is not a reading")
        val two = one.tapped(b)
        assertEquals(10.0, two.measurement?.distance)
        val three = two.tapped(c)
        assertEquals(c, three.first)
        assertNull(three.second)
        assertNull(three.measurement)
    }

    @Test
    fun aFreeTapLandsOnAWholeFontUnit() {
        assertEquals(Vec2(12.0, -4.0), MeasureState.wholeUnit(Vec2(11.6, -3.6)))
    }
}
