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
    fun aFingerReachesFurtherThanAMouse() {
        val glyph = bowl()
        val nearish = Vec2(100.0 + 15.0, 500.0)
        assertIs<Hit.Nothing>(DrawHit.hit(glyph, unitSpace, nearish.copy(y = 700.0), HitTolerance.MOUSE))
        assertIs<Hit.OnPoint>(DrawHit.hit(glyph, unitSpace, nearish, HitTolerance.FINGER))
        assertTrue(HitTolerance.FINGER.pointDp * 2 >= 44.0, "a finger target is at least 44 dp across")
    }

    @Test
    fun aHandleIsHitAndNamedOffCurve() {
        val hit = DrawHit.hit(bowl(), unitSpace, Vec2(100.0, 300.0), HitTolerance.MOUSE) // handle (0, 200)
        assertIs<Hit.OnPoint>(hit)
        assertEquals(PointRef(0, 1), hit.ref)
        assertFalse(hit.onCurve)
    }

    @Test
    fun aRetractedHandleSittingOnItsNodeCannotBeGrabbedSoTheNodeWins() {
        val line =
            Contour(
                listOf(on(0, 0), off(0, 0), off(100, 0), on(100, 0), off(100, 0), off(0, 0)),
                CurveFormat.CUBIC,
            )
        val hit = DrawHit.hit(Glyph("l", 100, listOf(line)), unitSpace, Vec2(100.0, 500.0), HitTolerance.MOUSE)
        assertIs<Hit.OnPoint>(hit)
        assertTrue(hit.onCurve, "the node, not one of its retracted handles")
        assertEquals(PointRef(0, 0), hit.ref)
    }

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

    @Test
    fun anotherNodesXAndYAreTargetsButTheMovingNodesOwnAreNot() {
        val glyph = bowl()
        val others = Snapper(glyph, metrics, 300, moving = setOf(PointRef(0, 0)), bandUnits = 10.0)
        val toNode = others.snap(Vec2(197.0, 33.0)) // node (200, 0) is the other node; x 197 is within 10 of 200
        assertTrue(toNode.targets.any { it.kind == SnapKind.NODE && it.axis == Axis.X })
        // The node at (0, 0) is moving, so it offers no target at x = 0 ... except the glyph's own origin line.
        val atOwnOldPlace = others.snap(Vec2(2.0, 100.0))
        assertTrue(atOwnOldPlace.targets.none { it.kind == SnapKind.NODE && it.axis == Axis.X })
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
