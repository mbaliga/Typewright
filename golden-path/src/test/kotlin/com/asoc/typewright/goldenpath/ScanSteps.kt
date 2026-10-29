// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.goldenpath

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.OutlineBounds
import com.asoc.typewright.core.geometry.outlineBounds
import com.asoc.typewright.engine.trace.GlyphTrace
import com.asoc.typewright.engine.trace.TraceParameters
import com.asoc.typewright.engine.trace.TraceStage
import com.asoc.typewright.engine.trace.capture.CaptureResult
import com.asoc.typewright.engine.trace.capture.CapturedSheet
import com.asoc.typewright.engine.trace.capture.CellStatus
import com.asoc.typewright.engine.trace.capture.PhotoSpec
import com.asoc.typewright.engine.trace.capture.SheetCapture
import com.asoc.typewright.engine.trace.capture.SyntheticPhotos
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
 * Golden-path steps 1b and 2, driven end to end on SYNTHETIC photographs of the printed Latin sheets: the repository's own
 * Hyle Deco outlines drawn into the cells of the real template sheets, then seen by a tilted, unevenly lit, noisy, tinted,
 * compressed camera. They are not photographs of paper; real ones are an owner-supplied fixture still owed.
 */
internal object ScanSteps {
    private const val UPM = 1000
    private const val BOX_TOLERANCE_UNITS = 30
    private val metricLines = listOf(-210, 0, 500, 700)

    /** One photograph per Latin sheet, each at a different pose and light, the last two among the hardest the generator makes. */
    private val specs =
        listOf(
            PhotoSpec(seed = 11),
            PhotoSpec(seed = 12, tiltDegrees = 10.0, tiltAxisDegrees = 0.0, rollDegrees = 3.0, fill = 0.8),
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
        )

    /** The five Latin sheets read from their photographs. Failing to read one is a step failure, not a skipped sheet. */
    private class Scan(
        val sheets: List<CapturedSheet>,
        val misses: List<String>,
        val cellsChecked: Int,
        val cellsCorrect: Int,
    )

