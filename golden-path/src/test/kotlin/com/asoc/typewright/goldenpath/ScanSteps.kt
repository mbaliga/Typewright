// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.goldenpath

import com.asoc.typewright.capturetestkit.CellArt
import com.asoc.typewright.capturetestkit.CellJudge
import com.asoc.typewright.capturetestkit.CellVerdict
import com.asoc.typewright.capturetestkit.HyleArt
import com.asoc.typewright.capturetestkit.PhotoCase
import com.asoc.typewright.capturetestkit.PhotoLibrary
import com.asoc.typewright.capturetestkit.ProxyArt
import com.asoc.typewright.capturetestkit.Setting
import com.asoc.typewright.capturetestkit.SyntheticPhotos
import com.asoc.typewright.capturetestkit.TraceTargets
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.outlineBounds
import com.asoc.typewright.engine.trace.GlyphTrace
import com.asoc.typewright.engine.trace.TraceParameters
import com.asoc.typewright.engine.trace.TraceStage
import com.asoc.typewright.engine.trace.capture.CaptureResult
import com.asoc.typewright.engine.trace.capture.CapturedCell
import com.asoc.typewright.engine.trace.capture.CapturedSheet
import com.asoc.typewright.engine.trace.capture.CellStatus
import com.asoc.typewright.engine.trace.capture.SheetCapture
import com.asoc.typewright.engine.trace.traceCell
import com.asoc.typewright.project.AcceptTrace
import com.asoc.typewright.project.ApprovalOrigin
import com.asoc.typewright.project.Brief
import com.asoc.typewright.project.BriefSource
import com.asoc.typewright.project.CreateResult
import com.asoc.typewright.project.EditResult
import com.asoc.typewright.project.FileSystemProjectStore
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.LockState
import com.asoc.typewright.project.MovePoints
import com.asoc.typewright.project.NewMaster
import com.asoc.typewright.project.NewProjectSpec
import com.asoc.typewright.project.OpenResult
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.Refusal
import com.asoc.typewright.project.StyleClass
import com.asoc.typewright.scripts.sheets.GuideStyle
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.PrintSheets
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden-path steps 1b and 2, driven end to end on SYNTHETIC photographs of the printed template sheets: the repository's own Hyle Deco
 * outlines drawn into the cells of the real template sheets, then seen by a tilted, unevenly lit, noisy, tinted, compressed camera. They
 * are not photographs of paper; real ones are an owner-supplied fixture still owed. The photographs, the judge and the trace targets are
 * the testkit's, the same ones engine-trace's own tests use.
 */
internal object ScanSteps {
    private const val UPM = 1000

    private fun artFor(case: PhotoCase): CellArt = if (case.rings) ProxyArt else HyleArt

    /** What is kept of a photograph once it is read: the verdicts and the names, not the cells' rasters (23 photographs would not fit in the test's heap). */
    private class Reading(
        val case: PhotoCase,
        val glyphNames: List<String>,
        val verdicts: List<CellVerdict>,
    ) {
        val right: Int get() = verdicts.count { it.correct }
        val accuracy: Double get() = right.toDouble() / verdicts.size
    }

    /** Every photograph of the accuracy library read and judged. Failing to read one is a step failure, not a skipped photograph. */
    private fun readLibrary(): List<Reading> =
        PhotoLibrary.accuracy.map { case ->
            val sheetSpec = PrintSheets.forScript(case.script).sheets[case.sheetIndex]
            val photo = SyntheticPhotos.make(sheetSpec, case.paper, case.guides, case.spec, artFor(case))
            val captured =
                when (val result = SheetCapture.read(photo.gray)) {
                    is CaptureResult.Success -> result.sheet
                    is CaptureResult.Failure -> error("${case.label}: ${result.failure.sentence}")
                }
            // Four corners found, and the right sheet read from the printed code.
            assertEquals(4, captured.marks.size, "${case.label}: four corner marks")
            assertEquals(case.sheetIndex, captured.identity.index, "${case.label}: identity")
            assertEquals(case.script, captured.identity.script, "${case.label}: script")
            Reading(
                case,
                captured.cells.map { it.glyphName },
                captured.cells.map { CellJudge.judge(it, artFor(case).contours(sheetSpec.glyphs[it.index], it.index)) },
            )
        }

