// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.imports

import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.font.sfnt.toUfoProject
import com.asoc.typewright.core.font.ufo.writeUfoProject
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.imports.ImportReview
import com.asoc.typewright.project.imports.ImportVerdict
import com.asoc.typewright.project.imports.LicenceKind
import com.asoc.typewright.project.imports.readUfoFolder
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.project.IN_MEMORY_WORKSPACE_ROOT
import com.asoc.typewright.ui.project.createProjectWorkspace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The "Open a font" flow as a state machine, with a fake picker and destination and the real importer, workspace and fonts. */
class FontImportControllerTest {
    private fun fontBytes(path: String): ByteArray = ImportFixtures.fontBytes(path)

    private class FakePicker(
        private val onPick: suspend () -> PickResult,
    ) : FontFilePicker {
        var cancelledWhilePicking = false
        override val canPickUfoFolder: Boolean get() = true
        override val ufoFolderUnavailableReason: String? get() = null

        override suspend fun pickFont(): PickResult =
            try {
                onPick()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelledWhilePicking = true
                throw e
            }

        override suspend fun pickUfoFolder(): PickResult = onPick()
    }

    private class FakeDestination(
        private val parent: ProjectLocation? = IN_MEMORY_WORKSPACE_ROOT,
        private val ask: suspend () -> ProjectLocation? = { null },
    ) : ImportDestination {
        override val persists: Boolean get() = false

        override suspend fun parentFolder(): ProjectLocation? = parent

        override suspend fun chooseParentFolder(): ProjectLocation? = ask()

        override fun describe(
            parent: ProjectLocation?,
            folderName: String,
        ): String = "FAKE: $folderName goes into ${parent ?: "a folder you will be asked for"}"
    }

