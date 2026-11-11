// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.Direction
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.direction
import com.asoc.typewright.core.geometry.outlineBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SvgDrawingReaderTest {
    private fun read(
        body: String,
        viewBox: String = "-160 -820 970 1150",
        template: SvgFrame? = SvgFrame(-160.0, -820.0, 970.0, 1150.0),
    ) = SvgDrawingReader.read("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"$viewBox\">$body</svg>", template)

    private fun drawing(
        body: String,
        viewBox: String = "-160 -820 970 1150",
        template: SvgFrame? = SvgFrame(-160.0, -820.0, 970.0, 1150.0),
    ): SvgReading.Drawing = assertIs(read(body, viewBox, template))

    private fun boundsOf(contours: List<Contour>) = checkNotNull(Glyph("g", 0, contours).outlineBounds())

    @Test
    fun aFilledPathIsReadWithTheVerticalFlippedSoTheBaselineIsZero() {
        val d = drawing("<g id=\"DRAW_HERE\"><path d=\"M 100 0 L 100 -700 L 300 -700 L 300 0 Z\" fill=\"#000\"/></g>")
        val b = boundsOf(d.contours)
        assertEquals(100, b.xMin)
        assertEquals(300, b.xMax)
        assertEquals(0, b.yMin, "the baseline stays on y = 0")
        assertEquals(700, b.yMax, "SVG's y runs down, a font's runs up")
        assertEquals(
            4,
            d.contours
                .single()
                .points.size / 3,
        )
    }

    @Test
    fun theGuidesAndTheReferenceLayerAreNotPartOfTheGlyph() {
        val d =
            drawing(
                "<g id=\"guides\"><path d=\"M 0 0 L 50 0 L 50 50 Z\"/><line x1=\"0\" y1=\"0\" x2=\"9\" y2=\"9\"/></g>" +
                    "<g id=\"reference_delete_me\"><path d=\"M 0 0 L 900 0 L 900 -900 Z\"/></g>" +
                    "<text x=\"1\" y=\"1\">T</text>" +
                    "<g id=\"DRAW_HERE\"><rect x=\"10\" y=\"-100\" width=\"80\" height=\"100\"/></g>",
            )
        assertEquals(1, d.contours.size)
        assertEquals(80, boundsOf(d.contours).xMax - boundsOf(d.contours).xMin)
    }

    @Test
    fun relativeCommandsCurvesAndSmoothContinuationsAreFollowed() {
        // A pebble with an absolute cubic, a smooth cubic continuation (S), a quadratic and a smooth quadratic (T).
        val d = drawing("<path d=\"M 0 0 C 0 -100 100 -100 100 0 S 200 -100 200 0 L 200 100 Q 150 150 100 100 T 0 100 Z\"/>")
        val contour = d.contours.single()
        assertEquals(CurveCount(4, 2), CurveCount.of(contour))
        // 'h', 'v' and 'l' relative.
        val box = drawing("<path d=\"m 10 0 h 50 v -50 h -50 z\"/>")
        val b = boundsOf(box.contours)
        assertEquals(listOf(10, 0, 60, 50), listOf(b.xMin, b.yMin, b.xMax, b.yMax))
    }

    @Test
    fun anArcIsCutIntoCubicPiecesThatStayOnTheCircle() {
        // Two half-circle arcs of radius 100 around (100, -100): a full circle.
        val d = drawing("<path d=\"M 0 -100 A 100 100 0 1 1 200 -100 A 100 100 0 1 1 0 -100 Z\"/>")
        val b = boundsOf(d.contours)
        assertTrue(b.xMin in -1..1 && b.xMax in 199..201, "x spans the diameter: $b")
        assertTrue(b.yMin in 0..1 && b.yMax in 199..201, "y spans the diameter: $b")
        val contour = d.contours.single()
        // Every on-curve point is on the circle to a unit or so.
        for (p in contour.points.filter { it.onCurve }) {
            val dx = p.point.x - 100.0
            val dy = p.point.y - 100.0
            val r = kotlin.math.sqrt(dx * dx + dy * dy)
            assertTrue(kotlin.math.abs(r - 100.0) < 1.5, "point ${p.point} is $r from the centre")
        }
        assertTrue(contour.points.size / 3 >= 4, "a half turn is at least two quarter-turn pieces")
    }

    @Test
    fun circlesEllipsesAndPolygonsAreShapes() {
        assertEquals(
            4,
            drawing("<circle cx=\"100\" cy=\"-100\" r=\"50\"/>")
                .contours
                .single()
                .points.size / 3,
        )
        assertEquals(
            4,
            drawing("<ellipse cx=\"100\" cy=\"-100\" rx=\"50\" ry=\"20\"/>")
                .contours
                .single()
                .points.size / 3,
        )
        assertEquals(
            3,
            drawing("<polygon points=\"0,0 100,0 50,-80\"/>")
                .contours
                .single()
                .points.size / 3,
        )
    }

    @Test
    fun transformsOnGroupsAndElementsAreApplied() {
        val d =
            drawing("<g transform=\"translate(100, -50)\"><rect x=\"0\" y=\"0\" width=\"10\" height=\"10\" transform=\"scale(2)\"/></g>")
        val b = boundsOf(d.contours)
        // The rect becomes 20 by 20 at (100, -50) in the file, so (100..120) across and 30..50 up in the font.
        assertEquals(listOf(100, 30, 120, 50), listOf(b.xMin, b.yMin, b.xMax, b.yMax))
        val rotated = drawing("<rect x=\"0\" y=\"0\" width=\"100\" height=\"10\" transform=\"rotate(90)\"/>")
        assertEquals(-10..0, boundsOf(rotated.contours).let { it.xMin..it.xMax })
    }

    @Test
    fun aFileSavedWithADifferentOriginIsPutBackWhereTheTemplateHadIt() {
        // The same stem, in a file whose viewBox starts at 0,0: the baseline is then 820 down and the left guide 160 in.
        val shifted =
            drawing(
                "<path d=\"M 260 820 L 260 120 L 460 120 L 460 820 Z\"/>",
                viewBox = "0 0 970 1150",
            )
        val b = boundsOf(shifted.contours)
        assertEquals(listOf(100, 0, 300, 700), listOf(b.xMin, b.yMin, b.xMax, b.yMax))
        // A different size is read as it stands, and says so.
        val odd = drawing("<path d=\"M 10 0 L 10 -10 L 20 -10 Z\"/>", viewBox = "0 0 500 500")
        assertTrue(odd.notes.single().contains("read as it stands"), odd.notes.toString())
        assertEquals(10, boundsOf(odd.contours).xMin)
    }

    @Test
    fun aShapeWithNoFillIsLeftOutAndTheNoteSaysToOutlineStrokes() {
        val d = drawing("<path d=\"M 0 0 L 100 0 L 100 -100 Z\"/><path d=\"M 0 0 L 400 0\" fill=\"none\" stroke=\"#000\"/>")
        assertEquals(1, d.contours.size)
        assertTrue(d.notes.single().contains("Outline Stroke"), d.notes.toString())
    }

    @Test
    fun aFileWithNothingToUseAnswersWhyInPlainWords() {
        assertTrue(assertIs<SvgReading.Failure>(read("<g id=\"DRAW_HERE\"></g>")).sentence.contains("no filled shapes"))
        assertTrue(assertIs<SvgReading.Failure>(SvgDrawingReader.read("hello")).sentence.contains("no <svg>"))
        assertTrue(assertIs<SvgReading.Failure>(SvgDrawingReader.read("<svg><path d=\"M 0 0 L 5 5\"")).sentence.contains("never closed"))
        assertTrue(assertIs<SvgReading.Failure>(read("<path d=\"M 0 0 L 100 0 L 200 0 Z\"/>")).sentence.contains("no area"))
    }

    @Test
    fun outerContoursRunCounterClockwiseAndHolesClockwise() {
        // A ring drawn with both subpaths the same way round: the inner one is turned to be a hole.
        val d = drawing("<path d=\"M 0 0 L 300 0 L 300 -300 L 0 -300 Z M 100 -100 L 200 -100 L 200 -200 L 100 -200 Z\"/>")
        assertEquals(2, d.contours.size)
        val byArea = d.contours.sortedByDescending { c -> boundsOf(listOf(c)).let { (it.xMax - it.xMin) } }
        assertEquals(Direction.COUNTER_CLOCKWISE, byArea[0].direction())
        assertEquals(Direction.CLOCKWISE, byArea[1].direction())
    }

    @Test
    fun commentsDeclarationsAndQuotedAnglesDoNotConfuseTheTagReader() {
        val text =
            "<?xml version=\"1.0\"?><!-- <path d=\"M 0 0 L 9 9\"/> --><!DOCTYPE svg><svg viewBox=\"0 0 1 1\">" +
                "<desc>a > b</desc><path d=\"M 0 0 L 100 0 L 100 -100 Z\" data-x=\"a>b\"/></svg>"
        assertEquals(1, assertIs<SvgReading.Drawing>(SvgDrawingReader.read(text)).contours.size)
    }

    private val box = "M 0 0 L 400 0 L 400 -100 L 0 -100 Z"
    private val pen = "M 0 0 L 400 0"

    private fun usable(body: String): SvgReading.Drawing = drawing("<path d=\"$box\"/>$body")

    @Test
    fun aStyleAttributeWithFillNoneMakesAShapeAStrokeAsInkscapeWritesIt() {
        val d = usable("<path d=\"$pen\" style=\"fill:none;stroke:#000000;stroke-width:12\"/>")
        assertEquals(1, d.contours.size, "only the filled box is kept")
        assertTrue(d.notes.single().contains("Outline Stroke"), d.notes.toString())
        // Spaces round the colon and a trailing important flag are the same declaration.
        assertEquals(1, usable("<path d=\"$pen\" style=\"stroke:#000; fill : none !important\"/>").contours.size)
        // A style attribute beats the fill attribute, in both directions.
        assertEquals(1, usable("<path d=\"$pen\" fill=\"#000\" style=\"fill:none\"/>").contours.size)
        assertEquals(2, usable("<path d=\"$box\" fill=\"none\" style=\"fill:#222\"/>").contours.size)
    }

    @Test
    fun aClassInAStyleBlockWithFillNoneMakesAShapeAStrokeAsIllustratorWritesIt() {
        val css = ".cls-1{fill:none;stroke:#000;stroke-miterlimit:10;}.cls-2{fill:#231f20;}"
        val d =
            usable(
                "<defs><style>$css</style></defs>" +
                    "<path class=\"cls-1\" d=\"$pen\"/><path class=\"cls-2\" d=\"M 10 -10 L 50 -10 L 50 -50 Z\"/>",
            )
        assertEquals(2, d.contours.size, "the box and the cls-2 triangle; the cls-1 line is a stroke")
        assertTrue(d.notes.single().contains("Outline Stroke"), d.notes.toString())
        // CDATA around the rules, a comment inside them, a comma list and an id selector all read the same.
        val wrapped = "<style type=\"text/css\"><![CDATA[ /* pen */ .a, #line {fill:none} ]]></style>"
        assertEquals(1, usable("$wrapped<path id=\"line\" d=\"$pen\"/><path class=\"a\" d=\"$pen\"/>").contours.size)
        // The more specific rule wins, whatever the order.
        val specific = "<style>#p{fill:#000} .s{fill:none}</style>"
        assertEquals(2, usable("$specific<path id=\"p\" class=\"s\" d=\"M 5 -5 L 60 -5 L 60 -60 Z\"/>").contours.size)
        // A class that no element uses changes nothing.
        assertEquals(2, usable("<style>.unused{fill:none}</style><path d=\"M 5 -5 L 60 -5 L 60 -60 Z\"/>").contours.size)
    }

    @Test
    fun fillNoneOnAGroupIsInheritedByTheShapesInsideItUnlessTheyAskForAFill() {
        val d =
            usable(
                "<g fill=\"none\" stroke=\"#000\"><path d=\"$pen\"/><g><path d=\"$pen\"/></g>" +
                    "<path fill=\"#000\" d=\"M 10 -10 L 50 -10 L 50 -50 Z\"/></g>",
            )
        assertEquals(2, d.contours.size, "the box, and the one shape inside the group that has its own fill")
        assertTrue(d.notes.single().startsWith("2 shapes had no fill"), d.notes.toString())
        // A group styled by class, and a group that is closed again: what follows it is no longer inside.
        val byClass = usable("<style>.g{fill:none}</style><g class=\"g\"><path d=\"$pen\"/></g><path d=\"M 10 -10 L 50 -10 L 50 -50 Z\"/>")
        assertEquals(2, byClass.contours.size)
        assertEquals(1, byClass.notes.size)
        // fill="inherit" takes the group's value.
        assertEquals(1, usable("<g style=\"fill:none\"><path fill=\"inherit\" d=\"$pen\"/></g>").contours.size)
    }

    @Test
    fun aDrawingMadeOnlyOfStrokesIsRefusedWithTheOutlineStrokeInstruction() {
        for (body in listOf(
            "<path d=\"$box\" style=\"fill:none;stroke:#000\"/>",
            "<style>.c{fill:none;stroke:#000}</style><path class=\"c\" d=\"$box\"/>",
            "<g style=\"fill:none\"><path d=\"$box\"/></g>",
        )) {
            val sentence = assertIs<SvgReading.Failure>(read(body)).sentence
            assertTrue(sentence.contains("Outline Stroke"), sentence)
            assertTrue(sentence.contains("strokes"), sentence)
        }
    }

    /** Segments of a cubic contour that are straight and that are curved. */
    private data class CurveCount(
        val curved: Int,
        val straight: Int,
    ) {
        companion object {
            fun of(c: Contour): CurveCount {
                var curved = 0
                var straight = 0
                val n = c.points.size
                for (i in 0 until n / 3) {
                    val a = c.points[3 * i].point
                    val b = c.points[(3 * i + 3) % n].point
                    if (c.points[3 * i + 1].point == a && c.points[3 * i + 2].point == b) straight++ else curved++
                }
                return CurveCount(curved, straight)
            }
        }
    }
}
