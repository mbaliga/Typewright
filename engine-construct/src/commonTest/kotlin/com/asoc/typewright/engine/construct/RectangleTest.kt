// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals

/** [RectanglePrimitive]'s two entry methods and [RoundedRectanglePrimitive]'s three. */
class RectangleTest {
    @Test
    fun twoCornersGivesTheExactBoundingBoxRegardlessOfCornerOrder() {
        val a = RectanglePrimitive.TwoCorners(Point(10, 10), Point(50, 40)).realize()
        val b = RectanglePrimitive.TwoCorners(Point(50, 10), Point(10, 40)).realize()
        for (box in listOf(a.boundingBox(), b.boundingBox())) {
            assertApprox(10.0, box.minX)
            assertApprox(10.0, box.minY)
            assertApprox(50.0, box.maxX)
            assertApprox(40.0, box.maxY)
        }
    }

    @Test
    fun centreSizeGivesTheExactBoundingBox() {
        val box = RectanglePrimitive.CentreSize(Point(0, 0), width = 100.0, height = 60.0).realize().boundingBox()
        assertApprox(-50.0, box.minX)
        assertApprox(-30.0, box.minY)
        assertApprox(50.0, box.maxX)
        assertApprox(30.0, box.maxY)
    }

    @Test
    fun roundedRectangleTwoCornersKeepsTheSameOuterBoundingBoxAsThePlainRectangle() {
        val plain = RectanglePrimitive.TwoCorners(Point(0, 0), Point(100, 60)).realize().boundingBox()
        val rounded = RoundedRectanglePrimitive.TwoCorners(Point(0, 0), Point(100, 60), radius = 15.0).realize().boundingBox()
        assertApprox(plain.minX, rounded.minX, tolerance = 0.5)
        assertApprox(plain.minY, rounded.minY, tolerance = 0.5)
        assertApprox(plain.maxX, rounded.maxX, tolerance = 0.5)
        assertApprox(plain.maxY, rounded.maxY, tolerance = 0.5)
    }

    @Test
    fun roundedRectangleWithZeroRadiusMatchesThePlainRectangleShape() {
        val plain = RectanglePrimitive.TwoCorners(Point(0, 0), Point(40, 30)).realize()
        val rounded = RoundedRectanglePrimitive.TwoCorners(Point(0, 0), Point(40, 30), radius = 0.0).realize()
        assertApprox(plain.boundingBox().maxX, rounded.boundingBox().maxX)
        assertApprox(plain.boundingBox().maxY, rounded.boundingBox().maxY)
    }

    @Test
    fun perCornerRadiusLeavesAZeroRadiusCornerSharp() {
        // Only the bottom-left corner gets a radius; the other three should sit exactly at the box's
        // own sharp corners.
        val contour = RoundedRectanglePrimitive.PerCornerRadius(Point(0, 0), Point(100, 100), 0.0, 0.0, 0.0, 20.0).realize()
        val corners = listOf(Point(100, 0), Point(100, 100), Point(0, 100))
        val onCurvePoints = contour.points.filter { it.onCurve }.map { it.point }
        for (corner in corners) {
            kotlin.test.assertTrue(onCurvePoints.contains(corner), "sharp corner $corner should be an exact on-curve point")
        }
        // The rounded corner's own arc must stay strictly inside the box near (0, 0) -- never touch it.
        kotlin.test.assertTrue(!onCurvePoints.contains(Point(0, 0)), "the rounded corner must not include the sharp corner point itself")
    }

    @Test
    fun cornerRadiiThatOverflowAreScaledDownProportionally() {
        // Two 60-radius corners on a 100-wide edge would overlap (120 > 100); clampCornerRadii must
        // scale every radius down by the same factor (the CSS corner-overflow algorithm) rather than
        // independently clipping just the offending pair.
        val clamped =
            clampCornerRadii(width = 100.0, height = 100.0, topLeft = 60.0, topRight = 60.0, bottomRight = 10.0, bottomLeft = 10.0)
        assertApprox(50.0, clamped[0], tolerance = 1e-9)
        assertApprox(50.0, clamped[1], tolerance = 1e-9)
        // The two smaller corners are scaled by the exact same factor (5/6), not left untouched.
        assertApprox(10.0 * (100.0 / 120.0), clamped[2], tolerance = 1e-9)
        assertApprox(10.0 * (100.0 / 120.0), clamped[3], tolerance = 1e-9)
    }

    @Test
    fun cornerRadiiThatFitAreLeftUnchanged() {
        val clamped =
            clampCornerRadii(width = 100.0, height = 100.0, topLeft = 10.0, topRight = 10.0, bottomRight = 10.0, bottomLeft = 10.0)
        assertEquals(listOf(10.0, 10.0, 10.0, 10.0), clamped)
    }
}
