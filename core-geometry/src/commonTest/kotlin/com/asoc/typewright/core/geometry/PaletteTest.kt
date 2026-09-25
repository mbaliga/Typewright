// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** [PaletteCommand]'s registry, [reverseContour], [roundContourCoordinates], [closePolylineToContour], and [straightLineCubic]'s own exact-linear property every other file here relies on. */
class PaletteTest {
    // -------------------------------------------------------------------------------------------
    // The registry itself: one entry per command the brief names, each with a usable id/label.
    // -------------------------------------------------------------------------------------------

    @Test
    fun registryHasExactlyTheEightBriefNamedCommands() {
        assertEquals(8, PaletteCommand.entries.size)
    }

    @Test
    fun everyCommandHasAUniqueNonBlankIdAndLabel() {
        val ids = PaletteCommand.entries.map { it.id }
        assertEquals(ids.toSet().size, ids.size, "duplicate command id in $ids")
        for (command in PaletteCommand.entries) {
            assertTrue(command.id.isNotBlank())
            assertTrue(command.label.isNotBlank())
        }
    }

    @Test
    fun onlyCorrectDirectionTargetsAWholeGlyphsContours() {
        val glyphTargeted = PaletteCommand.entries.filter { it.target == PaletteTarget.GLYPH_CONTOURS }
        assertEquals(listOf(PaletteCommand.CORRECT_DIRECTION), glyphTargeted)
    }

    // -------------------------------------------------------------------------------------------
    // reverseContour: a thin wrapper over Contour.reverse().
    // -------------------------------------------------------------------------------------------

    @Test
    fun reverseContourDelegatesToContourReverse() {
        val square = fitClosedContourToCubics(denseSquare(x0 = 0, y0 = 0, side = 100, step = 5))
        assertEquals(square.reverse(), reverseContour(square))
    }

    // -------------------------------------------------------------------------------------------
    // roundContourCoordinates: an honest identity, but a real, invariant-checked rebuild.
    // -------------------------------------------------------------------------------------------

    @Test
    fun roundContourCoordinatesIsAnIdentityOnAnAlreadyIntegerCubicContour() {
        val square = fitClosedContourToCubics(denseSquare(x0 = 0, y0 = 0, side = 100, step = 5))
        val rounded = roundContourCoordinates(square)
        assertEquals(square, rounded)
        assertEquals(CurveFormat.CUBIC, rounded.format)
        assertEquals(0, rounded.points.size % 3, "CUBIC triple invariant")
        for (i in rounded.points.indices step 3) {
            assertTrue(rounded.points[i].onCurve && !rounded.points[i + 1].onCurve && !rounded.points[i + 2].onCurve)
        }
    }

    @Test
    fun roundContourCoordinatesAlsoRoundTripsAQuadraticContour() {
        val quadratic =
            Contour(
                listOf(
                    ContourPoint(Point(0, 0), onCurve = true),
                    ContourPoint(Point(50, 50), onCurve = false),
                    ContourPoint(Point(100, 0), onCurve = true),
                    ContourPoint(Point(50, -50), onCurve = false),
                ),
                CurveFormat.QUADRATIC,
            )
        assertEquals(quadratic, roundContourCoordinates(quadratic))
    }

    // -------------------------------------------------------------------------------------------
    // closePolylineToContour: the one meaningful half of "close/open contour" -- see its own KDoc
    // for the "open" half's honest scope cut.
    // -------------------------------------------------------------------------------------------

    @Test
    fun closesASinglePointToADegenerateContour() {
        val closed = closePolylineToContour(listOf(Point(5, 5)))
        assertEquals(3, closed.points.size)
        assertTrue(closed.points.all { it.point == Point(5, 5) })
        assertEquals(0.0, closed.signedArea())
    }

    @Test
    fun closesAnOpenPolylineIntoAClosedRectangle() {
        // A pen tool's own four placed points, not yet looped back to the first.
        val open = listOf(Point(0, 0), Point(200, 0), Point(200, 100), Point(0, 100))
        val closed = closePolylineToContour(open)

        assertEquals(CurveFormat.CUBIC, closed.format)
        assertEquals(12, closed.points.size, "4 straight edges, one per input point, closing back to the first")
        assertEquals(20000.0, kotlin.math.abs(closed.signedArea()), 1e-6)

        val onCurvePoints = closed.points.filterIndexed { i, _ -> i % 3 == 0 }.map { it.point }
        assertEquals(open, onCurvePoints)
    }

    @Test
    fun closePolylineToContourRejectsAnEmptyList() {
        assertFailsWith<IllegalArgumentException> { closePolylineToContour(emptyList()) }
    }

    // -------------------------------------------------------------------------------------------
    // straightLineCubic: exact-linear parametrization, relied on by knifeContour/simplifyContour's
    // own exact-coordinate test expectations.
    // -------------------------------------------------------------------------------------------

    @Test
    fun straightLineCubicIsAnExactLinearParametrization() {
        val from = Vec2(10.0, 20.0)
        val to = Vec2(110.0, 220.0)
        val segment = straightLineCubic(from, to)
        for (t in listOf(0.0, 0.1, 0.25, 0.5, 0.55, 0.9, 1.0)) {
            val expected = from + (to - from) * t
            val actual = segment.pointAt(t)
            assertEquals(expected.x, actual.x, 1e-9)
            assertEquals(expected.y, actual.y, 1e-9)
        }
    }
}
