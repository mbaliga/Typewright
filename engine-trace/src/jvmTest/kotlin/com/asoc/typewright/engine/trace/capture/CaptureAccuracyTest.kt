// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.scripts.sheets.GuideStyle
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.PrintSheets
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The capture held to golden-path step 1b over a set of SYNTHETIC photographs: each is a printed template sheet with the
 * repository's own Hyle Deco glyphs drawn into its cells, seen by a pinhole camera at a random tilt and roll, unevenly lit,
 * blurred, noisy, JPEG-quantised and printed on tinted paper (see [PhotoSpec]; none is a photograph of paper, and real ones
 * are an owner-supplied fixture still owed). The set includes the hardest settings the generator has: 25 degrees of tilt,
 * 8 degrees of roll, 35% lighting falloff, 1.6 pixels of blur, nine levels of noise, quality-35 compression, pencil-grey ink.
 *
 * A cell counts as mapped correctly when the glyph name it carries is the template's, the cell is empty exactly where nothing
 * was drawn, and the box of the ink found agrees with the box of the ink drawn within [TOLERANCE_UNITS] font units (about a
 * millimetre) on every side. Every miss is listed.
 */
class CaptureAccuracyTest {
    private val latin = PrintSheets.forScript(PrintScript.LATIN)

    private class Case(
        val label: String,
        val script: PrintScript,
        val sheetIndex: Int,
        val paper: PaperSize,
        val spec: PhotoSpec,
        val guides: GuideStyle = GuideStyle.LIGHT,
        val proxy: Boolean = false,
    )

