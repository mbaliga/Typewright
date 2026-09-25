// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.Anchor
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Task P5a-foundations item 3: transformations with numeric entry, exhaustively tested including composition and round-trips. */
class TransformTest {
    private fun assertVec2Approx(
        expected: Vec2,
        actual: Vec2,
        tolerance: Double = 1e-6,
        message: String = "",
    ) {
        assertTrue(
            kotlin.math.abs(expected.x - actual.x) <= tolerance && kotlin.math.abs(expected.y - actual.y) <= tolerance,
            "$message expected $expected, got $actual",
        )
    }

    // -----------------------------------------------------------------------------------------
    // Scale
    // -----------------------------------------------------------------------------------------

    @Test
    fun scaleUniformAboutOrigin() {
        val t = scale(2.0)
        assertVec2Approx(Vec2(20.0, 40.0), t.apply(Vec2(10.0, 20.0)))
    }

    @Test
    fun scaleNonUniformAboutArbitraryCenter() {
        val t = scale(2.0, 3.0, center = Vec2(10.0, 10.0))
        // point at the centre itself never moves.
        assertVec2Approx(Vec2(10.0, 10.0), t.apply(Vec2(10.0, 10.0)))
        // a point 5 to the right and 4 above the centre scales to 10 right, 12 above.
        assertVec2Approx(Vec2(20.0, 22.0), t.apply(Vec2(15.0, 14.0)))
    }

    // -----------------------------------------------------------------------------------------
    // Rotate
    // -----------------------------------------------------------------------------------------

    @Test
    fun rotate90DegreesAboutOrigin() {
        val t = rotate(PI / 2.0)
        assertVec2Approx(Vec2(0.0, 10.0), t.apply(Vec2(10.0, 0.0)))
    }

    @Test
    fun rotateAboutArbitraryCenterKeepsCenterFixed() {
        val center = Vec2(5.0, 7.0)
        val t = rotate(PI / 3.0, center)
        assertVec2Approx(center, t.apply(center))
    }

    @Test
    fun rotateThenNegativeRotateIsIdentity() {
        val theta = 0.7
        val composed = rotate(theta, Vec2(3.0, -2.0)).andThen(rotate(-theta, Vec2(3.0, -2.0)))
        val p = Vec2(12.0, -5.0)
        assertVec2Approx(p, composed.apply(p), tolerance = 1e-9)
    }

    // -----------------------------------------------------------------------------------------
    // Shear: sign/origin convention (needed unambiguous for the oblique derivation next task).
    // -----------------------------------------------------------------------------------------

    @Test
    fun shearXPositiveAngleShiftsRightInProportionToHeightAboveOrigin() {
        val t = shearX(PI / 4.0) // tan(45deg) = 1
        // a point 10 units above the baseline (y=0) shifts right by 10*tan(45deg)=10.
        assertVec2Approx(Vec2(15.0, 10.0), t.apply(Vec2(5.0, 10.0)))
        // a point ON the baseline never moves under shearX about the origin.
        assertVec2Approx(Vec2(5.0, 0.0), t.apply(Vec2(5.0, 0.0)))
    }

    @Test
    fun shearXAboutArbitraryOriginUsesHeightRelativeToThatOrigin() {
        val origin = Vec2(0.0, 100.0) // e.g. a baseline drawn at y=100
        val t = shearX(PI / 4.0, origin)
        assertVec2Approx(Vec2(5.0, 100.0), t.apply(Vec2(5.0, 100.0)), message = "on the shear origin's own height")
        assertVec2Approx(Vec2(15.0, 110.0), t.apply(Vec2(5.0, 110.0)), message = "10 above the shear origin shifts right by 10")
    }

    @Test
    fun shearYPositiveAngleShiftsUpInProportionToXPastOrigin() {
        val t = shearY(PI / 4.0)
        assertVec2Approx(Vec2(10.0, 10.0), t.apply(Vec2(10.0, 0.0)))
    }

    @Test
    fun shearRejectsAngleAtOrPastRightAngle() {
        var threw = false
        try {
            shearX(PI / 2.0)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw, "shearX(PI/2) must be rejected (tan undefined)")
    }

    // -----------------------------------------------------------------------------------------
    // Mirror
    // -----------------------------------------------------------------------------------------

    @Test
    fun mirrorAboutPointTwiceIsIdentity() {
        val m = mirrorAboutPoint(Vec2(4.0, -3.0))
        val twice = m.andThen(m)
        val p = Vec2(9.0, 17.0)
        assertVec2Approx(p, twice.apply(p), tolerance = 1e-9)
    }

    @Test
    fun mirrorAboutXAxisNegatesY() {
        val m = mirrorAboutAxis(Vec2(0.0, 0.0), Vec2(1.0, 0.0))
        assertVec2Approx(Vec2(3.0, -5.0), m.apply(Vec2(3.0, 5.0)))
    }

