// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.engine.trace.GrayscaleRaster
import com.asoc.typewright.project.OpenResult
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.SessionEnvironment
import com.asoc.typewright.project.capture.CaptureKind
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.ui.project.SampleProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Capture against a real project: a synthetic photograph of the first Latin sheet (drawn from the sample font's outlines, a test
 * fixture) goes in through the library, is read, is kept in the project, and comes back after the project is closed and opened.
 */
class CaptureLibraryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val decoder = platformImageDecoder()

    /** A picture of Latin sheet [index], every box drawn in the sample font's own letter, turned [degrees]. */
    private fun sheetPicture(
        index: Int = 0,
        skip: Set<Int> = emptySet(),
        degrees: Double = 3.0,
    ): ByteArray {
        val project = SampleProject.openProject(SampleProject.workspace())
        val spec = SyntheticSheets.latinSheet(index)
        val art: Map<Int, List<Contour>> =
            spec.glyphs
                .withIndex()
                .filter { it.index !in skip }
                .mapNotNull { (i, g) ->
                    project.master
                        ?.ufo
                        ?.glyphs
                        ?.firstOrNull { it.name == g.glyphName }
                        ?.let { i to it.contours }
                }.toMap()
        return SyntheticSheets.png(SyntheticSheets.photograph(SyntheticSheets.page(spec, art), degrees))
    }

    private fun library(workspace: com.asoc.typewright.project.ProjectWorkspace): CaptureLibrary {
        val session = checkNotNull(workspace.current.value)
        return CaptureLibrary(session, decoder, scope).also { it.start() }
    }

    @Test
    fun aPhotographedSheetIsReadAndKept() =
        timed {
            val workspace = SampleProject.workspace()
            val library = library(workspace)
            library.add(listOf(PickedFile("scan_001.png", sheetPicture())), CaptureSource.FILE)
            val item = awaitSheet(library)
            val ready = assertIs<SheetState.Ready>(item.state)
            assertEquals(0, ready.sheet.identity.index)
            assertEquals("Sheet 1 · A–X", ready.title)
            assertTrue(ready.read >= 20, "most boxes read, was ${ready.summary}")
            assertEquals("From file · scan_001.png", item.sourceLine)
            assertEquals(CaptureKind.FILE, item.record.kind)
            // Kept in the project's own state, as the sheet and number it was read as.
            val record =
                checkNotNull(workspace.current.value)
                    .state.value.meta.captures.records
                    .single()
            assertEquals("LATIN", record.script)
            assertEquals(0, record.sheet)
            assertEquals(5, record.sheetCount)
            assertEquals("captures/cap-1.png", record.imagePath)
        }

    @Test
    fun aSheetComesBackWhenTheProjectIsClosedAndOpenedAgain() =
        timed {
            val workspace = SampleProject.workspace()
            val picture = sheetPicture()
            val first = library(workspace)
            first.add(listOf(PickedFile("scan_001.png", picture)), CaptureSource.FILE)
            val session = checkNotNull(workspace.current.value)
            val relabelled = awaitSheet(first).let { (it.state as SheetState.Ready).cells.first { c -> c.hasInk }.index }
            first.relabel("cap-1", relabelled, "Q")
            session.flush()
            val store = session.store
            session.close()

            val env = SessionEnvironment(scope = scope, io = Dispatchers.IO)
            val reopened = (ProjectSession.open(store, env) as OpenResult.Opened).session
            val second = CaptureLibrary(reopened, decoder, scope).also { it.start() }
            val item = awaitSheet(second)
            val ready = assertIs<SheetState.Ready>(item.state)
            assertEquals("Sheet 1 · A–X", ready.title)
            assertEquals("Q", ready.cells.first { it.index == relabelled }.glyphName, "the relabel survived")
            assertContentEquals(picture, store.read("captures/cap-1.png"), "the picture is stored as given")
        }

    @Test
    fun aPictureWithNoSheetInItIsNotKeptAndSaysWhatIsMissing() =
        timed {
            val workspace = SampleProject.workspace()
            val library = library(workspace)
            val blank = SyntheticSheets.png(GrayscaleRaster.filled(900, 1200))
            library.add(listOf(PickedFile("blank.png", blank)), CaptureSource.GALLERY)
            val failure = assertNotNull(library.failure.value)
            assertEquals("blank.png", failure.name)
            assertTrue("corner" in failure.sentence, failure.sentence)
            assertEquals(CaptureSource.GALLERY, failure.source, "a retake goes back to the same source")
            assertTrue(
                checkNotNull(workspace.current.value)
                    .state.value.meta.captures.records
                    .isEmpty(),
            )
            assertTrue(library.items.value.isEmpty())
        }

    @Test
    fun aThirdPartyFileThatIsNoPictureIsRefusedInPlainWords() =
        timed {
            val library = library(SampleProject.workspace())
            library.add(listOf(PickedFile("notes.png", "not a picture".encodeToByteArray())), CaptureSource.FILE)
            assertTrue("could not be read as a picture" in assertNotNull(library.failure.value).sentence)
        }

    @Test
    fun takingASheetAgainReplacesTheEarlierOne() =
        timed {
            val workspace = SampleProject.workspace()
            val library = library(workspace)
            library.add(listOf(PickedFile("a.png", sheetPicture()), PickedFile("b.png", sheetPicture(degrees = -2.0))), CaptureSource.FILE)
            val sheets = library.items.first { it.size == 1 && (it.single() as CaptureItem.Sheet).state is SheetState.Ready }
            assertEquals("b.png", (sheets.single() as CaptureItem.Sheet).record.name)
            assertEquals(
                1,
                checkNotNull(workspace.current.value)
                    .state.value.meta.captures.records.size,
            )
            assertTrue("replaced" in assertNotNull(library.note.value))
        }

    @Test
    fun removingASheetDeletesItsRecord() =
        timed {
            val workspace = SampleProject.workspace()
            val library = library(workspace)
            library.add(listOf(PickedFile("a.png", sheetPicture())), CaptureSource.FILE)
            library.remove(awaitSheet(library))
            assertTrue(
                checkNotNull(workspace.current.value)
                    .state.value.meta.captures.records
                    .isEmpty(),
            )
            library.items.first { it.isEmpty() }
        }

    @Test
    fun anSvgDrawingNamedLikeATemplateIsKeptAndStartsAtFit() =
        timed {
            val workspace = SampleProject.workspace()
            val library = library(workspace)
            val template = TemplatePack.packFiles(PrintScript.LATIN).entries.first { it.key.endsWith("053_DIGIT_ONE.svg") }
            val drawn =
                template.value.decodeToString().replace(
                    "<g id=\"DRAW_HERE\"></g>",
                    "<g id=\"DRAW_HERE\"><path d=\"M 280 0 L 280 -700 L 330 -700 L 330 0 Z\" fill=\"#000\"/></g>",
                )
            library.add(listOf(PickedFile("053_DIGIT_ONE.svg", drawn.encodeToByteArray())), CaptureSource.FILE)
            val batch = library.items.first { it.any { item -> item is CaptureItem.SvgBatch } }.single() as CaptureItem.SvgBatch
            assertEquals(1, batch.usable)
            val source = assertIs<TraceSource.Vector>(library.sourceFor("one"))
            assertEquals(1, source.contours.size)
            assertNull(library.sourceFor("two"))
        }

    @Test
    fun anSvgWithAnUnknownNameIsRefusedAndNamesTheProblem() =
        timed {
            val library = library(SampleProject.workspace())
            library.add(listOf(PickedFile("mine.svg", "<svg xmlns='http://www.w3.org/2000/svg'/>".encodeToByteArray())), CaptureSource.FILE)
            assertTrue("not named like one of the pack's templates" in assertNotNull(library.failure.value).sentence)
        }

    /** A read that never finishes is a failure, not a hang. */
    private fun timed(block: suspend () -> Unit) = runBlocking<Unit> { withTimeout(READ_TIMEOUT_MILLIS) { block() } }

    private suspend fun awaitSheet(library: CaptureLibrary): CaptureItem.Sheet =
        library.items
            .first { items -> items.any { it is CaptureItem.Sheet && it.state is SheetState.Ready } }
            .filterIsInstance<CaptureItem.Sheet>()
            .first()

    private companion object {
        const val READ_TIMEOUT_MILLIS = 120_000L
    }
}