    private val cases =
        listOf(
            Case("straight-on, clean", PrintScript.LATIN, 0, PaperSize.A4, PhotoSpec(seed = 11)),
            Case(
                "tilt 10, roll 3",
                PrintScript.LATIN,
                1,
                PaperSize.A4,
                PhotoSpec(seed = 12, tiltDegrees = 10.0, tiltAxisDegrees = 0.0, rollDegrees = 3.0, fill = 0.8),
            ),
            Case(
                "tilt 15 side-on, roll -4, light falloff 15%",
                PrintScript.LATIN,
                2,
                PaperSize.A4,
                PhotoSpec(seed = 13, tiltDegrees = 15.0, tiltAxisDegrees = 90.0, rollDegrees = -4.0, gradient = 0.15, fill = 0.78),
            ),
            Case(
                "tilt 20, roll 6, falloff 25%, warm paper",
                PrintScript.LATIN,
                3,
                PaperSize.A4,
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
            ),
            Case(
                "HARDEST A: tilt 25, roll 8, falloff 35%, blur 1.2, noise 6, q50, cool paper",
                PrintScript.LATIN,
                4,
                PaperSize.A4,
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
            ),
            Case(
                "HARDEST B: tilt 25, roll -8, falloff 30%, blur 1.6, noise 9, q35, pencil",
                PrintScript.LATIN,
                0,
                PaperSize.A4,
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
            ),
            Case(
                "HARDEST C: tilt 24, vignette 40%, falloff 20%, blur 1.4, noise 8, q40, green paper",
                PrintScript.LATIN,
                2,
                PaperSize.A4,
                PhotoSpec(
                    seed = 17,
                    tiltDegrees = 24.0,
                    tiltAxisDegrees = 300.0,
                    rollDegrees = 7.0,
                    vignette = 0.4,
                    gradient = 0.2,
                    fill = 0.7,
                    blurSigmaPx = 1.4,
                    noiseSigma = 8.0,
                    jpegQuality = 40,
                    tint = Triple(235, 250, 225),
                ),
            ),
            Case(
                "held sideways (roll 90)",
                PrintScript.LATIN,
                1,
                PaperSize.A4,
                PhotoSpec(
                    seed = 18,
                    widthPx = 2000,
                    heightPx = 1500,
                    rollDegrees = 90.0,
                    fill = 1.05,
                    tiltDegrees = 8.0,
                    tiltAxisDegrees = 60.0,
                ),
            ),
            Case(
                "upside down (roll 180)",
                PrintScript.LATIN,
                3,
                PaperSize.A4,
                PhotoSpec(seed = 19, rollDegrees = 180.0, tiltDegrees = 12.0, tiltAxisDegrees = 110.0, fill = 0.8),
            ),
            Case(
                "held sideways the other way (roll 270), noisy",
                PrintScript.LATIN,
                4,
                PaperSize.A4,
                PhotoSpec(
                    seed = 20,
                    widthPx = 2000,
                    heightPx = 1500,
                    rollDegrees = 270.0,
                    fill = 1.05,
                    tiltDegrees = 10.0,
                    noiseSigma = 5.0,
                    jpegQuality = 60,
                ),
            ),
            Case(
                "US Letter, tilt 14",
                PrintScript.LATIN,
                2,
                PaperSize.LETTER,
                PhotoSpec(seed = 21, tiltDegrees = 14.0, tiltAxisDegrees = 30.0, rollDegrees = 2.0, fill = 0.8, gradient = 0.15),
            ),
            Case(
                "no printed guides",
                PrintScript.LATIN,
                1,
                PaperSize.A4,
                PhotoSpec(seed = 22, tiltDegrees = 12.0, tiltAxisDegrees = 250.0, fill = 0.8, noiseSigma = 4.0),
                GuideStyle.NONE,
            ),
            Case(
                "bright desk, tilt 18",
                PrintScript.LATIN,
                0,
                PaperSize.A4,
                PhotoSpec(
                    seed = 23,
                    tiltDegrees = 18.0,
                    tiltAxisDegrees = 10.0,
                    rollDegrees = -5.0,
                    fill = 0.76,
                    deskGray = 215,
                    gradient = 0.15,
                ),
            ),
            Case(
                "Kana sheet 2, proxy drawings, tilt 16",
                PrintScript.KANA,
                1,
                PaperSize.A4,
                PhotoSpec(
                    seed = 24,
                    tiltDegrees = 16.0,
                    tiltAxisDegrees = 150.0,
                    rollDegrees = 5.0,
                    fill = 0.78,
                    gradient = 0.2,
                    blurSigmaPx = 0.8,
                ),
                proxy = true,
            ),
            Case(
                "Devanagari sheet 3 (18 cells), proxy drawings, Letter",
                PrintScript.DEVANAGARI,
                2,
                PaperSize.LETTER,
                PhotoSpec(seed = 25, tiltDegrees = 12.0, tiltAxisDegrees = 70.0, rollDegrees = -3.0, fill = 0.8, noiseSigma = 5.0),
                proxy = true,
            ),
            Case(
                "Naskh sheet 1, proxy drawings, tilt 20",
                PrintScript.NASKH,
                0,
                PaperSize.A4,
                PhotoSpec(seed = 26, tiltDegrees = 20.0, tiltAxisDegrees = 220.0, rollDegrees = 4.0, fill = 0.74, gradient = 0.25),
                proxy = true,
            ),
        )

    private class Outcome(
        val label: String,
        val cellsChecked: Int,
        val cellsCorrect: Int,
        val uncertain: Int,
        val misses: List<String>,
        val readMs: Long,
        val failure: String?,
    )

