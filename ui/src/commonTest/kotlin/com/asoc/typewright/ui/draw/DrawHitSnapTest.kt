// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.geometry.Axis
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.pointAt
import com.asoc.typewright.core.geometry.segments
import com.asoc.typewright.core.geometry.withHandleMoved
import com.asoc.typewright.project.PointRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun on(
    x: Int,
    y: Int,
    smooth: Boolean = false,
) = ContourPoint(Point(x, y), onCurve = true, smooth = smooth)

private fun off(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = false)

/** A bowl: two cubic curves between (0,0) and (200,0), the top one peaking at y = 150 away from any node. */
private fun bowl() =
    Glyph(
        "u",
        300,
        listOf(
            Contour(
                listOf(on(0, 0), off(0, 200), off(200, 200), on(200, 0), off(200, -200), off(0, -200)),
                CurveFormat.CUBIC,
            ),
        ),
    )

/** One font unit is one screen dp, with font (0,0) at screen (100, 500). */
private val unitSpace = DrawSpace(Vec2(100.0, 500.0), dpPerUnit = 1.0)

class DrawHitTest {
    @Test
    fun aPointWithinReachIsHitAndReportsItsKind() {
        val hit = DrawHit.hit(bowl(), unitSpace, Vec2(103.0, 497.0), HitTolerance.MOUSE)
        assertIs<Hit.OnPoint>(hit)
        assertEquals(PointRef(0, 0), hit.ref)
        assertTrue(hit.onCurve)
    }

    @Test
    fun aFingerReachesFurtherThanAMouseFromTheSameSpot() {
        val glyph = bowl()
        // 15 dp from node 0 and further than a mouse's segment reach from either curve: only the larger tolerance gets there.
        val nearish = Vec2(100.0 + 15.0, 500.0)
        assertIs<Hit.Nothing>(DrawHit.hit(glyph, unitSpace, nearish, HitTolerance.MOUSE), "a mouse is exact")
        val finger = DrawHit.hit(glyph, unitSpace, nearish, HitTolerance.FINGER)
        assertIs<Hit.OnPoint>(finger)
        assertEquals(PointRef(0, 0), finger.ref)
        assertEquals(15.0, finger.distanceDp, 1e-9)
        assertTrue(HitTolerance.FINGER.pointDp * 2 >= 44.0, "a finger target is at least 44 dp across")
        assertTrue(HitTolerance.MOUSE.pointDp < 15.0 && HitTolerance.FINGER.pointDp > 15.0)
    }

    @Test
    fun aHandleIsHitAndNamedOffCurve() {
        val hit = DrawHit.hit(bowl(), unitSpace, Vec2(100.0, 300.0), HitTolerance.MOUSE) // handle (0, 200)
        assertIs<Hit.OnPoint>(hit)
        assertEquals(PointRef(0, 1), hit.ref)
        assertFalse(hit.onCurve)
    }

    /** Two corners joined by straight sides, each handle on its own node: node 3's retracted handle 2 comes BEFORE node 3 in the list. */
    private fun twoCornerLine() =
        Glyph(
            "l",
            100,
            listOf(Contour(listOf(on(0, 0), off(0, 0), off(100, 0), on(100, 0), off(100, 0), off(0, 0)), CurveFormat.CUBIC)),
        )

    @Test
    fun aRetractedHandleSittingOnItsNodeCannotBeGrabbedSoTheNodeWinsEvenWhereTheHandleComesFirst() {
        val atNode3 = unitSpace.toScreen(Vec2(100.0, 0.0))
        val hit = DrawHit.hit(twoCornerLine(), unitSpace, atNode3, HitTolerance.MOUSE)
        assertIs<Hit.OnPoint>(hit)
        assertTrue(hit.onCurve, "the node, not one of its retracted handles")
        assertEquals(PointRef(0, 3), hit.ref, "handle 2 is listed first and sits exactly there; only the skip lets the node through")
    }