    @Test
    fun mirrorAboutArbitraryAxisTwiceIsIdentity() {
        val m = mirrorAboutAxis(Vec2(1.0, 2.0), Vec2(4.0, 9.0))
        val twice = m.andThen(m)
        val p = Vec2(-3.0, 6.0)
        assertVec2Approx(p, twice.apply(p), tolerance = 1e-6)
    }

    // -----------------------------------------------------------------------------------------
    // Composition
    // -----------------------------------------------------------------------------------------

    @Test
    fun rotateThenScaleEqualsComposedMatrixAppliedOnce() {
        val r = rotate(0.9)
        val s = scale(1.5, 0.5, center = Vec2(2.0, -4.0))
        val composed = r.andThen(s)
        val p = Vec2(13.0, 4.0)
        val sequential = s.apply(r.apply(p))
        val single = composed.apply(p)
        assertVec2Approx(sequential, single, tolerance = 1e-9)
    }

    @Test
    fun threeWayCompositionMatchesSequentialApplication() {
        val chain = translate(3.0, -1.0).andThen(rotate(1.1)).andThen(scale(2.0, 3.0))
        val p = Vec2(-7.0, 2.5)
        val sequential = scale(2.0, 3.0).apply(rotate(1.1).apply(translate(3.0, -1.0).apply(p)))
        assertVec2Approx(sequential, chain.apply(p), tolerance = 1e-9)
    }

    // -----------------------------------------------------------------------------------------
    // Every transform moves BOTH on-curve and off-curve points.
    // -----------------------------------------------------------------------------------------

    @Test
    fun applyToContourMovesOnCurveAndOffCurvePointsAlike() {
        val contour =
            Contour(
                listOf(
                    ContourPoint(Point(0, 0), onCurve = true),
                    ContourPoint(Point(10, 0), onCurve = false),
                    ContourPoint(Point(20, 10), onCurve = false),
                    ContourPoint(Point(30, 30), onCurve = true),
                    ContourPoint(Point(10, 40), onCurve = false),
                    ContourPoint(Point(-10, 20), onCurve = false),
                ),
                CurveFormat.CUBIC,
            )
        val t = translate(100.0, 200.0)
        val moved = t.apply(contour)
        for (i in contour.points.indices) {
            val original = contour.points[i]
            val movedPoint = moved.points[i]
            assertEquals(original.onCurve, movedPoint.onCurve)
            assertEquals(original.point.x + 100, movedPoint.point.x, "point $i (onCurve=${original.onCurve}) x did not move")
            assertEquals(original.point.y + 200, movedPoint.point.y, "point $i (onCurve=${original.onCurve}) y did not move")
        }
    }

    @Test
    fun applyToGlyphMovesAnchorsAndLeavesAdvanceWidthUnchanged() {
        val glyph =
            Glyph(
                name = "test",
                advanceWidth = 500,
                contours = listOf(rectangleContour(0, 0, 100, 100)),
                anchors = listOf(Anchor("top", Point(50, 100))),
            )
        val moved = scale(2.0).apply(glyph)
        assertEquals(500, moved.advanceWidth, "advanceWidth is deliberately left untransformed -- see AffineTransform.apply(Glyph)'s KDoc")
        assertEquals(Point(100, 200), moved.anchors.single().point)
        assertEquals("test", moved.name)
    }

    // -----------------------------------------------------------------------------------------
    // Bounding box: exact, curve-aware (uses extrema, not just control points).
    // -----------------------------------------------------------------------------------------

    @Test
    fun boundingBoxOfACircleMatchesItsTrueRadiusNotItsLooserControlPointBox() {
        val circle = circleContour(cx = 0.0, cy = 0.0, radius = 100.0)
        val box = circle.boundingBox()
        // The control-point box would be roughly [-100*(1+k), 100*(1+k)] on each axis (control
        // points sit outside the true circle); the curve's own extent should be within a couple
        // of units of the true radius, matching the well-known ~0.027% max deviation of the
        // 4-arc kappa approximation.
        assertTrue(kotlin.math.abs(box.maxX - 100.0) < 1.0, "maxX ${box.maxX}")
        assertTrue(kotlin.math.abs(box.minX + 100.0) < 1.0, "minX ${box.minX}")
        assertTrue(kotlin.math.abs(box.maxY - 100.0) < 1.0, "maxY ${box.maxY}")
        assertTrue(kotlin.math.abs(box.minY + 100.0) < 1.0, "minY ${box.minY}")
    }