    private fun scan(): Scan {
        val set = PrintSheets.forScript(PrintScript.LATIN)
        assertEquals(5, set.sheets.size, "the Latin template is five sheets")
        val sheets = mutableListOf<CapturedSheet>()
        val misses = mutableListOf<String>()
        var checked = 0
        var correct = 0
        for ((index, sheet) in set.sheets.withIndex()) {
            val photo = SyntheticPhotos.make(sheet, PaperSize.A4, GuideStyle.LIGHT, specs[index])
            val result = SheetCapture.read(photo.gray)
            val captured =
                when (result) {
                    is CaptureResult.Success -> result.sheet
                    is CaptureResult.Failure -> error("sheet ${index + 1}: ${result.failure.sentence}")
                }
            // Four corners found, and the right sheet read from the printed code.
            assertEquals(4, captured.marks.size, "sheet ${index + 1}: four corner marks")
            assertEquals(index, captured.identity.index, "sheet ${index + 1}: identity")
            assertEquals(PrintScript.LATIN, captured.identity.script)
            sheets += captured
            for (cell in captured.cells) {
                checked++
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
                        -> "ink box ${cell.inkBounds} against ${truth.bounds}"

                        else -> null
                    }
                if (reason == null) correct++ else misses += "sheet ${index + 1} cell ${cell.index} (${truth.glyphName}): $reason"
            }
        }
        return Scan(sheets, misses, checked, correct)
    }

    private fun close(
        a: OutlineBounds,
        b: OutlineBounds,
    ): Boolean =
        abs(a.xMin - b.xMin) <= BOX_TOLERANCE_UNITS && abs(a.xMax - b.xMax) <= BOX_TOLERANCE_UNITS &&
            abs(a.yMin - b.yMin) <= BOX_TOLERANCE_UNITS && abs(a.yMax - b.yMax) <= BOX_TOLERANCE_UNITS

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
            val scan = scan()
            println(
                "GOLDEN 1b: ${scan.cellsCorrect} of ${scan.cellsChecked} cells map to the template's glyph name over ${scan.sheets.size} photographs",
            )
            for (miss in scan.misses) println("GOLDEN 1b MISS $miss")
            assertTrue(scan.cellsChecked == 120, "five sheets of 24 cells, was ${scan.cellsChecked}")
            assertTrue(
                scan.cellsCorrect >= scan.cellsChecked * 95 / 100,
                "${scan.cellsCorrect} of ${scan.cellsChecked} cells correct; misses ${scan.misses}",
            )
            // Anti-fake: the five sheets name 120 different glyphs, and a sheet read as another sheet's would not.
            assertEquals(
                120,
                scan.sheets
                    .flatMap { s -> s.cells.map { it.glyphName } }
                    .toSet()
                    .size,
            )

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
        val name: String,
        val trace: GlyphTrace,
    )

    private fun traceOf(
        scan: Scan,
        name: String,
        params: TraceParameters = TraceParameters(metricLines = metricLines),
    ): Traced {
        val cell = scan.sheets.flatMap { it.cells }.single { it.glyphName == name }
        return Traced(name, traceCell(cell.raster!!, cell.frame!!, params))
    }

    fun step2() =
        runBlocking {
            // The easier photographs are held to the fitted targets (CLAUDE.md); the hardest are measured in engine-trace's tests.
            val easy = scanEasy()
            val targets = mapOf("T" to Triple(1, 8, 0), "H" to Triple(1, 12, 0), "o" to Triple(2, 16, 16), "n" to Triple(1, 14, 8))
            val traced = targets.keys.map { traceOf(easy, it) }
            val sourceFont = Seeds.sTtfFont
            for (t in traced) {
                val (contours, on, off) = targets.getValue(t.name)
                val trace = t.trace
                assertTrue(trace.stages.size >= 3, "${t.name}: at least three stages, was ${trace.stages.size}")
                val counts = trace.stages.mapNotNull { it.nodes }
                val contourStage = trace.stage(TraceStage.CONTOUR).nodes
                val fit = trace.stage(TraceStage.FIT).nodes!!
                if (contourStage !=
                    null
                ) {
                    assertTrue(fit.total <= contourStage.total, "${t.name}: Fit ${fit.total} > Contour ${contourStage.total}")
                }
                assertTrue(counts.isNotEmpty())
                assertEquals(contours, trace.contours.size, "${t.name}: contour count")
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
                    "${t.name}: ink box off by $off2 units (limit ${UPM * 2 / 100}) against the font's $source, ours $ours",
                )
                val final = trace.finalNodes!!
                println(
                    "GOLDEN 2 ${t.name}: contours ${trace.contours.size}, stages ${trace.stages.map {
                        "${it.stage.label}=${it.nodes?.total}"
                    }}, final ${final.onCurve}/${final.offCurve}, box off $off2",
                )
                assertEquals(on to off, final.onCurve to final.offCurve, "${t.name}: on/off nodes against the fitted target")
            }

            // Accept through the session, the lock persists across a close and reopen, and an edit is refused.
            val root = ProjectFixtures.tempDir("step2")
            val store = FileSystemProjectStore(root.toPath())
            val created = ProjectSession.create(store, newSpec(), ProjectFixtures.env(this)) as CreateResult.Created
            var session = created.session
            assertTrue(
                session.state.value.font.locks
                    .isEmpty(),
                "nothing is accepted before the person accepts",
            )
            for (t in traced) {
                val result = session.execute(AcceptTrace(GlyphRef("regular", t.name), t.trace.contours, t.trace.suggestedAdvanceWidth))
                assertTrue(result is EditResult.Applied, "${t.name}: accept gave $result")
            }
            session.flush()
            val accepted =
                traced.associate {
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
            val finer = TraceParameters(metricLines = metricLines).withParameter("fit.adapt", 0.0).withParameter("fit.tolerance", 0.5)
            val again = traceOf(easy, "o", finer)
            assertTrue(
                again.trace.contours != oBefore.contours,
                "the second trace must differ from the accepted one, or this proves nothing",
            )
            val refused = session.execute(AcceptTrace(oRef, again.trace.contours, again.trace.suggestedAdvanceWidth))
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

    private fun scanEasy(): Scan {
        // Only the two sheets that hold T, H, o and n are photographed for step 2, at the two easier settings.
        val set = PrintSheets.forScript(PrintScript.LATIN)
        val sheets =
            set.sheets.take(2).mapIndexed { index, sheet ->
                val photo = SyntheticPhotos.make(sheet, PaperSize.A4, GuideStyle.LIGHT, specs[index])
                when (val result = SheetCapture.read(photo.gray)) {
                    is CaptureResult.Success -> result.sheet
                    is CaptureResult.Failure -> error("sheet ${index + 1}: ${result.failure.sentence}")
                }
            }
        return Scan(sheets, emptyList(), sheets.sumOf { it.cells.size }, sheets.sumOf { it.cells.size })
    }
}