    @Test
    fun aRetractedHandleOfASelectedNodeIsPickedAtItsMarkAHandsBreadthFromTheNode() {
        // A square: pull a handle out of the bottom side of corner 0 (retracted handle 1) by grabbing its mark.
        val square = squareGlyph()
        val node = PointRef(0, 0)
        val marks = RetractedHandles.marks(square, unitSpace, DrawSelection(setOf(node)))
        val mark = marks.single { it.ref == PointRef(0, 1) }
        assertEquals(RetractedHandles.MARK_DISTANCE_DP / unitSpace.dpPerUnit, mark.offset.length(), 1e-9, "a fixed distance on screen")
        assertTrue(mark.offset.x > 0.0 && mark.offset.y == 0.0, "along the bottom side, towards the next corner")
        val hit = DrawHit.hit(square, unitSpace, unitSpace.toScreen(mark.at), HitTolerance.MOUSE, DrawSelection(setOf(node)))
        assertIs<Hit.OnPoint>(hit)
        assertEquals(PointRef(0, 1), hit.ref)
        assertTrue(!hit.onCurve)
        assertEquals(mark.offset, hit.markOffset)
        // Without the node selected there is nothing there: the mark is shown for what you are working on, not for every corner.
        val unselected = DrawHit.hit(square, unitSpace, unitSpace.toScreen(mark.at), HitTolerance.MOUSE)
        assertTrue(unselected !is Hit.OnPoint, "the spot is only the bottom side: $unselected")
        assertEquals(emptyList(), RetractedHandles.marks(square, unitSpace, DrawSelection.EMPTY))
    }

    @Test
    fun theMarkOfAShortSideStaysBetweenItsTwoNodes() {
        val tiny = Glyph("t", 100, listOf(rectangleContour(0, 0, 20, 20)))
        val mark = RetractedHandles.marks(tiny, unitSpace, DrawSelection(setOf(PointRef(0, 0)))).single { it.ref == PointRef(0, 1) }
        assertTrue(mark.offset.length() <= 20 * 0.4 + 1e-9, "no more than 40% of a 20 unit side: ${mark.offset.length()}")
    }

    @Test
    fun aHandleThatHasBeenPulledOutIsAnOrdinaryHandleWithNoMark() {
        val square = squareGlyph()
        val pulled = square.copy(contours = listOf(square.contours[0].withHandleMoved(1, 40, 0)))
        assertEquals(
            emptyList(),
            RetractedHandles.marks(pulled, unitSpace, DrawSelection(setOf(PointRef(0, 0)))).filter {
                it.ref ==
                    PointRef(0, 1)
            },
        )
    }

    private fun rectangleContour(
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ): Contour {
        val corners = listOf(Point(x0, y0), Point(x1, y0), Point(x1, y1), Point(x0, y1))
        return Contour(
            corners.flatMapIndexed { i, p ->
                listOf(ContourPoint(p, true), ContourPoint(p, false), ContourPoint(corners[(i + 1) % 4], false))
            },
            CurveFormat.CUBIC,
        )
    }

    private fun squareGlyph() = Glyph("s", 300, listOf(rectangleContour(0, 0, 200, 200)))

    @Test
    fun aCurveBetweenNodesIsHitAtTheParameterNearestThePointer() {
        val glyph = bowl()
        val onCurve = glyph.contours[0].segments()[0].pointAt(0.5)
        val screen = unitSpace.toScreen(onCurve)
        val hit = DrawHit.hit(glyph, unitSpace, Vec2(screen.x + 2.0, screen.y - 2.0), HitTolerance.MOUSE)
        assertIs<Hit.OnSegment>(hit)
        assertEquals(SegmentKey(0, 0), hit.key)
        assertEquals(0.5, hit.t, 0.05)
    }

    @Test
    fun aPointBeatsTheCurveItLiesOn() {
        val hit = DrawHit.hit(bowl(), unitSpace, Vec2(100.0, 501.0), HitTolerance.MOUSE)
        assertIs<Hit.OnPoint>(hit)
    }

    @Test
    fun emptyCanvasIsNothing() {
        assertEquals(Hit.Nothing, DrawHit.hit(bowl(), unitSpace, Vec2(400.0, 100.0), HitTolerance.FINGER))
    }

    @Test
    fun aRectangleCollectsThePointsInsideItWhicheverCornerItStartedFrom() {
        val glyph = bowl()
        val a = unitSpace.toScreen(Vec2(-10.0, 10.0))
        val b = unitSpace.toScreen(Vec2(210.0, -10.0))
        val forward = DrawHit.pointsInRect(glyph, unitSpace, a, b)
        val backward = DrawHit.pointsInRect(glyph, unitSpace, b, a)
        assertEquals(setOf(PointRef(0, 0), PointRef(0, 3)), forward)
        assertEquals(forward, backward)
    }

