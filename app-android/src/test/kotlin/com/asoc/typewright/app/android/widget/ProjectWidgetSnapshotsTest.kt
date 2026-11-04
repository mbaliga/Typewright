// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android.widget

import com.asoc.typewright.project.SaveStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

/** The open-project snapshot: what it counts, what it leaves out, and that it reads back exactly. */
class ProjectWidgetSnapshotsTest {
    @Test
    fun countsApprovedAndUnlockedGlyphsFromTheLocks() {
        val font = WidgetFixtures.font(listOf("a", "b", "c", "d", "e"), locked = setOf("a", "b"), unlocked = setOf("c"))
        val project = ProjectWidgetSnapshots.openProject("Mine", font, SaveStatus.Clean, WorkbookOutcome.Failed)
        assertEquals(5, project.glyphs)
        assertEquals(2, project.approved)
        assertEquals(1, project.unlocked)
        assertEquals(SaveState.SAVED, project.save)
        assertNull(project.savedAtMillis)
    }

    @Test
    fun aLockForAGlyphThatNoLongerExistsIsNotCounted() {
        val withLocks = WidgetFixtures.font(listOf("a", "b"), locked = setOf("a", "b"))
        val font = withLocks.copy(masters = listOf(withLocks.masters.single().let { it.copy(ufo = WidgetFixtures.ufo(listOf("a"))) }))
        val project = ProjectWidgetSnapshots.openProject("Mine", font, SaveStatus.Clean, WorkbookOutcome.Failed)
        assertEquals(1, project.glyphs)
        assertEquals(1, project.approved)
    }

    @Test
    fun saveStatusesReduceToTheHeadersWords() {
        val at = Instant.fromEpochMilliseconds(42)
        assertEquals(SaveState.SAVED, ProjectWidgetSnapshots.saveStateOf(SaveStatus.Clean))
        assertEquals(SaveState.SAVED, ProjectWidgetSnapshots.saveStateOf(SaveStatus.Saved(at)))
        assertEquals(SaveState.SAVING, ProjectWidgetSnapshots.saveStateOf(SaveStatus.Pending(at)))
        assertEquals(SaveState.SAVING, ProjectWidgetSnapshots.saveStateOf(SaveStatus.Saving))
        assertEquals(SaveState.FAILED, ProjectWidgetSnapshots.saveStateOf(SaveStatus.Failed("x.glif", "disk full")))
        assertEquals(SaveState.CONFLICT, ProjectWidgetSnapshots.saveStateOf(SaveStatus.Conflict(listOf("x.glif"))))
        assertEquals(SaveState.READ_ONLY, ProjectWidgetSnapshots.saveStateOf(SaveStatus.ReadOnly("newer format")))
        assertEquals(SaveState.TAB_ONLY, ProjectWidgetSnapshots.saveStateOf(SaveStatus.TabOnly(unexportedChanges = true)))
        val font = WidgetFixtures.font(listOf("a"))
        assertEquals(42L, ProjectWidgetSnapshots.openProject("Mine", font, SaveStatus.Saved(at), WorkbookOutcome.Failed).savedAtMillis)
    }

    @Test
    fun aSnapshotReadsBackExactly() {
        val open = WidgetFixtures.snapshot(WidgetFixtures.openProject())
        assertEquals(SnapshotRead.Present(open), ProjectWidgetSnapshots.decode(ProjectWidgetSnapshots.encode(open)))
        val failed =
            WidgetFixtures.snapshot(
                WidgetFixtures.openProject(save = SaveState.FAILED, savedAtMillis = null, workbook = WorkbookOutcome.Failed),
            )
        assertEquals(SnapshotRead.Present(failed), ProjectWidgetSnapshots.decode(ProjectWidgetSnapshots.encode(failed)))
        val notRun = WidgetFixtures.snapshot(WidgetFixtures.openProject(workbook = WorkbookOutcome.NotRun))
        assertEquals(SnapshotRead.Present(notRun), ProjectWidgetSnapshots.decode(ProjectWidgetSnapshots.encode(notRun)))
        val closed = ProjectWidgetSnapshots.closed(7, "writer-b")
        assertEquals(SnapshotRead.Present(closed), ProjectWidgetSnapshots.decode(ProjectWidgetSnapshots.encode(closed)))
        val tricky = WidgetFixtures.snapshot(WidgetFixtures.openProject().copy(name = "Ünïcode = name: #1 \\ ok"))
        assertEquals(SnapshotRead.Present(tricky), ProjectWidgetSnapshots.decode(ProjectWidgetSnapshots.encode(tricky)))
    }

    @Test
    fun nothingWrittenReadsAsAbsentAndAnythingElseAsUnreadable() {
        assertEquals(SnapshotRead.Absent, ProjectWidgetSnapshots.decode(null))
        assertEquals(SnapshotRead.Unreadable, ProjectWidgetSnapshots.decode(ByteArray(0)))
        assertEquals(SnapshotRead.Unreadable, ProjectWidgetSnapshots.decode("format=2\n".encodeToByteArray()))
        val good = String(ProjectWidgetSnapshots.encode(WidgetFixtures.snapshot(WidgetFixtures.openProject())))
        assertIs<SnapshotRead.Present>(ProjectWidgetSnapshots.decode(good.encodeToByteArray()))
        assertEquals(SnapshotRead.Unreadable, ProjectWidgetSnapshots.decode(good.replace("approved=52", "approved=-1").encodeToByteArray()))
        assertEquals(
            SnapshotRead.Unreadable,
            ProjectWidgetSnapshots.decode(good.replace("approved=52", "approved=999").encodeToByteArray()),
        )
        assertEquals(SnapshotRead.Unreadable, ProjectWidgetSnapshots.decode(good.replace("save=SAVED", "save=MAYBE").encodeToByteArray()))
    }

    @Test
    fun theSnapshotHoldsNoFolderGlyphNameOrPath() {
        val font =
            WidgetFixtures.font(
                listOf("secretglyph", "b"),
                locked = setOf("secretglyph"),
                path = "MyPrivateFolder-Regular.ufo",
            )
        val project =
            ProjectWidgetSnapshots.openProject(
                "Mine",
                font,
                SaveStatus.Failed("glyphs/secretglyph.glif", "boom"),
                WorkbookOutcome.Failed,
            )
        val text = String(ProjectWidgetSnapshots.encode(WidgetFixtures.snapshot(project)))
        assertFalse(text.contains("secretglyph"), text)
        assertFalse(text.contains("MyPrivateFolder"), text)
        assertFalse(text.contains("boom"), text)
    }
}
