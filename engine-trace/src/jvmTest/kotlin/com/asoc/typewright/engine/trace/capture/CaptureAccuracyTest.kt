// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.capturetestkit.CellArt
import com.asoc.typewright.capturetestkit.CellJudge
import com.asoc.typewright.capturetestkit.CellVerdict
import com.asoc.typewright.capturetestkit.HyleArt
import com.asoc.typewright.capturetestkit.PhotoCase
import com.asoc.typewright.capturetestkit.PhotoLibrary
import com.asoc.typewright.capturetestkit.PhotoSpec
import com.asoc.typewright.capturetestkit.ProxyArt
import com.asoc.typewright.capturetestkit.Setting
import com.asoc.typewright.capturetestkit.SyntheticPhotos
import com.asoc.typewright.scripts.sheets.GuideStyle
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.PrintSheetSpec
import com.asoc.typewright.scripts.sheets.PrintSheets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The capture held to golden-path step 1b over [PhotoLibrary.accuracy], a set of SYNTHETIC photographs: printed template sheets with
 * the repository's own Hyle Deco glyphs (or ring stand-ins for scripts it does not cover) drawn in their cells, seen by a pinhole
 * camera at a tilt and roll, unevenly lit, blurred, noisy, JPEG-quantised, on tinted paper, with printed guides that differ from the
 * template's. None is a photograph of paper; real ones are an owner-supplied fixture still owed.
 *
 * What is judged is that the right ink landed in the right cell. The cell's name comes from the template, so it is not what can go
 * wrong; each cell's ink is compared with the glyph that belongs there, drawn from the font's outlines independently of the photograph
 * ([CellJudge]: overlap of the two masks), a cell that should be empty must be, and every miss is listed. Accuracy is reported per
 * photograph and per setting, hardest settings included, and each photograph must clear [PER_PHOTO_FLOOR] on its own.
 * The judge itself is shown to fail: photographs of drawings one cell off, and of another sheet's drawings, must be caught as misses.
 */
class CaptureAccuracyTest {
    private fun artFor(case: PhotoCase): CellArt = if (case.rings) ProxyArt else HyleArt

    private class Outcome(
        val case: PhotoCase,
        val verdicts: List<CellVerdict>,
        val uncertain: Int,
        val uncertainRight: Int,
        val readMs: Long,
        val failure: String?,
    ) {
        val checked: Int get() = verdicts.size
        val correct: Int get() = verdicts.count { it.correct }
        val accuracy: Double get() = if (checked == 0) 0.0 else correct.toDouble() / checked
        val misses: List<String> get() = verdicts.filter { !it.correct }.map { "cell ${it.index} (${it.glyphName}): ${it.miss}" }
    }

    /** Reads [photoOf] (a photograph of [case]'s sheet) and judges every cell against the template's own glyph, from [expectedArt]. */
    private fun run(
        case: PhotoCase,
        spec: PhotoSpec = case.spec,
        drawn: CellArt = artFor(case),
        expectedArt: CellArt = artFor(case),
    ): Outcome {
        val sheet = PrintSheets.forScript(case.script).sheets[case.sheetIndex]
        val photo = SyntheticPhotos.make(sheet, case.paper, case.guides, spec, drawn)
        val start = System.nanoTime()
        val result = SheetCapture.read(photo.gray)
        val ms = (System.nanoTime() - start) / 1_000_000
        if (result is CaptureResult.Failure) return Outcome(case, emptyList(), 0, 0, ms, result.failure.sentence)
        val captured = (result as CaptureResult.Success).sheet
        val identityRight =
            captured.identity.script == case.script && captured.identity.index == case.sheetIndex && captured.identity.paper == case.paper
        assertTrue(identityRight, "${case.label}: the sheet was read as ${captured.identity}")
        val verdicts = captured.cells.map { cell -> CellJudge.judge(cell, expectedArt.contours(sheet.glyphs[cell.index], cell.index)) }
        val uncertain = captured.cells.filter { it.status is CellStatus.Uncertain }
        val uncertainRight = uncertain.count { verdicts[it.index].correct }
        return Outcome(case, verdicts, uncertain.size, uncertainRight, ms, null)
    }