    @Test
    fun aLassoCollectsThePointsInsideItsLoop() {
        val glyph = bowl()
        val loop = listOf(Vec2(50.0, 450.0), Vec2(320.0, 450.0), Vec2(320.0, 520.0), Vec2(50.0, 520.0))
        assertEquals(setOf(PointRef(0, 0), PointRef(0, 3)), DrawHit.pointsInPolygon(glyph, unitSpace, loop))
        assertEquals(emptySet(), DrawHit.pointsInPolygon(glyph, unitSpace, listOf(Vec2(0.0, 0.0), Vec2(1.0, 1.0))))
    }
}

class DrawSnapTest {
    private val metrics =
        drawMetricsFor(UfoFontInfo(unitsPerEm = 1000, xHeight = 500, capHeight = 700, ascender = 800, descender = -200))

    @Test
    fun aDraggedPointSnapsToTheNearestMetricLineWithinTheBand() {
        val snapper = Snapper(bowl(), metrics, advance = 300, moving = setOf(PointRef(0, 3)), bandUnits = 12.0)
        val result = snapper.snap(Vec2(150.0, 505.0))
        assertTrue(result.snapped)
        assertEquals(-5.0, result.dy, 1e-9)
        val target = result.targets.single { it.axis == Axis.Y }
        assertEquals(SnapKind.METRIC, target.kind)
        assertEquals("x-height 500", target.label)
    }

    @Test
    fun theSnapLetsGoOnceThePointIsPastTheBand() {
        val snapper = Snapper(bowl(), metrics, advance = 300, moving = emptySet(), bandUnits = 12.0)
        val result = snapper.snap(Vec2(150.0, 520.0))
        assertFalse(result.snapped)
        assertEquals(SnapResult.NONE, result)
    }

    @Test
    fun axesSnapIndependentlyToTheNearestOfSeveralCandidates() {
        val snapper = Snapper(bowl(), metrics, advance = 300, moving = emptySet(), bandUnits = 12.0)
        // y 697 is 3 from the cap height, x 296 is 4 from the advance.
        val result = snapper.snap(Vec2(296.0, 697.0))
        assertEquals(4.0, result.dx, 1e-9)
        assertEquals(3.0, result.dy, 1e-9)
        assertEquals(setOf(SnapKind.GLYPH_EDGE, SnapKind.METRIC), result.targets.map { it.kind }.toSet())
    }

    /** Two nodes at different heights, well away from the origin line and from every metric line: (80, 60) and (240, 120). */
    private fun offsetBowl() =
        Glyph(
            "u",
            400,
            listOf(
                Contour(
                    listOf(on(80, 60), off(80, 260), off(240, 260), on(240, 120), off(240, -140), off(80, -140)),
                    CurveFormat.CUBIC,
                ),
            ),
        )

    @Test
    fun anotherNodesXAndYAreTargetsButTheMovingNodesOwnAreNot() {
        val glyph = offsetBowl()
        val moving = Snapper(glyph, metrics, 400, moving = setOf(PointRef(0, 0)), bandUnits = 10.0)
        // Node (240, 120) stays: 3 units short of its x and 2 above its y, both within the band.
        val toOther = moving.snap(Vec2(237.0, 122.0))
        assertEquals(
            setOf(Axis.X, Axis.Y),
            toOther.targets
                .filter { it.kind == SnapKind.NODE }
                .map { it.axis }
                .toSet(),
        )
        assertEquals(3.0, toOther.dx, 1e-9)
        assertEquals(-2.0, toOther.dy, 1e-9)
        // Node (80, 60) is the one being dragged, so where it started is no target for itself: 2 units from its old x and 2 from its old y.
        val atOwnOldPlace = moving.snap(Vec2(82.0, 62.0))
        assertEquals(emptyList(), atOwnOldPlace.targets.filter { it.kind == SnapKind.NODE }, "a node does not snap to where it was")
        assertEquals(SnapResult.NONE, atOwnOldPlace)
        // The same probe with nothing moving does find the node: the exclusion is what made the difference.
        val nothingMoving = Snapper(glyph, metrics, 400, moving = emptySet(), bandUnits = 10.0)
        assertEquals(
            setOf(Axis.X, Axis.Y),
            nothingMoving
                .snap(Vec2(82.0, 62.0))
                .targets
                .filter {
                    it.kind == SnapKind.NODE
                }.map { it.axis }
                .toSet(),
        )
    }

