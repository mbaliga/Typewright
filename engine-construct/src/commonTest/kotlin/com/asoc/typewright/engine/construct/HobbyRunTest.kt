// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.brokenSmoothNodes
import com.asoc.typewright.core.geometry.polygonContour
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** [withHobbyHandles]: the handles between neighbouring selected nodes become the Hobby spline's, and nothing else moves. */
class HobbyRunTest {
    private val diamond: Contour =
        polygonContour(listOf(Point(100, 0), Point(0, 100), Point(-100, 0), Point(0, -100)), CurveFormat.CUBIC)

    private fun done(result: HobbyRunResult): HobbyRunResult.Done = assertIs<HobbyRunResult.Done>(result)

    @Test
    fun everyNodeOfARingSelectedGivesTheCircleConstantOnFourExtremes() {
        val result = done(diamond.withHobbyHandles(setOf(0, 3, 6, 9)))
        val contour = result.contour
        // Four points on a circle: the handle is the well-known 0.5523 of the radius, along the tangent.
        val out = contour.points[1].point
        assertTrue(abs(out.x - 100) <= 1 && abs(out.y - 55) <= 1, "handle was $out")
        assertEquals(4, result.segmentsChanged)
        assertEquals(1, result.runs)
        for (k in 0 until 4) assertEquals(diamond.points[3 * k].point, contour.points[3 * k].point, "node $k does not move")
    }

    @Test
    fun aRunInsideAContourChangesOnlyTheSegmentsBetweenItsNodes() {
        val arc =
            polygonContour(
                listOf(Point(0, 0), Point(60, 80), Point(150, 110), Point(240, 80), Point(300, 0), Point(150, -60)),
                CurveFormat.CUBIC,
            )
        val result = done(arc.withHobbyHandles(setOf(3, 6, 9)))
        val after = result.contour
        assertEquals(2, result.segmentsChanged)
        for (i in after.points.indices) {
            val inRun = i in 4..5 || i in 7..8
            if (!inRun) assertEquals(arc.points[i].point, after.points[i].point, "point $i is outside the run and stays")
        }
        assertTrue(after.points[4].point != arc.points[4].point)
        assertTrue(after.brokenSmoothNodes().isEmpty())
    }

    @Test
    fun twoNodesAreRefusedBecauseTheyOnlyMakeAStraightSide() {
        val refused = assertIs<HobbyRunResult.Refused>(diamond.withHobbyHandles(setOf(0, 3)))
        assertTrue(refused.reason.contains("three or more"), refused.reason)
        // Three nodes that are not neighbours are three runs of one.
        val six =
            polygonContour(listOf(Point(0, 0), Point(10, 0), Point(20, 5), Point(30, 0), Point(40, 9), Point(50, 0)), CurveFormat.CUBIC)
        assertIs<HobbyRunResult.Refused>(six.withHobbyHandles(setOf(0, 6, 12)))
    }

    @Test
    fun aQuadraticContourIsRefusedAndTheSameHandlesAgainAreSaidToBeDone() {
        val quad = polygonContour(listOf(Point(0, 0), Point(100, 0), Point(100, 100)), CurveFormat.QUADRATIC)
        assertTrue(assertIs<HobbyRunResult.Refused>(quad.withHobbyHandles(setOf(0, 1, 2))).reason.contains("quadratic"))
        val once = done(diamond.withHobbyHandles(setOf(0, 3, 6, 9))).contour
        val again = assertIs<HobbyRunResult.Refused>(once.withHobbyHandles(setOf(0, 3, 6, 9)))
        assertTrue(again.reason.contains("already"), again.reason)
    }

    @Test
    fun neighbouringNodesOnTheSameSpotAreRefused() {
        val twin = polygonContour(listOf(Point(0, 0), Point(0, 0), Point(50, 40), Point(100, 0)), CurveFormat.CUBIC)
        assertTrue(assertIs<HobbyRunResult.Refused>(twin.withHobbyHandles(setOf(0, 3, 6))).reason.contains("same spot"))
    }
}