    private fun run(case: Case): Outcome {
        val sheet = PrintSheets.forScript(case.script).sheets[case.sheetIndex]
        val photo = SyntheticPhotos.make(sheet, case.paper, case.guides, case.spec, if (case.proxy) ProxyArt else HyleArt)
        val start = System.nanoTime()
        val result = SheetCapture.read(photo.gray)
        val ms = (System.nanoTime() - start) / 1_000_000
        if (result is CaptureResult.Failure) {
            return Outcome(
                case.label,
                sheet.glyphs.size,
                0,
                0,
                listOf("the whole sheet: ${result.failure.sentence}"),
                ms,
                result.failure.sentence,
            )
        }
        val captured = (result as CaptureResult.Success).sheet
        val misses = mutableListOf<String>()
        if (captured.identity.script != case.script || captured.identity.index != case.sheetIndex ||
            captured.identity.paper != case.paper
        ) {
            misses += "identity read as ${captured.identity}"
        }
        var correct = 0
        for (cell in captured.cells) {
            val truth = photo.truth[cell.index]
            val reason =
                when {
                    cell.glyphName != truth.glyphName -> "named ${cell.glyphName}, should be ${truth.glyphName}"

                    truth.bounds == null && cell.status !is CellStatus.Empty -> "ink found where nothing was drawn"

                    truth.bounds != null && cell.status is CellStatus.Empty -> "empty, but ${truth.glyphName} was drawn"

                    truth.bounds != null &&
                        !close(
                            cell.inkBounds!!,
                            truth.bounds,
                        )
                    -> "ink box ${cell.inkBounds} against drawn ${truth.bounds}"

                    else -> null
                }
            if (reason == null) correct++ else misses += "cell ${cell.index} (${truth.glyphName}): $reason"
        }
        return Outcome(case.label, captured.cells.size, correct, captured.uncertainCount, misses, ms, null)
    }

    private fun close(
        a: com.asoc.typewright.core.geometry.OutlineBounds,
        b: com.asoc.typewright.core.geometry.OutlineBounds,
    ): Boolean =
        abs(a.xMin - b.xMin) <= TOLERANCE_UNITS && abs(a.xMax - b.xMax) <= TOLERANCE_UNITS &&
            abs(a.yMin - b.yMin) <= TOLERANCE_UNITS && abs(a.yMax - b.yMax) <= TOLERANCE_UNITS

    @Test
    fun atLeastNinetyFivePercentOfCellsMapToTheirGlyphOverTheWholeSet() {
        val outcomes = cases.map { run(it) }
        println("photo | cells | correct | flagged uncertain | read ms")
        for (o in outcomes) {
            println(
                "${o.label} | ${o.cellsChecked} | ${o.cellsCorrect} | ${o.uncertain} | ${o.readMs}${o.failure?.let {
                    " | FAILED: $it"
                } ?: ""}",
            )
        }
        val total = outcomes.sumOf { it.cellsChecked }
        val correct = outcomes.sumOf { it.cellsCorrect }
        println("TOTAL: $correct of $total cells correct = ${(correct * 1000.0 / total).toInt() / 10.0}% over ${outcomes.size} photographs")
        for (o in outcomes) for (miss in o.misses) println("MISS [${o.label}] $miss")
        assertTrue(outcomes.size >= 12)
        assertTrue(outcomes.none { it.failure != null }, "every photograph was read as a sheet: ${outcomes.mapNotNull { it.failure }}")
        assertTrue(correct.toDouble() / total >= 0.95, "$correct of $total cells correct")
    }

    @Test
    fun everyPhotographFindsAllFourCornersAndTheRightSheet() {
        for (case in cases) {
            val sheet = PrintSheets.forScript(case.script).sheets[case.sheetIndex]
            val photo = SyntheticPhotos.make(sheet, case.paper, case.guides, case.spec, if (case.proxy) ProxyArt else HyleArt)
            val marks = findCornerMarks(photo.gray)
            assertTrue(marks.complete, "${case.label}: ${marks.foundCount} of 4 corners")
            val result = SheetCapture.read(photo.gray)
            assertTrue(result is CaptureResult.Success, "${case.label}: ${(result as? CaptureResult.Failure)?.failure?.sentence}")
            val identity = (result as CaptureResult.Success).sheet.identity
            assertEquals(case.script, identity.script, case.label)
            assertEquals(case.sheetIndex, identity.index, case.label)
            assertEquals(case.paper, identity.paper, case.label)
        }
    }

    companion object {
        const val TOLERANCE_UNITS = 30
    }
}