    /** The project a scan starts: every template glyph present and empty, nothing drawn, nothing accepted. */
    private fun scanProject(): UfoProject {
        val glyphs =
            PrintSheets.forScript(PrintScript.LATIN).sheets.flatMap { it.glyphs }.map {
                Glyph(it.glyphName, 0, emptyList(), unicodes = listOfNotNull(it.codePoint))
            }
        assertEquals(120, glyphs.size, "one project glyph per template cell")
        val info =
            UfoFontInfo(
                familyName = "Scan Seed",
                styleName = "Regular",
                unitsPerEm = UPM,
                ascender = 800,
                descender = -210,
                xHeight = 500,
                capHeight = 700,
            )
        return UfoProject(info, glyphs)
    }

    private fun newSpec() =
        NewProjectSpec(
            name = "Scan Seed",
            brief = Brief(source = BriefSource.PAPER, styleClass = StyleClass(declared = "sans-geometric", confirmed = null)),
            scripts = listOf("Latn"),
            masters = listOf(NewMaster(id = "regular", styleName = "Regular", ufo = scanProject())),
        )

    fun step1b() =
        runBlocking {
            val readings = readLibrary()
            println("GOLDEN 1b: photograph | setting | cells with the right ink")
            for (r in readings) {
                println("GOLDEN 1b: ${r.case.label} | ${r.case.setting.label} | ${r.right} of ${r.verdicts.size}")
                for (v in r.verdicts.filter { !it.correct }) {
                    println(
                        "GOLDEN 1b MISS [${r.case.label}] cell ${v.index} (${v.glyphName}): ${v.miss}",
                    )
                }
            }
            val checked = readings.sumOf { it.verdicts.size }
            println("GOLDEN 1b: ${readings.sumOf { it.right }} of $checked cells hold the right ink over ${readings.size} photographs")
            assertTrue(readings.size >= 12, "at least 12 distinct photographs, was ${readings.size}")
            assertEquals(
                5,
                readings
                    .filter { it.case.script == PrintScript.LATIN }
                    .map { it.case.sheetIndex }
                    .toSet()
                    .size,
                "all five Latin sheets are photographed",
            )
            for (r in readings.filter { it.case.setting != Setting.FAINT_LIMIT }) {
                assertTrue(r.accuracy >= 0.95, "${r.case.label}: ${r.right} of ${r.verdicts.size}")
            }
            for (setting in Setting.entries) {
                val group = readings.filter { it.case.setting == setting }
                if (group.isEmpty() || setting == Setting.FAINT_LIMIT) continue
                val share = group.sumOf { it.right }.toDouble() / group.sumOf { it.verdicts.size }
                assertTrue(share >= 0.95, "${setting.label}: $share")
            }
            // Anti-fake: the five Latin sheets name 120 different glyphs, and a sheet read as another sheet's would not.
            assertEquals(
                120,
                readings
                    .filter { it.case.script == PrintScript.LATIN }
                    .distinctBy { it.case.sheetIndex }
                    .flatMap { it.glyphNames }
                    .toSet()
                    .size,
            )
            // Anti-fake: the judge fails. Drawings one cell off, from the next glyph's outlines, are not the right ink.
            val sheetSpec = PrintSheets.forScript(PrintScript.LATIN).sheets[1]
            val glyphs = PrintSheets.glyphs(PrintScript.LATIN)
            val cells = PrintSheets.CELLS_PER_SHEET
            val shifted = CellArt { _, index -> HyleArt.contours(glyphs[cells + (index + 1) % cells], index) }
            val photo = SyntheticPhotos.make(sheetSpec, PaperSize.A4, GuideStyle.LIGHT, PhotoLibrary.accuracy[1].spec, shifted)
            val offByOne = (SheetCapture.read(photo.gray) as CaptureResult.Success).sheet
            val rightByChance = offByOne.cells.count { CellJudge.judge(it, HyleArt.contours(sheetSpec.glyphs[it.index], it.index)).correct }
            assertTrue(rightByChance <= 6, "drawings one cell off must be caught, $rightByChance of 24 passed")

            val root = ProjectFixtures.tempDir("step1b")
            val session =
                (
                    ProjectSession.create(
                        FileSystemProjectStore(root.toPath()),
                        newSpec(),
                        ProjectFixtures.env(this),
                    ) as CreateResult.Created
                ).session
            val state = session.state.value.font
            assertEquals(
                120,
                state.masters
                    .single()
                    .ufo.glyphs.size,
            )
            assertTrue(state.locks.values.none { it.state == LockState.LOCKED }, "a project made from a scan holds no accepted glyphs")
            assertTrue(
                state.masters
                    .single()
                    .ufo.glyphs
                    .all { it.contours.isEmpty() },
                "a scan makes no outlines until a trace is accepted",
            )
            session.close()
        }

