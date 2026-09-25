// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.Point
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** [BowlPrimitive]: a superellipse skeleton, stroked, with an elliptical-pen contrast axis. */
class BowlTest {
    @Test
    fun monolinearBowlMatchesDirectlyStrokingTheSameSkeleton() {
        // contrast = 1.0 must literally delegate to Stroke.kt's own strokeClosedContour -- checked
        // here by reproducing that exact call and comparing bounding boxes (the two should be
        // identical, not merely "close").
        val bowl = BowlPrimitive(Point(0, 0), skeletonSemiMajor = 200.0, skeletonSemiMinor = 150.0, strokeWidth = 40.0)
        val rings = bowl.realize()
        assertEquals(2, rings.size)
        val skeleton = SuperellipsePrimitive.CentreRadii(Point(0, 0), 200.0, 150.0, 2.0, 0.0, DEFAULT_SUPERELLIPSE_SAMPLE_COUNT).realize()
        val directStroke = strokeClosedContour(skeleton, StrokeParameters(width = 40.0, join = LineJoin.ROUND))
        assertApprox(directStroke[0].boundingBox().maxX, rings[0].boundingBox().maxX, tolerance = 1e-6)
        assertApprox(directStroke[1].boundingBox().maxX, rings[1].boundingBox().maxX, tolerance = 1e-6)
    }

    @Test
    fun bowlProducesAnOuterAndInnerRingAroundTheStrokeWidth() {
        val bowl = BowlPrimitive(Point(0, 0), skeletonSemiMajor = 200.0, skeletonSemiMinor = 150.0, strokeWidth = 40.0, contrast = 0.6)
        val (outer, inner) = bowl.realize()
        val outerBox = outer.boundingBox()
        val innerBox = inner.boundingBox()
        // The outer ring must fully contain the inner one.
        assertTrue(outerBox.maxX > innerBox.maxX)
        assertTrue(outerBox.maxY > innerBox.maxY)
        assertTrue(outerBox.minX < innerBox.minX)
        assertTrue(outerBox.minY < innerBox.minY)
    }

    @Test
    fun contrastMakesTheThickAxisWiderThanTheThinAxis() {
        // contrastAngleRadians defaults to PI/2 (vertical is thick): the ring's own left/right
        // (horizontal) extent, near the equator, should show a thicker gap than the top/bottom does.
        val contrast = 0.5
        val bowl =
            BowlPrimitive(
                Point(0, 0),
                skeletonSemiMajor = 200.0,
                skeletonSemiMinor = 200.0,
                strokeWidth = 60.0,
                contrast = contrast,
                contrastAngleRadians = PI / 2.0,
            )
        // The support-function half-width, checked directly at the two axis angles: phi=0 (horizontal
        // normal -- the vertical stem's own sides) must be the full, uncontrasted half-width; phi=PI/2
        // (vertical normal -- the top/bottom) must be the contrasted (narrower) half-width.
        val thickHalfWidth = bowl.ellipticalPenHalfWidth(0.0)
        val thinHalfWidth = bowl.ellipticalPenHalfWidth(PI / 2.0)
        assertApprox(30.0, thickHalfWidth, tolerance = 1e-9)
        assertApprox(30.0 * contrast, thinHalfWidth, tolerance = 1e-9)
        assertTrue(thickHalfWidth > thinHalfWidth)
    }

    @Test
    fun ellipticalPenHalfWidthReducesToAConstantWhenContrastIsOne() {
        val bowl = BowlPrimitive(Point(0, 0), skeletonSemiMajor = 100.0, skeletonSemiMinor = 100.0, strokeWidth = 24.0, contrast = 1.0)
        for (phi in listOf(0.0, PI / 6.0, PI / 3.0, PI / 2.0, PI, -PI / 4.0)) {
            assertApprox(12.0, bowl.ellipticalPenHalfWidth(phi), tolerance = 1e-9, message = "phi=$phi")
        }
    }

    @Test
    fun ellipticalPenHalfWidthMatchesTheEllipseSupportFunctionDirectly() {
        // Independent check of the closed-form formula itself, h(phi) = sqrt((rx cos d)^2 + (ry sin d)^2)
        // with d = phi - (contrastAngle - PI/2), against a hand-evaluated point off either axis.
        val bowl =
            BowlPrimitive(
                Point(0, 0),
                skeletonSemiMajor = 10.0,
                skeletonSemiMinor = 10.0,
                strokeWidth = 20.0,
                contrast = 0.4,
                contrastAngleRadians = 0.0,
            )
        val rx = 10.0
        val ry = 4.0
        val penAngle = 0.0 - PI / 2.0
        for (phi in listOf(0.3, 1.1, 2.4, -0.8)) {
            val d = phi - penAngle
            val expected =
                kotlin.math.sqrt(
                    (rx * kotlin.math.cos(d)) * (rx * kotlin.math.cos(d)) + (ry * kotlin.math.sin(d)) * (ry * kotlin.math.sin(d)),
                )
            assertApprox(expected, bowl.ellipticalPenHalfWidth(phi), tolerance = 1e-9, message = "phi=$phi")
        }
    }

    @Test
    fun rejectsNonPositiveStrokeWidth() {
        assertFailsWith<IllegalArgumentException> {
            BowlPrimitive(Point(0, 0), skeletonSemiMajor = 100.0, skeletonSemiMinor = 100.0, strokeWidth = 0.0)
        }
    }
}
