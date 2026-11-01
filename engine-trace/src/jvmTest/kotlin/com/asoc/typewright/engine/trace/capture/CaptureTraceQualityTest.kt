// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.outlineBounds
import com.asoc.typewright.engine.trace.HyleDecoFixture
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
import kotlin.test.assertTrue

/**
 * The whole chain, photograph to fitted outline: SYNTHETIC photographs of the Latin sheets (see [PhotoSpec]) are read by
 * [SheetCapture], the cells of `T`, `H`, `o` and `n` are traced as the capture hands them over, and the node counts are
 * set against CLAUDE.md's fitted targets (T 8 - 0, o 16 - 16, n 14 - 8, H 12 - 0) and the ink box against Hyle Deco's own.
 * The straight-on and moderately tilted photographs are held to the targets; the hardest ones are measured and reported,
 * and held only to the things the chain must always get right (the contours found, the ink in the right place, and a bounded
 * number of points: the hardest photographs do not reach the targets, and the table says by how much).
 */
class CaptureTraceQualityTest {
    private class Variant(
        val label: String,
        val spec: PhotoSpec,
        val heldToTargets: Boolean,
    )

    private val variants =
        listOf(
            Variant("straight-on", PhotoSpec(seed = 11), true),
            Variant(
                "tilt 10, roll 3",
                PhotoSpec(seed = 12, tiltDegrees = 10.0, tiltAxisDegrees = 0.0, rollDegrees = 3.0, fill = 0.8),
                true,
            ),
            Variant(
                "tilt 20, roll 6, falloff 25%, warm paper, noise 4",
                PhotoSpec(
                    seed = 14,
                    tiltDegrees = 20.0,
                    tiltAxisDegrees = 45.0,
                    rollDegrees = 6.0,
                    gradient = 0.25,
                    fill = 0.76,
                    tint = Triple(255, 244, 214),
                    noiseSigma = 4.0,
                ),
                true,
            ),
            Variant(
                "HARDEST A: tilt 25, falloff 35%, blur 1.2, noise 6, q50",
                PhotoSpec(
                    seed = 15,
                    tiltDegrees = 25.0,
                    tiltAxisDegrees = 135.0,
                    rollDegrees = 8.0,
                    gradient = 0.35,
                    fill = 0.7,
                    blurSigmaPx = 1.2,
                    noiseSigma = 6.0,
                    jpegQuality = 50,
                    tint = Triple(225, 238, 255),
                ),
                false,
            ),
            Variant(
                "HARDEST B: tilt 25, blur 1.6, noise 9, q35, pencil",
                PhotoSpec(
                    seed = 16,
                    tiltDegrees = 25.0,
                    tiltAxisDegrees = 200.0,
                    rollDegrees = -8.0,
                    gradient = 0.3,
                    gradientAngleDegrees = 120.0,
                    fill = 0.7,
                    blurSigmaPx = 1.6,
                    noiseSigma = 9.0,
                    jpegQuality = 35,
                    inkGray = 90,
                ),
                false,
            ),
        )

    private class Target(
        val char: Char,
        val sheet: Int,
        val index: Int,
        val on: Int,
        val off: Int,
        val contours: Int,
    )

    private val targets =
        listOf(Target('T', 0, 19, 8, 0, 1), Target('H', 0, 7, 12, 0, 1), Target('o', 1, 16, 16, 16, 2), Target('n', 1, 15, 14, 8, 1))
    private val metricLines = listOf(-210, 0, 500, 700)

    /** The most on-curve points the hardest photographs may cost a glyph: measured 47 at the worst, against 110 to 158 before the tolerance followed the wobble. */
    private val hardestOnCurveLimit = 60

    private fun traced(
        variant: Variant,
        target: Target,
    ): Triple<com.asoc.typewright.engine.trace.GlyphTrace, CapturedCell, Glyph> {
        val sheet = PrintSheets.forScript(PrintScript.LATIN).sheets[target.sheet]
        val photo = SyntheticPhotos.make(sheet, PaperSize.A4, GuideStyle.LIGHT, variant.spec)
        val captured = (SheetCapture.read(photo.gray) as CaptureResult.Success).sheet
        val cell = captured.cells[target.index]
        val trace = traceCell(cell.raster!!, cell.frame!!, TraceParameters(metricLines = metricLines))
        return Triple(trace, cell, HyleDecoFixture.glyph(target.char))
    }

    @Test
    fun theChainFromPhotographToOutlineReachesTheFittedTargetsOnTheEasierPhotographs() {
        println("photo | glyph | contours | corners | fit on/off | final on/off | target on/off | box diff | deviation | mismatch")
        val failures = mutableListOf<String>()
        for (variant in variants) {
            for (target in targets) {
                val (trace, cell, glyph) = traced(variant, target)
                val final = trace.finalNodes!!
                val outline = Glyph("t", 0, trace.contours).outlineBounds()!!
                val source = glyph.outlineBounds()!!
                val boxDiff =
                    maxOf(
                        abs(outline.xMin - source.xMin),
                        abs(outline.xMax - source.xMax),
                        abs(outline.yMin - source.yMin),
                        abs(outline.yMax - source.yMax),
                    )
                val fit = trace.stage(TraceStage.FIT).nodes!!
                println(
                    "${variant.label} | ${target.char} | ${trace.contours.size} | ${trace.stage(TraceStage.CORNERS).nodes!!.total} | " +
                        "${fit.onCurve}/${fit.offCurve} | ${final.onCurve}/${final.offCurve} | ${target.on}/${target.off} | $boxDiff | " +
                        "${(trace.review!!.maxDeviation * 10).toInt() / 10.0} | ${(trace.review!!.mismatchFraction * 1000).toInt() / 10.0}%",
                )
                assertEquals(target.contours, trace.contours.size, "${variant.label} ${target.char}: contours")
                assertTrue(
                    boxDiff <= 20,
                    "${variant.label} ${target.char}: ink box within 2% of the em of the source's, was off by $boxDiff",
                )
                assertTrue(cell.status !is CellStatus.Empty)
                if (variant.heldToTargets && final != NodeCount(target.on, target.off)) {
                    failures += "${variant.label} ${target.char}: $final against ${target.on}/${target.off}"
                }
                if (!variant.heldToTargets) {
                    assertTrue(final.onCurve <= hardestOnCurveLimit, "${variant.label} ${target.char}: $final is not a usable outline")
                }
            }
        }
        assertTrue(failures.isEmpty(), "the easier photographs must hit the fixture counts:\n" + failures.joinToString("\n"))
    }
}