    @Test
    fun aCurveExtremeThatNoNodeSitsAtIsATarget() {
        // The top curve of the bowl peaks at y = 150, between its two nodes at y = 0; no node is there.
        val glyph = bowl()
        assertTrue(glyph.contours[0].points.none { it.onCurve && it.point.y == 150 })
        val snapper = Snapper(glyph, metrics, 300, moving = emptySet(), bandUnits = 10.0)
        val result = snapper.snap(Vec2(100.0, 146.0))
        val target = result.targets.single { it.axis == Axis.Y }
        assertEquals(SnapKind.EXTREME, target.kind)
        assertEquals(4.0, result.dy, 1e-9)
        assertEquals(150.0, target.at.y, 1e-9)
    }

    @Test
    fun anExtremeOfACurveThatIsBeingMovedIsNotATarget() {
        val glyph = bowl()
        val moving = setOf(PointRef(0, 1)) // a handle of the top curve: its extreme moves with it
        val snapper = Snapper(glyph, metrics, 300, moving = moving, bandUnits = 10.0)
        val result = snapper.snap(Vec2(100.0, 146.0))
        assertTrue(result.targets.none { it.kind == SnapKind.EXTREME && it.axis == Axis.Y && it.at.y == 150.0 })
    }

    @Test
    fun constrainingHoldsTheMoveToAnAxisOrTheDiagonal() {
        assertEquals(10.0 to 0.0, constrainToAxes(10.0, 2.0))
        assertEquals(0.0 to -10.0, constrainToAxes(1.0, -10.0))
        val diagonal = constrainToAxes(10.0, -8.0)
        assertEquals(9.0, diagonal.first, 1e-9)
        assertEquals(-9.0, diagonal.second, 1e-9)
    }
}

class DrawMetricsTest {
    @Test
    fun aFontGetsOnlyTheMetricLinesItNames() {
        val metrics = drawMetricsFor(UfoFontInfo(unitsPerEm = 1000, capHeight = 700))
        assertEquals(listOf(MetricKind.CAP_HEIGHT, MetricKind.BASELINE), metrics.lines.map { it.kind })
        assertNull(metrics.lines.firstOrNull { it.kind == MetricKind.X_HEIGHT }, "no x-height line is invented")
    }

    @Test
    fun linesAreOrderedTopDownAndLabelledWithTheirValue() {
        val metrics = drawMetricsFor(UfoFontInfo(unitsPerEm = 1000, xHeight = 500, capHeight = 700, ascender = 800, descender = -200))
        assertEquals(listOf(800, 700, 500, 0, -200), metrics.lines.map { it.y })
        assertEquals("x-height 500", metrics.lines[2].text)
        assertEquals((-200) to 800, metrics.span)
    }

    @Test
    fun overshootZonesComeFromThePostScriptBlueValues() {
        val info =
            UfoFontInfo(
                unitsPerEm = 1000,
                other =
                    listOf(
                        "postscriptBlueValues" to PlistValue.PArray(listOf(-12, 0, 500, 512).map { PlistValue.PInteger(it.toLong()) }),
                        "postscriptOtherBlues" to PlistValue.PArray(listOf(-212L, -200L).map { PlistValue.PInteger(it) }),
                    ),
            )
        assertEquals(listOf(OvershootZone(-12, 0), OvershootZone(500, 512), OvershootZone(-212, -200)), drawMetricsFor(info).zones)
        assertEquals(emptyList(), drawMetricsFor(UfoFontInfo(unitsPerEm = 1000)).zones)
    }

    @Test
    fun theNearestLineIsFoundOnlyWithinReach() {
        val metrics = drawMetricsFor(UfoFontInfo(unitsPerEm = 1000, xHeight = 500, capHeight = 700))
        assertEquals(500, metrics.nearestLine(512.0, 20.0)?.y)
        assertNull(metrics.nearestLine(600.0, 20.0))
    }

    @Test
    fun glyphLinesReportTheSidebearingsTheCommandSets() {
        val lines = GlyphLines.of(bowl())
        assertEquals(300, lines.advance)
        assertEquals(0, lines.lsb)
        assertEquals(100, lines.rsb)
        assertEquals(GlyphLines(250, null, null), GlyphLines.of(Glyph("space", 250, emptyList())))
    }
}