    /** A dispatcher that runs nothing until [runAll] is called, to hold the flow in the Reading state. */
    private class ManualDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            synchronized(queue) { queue.addLast(block) }
        }

        /** Waits for the flow to hand over its review (it sets the Reading state a moment before it does), then runs it. */
        fun runAll() {
            val deadline = System.nanoTime() + 20_000_000_000L
            while (synchronized(queue) { queue.isEmpty() }) {
                check(System.nanoTime() < deadline) { "the flow never handed its review to the dispatcher" }
                Thread.sleep(1)
            }
            while (true) {
                val next = synchronized(queue) { queue.removeFirstOrNull() } ?: return
                next.run()
            }
        }
    }

    private class Harness(
        picker: FontFilePicker,
        destination: ImportDestination = FakeDestination(),
        reviewDispatcher: CoroutineDispatcher = Dispatchers.Default,
        val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        val workspace: ProjectWorkspace = createProjectWorkspace(scope),
    ) {
        val imported: MutableList<ProjectSession> = java.util.Collections.synchronizedList(mutableListOf())
        val controller =
            FontImportController(workspace, picker, destination, scope, reviewDispatcher) { imported += it }

        fun stateNow() = controller.state.value

        suspend fun awaitState(predicate: (ImportState) -> Boolean): ImportState = withTimeout(20_000) { controller.state.first(predicate) }

        /** Waits until [count] imports have been handed to the app (the controller goes Idle just before it does). */
        suspend fun awaitImported(count: Int) {
            withTimeout(20_000) {
                while (imported.size < count) kotlinx.coroutines.yield()
            }
            awaitState { it == ImportState.Idle }
        }
    }

    private fun picking(
        bytes: ByteArray,
        name: String = "Font.ttf",
    ) = FakePicker { PickResult.Font(name, bytes) }

    @Test
    fun theSampleIsAuditedFirstAndOpensOnlyWhenTheRightCircleIsPressed() =
        runBlocking {
            val h = Harness(picking(ByteArray(0)))
            h.controller.trySample(DeckSection.HOME)
            val auditing = h.awaitState { it is ImportState.Auditing } as ImportState.Auditing
            assertTrue(auditing.sample)
            assertEquals(DeckSection.HOME, auditing.from)
            val ready = assertIs<ImportReview.Ready>(auditing.review)
            assertEquals(LicenceKind.OFL, ready.candidate.audit.licence)
            assertNull(h.workspace.current.value, "nothing is opened silently: the audit comes first")
            assertTrue(h.imported.isEmpty())

            h.controller.confirm()
            h.awaitImported(1)
            val session = assertNotNull(h.workspace.current.value)
            assertEquals(listOf(session), h.imported)
            assertEquals("Hyle Deco", session.state.value.meta.manifest.name)
            assertEquals("sans-geometric", session.state.value.meta.manifest.brief.styleClass.declared)
        }

    @Test
    fun theOpenProjectIsClosedFirstAndStaysOpenIfTheImportFails() =
        runBlocking {
            val poppins = fontBytes("data/learn-faces/poppins/Poppins-Regular.ttf")
            val h = Harness(picking(poppins, "Poppins-Regular.ttf"))
            h.controller.trySample(null)
            h.awaitState { it is ImportState.Auditing }
            h.controller.confirm()
            h.awaitImported(1)
            val first = assertNotNull(h.workspace.current.value)
            assertEquals("Hyle Deco", first.state.value.meta.manifest.name)

            // A second import into a folder that cannot be made: the destination offers none and does not ask for one.
            val failing = Harness(picking(poppins, "Poppins-Regular.ttf"), FakeDestination(parent = null), workspace = h.workspace)
            failing.controller.openFont(DeckSection.DRAW)
            failing.awaitState { it is ImportState.Auditing }
            failing.controller.confirm()
            val back = failing.awaitState { it is ImportState.Auditing && it.error != null } as ImportState.Auditing
            assertEquals("No folder was chosen, so nothing was imported.", back.error)
            val restored = assertNotNull(h.workspace.current.value, "a failed import costs the person nothing")
            assertEquals("Hyle Deco", restored.state.value.meta.manifest.name)

            // A successful second import replaces the first: the new project is current.
            h.controller.openFont(DeckSection.HOME)
            h.awaitState { it is ImportState.Auditing }
            h.controller.confirm()
            h.awaitImported(2)
            val second = assertNotNull(h.workspace.current.value)
            assertEquals("Poppins", second.state.value.meta.manifest.name)
            assertEquals(2, h.imported.size)
        }

    @Test
    fun closeIsInertWhileAFileIsBeingReadAndWhileTheProjectIsWritten() =
        runBlocking {
            val manual = ManualDispatcher()
            val gate = CompletableDeferred<ProjectLocation?>()
            val h =
                Harness(
                    picking(fontBytes("data/learn-faces/poppins/Poppins-Regular.ttf"), "Poppins-Regular.ttf"),
                    FakeDestination(parent = null, ask = { gate.await() }),
                    manual,
                )
            h.controller.openFont(DeckSection.CHECK)
            val reading = h.awaitState { it is ImportState.Reading }
            assertIs<ImportState.Reading>(reading)
            h.controller.close()
            assertIs<ImportState.Reading>(h.stateNow(), "Close does nothing while the file is being read")

            manual.runAll()
            val auditing = h.awaitState { it is ImportState.Auditing } as ImportState.Auditing
            assertEquals(DeckSection.CHECK, auditing.from, "the audit remembers the section the import started from")
            h.controller.confirm()
            h.awaitState { it is ImportState.Creating }
            h.controller.close()
            assertIs<ImportState.Creating>(h.stateNow(), "Close does nothing while the project is being written")

            // cancel() is the host going away: it ends the write and goes back to Idle.
            h.controller.cancel()
            assertEquals(ImportState.Idle, h.stateNow())
            gate.complete(null)
            assertNull(h.workspace.current.value, "a cancelled import opens nothing")
        }

    @Test
    fun closeLeavesAnAuditAndCancelsAnOpenPicker() =
        runBlocking {
            val never = CompletableDeferred<PickResult>()
            val picker = FakePicker { never.await() }
            val h = Harness(picker)
            h.controller.openFont(DeckSection.HOME)
            assertIs<ImportState.Picking>(h.stateNow())
            h.controller.close()
            assertEquals(ImportState.Idle, h.stateNow())
            withTimeout(10_000) {
                while (!picker.cancelledWhilePicking) kotlinx.coroutines.yield()
            }

            h.controller.trySample(DeckSection.HOME)
            h.awaitState { it is ImportState.Auditing }
            h.controller.close()
            assertEquals(ImportState.Idle, h.stateNow())
            assertNull(h.workspace.current.value, "closing the audit imports nothing")
        }

    @Test
    fun aSecondRequestWhileOneIsUnderWayDoesNothing() =
        runBlocking {
            val never = CompletableDeferred<PickResult>()
            val h = Harness(FakePicker { never.await() })
            h.controller.openFont(DeckSection.HOME)
            h.controller.trySample(DeckSection.DRAW)
            val state = h.stateNow()
            assertIs<ImportState.Picking>(state)
            assertEquals(DeckSection.HOME, state.from)
            h.controller.cancel()
        }

    @Test
    fun aPickerThatFailsLeavesAPlainMessageAndCloseClearsIt() =
        runBlocking {
            val h = Harness(FakePicker { PickResult.Failed("This computer has no screen to show a file dialog on.") })
            h.controller.openFont(DeckSection.HOME)
            val message = h.awaitState { it is ImportState.Message } as ImportState.Message
            assertEquals("This computer has no screen to show a file dialog on.", message.text)
            h.controller.close()
            assertEquals(ImportState.Idle, h.stateNow())
        }

    @Test
    fun aProprietaryFontIsShownRefusedAndTheRightCircleDoesNothing() =
        runBlocking {
            val h = Harness(picking(ImportFixtures.proprietaryPoppins(), "Proprietary.ttf"))
            h.controller.openFont(DeckSection.HOME)
            val auditing = h.awaitState { it is ImportState.Auditing } as ImportState.Auditing
            val refused = assertIs<ImportReview.Refused>(auditing.review)
            assertEquals(ImportVerdict.REFUSED, refused.audit.verdict)
            assertFalse(auditing.canImport)
            assertEquals(refused.audit.verdictWords, auditing.whyNot)
            h.controller.confirm()
            assertIs<ImportState.Auditing>(h.stateNow(), "Import stays off")
            assertNull(h.workspace.current.value)
        }

    @Test
    fun aFontThatReservesItsNameCannotBeImportedUntilItIsRenamedAndTheProjectStartsRenamed() =
        runBlocking {
            val h = Harness(picking(fontBytes("data/learn-faces/limelight/Limelight-Regular.ttf"), "Limelight-Regular.ttf"))
            h.controller.openFont(DeckSection.HOME)
            val first = h.awaitState { it is ImportState.Auditing } as ImportState.Auditing
            assertTrue(first.needsName)
            assertFalse(first.canImport)
            assertEquals("Give the font a new name first: it reserves a name of its own.", first.whyNot)
            h.controller.confirm()
            assertIs<ImportState.Auditing>(h.stateNow(), "Import is off until there is a new name")

            h.controller.setNewName("Limelight Redraw")
            val clash = h.stateNow() as ImportState.Auditing
            assertFalse(clash.canImport)
            assertTrue(clash.nameProblem!!.contains("Limelight"), clash.nameProblem)

            h.controller.setNewName("Lumen Display")
            val ok = h.stateNow() as ImportState.Auditing
            assertTrue(ok.canImport)
            assertTrue("Lumen Display" in ok.where, ok.where)

            h.controller.confirm()
            h.awaitImported(1)
            val session = assertNotNull(h.workspace.current.value)
            val info =
                session.state.value.font.masters
                    .single()
                    .ufo.fontInfo
            assertEquals("Lumen Display", info.familyName)
            assertEquals("Lumen Display", session.state.value.meta.manifest.name)
            assertFalse(session.history.value.canUndo, "the rename is how the project starts, not an edit to undo")
        }

    @Test
    fun aUfoAuditSaysWhereFilesGoAndWhichUfoIsAdoptedWhenSeveralShareAFolder() {
        val parent = Files.createTempDirectory("typewright-ufo-adopt-").toFile()
        val files = writeUfoProject(readSfntFont(fontBytes("fonts/HyleDeco-Regular.ttf")).toUfoProject())
        for (ufoName in listOf("HyleDeco-Regular.ufo", "HyleDeco-Bold.ufo")) {
            for ((path, text) in files) {
                File(parent, "$ufoName/$path").apply { parentFile.mkdirs() }.writeText(text)
            }
        }
        val read = readUfoFolder(File(parent, "HyleDeco-Regular.ufo").toPath())
        val folder = assertIs<com.asoc.typewright.project.imports.UfoFolderRead.Read>(read).folder
        val candidate = assertIs<ImportReview.Ready>(folder.review()).candidate
        val text = adoptionText(candidate, candidate.ufoFolder!!)
        assertTrue(parent.path in text, text)
        assertTrue("typewright.json" in text, text)
        assertTrue("HyleDeco-Regular.ufo itself is not rewritten" in text, text)
        assertTrue("HyleDeco-Bold.ufo" in text && "Only HyleDeco-Regular.ufo is imported" in text, text)
    }
}
