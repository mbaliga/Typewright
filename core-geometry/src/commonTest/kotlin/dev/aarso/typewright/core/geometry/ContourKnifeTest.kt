// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** [knifeContour]: cutting at existing anchors, cutting at interior (de Casteljau split) locations, area conservation, and its error cases. */
class ContourKnifeTest {
    /** (0,0) -> (200,0) -> (200,100) -> (0,100) -> back to (0,0); segments 0..3 in that order. */
    private fun rectangle(): Contour =
        buildCubicContour(
            listOf(
                straightLineCubic(Vec2(0.0, 0.0), Vec2(200.0, 0.0)),
                straightLineCubic(Vec2(200.0, 0.0), Vec2(200.0, 100.0)),
                straightLineCubic(Vec2(200.0, 100.0), Vec2(0.0, 100.0)),
                straightLineCubic(Vec2(0.0, 100.0), Vec2(0.0, 0.0)),
            ),
        )

    @Test
    fun cutsAtTwoExistingAnchorsIntoTwoValidContoursThatConserveArea() {
        val rect = rectangle()
        val (a, b) = knifeContour(rect, ContourLocation(segmentIndex = 1, t = 0.0), ContourLocation(segmentIndex = 3, t = 0.0))

        assertEquals(CurveFormat.CUBIC, a.format)
        assertEquals(CurveFormat.CUBIC, b.format)
        assertEquals(0, a.points.size % 3)
        assertEquals(0, b.points.size % 3)
        assertEquals(rect.signedArea(), a.signedArea() + b.signedArea(), 1e-6)
    }

    @Test
    fun cuttingAtExistingAnchorsIntroducesNoNewPoints() {
        val rect = rectangle()
        val (a, b) = knifeContour(rect, ContourLocation(segmentIndex = 1, t = 0.0), ContourLocation(segmentIndex = 3, t = 0.0))
        // Each result is 2 original edges + 1 straight closing cut = 3 segments = 9 points.
        assertEquals(9, a.points.size)
        assertEquals(9, b.points.size)
    }

    @Test
    fun cutAtOneExistingAnchorAndOneInteriorPointSubdividesOnlyTheInteriorOne() {
        val rect = rectangle()
        // t=1.0 on segment 0 canonicalizes to (segment 1, t=0.0) -- the (200,0) corner, exactly.
        val (a, b) =
            knifeContour(
                rect,
                ContourLocation(segmentIndex = 0, t = 1.0),
                ContourLocation(segmentIndex = 2, t = 0.5),
            )
        assertEquals(rect.signedArea(), a.signedArea() + b.signedArea(), 1e-6)
        val allOnCurvePoints =
            (a.points.filterIndexed { i, _ -> i % 3 == 0 } + b.points.filterIndexed { i, _ -> i % 3 == 0 }).map { it.point }.toSet()
        // The new interior cut point: segment 2 runs (200,100) -> (0,100); straightLineCubic's
        // control points at the 1/3 and 2/3 chord fractions make it an exact-linear
        // parametrization (PaletteTest.straightLineCubicIsAnExactLinearParametrization), so t=0.5
        // is exactly the midpoint (100,100), not merely close to it.
        assertTrue(Point(100, 100) in allOnCurvePoints, "expected the new midpoint anchor (100,100) among $allOnCurvePoints")
        assertTrue(Point(200, 0) in allOnCurvePoints)
    }

    @Test
    fun bothCutsInteriorToTheSameSegmentProducesAThinSliverAndTheRest() {
        val rect = rectangle()
        // Both cuts on segment 0 ((0,0) -> (200,0)): a thin sliver between x=90 and x=110.
        val (sliver, rest) =
            knifeContour(rect, ContourLocation(segmentIndex = 0, t = 0.45), ContourLocation(segmentIndex = 0, t = 0.55))
        assertEquals(rect.signedArea(), sliver.signedArea() + rest.signedArea(), 1e-6)
        // The sliver is a degenerate 2-segment loop (one short original arc + one closing cut).
        assertEquals(6, sliver.points.size)
    }

    @Test
    fun rejectsTwoLocationsThatResolveToTheSamePoint() {
        val rect = rectangle()
        assertFailsWith<IllegalArgumentException> {
            knifeContour(rect, ContourLocation(segmentIndex = 0, t = 1.0), ContourLocation(segmentIndex = 1, t = 0.0))
        }
    }

    @Test
    fun rejectsAnOutOfRangeSegmentIndex() {
        val rect = rectangle()
        assertFailsWith<IllegalArgumentException> {
            knifeContour(rect, ContourLocation(segmentIndex = 4, t = 0.5), ContourLocation(segmentIndex = 1, t = 0.5))
        }
    }

    @Test
    fun requiresACubicContour() {
        val quadratic =
            Contour(
                listOf(
                    ContourPoint(Point(0, 0), onCurve = true),
                    ContourPoint(Point(50, 50), onCurve = false),
                    ContourPoint(Point(100, 0), onCurve = true),
                ),
                CurveFormat.QUADRATIC,
            )
        assertFailsWith<IllegalArgumentException> {
            knifeContour(quadratic, ContourLocation(0, 0.1), ContourLocation(1, 0.1))
        }
    }

    @Test
    fun contourLocationRejectsAnOutOfRangeT() {
        assertFailsWith<IllegalArgumentException> { ContourLocation(segmentIndex = 0, t = -0.1) }
        assertFailsWith<IllegalArgumentException> { ContourLocation(segmentIndex = 0, t = 1.1) }
    }
}
