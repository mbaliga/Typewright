// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.polygonContour
import com.asoc.typewright.engine.trace.TraceParameters
import com.asoc.typewright.engine.trace.TraceStage
import com.asoc.typewright.engine.trace.capture.FoundMark
import com.asoc.typewright.engine.trace.capture.ImageCorner
import com.asoc.typewright.engine.trace.capture.MarkSearch
import com.asoc.typewright.engine.trace.capture.Pt
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.ui.ScreenshotHarness
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.ProjectDataPacks
import com.asoc.typewright.ui.project.SampleProject
import com.asoc.typewright.ui.project.projectStyle
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.trace.TraceActions
import com.asoc.typewright.ui.trace.TraceFlow
import com.asoc.typewright.ui.trace.TraceFrame
import com.asoc.typewright.ui.trace.TracePageState
import com.asoc.typewright.ui.trace.TraceRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Capture, Templates, Camera and Trace drawn at phone width (390 dp at density 2) as PNGs under `ui/build/screenshots/`, to be put
 * beside the boards in `ui/v1-screens/`. What a picture can answer is asserted here (the words on the page); how it looks is for a person.
 * The photographs are synthetic: drawn from the sample font's outlines on the repository's own sheet model.
 */
class CaptureScreenshotTest {
    private val texture: CanvasTexture = CanvasTextures.PAPER
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val width = 780
    private val height = 1688

    private class World(
        val workspace: ProjectWorkspace,
        val library: CaptureLibrary,
    )

    /** A box of ink running right out to the edge of its cell: the reader cannot tell what it is, so it asks. */
    private fun smear(): List<Contour> =
        listOf(polygonContour(listOf(Point(-20, -150), Point(700, -150), Point(700, 820), Point(-20, 820)), CurveFormat.CUBIC))

    private fun world(
        sheets: List<Int> = listOf(0),
        unsure: Set<Int> = setOf(11, 16),
    ): World {
        val workspace = SampleProject.workspace()
        val session = checkNotNull(workspace.current.value)
        val font =
            session.state.value.font.masters
                .first()
                .ufo.glyphs
        val library = CaptureLibrary(session, platformImageDecoder(), scope).also { it.start() }
        runBlocking {
            for ((n, index) in sheets.withIndex()) {
                val spec = SyntheticSheets.latinSheet(index)
                val art =
                    spec.glyphs
                        .withIndex()
                        .mapNotNull { (i, g) ->
                            when {
                                i in unsure && index == 0 -> i to smear()
                                else -> font.firstOrNull { it.name == g.glyphName }?.let { i to it.contours }
                            }
                        }.toMap()
                val bytes =
                    SyntheticSheets.png(
                        SyntheticSheets.photograph(
                            SyntheticSheets.page(spec, art, pxPerMm = 6.0),
                            if (n ==
                                0
                            ) {
                                3.0
                            } else {
                                -2.0
                            },
                        ),
                    )
                library.add(
                    listOf(
                        PickedFile(
                            if (n ==
                                0
                            ) {
                                "photo.jpg"
                            } else {
                                "scan_00${n + 1}.png"
                            },
                            bytes,
                        ),
                    ),
                    if (n == 0) CaptureSource.CAMERA else CaptureSource.FILE,
                )
            }
            library.items.first { items ->
                items.size == sheets.size &&
                    items.all { it is CaptureItem.Sheet && it.state is SheetState.Ready }
            }
        }
        return World(workspace, library)
    }

    @Composable
    private fun Frame(
        world: World?,
        page: CapturePageState = CapturePageState(),
        failure: AddFailure? = null,
        items: List<CaptureItem> =
            world
                ?.library
                ?.items
                ?.value
                .orEmpty(),
        hasProject: Boolean = true,
    ) {
        CaptureFrame(
            texture = texture,
            projectName = "Hyle Deco Redraw",
            hasProject = hasProject,
            items = items,
            failure = failure,
            note = null,
            busy = false,
            sources = listOf(CaptureSource.CAMERA, CaptureSource.GALLERY, CaptureSource.FILE),
            acceptsDrops = true,
            page = page,
            actions = CaptureActions(),
        )
    }

    @Test
    fun captureSheetsWithTwoSheetsAndTheHintToPrintTheRest() {
        val world = world(sheets = listOf(0, 1))
        val shot = ScreenshotHarness.captureWithText("capture-sheets", width, height) { Frame(world) }
        assertTrue(shot.has("Sheet 1 · A–X"), "the first sheet is named by its range")
        assertTrue(shot.has("Sheet 2 · Y–v"))
        assertTrue(shot.has("2 NEED YOU") || shot.has("2 need you".uppercase()), "the two smeared boxes need the person")
        assertTrue(shot.has("Photographed · perspective fixed".uppercase()))
        assertTrue(shot.has("From file · scan_002.png".uppercase()))
        assertTrue(shot.has("3 sheets to go"))
        assertTrue(shot.has("Print the sheets ›"))
        assertTrue(shot.has("not been tried on a real phone"), "Capture says the camera is unverified where it offers it")
    }