    @Test
    fun theRightInkLandsInTheRightCellOverTheWholeLibraryAndInEverySetting() {
        val outcomes = PhotoLibrary.accuracy.map { run(it) }
        println("photo | setting | cells | right ink | accuracy | flagged uncertain (right ink) | read ms")
        for (o in outcomes) {
            println(
                "${o.case.label} | ${o.case.setting.label} | ${o.checked} | ${o.correct} | ${pct(o.accuracy)} | " +
                    "${o.uncertain} (${o.uncertainRight}) | ${o.readMs}" + (o.failure?.let { " | FAILED: $it" } ?: ""),
            )
        }
        println("setting | photographs | cells | right ink | accuracy | worst photograph")
        for (setting in Setting.entries) {
            val group = outcomes.filter { it.case.setting == setting }
            if (group.isEmpty()) continue
            println(
                "${setting.label} | ${group.size} | ${group.sumOf { it.checked }} | ${group.sumOf { it.correct }} | " +
                    "${pct(group.sumOf { it.correct }.toDouble() / group.sumOf { it.checked })} | ${pct(group.minOf { it.accuracy })}",
            )
        }
        val total = outcomes.sumOf { it.checked }
        val correct = outcomes.sumOf { it.correct }
        println("TOTAL: $correct of $total cells have the right ink = ${pct(correct.toDouble() / total)} over ${outcomes.size} photographs")
        for (o in outcomes) for (miss in o.misses) println("MISS [${o.case.label}] $miss")

        assertTrue(outcomes.size >= 12, "at least 12 distinct photographs")
        assertEquals(
            5,
            outcomes
                .filter { it.case.script == PrintScript.LATIN }
                .map { it.case.sheetIndex }
                .distinct()
                .size,
            "all five Latin sheets are photographed",
        )
        assertTrue(outcomes.none { it.failure != null }, "every photograph was read as a sheet: ${outcomes.mapNotNull { it.failure }}")
        // The one setting with no floor is the measured limit of light pencil, which the next test holds to not being lost silently.
        val claimed = outcomes.filter { it.case.setting != Setting.FAINT_LIMIT }
        for (o in claimed) {
            assertTrue(o.accuracy >= PER_PHOTO_FLOOR, "${o.case.label}: ${o.correct} of ${o.checked}, misses: ${o.misses}")
        }
        for (setting in Setting.entries) {
            val group = outcomes.filter { it.case.setting == setting }
            assertTrue(group.isNotEmpty(), "${setting.label} has a photograph")
            if (setting == Setting.FAINT_LIMIT) continue
            val accuracy = group.sumOf { it.correct }.toDouble() / group.sumOf { it.checked }
            assertTrue(accuracy >= PER_SETTING_FLOOR, "${setting.label}: ${pct(accuracy)}")
        }
        val claimedCorrect = claimed.sumOf { it.correct }
        val claimedTotal = claimed.sumOf { it.checked }
        assertTrue(claimedCorrect.toDouble() / claimedTotal >= PER_SETTING_FLOOR, "$claimedCorrect of $claimedTotal")
    }

    @Test
    fun theUncertainCellsAreFewAndWhenFlaggedFaintTheyAreTheRightInk() {
        val faint = PhotoLibrary.accuracy.filter { it.setting == Setting.FAINT || it.setting == Setting.FAINT_LIMIT }.map { run(it) }
        for (o in faint) {
            // A light pencil drawing is not lost and not passed off as certain: every drawn cell comes back flagged.
            val drawn = o.verdicts.count { it.iou != null }
            assertTrue(o.uncertain >= drawn * 0.9, "${o.case.label}: only ${o.uncertain} of $drawn drawn cells were flagged faint")
            // Within the claimed range the flagged cell holds the right ink; past it (the limit case) it may not, which is what flagging is for.
            if (o.case.setting ==
                Setting.FAINT
            ) {
                assertEquals(o.uncertain, o.uncertainRight, "${o.case.label}: a flagged cell holds the right ink")
            }
        }
        // Pen-dark photographs flag almost nothing.
        for (o in PhotoLibrary.accuracy.filter { it.setting == Setting.STRAIGHT || it.setting == Setting.TILTED }.map { run(it) }) {
            assertTrue(o.uncertain <= 2, "${o.case.label}: ${o.uncertain} cells flagged on a clear photograph")
        }
    }

    @Test
    fun blankCellsStayEmptyWhateverThePrintersGuidesDo() {
        // Sheets whose Hyle Deco has no glyph for a cell leave it blank. With heavy, shifted or faint guides, no ghost of one is ink.
        val sheet = PrintSheets.forScript(PrintScript.LATIN).sheets[4]
        val blankArt = CellArt { _, _ -> emptyList() }
        for ((darkness, width, offset) in listOf(
            Triple(2.0, 2.0, 0.25),
            Triple(3.0, 1.0, 0.0),
            Triple(1.0, 3.0, 0.3),
            Triple(0.4, 0.5, 0.0),
        )) {
            val spec =
                PhotoSpec(
                    seed = 40,
                    tiltDegrees = 14.0,
                    fill = 0.8,
                    guideDarknessScale = darkness,
                    guideWidthScale = width,
                    guideOffsetMm = offset,
                )
            val photo = SyntheticPhotos.make(sheet, PaperSize.A4, GuideStyle.LIGHT, spec, blankArt)
            val captured = (SheetCapture.read(photo.gray) as CaptureResult.Success).sheet
            val ghosts = captured.cells.filter { it.status !is CellStatus.Empty }
            assertTrue(
                ghosts.isEmpty(),
                "guides x$darkness dark, x$width wide, $offset mm off: cells read as ink: " +
                    ghosts.joinToString { "${it.index} ${it.status::class.simpleName} box ${it.inkBounds} fraction ${it.inkFraction}" },
            )
        }
    }

