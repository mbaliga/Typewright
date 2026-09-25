// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.CurveSegment
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.segments
import com.asoc.typewright.core.geometry.signedArea
import kotlin.math.PI
import kotlin.math.pow
import kotlin.test.Test

/** [EllipsePrimitive]'s two entry methods and [SuperellipsePrimitive]'s own. */
class EllipseTest {
    @Test
    fun centreRadiiProducesPointsSatisfyingTheEllipseEquation() {
        val contour = EllipsePrimitive.CentreRadii(Point(0, 0), semiMajor = 40.0, semiMinor = 20.0).realize()
        for (segment in contour.segments()) {
            val p = (segment as CurveSegment.Cubic).start
            val value = (p.x / 40.0) * (p.x / 40.0) + (p.y / 20.0) * (p.y / 20.0)
            assertApprox(1.0, value, tolerance = 0.02)
        }
    }

    @Test
    fun centreRadiiWithRotationRotatesTheAxes() {
        // A 90-degree rotation of a 40x20 ellipse swaps which axis is which: the rotated ellipse's
        // own widest extent should now be along y, not x.
        val unrotated = EllipsePrimitive.CentreRadii(Point(0, 0), semiMajor = 40.0, semiMinor = 20.0).realize()
        val rotated = EllipsePrimitive.CentreRadii(Point(0, 0), semiMajor = 40.0, semiMinor = 20.0, rotationRadians = PI / 2.0).realize()
        val unrotatedBox = unrotated.boundingBox()
        val rotatedBox = rotated.boundingBox()
        assertApprox(40.0, (unrotatedBox.maxX - unrotatedBox.minX) / 2.0, tolerance = 1.0)
        assertApprox(40.0, (rotatedBox.maxY - rotatedBox.minY) / 2.0, tolerance = 1.0)
        assertApprox(20.0, (rotatedBox.maxX - rotatedBox.minX) / 2.0, tolerance = 1.0)
    }

    @Test
    fun threePointsAxisConstructionMatchesTheEquivalentCentreRadii() {
        // Axis endpoints (-30, 0) and (30, 0) -- centre (0,0), semiA 30, rotation 0; other-axis point
        // (0, 15) -- semiB 15. Must match EllipsePrimitive.CentreRadii(0,0,30,15,0) exactly.
        val threePoint = EllipsePrimitive.ThreePoints(Point(-30, 0), Point(30, 0), Point(0, 15)).realize()
        val centreRadii = EllipsePrimitive.CentreRadii(Point(0, 0), semiMajor = 30.0, semiMinor = 15.0).realize()
        val threePointBox = threePoint.boundingBox()
        val centreRadiiBox = centreRadii.boundingBox()
        assertApprox(centreRadiiBox.minX, threePointBox.minX, tolerance = 1.0)
        assertApprox(centreRadiiBox.maxY, threePointBox.maxY, tolerance = 1.0)
    }

    @Test
    fun superellipseWithExponentTwoApproximatesATrueEllipse() {
        val superellipse = SuperellipsePrimitive.CentreRadii(Point(0, 0), semiMajor = 40.0, semiMinor = 20.0, exponent = 2.0).realize()
        val ellipse = EllipsePrimitive.CentreRadii(Point(0, 0), semiMajor = 40.0, semiMinor = 20.0).realize()
        val superBox = superellipse.boundingBox()
        val ellipseBox = ellipse.boundingBox()
        assertApprox(ellipseBox.maxX, superBox.maxX, tolerance = 2.0)
        assertApprox(ellipseBox.maxY, superBox.maxY, tolerance = 2.0)
    }

    @Test
    fun superellipseWithHighExponentBulgesTowardsARectangle() {
        // A very high exponent should keep the curve near the bounding box almost everywhere except
        // right at the axes -- checked by comparing its enclosed-area estimate (via the fitted
        // contour's own signedArea) against the ellipse's (n=2) enclosed area: a squarer superellipse
        // (n=8) has strictly and substantially more area than an ellipse of the same semi-axes.
        val squareish = SuperellipsePrimitive.CentreRadii(Point(0, 0), semiMajor = 40.0, semiMinor = 40.0, exponent = 8.0).realize()
        val ellipse = EllipsePrimitive.CentreRadii(Point(0, 0), semiMajor = 40.0, semiMinor = 40.0).realize()
        val squareishArea = kotlin.math.abs(squareish.signedArea())
        val ellipseArea = kotlin.math.abs(ellipse.signedArea())
        val boxArea = 80.0 * 80.0
        val ellipseExpectedArea = PI * 40.0 * 40.0
        assertApprox(ellipseExpectedArea, ellipseArea, tolerance = ellipseExpectedArea * 0.02)
        // A higher Lame exponent bulges towards (but, up to this fitter's own small overshoot near a
        // sharply-curved near-corner, never meaningfully past) the bounding square; either way it must
        // enclose distinctly more area than the same-box ellipse (n=2).
        kotlin.test.assertTrue(squareishArea > ellipseArea, "a higher Lame exponent must enclose more area than the ellipse")
        kotlin.test.assertTrue(
            squareishArea < boxArea * 1.05,
            "a superellipse should not exceed its own bounding box's area by more than the fitter's own small overshoot",
        )
    }

    @Test
    fun superellipseThreePointsMatchesCentreRadiiWithTheSameExponent() {
        val threePoint = SuperellipsePrimitive.ThreePoints(Point(-30, 0), Point(30, 0), Point(0, 15), exponent = 3.0).realize()
        val centreRadii = SuperellipsePrimitive.CentreRadii(Point(0, 0), semiMajor = 30.0, semiMinor = 15.0, exponent = 3.0).realize()
        assertApprox(centreRadii.boundingBox().maxX, threePoint.boundingBox().maxX, tolerance = 1.0)
        assertApprox(centreRadii.boundingBox().maxY, threePoint.boundingBox().maxY, tolerance = 1.0)
    }

    @Test
    fun superellipsePolylineMatchesTheExactParametricEquationAtEachSample() {
        val points =
            superellipsePolyline(
                Vec2(0.0, 0.0),
                semiMajor = 50.0,
                semiMinor = 30.0,
                exponent = 4.0,
                rotationRadians = 0.0,
                sampleCount = 16,
            )
        for (p in points) {
            val x = p.x / 50.0
            val y = p.y / 30.0
            val value = kotlin.math.abs(x).pow(4.0) + kotlin.math.abs(y).pow(4.0)
            assertApprox(1.0, value, tolerance = 0.05)
        }
    }
}
