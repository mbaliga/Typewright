// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.imports

import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.font.sfnt.toUfoProject
import com.asoc.typewright.core.font.ufo.readUfoProject
import com.asoc.typewright.core.font.ufo.writeUfoProject
import com.asoc.typewright.project.FileSystemProjectStore
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.StorageProvider
import com.asoc.typewright.project.StoreLookup
import com.asoc.typewright.project.imports.ImportReview
import com.asoc.typewright.project.imports.ImportVerdict
import com.asoc.typewright.project.imports.LicenceKind
import com.asoc.typewright.project.imports.UfoFolderRead
import com.asoc.typewright.project.imports.nameTableKeysMentioning
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
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
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

    // ---- Picking: the wait for a picker that may never answer ---------------------------------------------

    @Test
    fun aPickerThatNeverAnswersDoesNotBlockTheNextRequest() =
        runBlocking {
            val poppins = fontBytes("data/learn-faces/poppins/Poppins-Regular.ttf")
            val never = CompletableDeferred<PickResult>()
            val calls =
                java.util.concurrent.atomic
                    .AtomicInteger()
            val picker =
                FakePicker {
                    if (calls.incrementAndGet() == 1) never.await() else PickResult.Font("Poppins-Regular.ttf", poppins)
                }
            val h = Harness(picker)
            h.controller.openFont(DeckSection.HOME)
            assertIs<ImportState.Picking>(h.stateNow())
            withTimeout(10_000) { while (calls.get() < 1) kotlinx.coroutines.yield() }

            // The first picker never answers (a browser that sends no cancel). Asking again must not be ignored.
            h.controller.openFont(DeckSection.DRAW)
            val auditing = h.awaitState { it is ImportState.Auditing } as ImportState.Auditing
            assertEquals(DeckSection.DRAW, auditing.from, "the second request took the first one's place")
            assertEquals("Poppins-Regular.ttf", auditing.review.fileName)
            assertEquals(2, calls.get())
            withTimeout(10_000) { while (!picker.cancelledWhilePicking) kotlinx.coroutines.yield() }
            h.controller.cancel()
        }

    @Test
    fun aLateAnswerFromAPickThatWasEndedIsDropped() =
        runBlocking {
            // A picker whose wait cannot be cancelled and which answers whenever it likes, as a dialog that cannot be
            // dismissed from outside does: a plain continuation, with no cancellation check on the way back.
            val waiting =
                java.util.concurrent.atomic
                    .AtomicReference<kotlin.coroutines.Continuation<PickResult>?>(null)
            val picker = FakePicker { kotlin.coroutines.suspendCoroutine { waiting.set(it) } }
            val h = Harness(picker)
            h.controller.openFont(DeckSection.HOME)
            withTimeout(10_000) { while (waiting.get() == null) kotlinx.coroutines.yield() }
            h.controller.close()
            assertEquals(ImportState.Idle, h.stateNow())

            // The stale dialog answers after Close. Nothing may move: no Reading state, no audit, no message.
            waiting.get()!!.resume(PickResult.Font("Poppins-Regular.ttf", fontBytes("data/learn-faces/poppins/Poppins-Regular.ttf")))
            kotlinx.coroutines.delay(300)
            assertEquals(ImportState.Idle, h.stateNow(), "a late answer must not wedge the flow in Reading")

            // And the flow is usable straight away.
            h.controller.trySample(null)
            h.awaitState { it is ImportState.Auditing }
            h.controller.cancel()
        }

    @Test
    fun closeIsAlwaysAvailableWhilePickingAndTheFlowStartsAgainAfterIt() =
        runBlocking {
            val never = CompletableDeferred<PickResult>()
            val h = Harness(FakePicker { never.await() })
            repeat(3) {
                h.controller.openFont(DeckSection.CHECK)
                assertIs<ImportState.Picking>(h.stateNow())
                h.controller.close()
                assertEquals(ImportState.Idle, h.stateNow(), "Close ends the wait (round ${it + 1})")
            }
        }

    // ---- A UFO that reserves a name ------------------------------------------------------------------------

    /** Real folders: what the desktop app's storage is for a [ProjectLocation.FileSystem]. */
    private class DiskStorage : StorageProvider {
        override suspend fun storeFor(location: ProjectLocation): StoreLookup {
            val path = Path.of((location as? ProjectLocation.FileSystem)?.path ?: return StoreLookup.Missing)
            return if (Files.isDirectory(path)) StoreLookup.Available(FileSystemProjectStore(path)) else StoreLookup.Missing
        }

        override suspend fun createChild(
            parent: ProjectLocation,
            name: String,
        ): ProjectLocation {
            val child = Path.of((parent as ProjectLocation.FileSystem).path).resolve(name)
            Files.createDirectories(child)
            return ProjectLocation.FileSystem(child.toString())
        }
    }

    /** A folder holding `Foo-Regular.ufo`: Hyle Deco's glyphs under the family "Foo", with a Reserved Font Name in its copyright line. */
    private fun folderWithAReservedNameUfo(): File {
        val parent = Files.createTempDirectory("typewright-ufo-reserved-").toFile()
        val hyle = readSfntFont(fontBytes("fonts/HyleDeco-Regular.ttf")).toUfoProject()
        val info =
            hyle.fontInfo.copy(
                familyName = "Foo",
                postscriptFontName = "Foo-Regular",
                copyright = "Copyright 2012 The Foo Project Authors, with the Reserved Font Name Foo",
            )
        for ((path, text) in writeUfoProject(hyle.copy(fontInfo = info))) {
            File(parent, "Foo-Regular.ufo/$path").apply { parentFile.mkdirs() }.writeText(text)
        }
        return parent
    }

    private fun snapshot(dir: File): Map<String, String> =
        dir
            .walkTopDown()
            .filter { it.isFile }
            .associate { it.relativeTo(dir).path to it.readText() }

    private fun ufoPicker(parent: File): FakePicker {
        val read = readUfoFolder(File(parent, "Foo-Regular.ufo").toPath())
        val folder = assertIs<UfoFolderRead.Read>(read).folder
        return FakePicker { PickResult.Ufo(folder) }
    }

    @Test
    fun aUfoThatReservesANameIsRenamedWhereItLiesAndTheAuditSaysSoBeforeHand() =
        runBlocking {
            val parent = folderWithAReservedNameUfo()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val h = Harness(ufoPicker(parent), scope = scope, workspace = createProjectWorkspace(scope, storage = DiskStorage()))
            h.controller.openUfoFolder(DeckSection.HOME)
            val first = h.awaitState { it is ImportState.Auditing } as ImportState.Auditing
            assertTrue(first.needsName)
            assertTrue(first.offersCopy, "a UFO that reserves a name can be imported as a renamed copy instead")
            assertFalse(first.asCopy, "adopting in place is the default")
            assertFalse(first.canImport)
            // The audit says, before anything is pressed, that the .ufo is edited for the rename, which file, and the way out.
            assertTrue("Foo-Regular.ufo will be edited for the rename" in first.where, first.where)
            assertTrue("fontinfo.plist" in first.where && "no other file in it changes" in first.where, first.where)
            assertTrue("renamed copy" in first.where, first.where)

            h.controller.setNewName("Bar Redraw")
            assertTrue((h.stateNow() as ImportState.Auditing).canImport)
            val before = snapshot(File(parent, "Foo-Regular.ufo"))
            h.controller.confirm()
            h.awaitImported(1)

            val after = snapshot(File(parent, "Foo-Regular.ufo"))
            assertEquals(before.keys, after.keys)
            assertEquals(
                listOf("fontinfo.plist"),
                before.keys.filter { before[it] != after[it] },
                "the rename is written to fontinfo.plist only",
            )
            val onDisk = readUfoProject(after).fontInfo
            assertEquals("Bar Redraw", onDisk.familyName)
            assertEquals(emptyList(), nameTableKeysMentioning(onDisk, listOf("Foo")))
            val session = assertNotNull(h.workspace.current.value)
            assertEquals(
                "Bar Redraw",
                session.state.value.font.masters
                    .single()
                    .ufo.fontInfo.familyName,
            )
            assertFalse(session.history.value.canUndo)
            assertTrue(File(parent, "typewright.json").isFile, "the project files are written beside the .ufo")
        }

    @Test
    fun aRenamedCopyOfAUfoLeavesTheOriginalExactlyAsItWas() =
        runBlocking {
            val parent = folderWithAReservedNameUfo()
            val out = Files.createTempDirectory("typewright-ufo-copy-").toFile()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val h =
                Harness(
                    ufoPicker(parent),
                    FakeDestination(parent = ProjectLocation.FileSystem(out.path)),
                    scope = scope,
                    workspace = createProjectWorkspace(scope, storage = DiskStorage()),
                )
            h.controller.openUfoFolder(DeckSection.HOME)
            h.awaitState { it is ImportState.Auditing }
            h.controller.setNewName("Bar Redraw")
            h.controller.setAsCopy(true)
            val chosen = h.stateNow() as ImportState.Auditing
            assertTrue(chosen.asCopy)
            assertTrue("Foo-Regular.ufo is not touched" in chosen.where, chosen.where)
            assertFalse(
                "fontinfo.plist is rewritten" in chosen.where,
                "the copy text must not claim the original is edited: ${chosen.where}",
            )

            val before = snapshot(parent)
            h.controller.confirm()
            h.awaitImported(1)

            assertEquals(before, snapshot(parent), "nothing in the folder the person opened changed, not even fontinfo.plist")
            assertTrue(out.listFiles().orEmpty().any { it.isDirectory && it.name == "Bar Redraw" }, "the copy went into the chosen folder")
            val session = assertNotNull(h.workspace.current.value)
            assertEquals(
                "Bar Redraw",
                session.state.value.font.masters
                    .single()
                    .ufo.fontInfo.familyName,
            )
        }

    @Test
    fun theChoiceBetweenInPlaceAndACopyIsOfferedOnlyToAUfoThatReservesAName() =
        runBlocking {
            // A TrueType font that reserves a name always becomes a new project folder: there is no .ufo of the person's to edit.
            val h = Harness(picking(fontBytes("data/learn-faces/limelight/Limelight-Regular.ttf"), "Limelight-Regular.ttf"))
            h.controller.openFont(DeckSection.HOME)
            val state = h.awaitState { it is ImportState.Auditing } as ImportState.Auditing
            assertTrue(state.needsName)
            assertFalse(state.offersCopy)
            h.controller.setAsCopy(true)
            assertFalse((h.stateNow() as ImportState.Auditing).asCopy, "setAsCopy does nothing where the choice is not on offer")
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
        val folder = assertIs<UfoFolderRead.Read>(read).folder
        val candidate = assertIs<ImportReview.Ready>(folder.review()).candidate
        val text = adoptionText(candidate, candidate.ufoFolder!!)
        assertTrue(parent.path in text, text)
        assertTrue("typewright.json" in text, text)
        assertTrue("HyleDeco-Regular.ufo itself is not rewritten" in text, text)
        assertTrue("HyleDeco-Bold.ufo" in text && "Only HyleDeco-Regular.ufo is imported" in text, text)
    }
}
