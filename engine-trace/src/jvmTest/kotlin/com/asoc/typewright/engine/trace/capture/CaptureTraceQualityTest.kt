// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.capturetestkit.HyleArt
import com.asoc.typewright.capturetestkit.HyleDecoFixture
import com.asoc.typewright.capturetestkit.PhotoLibrary
import com.asoc.typewright.capturetestkit.SyntheticPhotos
import com.asoc.typewright.capturetestkit.TraceTargets
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.outlineBounds
import com.asoc.typewright.engine.trace.GlyphTrace
import com.asoc.typewright.engine.trace.NodeCount
import com.asoc.typewright.engine.trace.TraceParameters
import com.asoc.typewright.engine.trace.TraceStage
import com.asoc.typewright.engine.trace.traceCell
import com.asoc.typewright.scripts.sheets.GuideStyle
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.PrintSheets
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The whole chain, photograph to fitted outline, over [PhotoLibrary.trace]: SYNTHETIC photographs of the Latin sheets (see
 * [com.asoc.typewright.capturetestkit.PhotoSpec]) are read by [SheetCapture], the cells of `T`, `H`, `o` and `n` are traced as the
 * capture hands them over (with the edge softness it measured), and the node counts are set against CLAUDE.md's fitted targets
 * (T 8 - 0, o 16 - 16, n 14 - 8, H 12 - 0) and the ink box against Hyle Deco's own.
 *
 * Every glyph of every photograph is printed (the table is the point), and:
 * - the straight-on, tilted and moderately soft photographs must reach the targets exactly;
 * - the hardest photographs do not, and the claim made for them is the one written in [TraceTargets.hardClaim]: right contours, the ink
 *   box within 2% of the em, and at most that many points. That bound was written after measuring them, and says so; the fitter is not
 *   claimed to meet the targets there (docs/CAPTURE.md has the table, docs/OPEN_QUESTIONS.md the question).
 */
class CaptureTraceQualityTest {
    private class Row(
        val photo: PhotoLibrary.TracePhoto,
        val target: TraceTargets.Target,
        val trace: GlyphTrace,
        val boxDifference: Int,
    ) {
        val final: NodeCount get() = trace.finalNodes!!
    }

    private fun rows(): List<Row> {
        val out = mutableListOf<Row>()
        for (photo in PhotoLibrary.trace) {
            val captured = mutableMapOf<Int, CapturedSheet>()
            for (target in TraceTargets.all) {
                val sheet =
                    captured.getOrPut(target.sheet) {
                        val spec = PrintSheets.forScript(PrintScript.LATIN).sheets[target.sheet]
                        val shot = SyntheticPhotos.make(spec, PaperSize.A4, GuideStyle.LIGHT, photo.spec, HyleArt)
                        (SheetCapture.read(shot.gray) as CaptureResult.Success).sheet
                    }
                val cell = sheet.cells[target.cell]
                assertTrue(cell.status !is CellStatus.Empty, "${photo.label} ${target.char}: the cell was read as empty")
                val trace =
                    traceCell(
                        cell.raster!!,
                        cell.frame!!,
                        TraceParameters(metricLines = TraceTargets.metricLines, edgeSoftnessUnits = cell.edgeSoftnessUnits),
                    )
                val outline = assertNotNull(Glyph("t", 0, trace.contours).outlineBounds())
                val source = assertNotNull(HyleDecoFixture.glyph(target.char).outlineBounds())
                val box =
                    maxOf(
                        abs(outline.xMin - source.xMin),
                        abs(outline.xMax - source.xMax),
                        abs(outline.yMin - source.yMin),
                        abs(outline.yMax - source.yMax),
                    )
                out += Row(photo, target, trace, box)
            }
        }
        return out
    }

    @Test
    fun theChainFromPhotographToOutlineIsHeldToTheTargetsOrToTheStatedClaim() {
        val rows = rows()
        println("photo | class | glyph | contours | corners | fit on/off | final on/off | target on/off | box diff | deviation | mismatch")
        for (r in rows) {
            val fit = assertNotNull(r.trace.stage(TraceStage.FIT).nodes)
            val review = assertNotNull(r.trace.review)
            println(
                "${r.photo.label} | ${r.photo.traceClass} | ${r.target.char} | ${r.trace.contours.size} | " +
                    "${r.trace.stage(TraceStage.CORNERS).nodes!!.total} | ${fit.onCurve}/${fit.offCurve} | " +
                    "${r.final.onCurve}/${r.final.offCurve} | ${r.target.nodes.onCurve}/${r.target.nodes.offCurve} | " +
                    "${r.boxDifference} | ${(review.maxDeviation * 10).toInt() / 10.0} | " +
                    "${(review.mismatchFraction * 1000).toInt() / 10.0}%",
            )
        }
        val exact = mutableListOf<String>()
        val beyondClaim = mutableListOf<String>()
        for (r in rows) {
            assertEquals(r.target.contours, r.trace.contours.size, "${r.photo.label} ${r.target.char}: contours")
            assertTrue(
                r.boxDifference <= TraceTargets.BOX_TOLERANCE_UNITS,
                "${r.photo.label} ${r.target.char}: ink box within 2% of the em of the source's, was off by ${r.boxDifference}",
            )
            if (r.photo.traceClass == PhotoLibrary.TraceClass.EXACT) {
                if (r.final != r.target.nodes) exact += "${r.photo.label} ${r.target.char}: ${r.final} against ${r.target.nodes}"
            } else {
                val claim = TraceTargets.hardClaim(r.target.nodes)
                if (r.final.onCurve > claim.onCurve || r.final.offCurve > claim.offCurve) {
                    beyondClaim += "${r.photo.label} ${r.target.char}: ${r.final} beyond the claim $claim"
                }
            }
        }
        assertTrue(exact.isEmpty(), "these must hit the fixture counts exactly:\n" + exact.joinToString("\n"))
        assertTrue(beyondClaim.isEmpty(), "these exceed the stated claim for the hardest photographs:\n" + beyondClaim.joinToString("\n"))
    }

    @Test
    fun theReviewSentenceNamesTheFinalOutlinesDeviationFromTheBoundaryItWasFittedTo() {
        val target = TraceTargets.all.first { it.char == 'T' }
        val photo = PhotoLibrary.trace.first()
        val spec = PrintSheets.forScript(PrintScript.LATIN).sheets[target.sheet]
        val shot = SyntheticPhotos.make(spec, PaperSize.A4, GuideStyle.LIGHT, photo.spec, HyleArt)
        val cell = (SheetCapture.read(shot.gray) as CaptureResult.Success).sheet.cells[target.cell]
        val trace = traceCell(cell.raster!!, cell.frame!!, TraceParameters(metricLines = TraceTargets.metricLines))
        val review = assertNotNull(trace.review)
        // Snap moved the T's stem foot (y=1) onto the baseline, and the sentence's figure is measured after that move.
        val boundary = trace.stage(TraceStage.CONTOUR).polylines
        val remeasured =
            com.asoc.typewright.core.geometry
                .outlineDeviation(boundary, trace.contours)
        assertEquals(remeasured, review.maxDeviation, 1e-9)
        assertTrue(trace.stage(TraceStage.REVIEW).sentence.contains("${kotlin.math.round(review.maxDeviation * 10) / 10.0} units"))
    }
}