    @Test
    fun boundingBoxOfARectangleMatchesItsCorners() {
        val rect = rectangleContour(10, 20, 110, 220)
        val box = rect.boundingBox()
        assertEquals(10.0, box.minX)
        assertEquals(110.0, box.maxX)
        assertEquals(20.0, box.minY)
        assertEquals(220.0, box.maxY)
    }

    // -----------------------------------------------------------------------------------------
    // Align / distribute
    // -----------------------------------------------------------------------------------------

    @Test
    fun alignHorizontallyLeftMovesEveryContourToTheGroupsOwnLeftmostEdge() {
        val a = rectangleContour(0, 0, 10, 10)
        val b = rectangleContour(50, 0, 80, 10)
        val aligned = alignHorizontally(listOf(a, b), HorizontalEdge.LEFT)
        assertEquals(0, aligned[0].boundingBox().minX.toInt())
        assertEquals(0, aligned[1].boundingBox().minX.toInt())
    }

    @Test
    fun alignVerticallyMiddleMovesEveryContourToTheGroupsOwnVerticalCenter() {
        val a = rectangleContour(0, 0, 10, 10) // centre y = 5
        val b = rectangleContour(0, 100, 10, 140) // centre y = 120
        val groupCenter = listOf(a, b).boundingBox().centerY
        val aligned = alignVertically(listOf(a, b), VerticalEdge.MIDDLE)
        for (c in aligned) {
            assertTrue(kotlin.math.abs(c.boundingBox().centerY - groupCenter) < 1e-6)
        }
    }

    @Test
    fun aligningASingleContourToItselfIsANoOp() {
        val a = rectangleContour(5, 5, 25, 25)
        val aligned = alignHorizontally(listOf(a), HorizontalEdge.CENTER).single()
        assertEquals(a.boundingBox().minX, aligned.boundingBox().minX)
    }

    @Test
    fun distributeHorizontallyKeepsFirstAndLastFixedAndSpacesCentersEvenly() {
        val a = rectangleContour(0, 0, 10, 10) // centre x = 5
        val b = rectangleContour(20, 0, 30, 10) // centre x = 25, will move
        val c = rectangleContour(90, 0, 110, 10) // centre x = 100
        val distributed = distributeHorizontally(listOf(a, b, c))
        val centers = distributed.map { it.boundingBox().centerX }.sorted()
        assertEquals(3, centers.size)
        // Tolerance 1.0, not an exact match: every transform rounds to an integer font unit
        // ("integers at rest"), so a translation by a fractional amount (27.5 here) cannot land
        // the middle contour's own centre at the mathematically exact 52.5.
        assertTrue(kotlin.math.abs(centers[0] - 5.0) < 1.0, "first contour's centre stays put: ${centers[0]}")
        assertTrue(kotlin.math.abs(centers[2] - 100.0) < 1.0, "last contour's centre stays put: ${centers[2]}")
        assertTrue(kotlin.math.abs(centers[1] - 52.5) < 1.0, "middle contour's centre is evenly spaced: ${centers[1]}")
    }

    @Test
    fun distributeWithFewerThanThreeContoursIsANoOp() {
        val a = rectangleContour(0, 0, 10, 10)
        val b = rectangleContour(50, 0, 60, 10)
        val result = distributeHorizontally(listOf(a, b))
        assertEquals(listOf(a, b), result)
    }

    @Test
    fun affineTransformCompositionMatchesMatrixMultiplicationDirectly() {
        // A direct check of andThen's own coefficients against the hand-derived formula, not just
        // point application (catches a bug that happens to cancel out for the one test point).
        val m1 = AffineTransform(2.0, 0.5, -0.3, 1.2, 4.0, -1.0)
        val m2 = AffineTransform(-1.0, 0.7, 0.9, 0.4, 2.0, 3.0)
        val composed = m1.andThen(m2)
        for (p in listOf(Vec2(0.0, 0.0), Vec2(1.0, 0.0), Vec2(0.0, 1.0), Vec2(3.7, -2.2))) {
            assertVec2Approx(m2.apply(m1.apply(p)), composed.apply(p), tolerance = 1e-9)
        }
    }

    @Test
    fun identityTransformChangesNothing() {
        val p = Vec2(17.3, -4.2)
        assertVec2Approx(p, AffineTransform.IDENTITY.apply(p), tolerance = 1e-12)
    }

    @Test
    fun rotationPreservesDistanceFromCenter() {
        val center = Vec2(1.0, 1.0)
        val p = Vec2(11.0, 1.0) // 10 units from center
        val rotated = rotate(1.234, center).apply(p)
        val distance = sqrt((rotated.x - center.x) * (rotated.x - center.x) + (rotated.y - center.y) * (rotated.y - center.y))
        assertTrue(kotlin.math.abs(distance - 10.0) < 1e-9)
    }
}