    private class Traced(
        val photo: PhotoLibrary.TracePhoto,
        val target: TraceTargets.Target,
        val cell: CapturedCell,
        val trace: GlyphTrace,
    ) {
        val name: String get() = target.char.toString()
    }

    private fun traceParams(cell: CapturedCell) =
        TraceParameters(metricLines = TraceTargets.metricLines, edgeSoftnessUnits = cell.edgeSoftnessUnits)

    /** T, H, o and n traced from every photograph of the trace library. */
    private fun traceLibrary(): List<Traced> =
        PhotoLibrary.trace.flatMap { photo ->
            val sheets = mutableMapOf<Int, CapturedSheet>()
            TraceTargets.all.map { target ->
                val sheet =
                    sheets.getOrPut(target.sheet) {
                        val spec = PrintSheets.forScript(PrintScript.LATIN).sheets[target.sheet]
                        val shot = SyntheticPhotos.make(spec, PaperSize.A4, GuideStyle.LIGHT, photo.spec, HyleArt)
                        when (val result = SheetCapture.read(shot.gray)) {
                            is CaptureResult.Success -> result.sheet
                            is CaptureResult.Failure -> error("${photo.label}: ${result.failure.sentence}")
                        }
                    }
                val cell = sheet.cells[target.cell]
                assertTrue(cell.status !is CellStatus.Empty, "${photo.label} ${target.char}: read as empty")
                Traced(photo, target, cell, traceCell(cell.raster!!, cell.frame!!, traceParams(cell)))
            }
        }

