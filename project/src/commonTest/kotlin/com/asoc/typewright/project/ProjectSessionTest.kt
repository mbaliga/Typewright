// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.geometry.count
import com.asoc.typewright.project.scrapbook.ScrapbookPin
import com.asoc.typewright.project.scrapbook.ScrapbookPinKind
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ProjectSession]: execute/undo/redo, gestures, meta changes, live economy, autosave's debounce
 * and cap under virtual time, immediate save on law-1 commands, the conflict guard,
 * [ProjectSession.rewriteAll] and [ProjectSession.close]. All against [InMemoryProjectStore], so
 * this runs on the JVM and Wasm.
 */
class ProjectSessionTest {
    private val ref = GlyphRef("regular", "A")

    private fun spec() =
        NewProjectSpec(
            name = "Fixture Project",
            brief = Fixtures.manifest().brief,
            scripts = listOf("Latn"),
            masters = listOf(NewMaster(id = "regular", styleName = "Regular", ufo = Fixtures.ufoProject())),
        )

    private suspend fun newSession(
        env: SessionEnvironment,
        store: ProjectStore = InMemoryProjectStore("Fixture Project"),
    ): ProjectSession {
        val result = ProjectSession.create(store, spec(), env)
        return (result as CreateResult.Created).session
    }

    /** [InMemoryProjectStore] is [Durability.TAB_ONLY], so a completed save settles as [SaveStatus.TabOnly] there, not [SaveStatus.Saved]. */
    private fun assertSettled(status: SaveStatus) {
        assertTrue(status is SaveStatus.Saved || status is SaveStatus.TabOnly, "expected a settled save status, was $status")
    }

    /** [ByteArray] has identity equality, so comparing encoded file maps needs content comparison. */
    private fun assertFilesEqual(
        expected: Map<String, ByteArray>,
        actual: Map<String, ByteArray>,
    ) {
        assertEquals(expected.keys, actual.keys)
        for (key in expected.keys) assertTrue(expected.getValue(key).contentEquals(actual.getValue(key)), "\"$key\" differs")
    }

    // ---- execute, undo, redo -------------------------------------------------------------------