    @Test
    fun aLowerCaseCellIsLabelledInLowerCaseSoLAndCapitalIStayApart() {
        val world = world(sheets = listOf(1))
        val ready =
            (
                world.library.items.value
                    .first() as CaptureItem.Sheet
            ).state as SheetState.Ready
        val l = ready.cells.firstOrNull { it.glyphName == "l" }
        assertNotNull(l, "the second Latin sheet carries a lower-case l")
        assertEquals("l", cellLabel(PrintScript.LATIN, l), "the cell reads as the character the template names")
        val page =
            CapturePageState(
                view = CaptureView.CELLS,
                sheet =
                    (
                        world.library.items.value
                            .first() as CaptureItem.Sheet
                    ).record.id,
            )
        ScreenshotHarness.captureWithText("capture-cells-lower", width, height) { Frame(world, page) }
    }

    @Test
    fun captureCellsShowsTheSmearedBoxesAsInkBlocksAndTheRelabelList() {
        val world = world()
        val sheet =
            world.library.items.value
                .first() as CaptureItem.Sheet
        val ready = sheet.state as SheetState.Ready
        val unsure = ready.cells.filter { it.needsYou }
        assertTrue(unsure.size >= 2, "the two smeared boxes are unsure, was ${unsure.map { it.index }}")
        val page = CapturePageState(view = CaptureView.CELLS, sheet = sheet.record.id)
        val shot = ScreenshotHarness.captureWithText("capture-cells", width, height) { Frame(world, page) }
        assertTrue(shot.has("Sheet 1 · cells"))
        val open = CapturePageState(view = CaptureView.CELLS, sheet = sheet.record.id, relabelCell = unsure.first().index)
        val listShot = ScreenshotHarness.captureWithText("capture-cells-relabel", width, height) { Frame(world, open) }
        assertTrue(listShot.has("CELL ${unsure.first().index + 1} · MOST LIKELY FIRST"))
        assertTrue(listShot.has("OR TYPE ANY NAME"))
    }

    @Test
    fun captureStatesEmptyFailedAndNoProject() {
        val empty = ScreenshotHarness.captureWithText("capture-empty", width, height) { Frame(null) }
        assertTrue(empty.has("No sheets yet"))
        assertTrue(empty.has("Print the sheets ›"))
        val failure =
            AddFailure(
                "Photo",
                "3 of 4 corners: the bottom right mark is missing. Move back until all four black squares are in the picture.",
                CaptureSource.CAMERA,
            )
        val failed = ScreenshotHarness.captureWithText("capture-failed", width, height) { Frame(null, failure = failure) }
        assertTrue(failed.has("bottom right mark is missing"))
        assertTrue(failed.has("Retake"))
        val none = ScreenshotHarness.captureWithText("capture-no-project", width, height) { Frame(null, hasProject = false) }
        assertTrue(none.has("Open or start a project"))
        assertTrue(none.has("Print the sheets ›"), "Templates stay reachable with no project open")
    }

    @Test
    fun templatesShowsTheFiveLatinSheetsAndTheTwoExports() {
        val shot =
            ScreenshotHarness.captureWithText("templates", width, height) {
                TemplatesFrame(texture, TemplatesState(), exporterNote = "a save dialog", onBack = {}, onExportPdf = {}, onExportPack = {})
            }
        assertTrue(shot.has("Print sheets · for pen and paper"))
        assertTrue(shot.has("1 · A–X".uppercase()))
        assertTrue(shot.has("5 · ".uppercase()), "five sheets")
        assertTrue(shot.has("Export the SVG pack ›"))
        assertTrue(shot.has("Templates are CC0. What you draw on them is yours."))
        val kana = TemplatesState(script = PrintScript.KANA)
        val kanaShot =
            ScreenshotHarness.captureWithText("templates-kana", width, height) {
                TemplatesFrame(texture, kana, exporterNote = "a save dialog", onBack = {}, onExportPdf = {}, onExportPack = {})
            }
        assertTrue(kanaShot.has("Kana · 112 glyphs"))
    }

    @Test
    fun cameraWithThreeCornersLockedAndTheShutterNotYetArmed() {
        val tracker = CameraLockTracker()
        val three = listOf(ImageCorner.TOP_LEFT, ImageCorner.TOP_RIGHT, ImageCorner.BOTTOM_LEFT)
        repeat(2) { tracker.offer(MarkSearch(three.map { FoundMark(Pt(1.0, 1.0), 10.0, it) }, listOf(ImageCorner.BOTTOM_RIGHT), 3)) }
        val shot =
            ScreenshotHarness.captureWithText("camera", width, height) {
                CameraFrame(CameraAccess.Ready(FakeFeed), onClose = {}, onPhoto = { _, _ -> }, tracker = tracker)
            }
        assertTrue(shot.has("3 of 4 corners · the bottom right one is missing"))
        assertTrue(shot.has("The shutter arms when the last corner locks · hold for manual"))
        assertTrue(shot.has("not been tried on a real phone"), "the camera says in words that it is unverified")
        val none =
            ScreenshotHarness.captureWithText("camera-unavailable", width, height) {
                CameraFrame(CameraAccess.Unavailable(NO_CAMERA_HERE), onClose = {}, onPhoto = { _, _ -> })
            }
        assertTrue(none.has("only in the Android app"))
    }

