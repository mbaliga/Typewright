// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.core.geometry.CurveSegment
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.lineSegmentCount
import com.asoc.typewright.core.geometry.segments
import com.asoc.typewright.engine.trace.TraceStage
import com.asoc.typewright.engine.trace.traceOutlines
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.PrintSheets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A clean drawing is traced as it is drawn, however small its round parts, and a rough contour never widens the tests for a clean
 * one beside it. Hyle Deco has no small round feature, so the fixture counts alone could not show either.
 */
class CleanSmallShapesTraceTest {
    private val tFile = PrintSheets.glyphs(PrintScript.LATIN).first { it.glyphName == "T" }.templateFileName

    private fun import(draw: String): SvgImport.Drawing {
        val svg =
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="-160 -820 970 1150" width="970" height="1150">
<g id="guides"></g><g id="reference_delete_me"></g><g id="DRAW_HERE">$draw</g></svg>"""
        val read = SvgTemplateImport.import(tFile, svg)
        assertIs<SvgImport.Drawing>(read, read.sentence)
        return read
    }

    @Test
    fun anIWithACleanRoundDotKeepsItsStemAndItsDotExactly() {
        // A stem 80 wide and 500 tall under a dot of radius 50, as an i is drawn.
        val read = import("""<path d="M100,0 H180 V-500 H100 Z"/><circle cx="140" cy="-650" r="50"/>""")
        val trace = traceOutlines(read.outlines)
        val fit = trace.stage(TraceStage.FIT)
        assertTrue(!fit.sentence.contains("wobbles"), "a clean drawing is not called rough: ${fit.sentence}")
        assertTrue(!fit.sentence.contains("raised"), fit.sentence)
        val contours = trace.contours
        assertEquals(2, contours.size)
        // The stem is four corners and four straight sides, exactly where it was drawn.
        val stem = contours.single { it.lineSegmentCount() == it.segments().size }
        val corners = stem.points.filter { it.onCurve }.map { it.point }
        assertEquals(4, corners.size)
        assertEquals(listOf(100, 180), listOf(corners.minOf { it.x }, corners.maxOf { it.x }))
        assertEquals(listOf(0, 500), listOf(corners.minOf { it.y }, corners.maxOf { it.y }))
        // The dot is four curves and has not shrunk.
        val dot = contours.single { it !== stem }
        assertEquals(4, dot.points.count { it.onCurve })
        assertEquals(700, assertNotNull(assertNotNull(trace.review).outlineBounds).yMax, "the dot's top is where it was drawn")
        val dotXs = dot.points.filter { it.onCurve }.map { it.point.x }
        assertTrue(dotXs.max() - dotXs.min() in 99..101, "dot width ${dotXs.max() - dotXs.min()}")
    }

    /** A rectangle's outline as straight segments with grain of about [amplitude] units on every side, from a fixed sequence. */
    private fun roughRectangle(
        left: Double,
        bottom: Double,
        width: Double,
        height: Double,
        amplitude: Double,
    ): List<CurveSegment> {
        var state = 12345L

        fun grain(): Double {
            state = state * 6364136223846793005L + 1442695040888963407L
            return (((state ushr 33) % 2001) / 1000.0 - 1.0) * amplitude
        }
        val step = 6.0
        val corners =
            listOf(Vec2(left, bottom), Vec2(left + width, bottom), Vec2(left + width, bottom + height), Vec2(left, bottom + height))
        val points = mutableListOf<Vec2>()
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % 4]
            val along = b - a
            val normal = Vec2(along.y, -along.x) * (1.0 / along.length())
            val count = (along.length() / step).toInt()
            for (k in 0 until count) {
                val edge = if (k == 0) 0.0 else grain()
                points += a + along * (k.toDouble() / count) + normal * edge
            }
        }
        return points.indices.map { CurveSegment.Line(points[it], points[(it + 1) % points.size]) }
    }

    /** A clean bar 300 wide and 200 tall whose top has a step 9 units deep halfway along, as a drawn detail smaller than a raised tolerance. */
    private fun steppedBar(
        left: Double,
        bottom: Double,
    ): List<CurveSegment> {
        val c =
            listOf(
                Vec2(left, bottom),
                Vec2(left + 300, bottom),
                Vec2(left + 300, bottom + 200),
                Vec2(left + 150, bottom + 200),
                Vec2(left + 150, bottom + 191),
                Vec2(left, bottom + 191),
            )
        return c.indices.map { CurveSegment.Line(c[it], c[(it + 1) % c.size]) }
    }

    @Test
    fun aRoughContourDoesNotWidenTheTestsForACleanOneBesideIt() {
        // A rough bar raises the fit tolerance to several units for itself. The clean bar beside it has a 9-unit step that a tolerance
        // raised for the whole glyph would fit away; it is the person's drawing and must stay.
        val rough = roughRectangle(0.0, 0.0, 700.0, 300.0, 3.0)
        val clean = steppedBar(900.0, 100.0)
        val trace = traceOutlines(listOf(rough, clean))
        assertTrue(
            trace.stage(TraceStage.FIT).sentence.contains("wobbles"),
            "the rough bar is read as rough: ${trace.stage(TraceStage.FIT).sentence}",
        )
        val bar = trace.contours.single { c -> c.points.filter { it.onCurve }.all { it.point.x >= 890 } }
        assertEquals(bar.segments().size, bar.lineSegmentCount(), "the clean bar stays straight sides: ${bar.points}")
        assertEquals(6, bar.points.count { it.onCurve }, "the step is kept: ${bar.points}")
        assertTrue(assertNotNull(trace.review).maxDeviation < 15.0)
    }

    @Test
    fun anEvenOddDrawingWhoseCounterRunsTheSameWayIsReviewedAsHollow() {
        // An o drawn as one path with fill-rule evenodd: the inner loop winds the same way as the outer. Filled non-zero, the reference
        // would be solid and the (hollow) redrawn outline would disagree over the whole counter.
        val read = import("""<path fill-rule="evenodd" d="M0,0 H400 V-400 H0 Z M100,-100 H300 V-300 H100 Z"/>""")
        val trace = traceOutlines(read.outlines)
        val review = assertNotNull(trace.review)
        assertEquals(2, trace.contours.size)
        assertTrue(
            review.mismatchFraction < 0.02,
            "a faithful trace of a hollow shape disagrees with itself over ${review.mismatchFraction}",
        )
    }
}
