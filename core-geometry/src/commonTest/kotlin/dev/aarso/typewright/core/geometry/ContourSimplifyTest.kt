package dev.aarso.typewright.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** [simplifyContour]: collinear-point removal, near-duplicate-point removal, the tolerance boundary, and the live before/after count the brief names. */
class ContourSimplifyTest {
    /** A clean, minimal rectangle: (0,0) -> (200,0) -> (200,100) -> (0,100) -> back to (0,0). */
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
    fun removesACollinearOnCurvePointOnAStraightRun() {
        // The same rectangle, with one redundant extra anchor exactly at the midpoint of its
        // bottom edge -- a real "collinear point on a straight run" the brief names.
        val withExtraPoint =
            buildCubicContour(
                listOf(
                    straightLineCubic(Vec2(0.0, 0.0), Vec2(100.0, 0.0)),
                    straightLineCubic(Vec2(100.0, 0.0), Vec2(200.0, 0.0)),
                    straightLineCubic(Vec2(200.0, 0.0), Vec2(200.0, 100.0)),
                    straightLineCubic(Vec2(200.0, 100.0), Vec2(0.0, 100.0)),
                    straightLineCubic(Vec2(0.0, 100.0), Vec2(0.0, 0.0)),
                ),
            )
        val before = withExtraPoint.count()
        assertEquals(5, before.onCurveEquivalent)

        val result = simplifyContour(withExtraPoint)

        assertEquals(before, result.before)
        assertEquals(4, result.after.onCurveEquivalent, "the collinear midpoint should be removed")
        assertEquals(CurveFormat.CUBIC, result.contour.format)
        assertEquals(0, result.contour.points.size % 3, "CUBIC triple invariant")
        val onCurvePoints =
            result.contour.points
                .filterIndexed { index, _ -> index % 3 == 0 }
                .map { it.point }
        assertEquals(setOf(Point(0, 0), Point(200, 0), Point(200, 100), Point(0, 100)), onCurvePoints.toSet())
    }

    @Test
    fun removesANearDuplicatePointEvenWhenSlightlyOffTheStraightLine() {
        // A rectangle with an extra point one unit off the bottom edge -- close enough to its
        // neighbours that removing it does not move the visible shape beyond the default
        // tolerance, even though it is not perfectly collinear.
        val withNearDuplicate =
            buildCubicContour(
                listOf(
                    straightLineCubic(Vec2(0.0, 0.0), Vec2(100.0, 0.0)),
                    straightLineCubic(Vec2(100.0, 0.0), Vec2(101.0, 1.0)),
                    straightLineCubic(Vec2(101.0, 1.0), Vec2(200.0, 0.0)),
                    straightLineCubic(Vec2(200.0, 0.0), Vec2(200.0, 100.0)),
                    straightLineCubic(Vec2(200.0, 100.0), Vec2(0.0, 100.0)),
                    straightLineCubic(Vec2(0.0, 100.0), Vec2(0.0, 0.0)),
                ),
            )
        val result = simplifyContour(withNearDuplicate, toleranceUnits = 1.5)
        assertEquals(4, result.after.onCurveEquivalent)
    }

    @Test
    fun preservesAPointBeyondTolerance() {
        // The same near-duplicate fixture, but the middle point is now 10 units off the line --
        // clearly a real, visible detail (a small notch), not noise -- with a tight tolerance.
        val withRealNotch =
            buildCubicContour(
                listOf(
                    straightLineCubic(Vec2(0.0, 0.0), Vec2(100.0, 0.0)),
                    straightLineCubic(Vec2(100.0, 0.0), Vec2(100.0, 10.0)),
                    straightLineCubic(Vec2(100.0, 10.0), Vec2(200.0, 0.0)),
                    straightLineCubic(Vec2(200.0, 0.0), Vec2(200.0, 100.0)),
                    straightLineCubic(Vec2(200.0, 100.0), Vec2(0.0, 100.0)),
                    straightLineCubic(Vec2(0.0, 100.0), Vec2(0.0, 0.0)),
                ),
            )
        val result = simplifyContour(withRealNotch, toleranceUnits = 1.5)
        assertEquals(6, result.after.onCurveEquivalent, "a real 10-unit notch must survive a 1.5-unit tolerance")
        assertEquals(withRealNotch, result.contour)
    }