    private object FakeFeed : CameraFeed {
        @Composable
        override fun Preview(modifier: Modifier) {
            Canvas(modifier) {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0xFF4A463E), Color(0xFF26241F), Color(0xFF0B0B0A)),
                        center =
                            Offset(size.width / 2, size.height * 0.42f),
                        radius = size.width,
                    ),
                )
            }
        }

        override fun analyse(onFrame: ((com.asoc.typewright.engine.trace.GrayscaleRaster) -> Unit)?) = Unit

        override fun setFlash(on: Boolean) = Unit

        override suspend fun takePhoto(): ByteArray? = null
    }

    // ---- Trace --------------------------------------------------------------------------------

    private fun trace(
        name: String,
        stage: TraceStage,
        locked: Boolean = false,
        all: Boolean = false,
        parameters: TraceParameters = TraceParameters(),
        file: String,
        degrees: Double = 0.0,
        running: Boolean = false,
        message: String? = null,
    ) {
        val workspace = SampleProject.workspace()
        val session = checkNotNull(workspace.current.value)
        val state = session.state.value
        val master = state.font.masters.first()
        val spec = SyntheticSheets.latinSheet(if (name.first().isLowerCase()) 1 else 0)
        val art =
            spec.glyphs
                .withIndex()
                .mapNotNull { (i, g) ->
                    master.ufo.glyphs.firstOrNull { it.name == g.glyphName }?.let {
                        i to
                            it.contours
                    }
                }.toMap()
        val png = SyntheticSheets.png(SyntheticSheets.photograph(SyntheticSheets.page(spec, art, pxPerMm = 8.0), degrees))
        val library = CaptureLibrary(session, platformImageDecoder(), scope).also { it.start() }
        runBlocking {
            library.add(listOf(PickedFile("sheet.png", png)), CaptureSource.FILE)
            library.items.first { items -> items.any { it is CaptureItem.Sheet && it.state is SheetState.Ready } }
        }
        val source = checkNotNull(library.sourceFor(name))
        val lines = TraceFlow.metricLines(master.ufo.fontInfo, source.script)
        val outcome = TraceRunner.run(source, parameters.copy(metricLines = lines))
        val project = SampleProject.openProject(workspace)
        val style = projectStyle(project.manifest, checkNotNull(project.font))
        val box = style?.let { ProjectDataPacks.corpus?.onCurveBox(it.key, name) }
        val page = TracePageState(all = all, stage = stage)
        page.message = message
        val shot =
            ScreenshotHarness.captureWithText(file, width, height) {
                CompositionLocalProvider(LocalProjectWorkspace provides workspace) {
                    TraceFrame(
                        texture = texture,
                        hasProject = true,
                        sourceCount = library.sources().size,
                        source = source,
                        outcome = if (running) null else outcome,
                        outcomeIsCurrent = !running,
                        page = page,
                        locked = locked,
                        box = box,
                        styleName = style?.name,
                        styleIsHeuristic = style?.isHeuristic ?: false,
                        actions = TraceActions(),
                        metricLabels = TraceFlow.metricLabels(master.ufo.fontInfo, source.script),
                        metricLines = lines,
                    )
                }
            }
        assertTrue(shot.has("$name"), "the header names the glyph")
        if (running) {
            assertTrue(shot.has("TRACING…"), "while the first run is in flight the page says so")
        } else {
            assertTrue(shot.has(outcome.stage(stage)!!.sentence.take(40)), "the stage's sentence is on the page")
        }
        message?.let { assertTrue(shot.has(it), "the confirmation is on the page") }
        if (locked) assertTrue(shot.has("Unlock to re-trace"))
    }

    @Test
    fun traceTAtFitAndAtTheRawBoundary() {
        trace("T", TraceStage.FIT, file = "trace-T-fit")
        trace("T", TraceStage.CONTOUR, all = true, file = "trace-T-contour-all")
    }

    @Test
    fun traceOAtSnapAndALockedGlyph() {
        trace("O", TraceStage.SNAP, all = true, file = "trace-O-snap")
        trace("T", TraceStage.REVIEW, locked = true, file = "trace-T-locked")
    }

    @Test
    fun traceWhileTheFirstRunIsInFlightAndAfterAcceptingTheGlyphBefore() {
        trace("O", TraceStage.FIT, running = true, file = "trace-O-running")
        trace("O", TraceStage.FIT, message = "T accepted and locked. On to O.", file = "trace-O-after-accept")
    }

    @Test
    fun traceWithNothingCapturedPointsToCapture() {
        val shot =
            ScreenshotHarness.captureWithText("trace-empty", width, height) {
                TraceFrame(texture, true, 0, null, null, TracePageState(), false, null, null, false, TraceActions())
            }
        assertTrue(shot.has("Nothing has been captured yet"))
        assertTrue(shot.has("Capture ›"))
    }
}