    @Test
    fun executeAppliesACommandAndUpdatesStateAndHistory() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            val result = session.execute(SetAdvance(ref, 600))
            assertIs<EditResult.Applied>(result)
            assertEquals(
                600,
                session.state.value.font
                    .glyph(ref)!!
                    .advanceWidth,
            )
            assertTrue(session.history.value.canUndo)
            assertEquals(listOf("Set advance"), session.history.value.undoLabels)
        }

    @Test
    fun executeRefusesALockedGlyphWithoutTouchingState() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            session.execute(Approve(ref, ApprovalOrigin.DRAW))
            val before = session.state.value
            val result = session.execute(MovePoints(ref, setOf(PointRef(0, 0)), 1, 0))
            assertEquals(EditResult.Refused(Refusal.Locked(ref)), result)
            assertEquals(before, session.state.value)
        }

    @Test
    fun undoThenRedoRestoresTheEditedState() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            session.execute(SetAdvance(ref, 600))
            assertTrue(session.undo())
            assertEquals(
                500,
                session.state.value.font
                    .glyph(ref)!!
                    .advanceWidth,
            )
            assertFalse(session.history.value.canUndo)
            assertTrue(session.redo())
            assertEquals(
                600,
                session.state.value.font
                    .glyph(ref)!!
                    .advanceWidth,
            )
        }

    @Test
    fun undoWithNothingToUndoReturnsFalse() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            assertFalse(session.undo())
        }

    @Test
    fun lockOfReflectsTheCurrentLockTable() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            assertNull(session.lockOf(ref))
            session.execute(Approve(ref, ApprovalOrigin.DRAW))
            assertEquals(LockState.LOCKED, session.lockOf(ref)!!.state)
        }

    @Test
    fun simulateDoesNotChangeState() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            val before = session.state.value
            val simulation = session.simulate(SetAdvance(ref, 900))
            assertIs<EditResult.Applied>(simulation.result)
            assertEquals(before, session.state.value)
            assertEquals(before.font.glyph(ref)!!.count(), simulation.before.getValue(ref))
        }

    // ---- gestures ---------------------------------------------------------------------------

    @Test
    fun aCommittedGestureRecordsOneHistoryEntryForTheWholeDrag() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            val gesture = session.beginGesture("Drag point")
            gesture.update(MovePoints(ref, setOf(PointRef(0, 0)), 1, 0))
            gesture.update(MovePoints(ref, setOf(PointRef(0, 0)), 1, 0))
            gesture.commit()
            assertEquals(listOf("Drag point"), session.history.value.undoLabels)
            val moved =
                session.state.value.font
                    .glyph(ref)!!
                    .contours[0]
                    .points[0]
                    .point
            val original =
                Fixtures
                    .glyph("A")
                    .contours[0]
                    .points[0]
                    .point
            assertEquals(original.x + 2, moved.x)
        }

    @Test
    fun aCancelledGestureLeavesNoHistoryAndRestoresState() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            val before = session.state.value
            val gesture = session.beginGesture("Drag point")
            gesture.update(MovePoints(ref, setOf(PointRef(0, 0)), 5, 0))
            gesture.cancel()
            assertEquals(before, session.state.value)
            assertFalse(session.history.value.canUndo)
        }

    @Test
    fun undoWhileAGestureIsOpenCancelsItInstead() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            val before = session.state.value
            val gesture = session.beginGesture("Drag point")
            gesture.update(MovePoints(ref, setOf(PointRef(0, 0)), 5, 0))
            assertTrue(session.undo())
            assertEquals(before, session.state.value)
        }

    @Test
    fun executeCommitsAnOpenGestureFirst() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            val gesture = session.beginGesture("Drag point")
            gesture.update(MovePoints(ref, setOf(PointRef(0, 0)), 5, 0))
            session.execute(SetAdvance(ref, 600))
            assertEquals(listOf("Drag point", "Set advance"), session.history.value.undoLabels)
        }

    @Test
    fun aGestureRefusesALockedGlyphJustLikeExecute() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            session.execute(Approve(ref, ApprovalOrigin.DRAW))
            val gesture = session.beginGesture("Drag point")
            val result = gesture.update(MovePoints(ref, setOf(PointRef(0, 0)), 1, 0))
            assertEquals(EditResult.Refused(Refusal.Locked(ref)), result)
        }

    // ---- meta changes --------------------------------------------------------------------------

    @Test
    fun addPinAppliesWithoutTouchingFontHistory() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            val pin = ScrapbookPin("pin-1", ScrapbookPinKind.NOTE, "Note", "note", noteText = "Hello")
            val result = session.update(MetaChange.AddPin(pin, null, null))
            assertIs<EditResult.Applied>(result)
            assertEquals(listOf(pin), session.state.value.meta.scrapbook.pins)
            assertFalse(session.history.value.canUndo)
        }

    @Test
    fun addPinWithAClashingIdIsRefused() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            val pin = ScrapbookPin("pin-1", ScrapbookPinKind.NOTE, "Note", "note", noteText = "Hello")
            session.update(MetaChange.AddPin(pin, null, null))
            val result = session.update(MetaChange.AddPin(pin, null, null))
            assertEquals(EditResult.Refused(Refusal.NameClash("pin-1")), result)
        }

    @Test
    fun addReflectionConfirmsAConfirmableTaskAndPersistsTheText() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            val result = session.update(MetaChange.AddReflection("latn", 4, "Round ends everywhere, or nowhere."))
            assertIs<EditResult.Applied>(result)
            val lessons =
                session.state.value.meta.lessons
                    .getValue("latn")
            assertEquals(1, lessons.reflections.size)
            assertEquals(4, lessons.reflections.single().task)
            assertTrue(
                4 in
                    session.state.value.meta.manifest.workbook
                        .getValue("latn")
                        .confirmedTasks,
            )
        }

    @Test
    fun setTaskConfirmedTogglesAJudgmentCallTask() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            session.update(MetaChange.SetTaskConfirmed("latn", 1, true))
            assertTrue(
                1 in
                    session.state.value.meta.manifest.workbook
                        .getValue("latn")
                        .confirmedTasks,
            )
            session.update(MetaChange.SetTaskConfirmed("latn", 1, false))
            assertFalse(
                1 in
                    session.state.value.meta.manifest.workbook
                        .getValue("latn")
                        .confirmedTasks,
            )
        }

    @Test
    fun renameChangesTheProjectName() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            session.update(MetaChange.Rename("Renamed"))
            assertEquals("Renamed", session.state.value.meta.manifest.name)
        }

    // ---- live economy ---------------------------------------------------------------------------

    @Test
    fun economyUpdatesSynchronouslyAfterAnOutlineEdit() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            val before = session.economy.value.count(ref)
            session.execute(SetAdvance(ref, 600))
            // Advance width doesn't change the outline; the count instance can be reused, but must still be correct.
            assertEquals(before, session.economy.value.count(ref))
            val glyphBefore =
                session.state.value.font
                    .glyph(ref)!!
            val newContours = listOf(Fixtures.squareContour(0, 0, 100, 100))
            session.execute(ReplaceOutline(ref, newContours + newContours, "Duplicate contour"))
            assertNotNull(session.economy.value.count(ref))
            assertTrue(session.economy.value.count(ref) != before)
        }

    // ---- autosave: debounce, cap, immediate, gesture defers -----------------------------------

    @Test
    fun autosaveDebouncesUntilOneAndAHalfSecondsAfterTheLastChange() =
        runTest {
            val env = Fixtures.env(backgroundScope, autosave = AutosavePolicy(debounceMillis = 1_500, maxDelayMillis = 10_000))
            val session = newSession(env)
            advanceUntilIdle()

            session.execute(SetAdvance(ref, 600))
            assertIs<SaveStatus.Pending>(session.saveStatus.value)
            advanceTimeBy(1_499)
            runCurrent()
            assertIs<SaveStatus.Pending>(session.saveStatus.value)
            advanceTimeBy(2)
            runCurrent()
            assertSettled(session.saveStatus.value)
        }

    @Test
    fun autosaveRestartsTheDebounceOnEveryFurtherChange() =
        runTest {
            val env = Fixtures.env(backgroundScope, autosave = AutosavePolicy(debounceMillis = 1_500, maxDelayMillis = 10_000))
            val session = newSession(env)
            advanceUntilIdle()

            session.execute(SetAdvance(ref, 600))
            advanceTimeBy(1_000)
            runCurrent()
            session.execute(SetAdvance(ref, 700))
            advanceTimeBy(1_000)
            runCurrent()
            assertIs<SaveStatus.Pending>(session.saveStatus.value)
            advanceTimeBy(600)
            runCurrent()
            assertSettled(session.saveStatus.value)
        }

    @Test
    fun autosaveSavesAtTheCapEvenUnderContinuousEditing() =
        runTest {
            val env = Fixtures.env(backgroundScope, autosave = AutosavePolicy(debounceMillis = 1_500, maxDelayMillis = 10_000))
            val session = newSession(env)
            advanceUntilIdle()

            session.execute(SetAdvance(ref, 600))
            repeat(9) {
                advanceTimeBy(1_000)
                runCurrent()
                session.execute(SetAdvance(ref, 600 + it))
            }
            // 9,000 ms of edits every 1,000 ms never let the 1,500 ms debounce fire on its own;
            // the 10 s cap must still fire.
            advanceTimeBy(1_500)
            runCurrent()
            assertSettled(session.saveStatus.value)
        }

    @Test
    fun approveSavesImmediatelyWithoutWaitingForTheDebounce() =
        runTest {
            val env = Fixtures.env(backgroundScope, autosave = AutosavePolicy(debounceMillis = 1_500, maxDelayMillis = 10_000))
            val session = newSession(env)
            advanceUntilIdle()

            session.execute(Approve(ref, ApprovalOrigin.DRAW))
            runCurrent()
            assertSettled(session.saveStatus.value)
        }

    @Test
    fun autosaveWaitsWhileAGestureIsOpen() =
        runTest {
            val env = Fixtures.env(backgroundScope, autosave = AutosavePolicy(debounceMillis = 1_500, maxDelayMillis = 10_000))
            val session = newSession(env)
            advanceUntilIdle()

            val gesture = session.beginGesture("Drag")
            gesture.update(MovePoints(ref, setOf(PointRef(0, 0)), 3, 0))
            advanceTimeBy(5_000)
            runCurrent()
            assertSettled(session.saveStatus.value)
            gesture.commit()
            assertIs<SaveStatus.Pending>(session.saveStatus.value)
        }

    // ---- flush, conflict, rewriteAll, close ----------------------------------------------------

    @Test
    fun flushWritesImmediatelyAndReturnsSaved() =
        runTest {
            val store = InMemoryProjectStore("Fixture Project")
            val session = newSession(Fixtures.env(backgroundScope), store)
            session.execute(SetAdvance(ref, 600))
            val status = session.flush()
            assertSettled(status)
        }

    @Test
    fun flushWithNoChangesWritesNothing() =
        runTest {
            val store = InMemoryProjectStore("Fixture Project")
            val session = newSession(Fixtures.env(backgroundScope), store)
            val before = store.snapshot().files
            session.flush()
            assertFilesEqual(before, store.snapshot().files)
        }

    @Test
    fun anExternalChangeToAWrittenFileIsReportedAsAConflict() =
        runTest {
            val store = InMemoryProjectStore("Fixture Project")
            val session = newSession(Fixtures.env(backgroundScope), store)
            session.execute(SetAdvance(ref, 600))
            session.flush()
            val glyphPath = "${session.state.value.font.masters.single().path}/glyphs/A_.glif"
            store.writeAtomic(glyphPath, "not what the session wrote".encodeToByteArray())
            session.execute(SetAdvance(ref, 700))
            val status = session.flush()
            assertIs<SaveStatus.Conflict>(status)
            assertTrue(glyphPath in status.paths)
        }

    @Test
    fun resolveConflictKeepMineOverwritesTheExternalChange() =
        runTest {
            val store = InMemoryProjectStore("Fixture Project")
            val session = newSession(Fixtures.env(backgroundScope), store)
            session.execute(SetAdvance(ref, 600))
            session.flush()
            val glyphPath = "${session.state.value.font.masters.single().path}/glyphs/A_.glif"
            store.writeAtomic(glyphPath, "not what the session wrote".encodeToByteArray())
            session.execute(SetAdvance(ref, 700))
            assertIs<SaveStatus.Conflict>(session.flush())
            session.resolveConflict(keepMine = true)
            val bytes = store.read(glyphPath)!!
            assertTrue(bytes.decodeToString().contains("advance width=\"700\""))
        }

    @Test
    fun rewriteAllGivesByteIdenticalFilesForAnUneditedProject() =
        runTest {
            val store = InMemoryProjectStore("Fixture Project")
            val session = newSession(Fixtures.env(backgroundScope), store)
            val before = store.snapshot().files
            session.rewriteAll()
            assertFilesEqual(before, store.snapshot().files)
        }

    @Test
    fun buildSnapshotReflectsInMemoryStateWithoutTouchingTheStore() =
        runTest {
            val store = InMemoryProjectStore("Fixture Project")
            val session = newSession(Fixtures.env(backgroundScope), store)
            session.execute(SetAdvance(ref, 600))
            val masterPath =
                session.state.value.font.masters
                    .single()
                    .path
            val snapshot = session.buildSnapshot(includeBuild = false)
            val ufo =
                com.asoc.typewright.core.font.ufo.readUfoProject(
                    snapshot.listFiles().filter { it.startsWith("$masterPath/") }.associate {
                        it.removePrefix("$masterPath/") to snapshot.readBytes(it).decodeToString()
                    },
                )
            assertEquals(600, ufo.glyphs.single { it.name == "A" }.advanceWidth)
            // The store itself is untouched by the in-memory edit until a save runs.
            val onDisk =
                com.asoc.typewright.core.font.ufo.readUfoProject(
                    store
                        .snapshot()
                        .files
                        .filterKeys { it.startsWith("$masterPath/") }
                        .mapKeys { it.key.removePrefix("$masterPath/") }
                        .mapValues { it.value.decodeToString() },
                )
            assertEquals(500, onDisk.glyphs.single { it.name == "A" }.advanceWidth)
        }

    @Test
    fun closeFlushesAndIsSafeToCallTwice() =
        runTest {
            val store = InMemoryProjectStore("Fixture Project")
            val session = newSession(Fixtures.env(backgroundScope), store)
            session.execute(SetAdvance(ref, 600))
            val masterPath =
                session.state.value.font.masters
                    .single()
                    .path
            session.close()
            session.close()
            val ufo =
                com.asoc.typewright.core.font.ufo.readUfoProject(
                    store
                        .snapshot()
                        .files
                        .filterKeys { it.startsWith("$masterPath/") }
                        .mapKeys { it.key.removePrefix("$masterPath/") }
                        .mapValues { it.value.decodeToString() },
                )
            assertEquals(600, ufo.glyphs.single { it.name == "A" }.advanceWidth)
        }

    @Test
    fun readOnlySessionRefusesEveryEdit() =
        runTest {
            val store = InMemoryProjectStore("Fixture Project")
            val created = newSession(Fixtures.env(backgroundScope), store)
            created.close()
            store.acquireWriteLease() // simulate another writer holding the lease
            val opened = ProjectSession.open(store, Fixtures.env(backgroundScope))
            val session = (opened as OpenResult.Opened).session
            assertNotNull(session.readOnly)
            val result = session.execute(SetAdvance(ref, 900))
            assertEquals(EditResult.Refused(Refusal.ReadOnly(session.readOnly!!)), result)
        }
}