    @Test
    fun anAlreadyMinimalContourIsUnchanged() {
        val rect = rectangle()
        val result = simplifyContour(rect)
        assertEquals(rect, result.contour)
        assertEquals(result.before, result.after)
    }

    @Test
    fun collapsesALongCollinearRunInOneCall() {
        // Three extra collinear points in a row on the same straight edge -- the iterative,
        // restart-after-each-removal design must catch every one of them, not just the first.
        val withThreeExtraPoints =
            buildCubicContour(
                listOf(
                    straightLineCubic(Vec2(0.0, 0.0), Vec2(50.0, 0.0)),
                    straightLineCubic(Vec2(50.0, 0.0), Vec2(100.0, 0.0)),
                    straightLineCubic(Vec2(100.0, 0.0), Vec2(150.0, 0.0)),
                    straightLineCubic(Vec2(150.0, 0.0), Vec2(200.0, 0.0)),
                    straightLineCubic(Vec2(200.0, 0.0), Vec2(200.0, 100.0)),
                    straightLineCubic(Vec2(200.0, 100.0), Vec2(0.0, 100.0)),
                    straightLineCubic(Vec2(0.0, 100.0), Vec2(0.0, 0.0)),
                ),
            )
        val result = simplifyContour(withThreeExtraPoints)
        assertEquals(4, result.after.onCurveEquivalent)
    }

    @Test
    fun rejectsANegativeTolerance() {
        assertFailsWith<IllegalArgumentException> { simplifyContour(rectangle(), toleranceUnits = -1.0) }
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
        assertFailsWith<IllegalArgumentException> { simplifyContour(quadratic) }
    }

    @Test
    fun zeroToleranceStillRemovesAnExactlyCollinearPoint() {
        val withExtraPoint =
            buildCubicContour(
                listOf(
                    straightLineCubic(Vec2(0.0, 0.0), Vec2(100.0, 0.0)),
                    straightLineCubic(Vec2(100.0, 0.0), Vec2(200.0, 0.0)),
                    straightLineCubic(Vec2(200.0, 0.0), Vec2(200.0, 100.0)),
                    straightLineCubic(Vec2(200.0, 100.0), Vec2(0.0, 100.0)),
                    straightLineCubic(Vec2(0.0, 100.0), Vec2(0.0, 0.0)),
                ),
            )
        val result = simplifyContour(withExtraPoint, toleranceUnits = 0.0)
        assertEquals(4, result.after.onCurveEquivalent)
    }

    @Test
    fun liveCountReportsBothOnCurveAndOffCurveTotals() {
        val withExtraPoint =
            buildCubicContour(
                listOf(
                    straightLineCubic(Vec2(0.0, 0.0), Vec2(100.0, 0.0)),
                    straightLineCubic(Vec2(100.0, 0.0), Vec2(200.0, 0.0)),
                    straightLineCubic(Vec2(200.0, 0.0), Vec2(200.0, 100.0)),
                    straightLineCubic(Vec2(200.0, 100.0), Vec2(0.0, 100.0)),
                    straightLineCubic(Vec2(0.0, 100.0), Vec2(0.0, 0.0)),
                ),
            )
        val result = simplifyContour(withExtraPoint)
        assertEquals(ContourCount(onCurveEquivalent = 5, offCurve = 10), result.before)
        assertEquals(ContourCount(onCurveEquivalent = 4, offCurve = 8), result.after)
        assertTrue(result.after.onCurveEquivalent < result.before.onCurveEquivalent)
    }
}
