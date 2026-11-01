// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.core.geometry.CurveSegment
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.engine.trace.TraceParameters
import com.asoc.typewright.engine.trace.TraceStage
import com.asoc.typewright.engine.trace.traceOutlines
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.PrintSheets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading the pack's SVG files into outlines. The text below is laid out exactly as the pack's own files are (a `guides` layer,
 * a faint `reference_delete_me` layer, an empty `DRAW_HERE` layer and a label); the tests in `SvgPackZipTest` read the real files.
 */
class SvgTemplateImportTest {
    private val tFile = PrintSheets.glyphs(PrintScript.LATIN).first { it.glyphName == "T" }.templateFileName

    private fun template(
        draw: String,
        viewBox: String = "-160 -820 970 1150",
        reference: String = """<path d="M0,0 L100,0 L100,-100 Z" fill="#111" fill-rule="evenodd"/>""",
    ) = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="$viewBox" width="970" height="1150">
<g id="guides"><line x1="-140" y1="0" x2="790" y2="0" stroke="#c0392b" stroke-width="3"  opacity="0.5"/></g>
<g id="reference_delete_me" opacity="0.22">$reference</g>
<g id="DRAW_HERE">$draw</g>
<text x="-140" y="-780" font-family="monospace" font-size="42" fill="#111">U+0054  T  LATIN_CAPITAL_LETTER_T</text>
</svg>"""

    private fun drawing(svg: String): SvgImport.Drawing {
        val read = SvgTemplateImport.import(tFile, svg)
        assertIs<SvgImport.Drawing>(read, read.sentence)
        return read
    }

    private fun starts(loop: List<CurveSegment>): List<Vec2> = loop.map { it.start }

    @Test
    fun aTemplateIsMatchedToItsGlyphByFileName() {
        val match = assertNotNull(SvgTemplateImport.matchTemplate("svg/Latin/$tFile"))
        assertEquals(PrintScript.LATIN, match.first)
        assertEquals("T", match.second.glyphName)
        assertEquals("T", assertNotNull(SvgTemplateImport.matchTemplate(tFile)).second.glyphName)
        // A renumbered copy keeps its name after the number.
        assertEquals("T", assertNotNull(SvgTemplateImport.matchTemplate("123_LATIN_CAPITAL_LETTER_T.svg")).second.glyphName)
        assertNull(SvgTemplateImport.matchTemplate("holiday.svg"))
    }

    @Test
    fun theFileNameDecidesTheGlyphAndNotTheContents() {
        val other = PrintSheets.glyphs(PrintScript.LATIN).first { it.glyphName == "H" }.templateFileName
        val read = SvgTemplateImport.import(other, template("""<path d="M0,0 H100 V-700 H0 Z"/>"""))
        assertEquals("H", assertIs<SvgImport.Drawing>(read).glyph.glyphName)
    }

    @Test
    fun aDrawnShapeComesBackInFontUnitsWithYUp() {
        val read = drawing(template("""<path d="M0,0 H100 V-700 H0 Z"/>"""))
        assertEquals(1, read.outlines.size)
        // The pack's y runs down from the baseline, so the cap line 700 is written -700 and is 700 in a font.
        assertEquals(listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(100.0, 700.0), Vec2(0.0, 700.0)), starts(read.outlines.single()))
    }

    @Test
    fun theGuidesTheReferenceGlyphAndTheLabelAreNotADrawing() {
        val blank = SvgTemplateImport.import(tFile, template(draw = ""))
        assertIs<SvgImport.Blank>(blank)
        assertEquals("T", blank.glyph.glyphName)
        assertTrue(blank.sentence.contains("nothing drawn"), blank.sentence)
    }

    @Test
    fun aDrawingMadeOfStrokesIsRefusedWithWhatToDo() {
        val read = SvgTemplateImport.import(tFile, template("""<path d="M0,0 L100,-700" fill="none" stroke="#000" stroke-width="40"/>"""))
        assertIs<SvgImport.Unusable>(read)
        assertTrue(read.sentence.contains("Outline Stroke"), read.sentence)
        assertEquals("T", read.glyph?.glyphName)
    }

    @Test
    fun aStrokeBesideAFilledShapeIsLeftOutAndSaid() {
        val read =
            drawing(
                template("""<path d="M0,0 H100 V-700 H0 Z"/><path d="M0,0 L100,-700" fill="none" stroke="#000"/>"""),
            )
        assertEquals(1, read.outlines.size)
        assertTrue(read.sentence.contains("left out"), read.sentence)
    }

    @Test
    fun groupTransformsAndStylesAreFollowed() {
        val read =
            drawing(
                template(
                    """<g transform="translate(50 0)"><path d="M0,0 H100 V-700 H0 Z" style="fill:#111;stroke:none"/></g>""",
                ),
            )
        assertEquals(Vec2(50.0, 0.0), starts(read.outlines.single()).first())
        val scaled = drawing(template("""<g transform="scale(2)"><path d="M0,0 H100 V-350 H0 Z"/></g>"""))
        assertEquals(Vec2(200.0, 700.0), starts(scaled.outlines.single())[2])
        val matrix = drawing(template("""<path transform="matrix(1 0 0 1 10 -20)" d="M0,0 H100 V-700 H0 Z"/>"""))
        assertEquals(Vec2(10.0, 20.0), starts(matrix.outlines.single()).first())
    }

    @Test
    fun aFileWhoseViewBoxWasMovedIsPutBackWhereThePackHasIt() {
        // An editor that re-bases the viewBox to the origin shifts every coordinate by (160, 820).
        val read = drawing(template("""<path d="M160,820 h100 v-700 h-100 z"/>""", viewBox = "0 0 970 1150"))
        assertEquals(Vec2(0.0, 0.0), starts(read.outlines.single()).first())
        assertEquals(Vec2(100.0, 700.0), starts(read.outlines.single())[2])
        assertTrue(read.sentence.contains("viewBox"), read.sentence)
    }

    @Test
    fun relativeCommandsAndImplicitLinesAreRead() {
        val read = drawing(template("""<path d="m0,0 l100,0 0,-700 -100,0 z"/>"""))
        assertEquals(listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(100.0, 700.0), Vec2(0.0, 700.0)), starts(read.outlines.single()))
        val horizontal = drawing(template("""<path d="M10 0 h90 v-700 H10 Z"/>"""))
        assertEquals(Vec2(10.0, 700.0), starts(horizontal.outlines.single())[3])
    }

    @Test
    fun curvesAreKeptAsCurvesAndSmoothCommandsReflectTheLastControl() {
        val cubic = drawing(template("""<path d="M0,0 C0,-100 100,-100 100,0 S200,100 200,0 Z"/>"""))
        val segments = cubic.outlines.single()
        assertIs<CurveSegment.Cubic>(segments[0])
        val second = assertIs<CurveSegment.Cubic>(segments[1])
        // S reflects the first curve's second control (100,-100) about its end (100,0): (100, 100) in the file, so -100 in font y.
        assertEquals(Vec2(100.0, -100.0), second.control1)
        val quad = drawing(template("""<path d="M0,0 Q50,-100 100,0 T200,0 Z"/>"""))
        val t = assertIs<CurveSegment.Quadratic>(quad.outlines.single()[1])
        assertEquals(Vec2(150.0, -100.0), t.control)
    }

    @Test
    fun anArcBecomesCubicsThatStayOnTheCircle() {
        val read = drawing(template("""<path d="M100,-300 a100,100 0 1,0 -200,0 a100,100 0 1,0 200,0 z"/>"""))
        val segments = read.outlines.single()
        assertTrue(segments.all { it is CurveSegment.Cubic })
        assertTrue(segments.size >= 4)
        for (segment in segments) {
            val p = segment.start
            assertEquals(100.0, kotlin.math.sqrt(p.x * p.x + (p.y - 300.0) * (p.y - 300.0)), 1e-6)
        }
    }

    @Test
    fun rectanglesCirclesEllipsesAndPolygonsAreShapes() {
        val rect = drawing(template("""<rect x="0" y="-700" width="100" height="700"/>"""))
        assertEquals(4, rect.outlines.single().size)
        val rounded = drawing(template("""<rect x="0" y="-700" width="100" height="700" rx="20"/>"""))
        assertEquals(8, rounded.outlines.single().size)
        val circle = drawing(template("""<circle cx="300" cy="-300" r="100"/>"""))
        assertEquals(4, circle.outlines.single().size)
        val ellipse = drawing(template("""<ellipse cx="300" cy="-300" rx="100" ry="50"/>"""))
        assertEquals(4, ellipse.outlines.single().size)
        val polygon = drawing(template("""<polygon points="0,0 100,0 50,-700"/>"""))
        assertEquals(3, polygon.outlines.single().size)
    }

    @Test
    fun aCompoundPathGivesOneLoopPerSubpath() {
        val read = drawing(template("""<path d="M0,0 H300 V-300 H0 Z M100,-100 V-200 H200 V-100 Z" fill-rule="evenodd"/>"""))
        assertEquals(2, read.outlines.size)
    }

    @Test
    fun anOpenSubpathIsClosedAsAFillClosesIt() {
        val read = drawing(template("""<path d="M0,0 L100,0 L100,-100"/>"""))
        val loop = read.outlines.single()
        assertEquals(3, loop.size)
        assertEquals(loop.first().start, loop.last().end)
    }

    @Test
    fun aFileThatIsNotATemplateSaysSoAndNastaliqHasNoSheet() {
        val unknown = SvgTemplateImport.import("holiday.svg", template("""<path d="M0,0 H100 V-700 H0 Z"/>"""))
        assertIs<SvgImport.Unusable>(unknown)
        assertTrue(unknown.sentence.contains("file names"), unknown.sentence)
        assertNull(unknown.glyph)
        val broken = SvgTemplateImport.import(tFile, template("""<path d="X 10 10"/>"""))
        assertIs<SvgImport.Unusable>(broken)
        assertEquals("T", broken.glyph?.glyphName)
    }

    @Test
    fun aPackKeepsTheFirstOfTwoFilesForTheSameGlyph() {
        val a = template("""<path d="M0,0 H100 V-700 H0 Z"/>""")
        val b = template("""<path d="M0,0 H200 V-700 H0 Z"/>""")
        val pack = SvgTemplateImport.importPack(linkedMapOf("svg/Latin/$tFile" to a, "svg/Latin/999_LATIN_CAPITAL_LETTER_T.svg" to b))
        assertEquals(1, pack.drawings.size)
        assertEquals(1, pack.unusable.size)
        assertTrue(
            pack.unusable
                .single()
                .sentence
                .contains("two files"),
            pack.unusable.single().sentence,
        )
        assertEquals("1 drawn · 0 blank · 1 can't be used", pack.summary)
    }

    @Test
    fun theExpectedViewBoxOriginIsTheGuidesPlusThePacksMargin() {
        assertEquals(Vec2(-160.0, -820.0), SvgTemplateImport.expectedViewBox(PrintScript.LATIN))
        assertEquals(Vec2(-160.0, -1000.0), SvgTemplateImport.expectedViewBox(PrintScript.KANA))
        assertEquals(Vec2(-160.0, -880.0), SvgTemplateImport.expectedViewBox(PrintScript.DEVANAGARI))
        assertEquals(Vec2(-160.0, -840.0), SvgTemplateImport.expectedViewBox(PrintScript.NASKH))
    }

    // ---- the trace chain from Fit ---------------------------------------------------------------

    @Test
    fun anSvgDrawingIsTracedFromFitWithoutTheRasterStages() {
        // A box whose long sides carry extra points: the fit must keep four corners and no curves.
        val read =
            drawing(
                template("""<path d="M0,0 H40 H80 H100 V-200 V-400 V-700 H50 H0 V-300 Z"/>"""),
            )
        val trace = traceOutlines(read.outlines)
        val stages = trace.stages.map { it.stage }
        assertEquals(
            listOf(TraceStage.CAPTURE, TraceStage.CORNERS, TraceStage.FIT, TraceStage.SNAP, TraceStage.REGULARISE, TraceStage.REVIEW),
            stages,
        )
        assertTrue(trace.stage(TraceStage.CAPTURE).sentence.contains("starts at Fit"), trace.stage(TraceStage.CAPTURE).sentence)
        assertNull(trace.cleaned)
        assertNull(trace.frame)
        assertTrue(trace.inkFound)
        val final = assertNotNull(trace.finalNodes)
        assertEquals(4, final.onCurve)
        assertEquals(0, final.offCurve)
        val review = assertNotNull(trace.review)
        assertEquals(0, review.boundsDifference)
        assertTrue(review.maxDeviation < 1.0, "an exact drawing is fitted exactly: ${review.maxDeviation}")
        assertTrue(review.mismatchFraction < 0.02, "${review.mismatchFraction}")
    }

    @Test
    fun theNodeCountNeverRisesFromTheDrawingsPointsToFit() {
        val read = drawing(template("""<circle cx="325" cy="-250" r="200"/>"""))
        val trace = traceOutlines(read.outlines)
        val points = trace.stage(TraceStage.CAPTURE).polylines.sumOf { it.size }
        val fit = assertNotNull(trace.stage(TraceStage.FIT).nodes)
        assertTrue(fit.total < points / 10, "${fit.total} nodes from $points points")
        // A circle is four curves.
        assertEquals(4, fit.onCurve)
        assertEquals(8, fit.offCurve)
        // The deviation reported is the final outline's, measured both ways.
        assertTrue(assertNotNull(trace.review).maxDeviation < 1.5)
    }

    @Test
    fun anEmptySvgTracesToNothingAndSaysSo() {
        val trace = traceOutlines(emptyList())
        assertTrue(trace.contours.isEmpty())
        assertTrue(!trace.inkFound)
        assertEquals("The SVG holds no outline.", trace.stages.single().sentence)
        assertNull(trace.review)
    }

    @Test
    fun theDeviationReportedIsTheFinalOutlinesNotTheFitsBeforeSnap() {
        // A box 702 tall with a metric line at 700: Snap moves the top onto the line, two units from where it was drawn. The fit
        // alone is exact, so a review that reported the fit's deviation would say 0; the outline that will be written is 2 away.
        val read = drawing(template("""<path d="M0,0 H300 V-702 H0 Z"/>"""))
        val trace = traceOutlines(read.outlines, TraceParameters(metricLines = listOf(0, 700)))
        val review = assertNotNull(trace.review)
        assertEquals(700, assertNotNull(review.outlineBounds).yMax)
        assertEquals(2.0, review.maxDeviation, 0.3)
        assertTrue(trace.stage(TraceStage.REVIEW).sentence.contains("2.0 units"), trace.stage(TraceStage.REVIEW).sentence)
    }
}