    // ---- the judge can fail -----------------------------------------------------------------

    private val latinCase = PhotoLibrary.accuracy.first { it.setting == Setting.TILTED && it.script == PrintScript.LATIN }

    /** Hyle Deco drawn [delta] cells along the run of Latin glyphs from the cell it belongs in. */
    private fun shiftedArt(
        sheet: PrintSheetSpec,
        delta: Int,
    ): CellArt {
        val all = PrintSheets.glyphs(PrintScript.LATIN)
        val offset = sheet.index * PrintSheets.CELLS_PER_SHEET
        return CellArt { _, index -> HyleArt.contours(all[(offset + index + delta) % all.size], index) }
    }

    @Test
    fun drawingsOneCellOffAreCaughtAsMisses() {
        val sheet = PrintSheets.forScript(PrintScript.LATIN).sheets[latinCase.sheetIndex]
        val honest = run(latinCase)
        val shifted = run(latinCase, drawn = shiftedArt(sheet, 1))
        println("honest ${honest.correct}/${honest.checked}; drawings one cell off: ${shifted.correct}/${shifted.checked} judged right")
        assertTrue(honest.accuracy >= PER_PHOTO_FLOOR)
        // A few neighbours look alike enough to pass (the judge compares shapes, not names); nearly all must not.
        assertTrue(
            shifted.accuracy <= 0.25,
            "a grid shifted by one cell must be caught: ${shifted.correct} of ${shifted.checked} judged right",
        )
        assertTrue(shifted.misses.size >= shifted.checked * 3 / 4)
    }

    @Test
    fun aPhotographOfAnotherSheetsDrawingsIsCaughtAsMisses() {
        val sheet = PrintSheets.forScript(PrintScript.LATIN).sheets[latinCase.sheetIndex]
        // The person drew a whole sheet further on (the small letters on the capitals' sheet): the identity code says one sheet, the ink another.
        val wrongSheet = run(latinCase, drawn = shiftedArt(sheet, PrintSheets.CELLS_PER_SHEET))
        println("another sheet's drawings: ${wrongSheet.correct}/${wrongSheet.checked} judged right")
        assertTrue(wrongSheet.accuracy <= 0.25, "${wrongSheet.correct} of ${wrongSheet.checked} judged right")
    }

    @Test
    fun aCellDrawnButReadEmptyOrBlankButReadAsInkIsAMiss() {
        val sheet = PrintSheets.forScript(PrintScript.LATIN).sheets[0]
        val blankFive = CellArt { glyph, index -> if (index == 5) emptyList() else HyleArt.contours(glyph, index) }
        val photo = SyntheticPhotos.make(sheet, PaperSize.A4, GuideStyle.LIGHT, PhotoSpec(seed = 41), blankFive)
        val captured = (SheetCapture.read(photo.gray) as CaptureResult.Success).sheet
        val cell = captured.cells[19]
        val truth = HyleArt.contours(sheet.glyphs[19], 19)
        assertTrue(CellJudge.judge(cell, truth).correct)
        // Expecting nothing where ink was read, and ink where a different glyph's ink was read, are both misses.
        assertTrue(!CellJudge.judge(cell, emptyList()).correct)
        assertTrue(!CellJudge.judge(cell, HyleArt.contours(sheet.glyphs[7], 7)).correct)
        val empty = captured.cells[5]
        assertTrue(empty.status is CellStatus.Empty)
        assertTrue(!CellJudge.judge(empty, truth).correct)
    }

    @Test
    fun everyPhotographFindsAllFourCornersAndTheRightSheet() {
        for (case in PhotoLibrary.accuracy) {
            val sheet = PrintSheets.forScript(case.script).sheets[case.sheetIndex]
            val photo = SyntheticPhotos.make(sheet, case.paper, case.guides, case.spec, artFor(case))
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

    private fun pct(x: Double): String = "${(x * 1000).toInt() / 10.0}%"

    companion object {
        /** Each photograph, the hardest included, must have the right ink in at least this share of its cells. */
        const val PER_PHOTO_FLOOR = 0.95

        /** Each setting taken together, and the whole library, must too. */
        const val PER_SETTING_FLOOR = 0.95
    }
}
