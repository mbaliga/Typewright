// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.geometry

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** [PenPath]: clicks are corners, drags are smooth nodes with mirrored handles, and only a closed path becomes a contour. */
class PenPathTest {
    private fun shape(result: PenClosed): Contour = assertIs<PenClosed.Shape>(result).contour

    @Test
    fun fourClicksCloseToASquareOfCornersWrittenAsLines() {
        val path =
            PenPath()
                .plus(Point(0, 0))
                .plus(Point(100, 0))
                .plus(Point(100, 100))
                .plus(Point(0, 100))
        val contour = shape(path.closed())
        assertEquals(12, contour.points.size)
        assertEquals(4, contour.points.count { it.onCurve })
        assertTrue(contour.points.none { it.smooth }, "clicks are corners")
        // Every side is a retracted pair: controls sit on the side's own ends, which .glif stores as a plain line.
        assertEquals(contour.points[0].point, contour.points[1].point)
        assertEquals(contour.points[3].point, contour.points[2].point)
        assertEquals(10000.0, contour.signedArea(), 1e-9)
    }

    @Test
    fun aDragMakesASmoothNodeWithMirroredHandlesThatHolds() {
        val path =
            PenPath()
                .plus(Point(0, 0))
                .plus(Point(200, 0), pull = Point(40, 30))
                .plus(Point(200, 200))
                .plus(Point(0, 200))
        val contour = shape(path.closed())
        val node = 3
        assertTrue(contour.points[node].smooth)
        assertEquals(Point(240, 30), contour.points[node + 1].point, "outgoing handle is the pull")
        assertEquals(Point(160, -30), contour.points[node - 1].point, "incoming handle is its mirror")
        assertTrue(contour.smoothHolds(node), "the flag is true to the geometry")
        assertTrue(contour.brokenSmoothNodes().isEmpty())
    }

    @Test
    fun aDraggedStartClosesSmoothlyAndAClosingDragOverridesTheIncomingHandle() {
        val base = PenPath().plus(Point(0, 0), pull = Point(0, 50)).plus(Point(200, 100)).plus(Point(200, -100))
        val mirrored = shape(base.closed())
        assertEquals(Point(0, -50), mirrored.points[mirrored.points.size - 1].point, "start closes with the mirror handle")
        assertTrue(mirrored.points[0].smooth)
        val pulled = shape(base.closed(closingPull = Point(30, 30)))
        assertEquals(Point(-30, -30), pulled.points[pulled.points.size - 1].point)
        assertFalse(pulled.points[0].smooth, "handles no longer in line, so the node is a corner, never a false claim")
        assertTrue(pulled.brokenSmoothNodes().isEmpty())
    }

    @Test
    fun aZeroPullIsAClick() {
        val path = PenPath().plus(Point(0, 0), pull = Point(0, 0))
        assertFalse(path.anchors.single().hasHandles)
    }

    @Test
    fun tooFewAnchorsAndFlatPathsAreRefusedWithAReason() {
        assertEquals(PenRefusal.TOO_FEW, assertIs<PenClosed.Refused>(PenPath().plus(Point(0, 0)).closed()).why)
        assertEquals(PenRefusal.TOO_FEW, assertIs<PenClosed.Refused>(PenPath().plus(Point(0, 0)).plus(Point(10, 10)).closed()).why)
        val flat = PenPath().plus(Point(0, 0)).plus(Point(50, 50)).plus(Point(100, 100))
        assertEquals(PenRefusal.NO_AREA, assertIs<PenClosed.Refused>(flat.closed()).why)
    }

    @Test
    fun twoAnchorsWithACurveMakeALens() {
        val lens = PenPath().plus(Point(0, 0), pull = Point(50, 100)).plus(Point(200, 0), pull = Point(50, -100))
        val contour = shape(lens.closed())
        assertEquals(2, contour.points.count { it.onCurve })
        assertTrue(abs(contour.signedArea()) > 100.0)
    }

    @Test
    fun aQuadraticProjectCannotHoldHandlesAndSaysSo() {
        val curved = PenPath().plus(Point(0, 0)).plus(Point(100, 0), pull = Point(10, 10)).plus(Point(50, 80))
        assertEquals(PenRefusal.NEEDS_CUBIC, assertIs<PenClosed.Refused>(curved.closed(format = CurveFormat.QUADRATIC)).why)
        val flat = PenPath().plus(Point(0, 0)).plus(Point(100, 0)).plus(Point(50, 80))
        val contour = shape(flat.closed(format = CurveFormat.QUADRATIC))
        assertEquals(CurveFormat.QUADRATIC, contour.format)
        assertEquals(3, contour.points.size)
    }

    @Test
    fun previewSegmentsFollowTheHandlesIncludingTheLiveOne() {
        val path = PenPath().plus(Point(0, 0), pull = Point(30, 0)).plus(Point(100, 0))
        val segments = path.previewSegments(PenAnchor(Point(100, 100), Point(0, 20)))
        assertEquals(2, segments.size)
        assertEquals(Vec2(30.0, 0.0), segments[0].control1)
        assertEquals(Vec2(100.0, 0.0), segments[0].control2, "a clicked end has no handle")
        assertEquals(Vec2(100.0, 80.0), segments[1].control2, "the live anchor's incoming handle is the mirror of the drag")
        assertEquals(1, path.withoutLast().anchors.size)
        assertTrue(PenPath().isEmpty)
    }
}
