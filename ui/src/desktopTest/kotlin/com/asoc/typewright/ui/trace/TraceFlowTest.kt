// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.trace

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.count
import com.asoc.typewright.engine.trace.GrayscaleRaster
import com.asoc.typewright.engine.trace.TraceParameters
import com.asoc.typewright.engine.trace.TraceStage
import com.asoc.typewright.project.EditResult
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.LockState
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.Refusal
import com.asoc.typewright.project.Unlock
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.PrintSheets
import com.asoc.typewright.ui.capture.CaptureLibrary
import com.asoc.typewright.ui.capture.CaptureSource
import com.asoc.typewright.ui.capture.PickedFile
import com.asoc.typewright.ui.capture.SheetState
import com.asoc.typewright.ui.capture.SyntheticSheets
import com.asoc.typewright.ui.capture.TraceSource
import com.asoc.typewright.ui.capture.platformImageDecoder
import com.asoc.typewright.ui.project.SampleProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Trace on a captured drawing of a real project: the chain, Accept, the lock, and the glyph that comes next. */
class TraceFlowTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** A project whose font is the sample, with a synthetic photograph of Latin sheet 1 (drawn from that font) captured into it. */
    private class Captured(
        val workspace: ProjectWorkspace,
        val library: CaptureLibrary,
    ) {
        val session get() = checkNotNull(workspace.current.value)
        val master get() =
            checkNotNull(
                session.state.value.font.masters
                    .first(),
            )

        fun source(name: String): TraceSource.Raster = assertIs(library.sourceFor(name))
    }

    private fun captured(
        degrees: Double = 3.0,
        pxPerMm: Double = 6.0,
        sheet: Int = 0,
    ): Captured {
        val workspace = SampleProject.workspace()
        val session = checkNotNull(workspace.current.value)
        val spec = SyntheticSheets.latinSheet(sheet)
        val font =
            session.state.value.font.masters
                .first()
                .ufo.glyphs
        val art: Map<Int, List<Contour>> =
            spec.glyphs
                .withIndex()
                .mapNotNull { (i, g) -> font.firstOrNull { it.name == g.glyphName }?.let { i to it.contours } }
                .toMap()
        val png = SyntheticSheets.png(SyntheticSheets.photograph(SyntheticSheets.page(spec, art, pxPerMm = pxPerMm), degrees))
        val library = CaptureLibrary(session, platformImageDecoder(), scope).also { it.start() }
        runBlocking {
            library.add(listOf(PickedFile("sheet1.png", png)), CaptureSource.FILE)
            library.items.first { items ->
                items.any {
                    it is com.asoc.typewright.ui.capture.CaptureItem.Sheet &&
                        it.state is SheetState.Ready
                }
            }
        }
        return Captured(workspace, library)
    }

    private fun run(
        source: TraceSource,
        parameters: TraceParameters = TraceParameters(),
    ): TraceOutcome {
        val lines =
            TraceFlow.metricLines(
                checkNotNull(SampleProject.openProject(SampleProject.workspace()).master).ufo.fontInfo,
                source.script,
            )
        return TraceRunner.run(source, parameters.copy(metricLines = lines))
    }

    @Test
    fun theChainReducesACapturedLetterToAFewPointsAndKeepsEveryStage() {
        val t = captured().source("T")
        val outcome = run(t)
        assertEquals(TraceStage.entries.toList(), outcome.stages.map { it.stage })
        assertTrue(checkNotNull(outcome.boundaryCount) > 200, "the boundary of a pen drawing is hundreds of points")
        val final = checkNotNull(outcome.finalCount)
        assertTrue(final.total <= 30, "a T is a few corners, was $final")
        assertTrue(final.total * 20 < checkNotNull(outcome.boundaryCount), "the fit keeps under a twentieth of the boundary: $final")
        // The header's count is the one the stage shows, and falls from the boundary to the fit.
        assertEquals(outcome.boundaryCount, outcome.countAt(TraceStage.CLEAN)?.total)
        assertEquals(final, outcome.countAt(TraceStage.REVIEW))
        assertTrue(outcome.inkFound)
    }

    @Test
    fun aScanOfTheSheetGivesTheTheEightCornersOfTheT() {
        val outcome = run(captured(degrees = 0.0, pxPerMm = 8.0).source("T"))
        assertEquals(8, outcome.finalCount?.total, "a T scanned flat is eight corners and no curves: ${outcome.finalCount}")
        assertEquals(0, outcome.finalCount?.offCurve)
    }

    @Test
    fun aRoundLetterKeepsItsCurvesAndAFlatTopIsSaidToNeedNoOvershoot() {
        val captured = captured()
        val o = run(captured.source("O"))
        assertTrue(checkNotNull(o.finalCount).offCurve > 0, "an o is curves")
        assertTrue(o.hasCurves)
        // Among the capitals some have curves and a flat top or bottom (D, P, R, U...): overshoot does not apply on the flat side.
        val flat =
            captured.library
                .sources()
                .map { it.glyphName to run(it) }
                .firstOrNull { it.second.flatRound }
        val (_, outcome) = assertNotNull(flat, "some capital has curves and a flat side")
        assertTrue(
            outcome.stage(TraceStage.REVIEW)!!.sentence.contains("overshoot does not apply"),
            outcome.stage(TraceStage.REVIEW)!!.sentence,
        )
        // A drawing with no curve at all has nothing to overshoot, so the note does not apply to it.
        assertFalse(run(captured(degrees = 0.0, pxPerMm = 8.0).source("T")).flatRound)
    }

    @Test
    fun changingASettingTracesAgainAndChangesTheCount() {
        val source = captured().source("O")
        val fine = run(source, TraceParameters().withParameter("fit.tolerance", 0.5).withParameter("fit.adapt", 0.0))
        val coarse = run(source, TraceParameters().withParameter("fit.tolerance", 8.0).withParameter("fit.adapt", 0.0))
        assertTrue(
            checkNotNull(fine.finalCount).total >= checkNotNull(coarse.finalCount).total,
            "${fine.finalCount} vs ${coarse.finalCount}",
        )
        val spec = TraceView.parametersFor(fine, TraceStage.FIT, all = false).first { it.id == "fit.tolerance" }
        assertEquals(0.5, spec.value)
        assertEquals(1.0, TraceView.stepped(spec, 1))
        assertEquals(0.5, TraceView.stepped(spec, -1), "held at the bottom of its range")
    }

    @Test
    fun acceptingReplacesTheOutlineLocksTheGlyphAndCountsTheSameWayEverywhere() {
        val captured = captured()
        val source = captured.source("T")
        val outcome = run(source)
        // An imported font arrives approved and locked: tracing over a letter needs the person to unlock it first (law 1).
        val refused = captured.session.execute(TraceFlow.acceptCommand(captured.master, source, outcome))
        assertIs<Refusal.Locked>(assertIs<EditResult.Refused>(refused).refusal)
        assertIs<EditResult.Applied>(captured.session.execute(Unlock(GlyphRef(captured.master.id, "T"))))
        val result = captured.session.execute(TraceFlow.acceptCommand(captured.master, source, outcome))
        assertIs<EditResult.Applied>(result)
        val state = captured.session.state.value
        assertTrue(TraceFlow.isLocked(state, captured.master.id, "T"))
        val glyph =
            state.font.masters
                .first()
                .ufo.glyphs
                .first { it.name == "T" }
        // The count the screen showed is the count the project, and so Check and Home, now hold.
        assertEquals(checkNotNull(outcome.finalCount).onCurve, glyph.count().onCurveEquivalent)
        assertEquals(
            LockState.LOCKED,
            state.font.locks
                .getValue(GlyphRef(captured.master.id, "T"))
                .state,
        )
        // Accepting the same outline again changes nothing; a different one is refused until the glyph is unlocked (law 1).
        assertEquals(EditResult.NoChange, captured.session.execute(TraceFlow.acceptCommand(captured.master, source, outcome)))
        val different =
            run(
                source,
                TraceParameters().withParameter("fit.tolerance", 8.0).withParameter("fit.adapt", 0.0).withParameter("corner.turn", 70.0),
            )
        assertTrue(different.contours != outcome.contours, "the test needs a different outline")
        val again = captured.session.execute(TraceFlow.acceptCommand(captured.master, source, different))
        assertIs<Refusal.Locked>(assertIs<EditResult.Refused>(again).refusal)
        assertIs<EditResult.Applied>(captured.session.execute(Unlock(GlyphRef(captured.master.id, "T"))))
        assertIs<EditResult.Applied>(captured.session.execute(TraceFlow.acceptCommand(captured.master, source, different)))
    }

    @Test
    fun aGlyphTheFontDoesNotHaveYetIsAddedWithItsCodePointAndAcceptedInOneEntry() {
        val captured = captured()
        // Take 'T' out of the font so the capture is the only thing that knows it.
        captured.session.execute(Unlock(GlyphRef(captured.master.id, "T")))
        assertIs<EditResult.Applied>(
            captured.session.execute(
                com.asoc.typewright.project
                    .RemoveGlyph(GlyphRef(captured.master.id, "T")),
            ),
        )
        val source = captured.source("T")
        val outcome = run(source)
        val before = captured.session.history.value
        assertIs<EditResult.Applied>(captured.session.execute(TraceFlow.acceptCommand(captured.master, source, outcome)))
        val glyph =
            captured.session.state.value.font.masters
                .first()
                .ufo.glyphs
                .first { it.name == "T" }
        assertEquals(listOf(0x54), glyph.unicodes)
        assertEquals(outcome.suggestedAdvance, glyph.advanceWidth)
        assertTrue(glyph.contours.isNotEmpty())
        assertEquals(
            before.undoLabels.size + 1,
            captured.session.history.value.undoLabels.size,
            "one history entry for the add and the accept",
        )
    }

    @Test
    fun nextUntracedSkipsLockedGlyphsAndWrapsRound() {
        val sources =
            listOf("A", "B", "C", "D").map {
                TraceSource.Vector(it, com.asoc.typewright.scripts.sheets.PrintScript.LATIN, null, "x", emptyList())
            }
        assertEquals("B", TraceFlow.nextUntraced(sources, "A") { false })
        assertEquals("D", TraceFlow.nextUntraced(sources, "A") { it == "B" || it == "C" })
        assertEquals("A", TraceFlow.nextUntraced(sources, "D") { false }, "wraps round")
        assertEquals("B", TraceFlow.nextUntraced(sources, "D") { it == "A" })
        assertNull(TraceFlow.nextUntraced(sources, "A") { true }, "every glyph locked")
        assertNull(TraceFlow.nextUntraced(emptyList(), null) { false })
        assertEquals("C", TraceFlow.opening(sources, "C") { false })
        assertEquals("B", TraceFlow.opening(sources, null) { it == "A" })
        assertEquals("A", TraceFlow.opening(sources, "Z") { false }, "a glyph with no drawing opens on the first that has one")
    }

    @Test
    fun anSvgDrawingStartsAtFitKeepsItsCurvesAndSnapsToTheMetricLines() {
        // A letter-like shape drawn a unit under the x-height line: Snap should move its top onto 500.
        val rect = svgContours("M 100 0 L 100 -499 L 300 -499 L 300 0 Z")
        val outcome =
            TraceRunner.run(
                TraceSource.Vector("x", com.asoc.typewright.scripts.sheets.PrintScript.LATIN, null, "SVG · x.svg", rect),
                TraceParameters(metricLines = listOf(0, 500)),
            )
        assertEquals(setOf(TraceStage.CAPTURE, TraceStage.CLEAN, TraceStage.CONTOUR, TraceStage.CORNERS), outcome.unavailable)
        assertEquals(TraceStage.FIT, TraceView.defaultStage(outcome, all = false))
        assertEquals(listOf(TraceStage.FIT, TraceStage.REVIEW), TraceView.scrubberStages(outcome, all = false), "Clean is not offered")
        assertEquals(4, outcome.stage(TraceStage.FIT)!!.nodes!!.total, "kept exactly as drawn")
        assertEquals(
            500,
            outcome.contours
                .single()
                .points
                .maxOf { it.point.y },
            "snapped onto the x-height line",
        )
        assertNull(outcome.boundaryCount, "an SVG was never a picture")
        assertEquals(
            listOf("snap.band", "snap.axis").sorted(),
            TraceView
                .parametersFor(outcome, TraceStage.SNAP, all = true)
                .map {
                    it.id
                }.sorted(),
        )
        assertFalse(outcome.flatRound, "no curves, so no overshoot question")
    }

    @Test
    fun theHeaderReadsTheCountAtTheSelectedStageAndWhatItWas() {
        val outcome = run(captured().source("T"))
        val boundary = checkNotNull(outcome.boundaryCount)
        assertEquals("T · ${TraceRunner.grouped(boundary)} points", TraceView.title("T", outcome.countAt(TraceStage.CLEAN)?.total))
        val fit = outcome.countAt(TraceStage.FIT)!!.total
        val subtitle = TraceView.subtitle(outcome, fit, "sheet 1 · cell 20 of 24")
        assertEquals("was ${TraceRunner.grouped(boundary)} · sheet 1 · cell 20 of 24", subtitle)
        assertEquals(
            "sheet 1 · cell 20 of 24",
            TraceView.subtitle(outcome, boundary, "sheet 1 · cell 20 of 24"),
            "no 'was' while the count is the boundary",
        )
        assertNotNull(TraceView.hiddenStagesNote(outcome, all = false).takeIf { it == "+5 in all stages" })
        assertEquals("", TraceView.hiddenStagesNote(outcome, all = true))
    }

    @Test
    fun aScannedOIsSixteenPointsOnTheCurveAndSixteenOffIt() {
        // The screen's own "done when": the round letter is 80 points in the shipped outline and 16 on, 16 off once fitted.
        val sheet = PrintSheets.forScript(PrintScript.LATIN).sheets.indexOfFirst { page -> page.glyphs.any { it.glyphName == "o" } }
        assertTrue(sheet >= 0, "some Latin sheet carries the lower-case o")
        val o = run(captured(degrees = 0.0, pxPerMm = 8.0, sheet = sheet).source("o"))
        val count = checkNotNull(o.finalCount)
        assertEquals(16, count.onCurve, "on-curve points of the scanned o: $count")
        assertEquals(16, count.offCurve, "off-curve points of the scanned o: $count")
    }

    @Test
    fun anOutcomeBelongsToTheDrawingAndTheSettingsThatMadeIt() {
        val captured = captured()
        val t = captured.source("T")
        val o = captured.source("O")
        val params = TraceParameters(metricLines = listOf(0, 500, 700))
        val outcome = TraceRunner.run(t, params)
        assertTrue(TraceFlow.isCurrent(outcome, t, params))
        assertFalse(TraceFlow.isCurrent(outcome, o, params), "another glyph's drawing")
        assertFalse(TraceFlow.isCurrent(outcome, t, params.withParameter("fit.tolerance", 8.0)), "another setting")
        assertFalse(TraceFlow.isCurrent(outcome, t, params.copy(metricLines = listOf(0, 500))), "another set of metric lines")
        assertFalse(TraceFlow.isCurrent(null, t, params))
        assertFalse(TraceFlow.isCurrent(outcome, null, params))
        // The same glyph from another capture (a retake) is another drawing, even with the same name.
        val retake =
            TraceSource.Raster(
                t.glyphName,
                t.script,
                t.codePoint,
                t.origin,
                GrayscaleRaster(t.raster.width, t.raster.height, t.raster.pixels.copyOf()),
                t.frame,
            )
        assertFalse(TraceFlow.isCurrent(outcome, retake, params))
        // The library hands out new source objects whenever anything is read; the same drawing is still the same drawing.
        val rebuilt = TraceSource.Raster(t.glyphName, t.script, t.codePoint, t.origin, t.raster, t.frame)
        assertTrue(TraceFlow.isCurrent(outcome, rebuilt, params))
    }

    @Test
    fun acceptRefusesAnOutcomeFromTheGlyphBeforeAndWritesNothing() {
        val captured = captured()
        val glyphO = GlyphRef(captured.master.id, "O")
        assertIs<EditResult.Applied>(captured.session.execute(Unlock(glyphO)))
        val params = TraceParameters()
        val t = captured.source("T")
        val o = captured.source("O")
        val staleOutcome = TraceRunner.run(t, params)
        val before = captured.session.state.value
        val steps = captured.session.history.value.undoLabels.size
        val page = TracePageState()
        // The screen has moved on to O but the run for O has not finished: the outcome in hand is T's.
        acceptTrace(SampleProject.openProject(captured.workspace), o, staleOutcome, params, listOf(t, o), null, page)
        assertTrue(checkNotNull(page.message).contains("still tracing O"), page.message)
        assertEquals(before, captured.session.state.value, "nothing was written")
        assertEquals(steps, captured.session.history.value.undoLabels.size)
        assertFalse(TraceFlow.isLocked(captured.session.state.value, captured.master.id, "O"), "O was not locked")
        // The right outcome goes through, and the glyph is locked by it.
        acceptTrace(SampleProject.openProject(captured.workspace), o, TraceRunner.run(o, params), params, listOf(t, o), null, page)
        assertTrue(TraceFlow.isLocked(captured.session.state.value, captured.master.id, "O"))
        assertTrue(checkNotNull(page.message).startsWith("O accepted and locked"), page.message)
    }

    @Test
    fun acceptRefusesAnOutcomeMadeWithTheSettingsBeforeAStepperTap() {
        val captured = captured()
        assertIs<EditResult.Applied>(captured.session.execute(Unlock(GlyphRef(captured.master.id, "T"))))
        val t = captured.source("T")
        val older = TraceParameters()
        val newer = older.withParameter("fit.tolerance", 8.0).withParameter("fit.adapt", 0.0).withParameter("corner.turn", 70.0)
        val olderOutcome = TraceRunner.run(t, older)
        assertTrue(olderOutcome.contours != TraceRunner.run(t, newer).contours, "the test needs the two settings to differ")
        val before = captured.session.state.value
        val page = TracePageState()
        acceptTrace(SampleProject.openProject(captured.workspace), t, olderOutcome, newer, listOf(t), null, page)
        assertEquals(before, captured.session.state.value, "the older outline was not written")
        assertTrue(checkNotNull(page.message).contains("still tracing"), page.message)
    }

    @Test
    fun theConfirmationSurvivesTheMoveToTheNextGlyph() {
        val captured = captured()
        assertIs<EditResult.Applied>(captured.session.execute(Unlock(GlyphRef(captured.master.id, "T"))))
        // O is still locked, as an imported letter is, so it is not the next to trace; unlocking it makes it so.
        assertIs<EditResult.Applied>(captured.session.execute(Unlock(GlyphRef(captured.master.id, "O"))))
        val t = captured.source("T")
        val next = captured.source("O")
        val carried = arrayOfNulls<String>(1)
        var selected: String? = null
        val nav =
            com.asoc.typewright.ui.shell
                .GlyphNav("T", open = {}, select = { selected = it })
        acceptTrace(
            SampleProject.openProject(captured.workspace),
            t,
            TraceRunner.run(t, TraceParameters()),
            TraceParameters(),
            listOf(t, next),
            nav,
            TracePageState(),
            carried,
        )
        assertEquals("O", selected)
        assertEquals("T accepted and locked. On to O.", carried[0], "the next glyph's page picks this up")
    }

    @Test
    fun anSvgDrawingsAdvanceComesFromTheTemplatesAdvanceGuides() {
        val latin = com.asoc.typewright.scripts.sheets.PrintScript.LATIN
        val guides =
            com.asoc.typewright.scripts.sheets.SheetLayout
                .of(com.asoc.typewright.scripts.sheets.PaperSize.A4, latin)
                .guides
        val inside =
            TraceRunner.run(
                TraceSource.Vector("i", latin, null, "SVG · i.svg", svgContours("M 100 0 L 100 -500 L 300 -500 L 300 0 Z")),
                TraceParameters(),
            )
        assertEquals(guides.advanceRight - guides.advanceLeft, inside.suggestedAdvance, "between the guides: the guides' own width")
        val over =
            TraceRunner.run(
                TraceSource.Vector("w", latin, null, "SVG · w.svg", svgContours("M 100 0 L 100 -500 L 900 -500 L 900 0 Z")),
                TraceParameters(),
            )
        assertEquals(900, over.suggestedAdvance, "ink past the right guide widens the advance to cover it")
        assertEquals(0, TraceRunner.suggestedAdvance(null, latin), "no ink, no advance")
    }

    private fun svgContours(d: String): List<Contour> {
        val reading =
            com.asoc.typewright.ui.capture.SvgDrawingReader
                .read("<svg viewBox='0 0 10 10'><path d='$d'/></svg>")
        return assertIs<com.asoc.typewright.ui.capture.SvgReading.Drawing>(reading).contours
    }
}