    fun step2() =
        runBlocking {
            val traced = traceLibrary()
            val sourceFont = Seeds.sTtfFont
            println("GOLDEN 2: photograph | class | glyph | final on/off | target on/off | box off")
            for (t in traced) {
                val trace = t.trace
                assertTrue(trace.stages.size >= 3, "${t.name}: at least three stages, was ${trace.stages.size}")
                val contourNodes = trace.stage(TraceStage.CONTOUR).nodes!!
                val fit = trace.stage(TraceStage.FIT).nodes!!
                assertTrue(
                    fit.total <= contourNodes.total / 5,
                    "${t.photo.label} ${t.name}: Fit ${fit.total} against Contour ${contourNodes.total}",
                )
                assertEquals(t.target.contours, trace.contours.size, "${t.photo.label} ${t.name}: contour count")
                // Anti-fake: ink box within 2% of the UPM of the real font's.
                val source = sourceFont.glyphForCodePoint(t.name[0].code)!!.outlineBounds()!!
                val ours = Glyph(t.name, 0, trace.contours).outlineBounds()!!
                val off2 =
                    maxOf(
                        abs(ours.xMin - source.xMin),
                        abs(ours.xMax - source.xMax),
                        abs(ours.yMin - source.yMin),
                        abs(ours.yMax - source.yMax),
                    )
                assertTrue(
                    off2 <= UPM * 2 / 100,
                    "${t.photo.label} ${t.name}: ink box off by $off2 units (limit ${UPM * 2 / 100}) against the font's $source, ours $ours",
                )
                val final = trace.finalNodes!!
                println(
                    "GOLDEN 2: ${t.photo.label} | ${t.photo.traceClass} | ${t.name} | ${final.onCurve}/${final.offCurve} | " +
                        "${t.target.nodes.onCurve}/${t.target.nodes.offCurve} | $off2",
                )
                if (t.photo.traceClass == PhotoLibrary.TraceClass.EXACT) {
                    assertEquals(t.target.nodes, final, "${t.photo.label} ${t.name}: nodes against the fitted target")
                } else {
                    val claim = TraceTargets.hardClaim(t.target.nodes)
                    assertTrue(
                        final.onCurve <= claim.onCurve && final.offCurve <= claim.offCurve,
                        "${t.photo.label} ${t.name}: $final beyond the stated claim for the hardest photographs, $claim",
                    )
                }
            }
            assertEquals(PhotoLibrary.trace.size * TraceTargets.all.size, traced.size, "every photograph traced all four glyphs")

            // Accepted from the first EXACT photograph: through the session, the lock persists across a close and reopen, an edit is refused.
            val acceptFrom = PhotoLibrary.trace.first { it.traceClass == PhotoLibrary.TraceClass.EXACT }
            val accept = traced.filter { it.photo === acceptFrom }
            assertEquals(4, accept.size)
            val root = ProjectFixtures.tempDir("step2")
            val store = FileSystemProjectStore(root.toPath())
            val created = ProjectSession.create(store, newSpec(), ProjectFixtures.env(this)) as CreateResult.Created
            var session = created.session
            assertTrue(
                session.state.value.font.locks
                    .isEmpty(),
                "nothing is accepted before the person accepts",
            )
            for (t in accept) {
                val result = session.execute(AcceptTrace(GlyphRef("regular", t.name), t.trace.contours, t.trace.suggestedAdvanceWidth))
                assertTrue(result is EditResult.Applied, "${t.name}: accept gave $result")
            }
            session.flush()
            val accepted =
                accept.associate {
                    it.name to
                        session.state.value.font
                            .glyph(GlyphRef("regular", it.name))!!
                }
            for ((name, glyph) in accepted) {
                assertEquals(
                    LockState.LOCKED,
                    session.state.value.font.locks
                        .getValue(GlyphRef("regular", name))
                        .state,
                    "$name is locked once accepted",
                )
                assertTrue(glyph.contours.isNotEmpty())
            }
            assertEquals(4, session.state.value.font.locks.size, "only the four accepted glyphs hold a lock")
            session.close()

            val reopened = (ProjectSession.open(store, ProjectFixtures.env(this)) as OpenResult.Opened).session
            session = reopened
            for ((name, glyph) in accepted) {
                val ref = GlyphRef("regular", name)
                assertEquals(
                    LockState.LOCKED,
                    session.state.value.font.locks
                        .getValue(ref)
                        .state,
                    "$name: the lock persists on disk",
                )
                assertEquals(
                    ApprovalOrigin.TRACE,
                    session.state.value.font.locks
                        .getValue(ref)
                        .origin,
                )
                assertEquals(
                    glyph.contours,
                    session.state.value.font
                        .glyph(ref)!!
                        .contours,
                    "$name: the accepted outline persists",
                )
            }

            // An edit to a locked glyph is refused and changes nothing.
            val tRef = GlyphRef("regular", "T")
            val before =
                session.state.value.font
                    .glyph(tRef)!!
            assertEquals(EditResult.Refused(Refusal.Locked(tRef)), session.execute(MovePoints(tRef, setOf(PointRef(0, 0)), 5, 0)))
            // Re-tracing with different settings does not overwrite the accepted glyph: accepting again over the lock is refused.
            val oRef = GlyphRef("regular", "o")
            val oBefore =
                session.state.value.font
                    .glyph(oRef)!!
            val oCell = accept.single { it.name == "o" }.cell
            val finer = traceParams(oCell).withParameter("fit.adapt", 0.0).withParameter("fit.tolerance", 0.5)
            val again = traceCell(oCell.raster!!, oCell.frame!!, finer)
            assertTrue(
                again.contours != oBefore.contours,
                "the second trace must differ from the accepted one, or this proves nothing",
            )
            val refused = session.execute(AcceptTrace(oRef, again.contours, again.suggestedAdvanceWidth))
            assertEquals(EditResult.Refused(Refusal.Locked(oRef)), refused)
            assertEquals(
                oBefore,
                session.state.value.font
                    .glyph(oRef),
                "o is exactly as accepted",
            )
            assertEquals(
                before,
                session.state.value.font
                    .glyph(tRef),
                "T is exactly as accepted",
            )
            session.close()
        }
}
