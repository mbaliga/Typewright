// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android.widget

import com.asoc.typewright.learn.scenes.LineagesResources
import com.asoc.typewright.project.ProjectLocation
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What each widget shows for each state and size (SCREENS_V1 §9.2). */
class WidgetContentTest {
    private fun present(project: OpenProject?) = SnapshotRead.Present(WidgetFixtures.snapshot(project))

    // ---- shared states

    @Test
    fun withNoSnapshotOrAClosedProjectEveryProjectWidgetSaysNoProjectOpen() {
        for (read in listOf(SnapshotRead.Absent, present(null))) {
            assertEquals(OpenProjectView.NoProject, approvedView(read))
            assertEquals(OpenProjectView.NoProject, savedView(read, "writer-a"))
            assertEquals(OpenProjectView.NoProject, workbookView(read))
        }
    }

    @Test
    fun anUnreadableSnapshotIsAnErrorNotAnEmptyProject() {
        assertEquals(OpenProjectView.Unreadable, approvedView(SnapshotRead.Unreadable))
        assertEquals(OpenProjectView.Unreadable, savedView(SnapshotRead.Unreadable, "writer-a"))
        assertEquals(OpenProjectView.Unreadable, workbookView(SnapshotRead.Unreadable))
    }

    @Test
    fun aReadyViewCarriesTheNameOnlyWhenNamesMayShowAndTheTimeItWasPublished() {
        val read = present(WidgetFixtures.openProject())
        val shown = assertIs<OpenProjectView.Ready<ApprovedCounts>>(approvedView(read, ProjectNames.SHOWN))
        assertEquals("Hyle Deco", shown.name)
        assertEquals(1_790_000_100_000, shown.asOfMillis)
        for (view in listOf(
            approvedView(read, ProjectNames.WITHHELD),
            savedView(read, "writer-a", ProjectNames.WITHHELD),
            workbookView(read, ProjectNames.WITHHELD),
        )) {
            val withheld = assertIs<OpenProjectView.Ready<*>>(view)
            assertNull(withheld.name)
            assertEquals(1_790_000_100_000, withheld.asOfMillis)
        }
    }

    @Test
    fun namesAreWithheldUntilTheOwnerSaysOtherwise() {
        // docs/OPEN_QUESTIONS.md item 131: a project's name is also its folder's name.
        assertEquals(ProjectNames.WITHHELD, WidgetPrivacy.projectNames)
        val view = assertIs<OpenProjectView.Ready<ApprovedCounts>>(approvedView(present(WidgetFixtures.openProject())))
        assertNull(view.name)
    }

    // ---- W1

    @Test
    fun approvedCountsAndTheRowsEachSizeShows() {
        val view = assertIs<OpenProjectView.Ready<ApprovedCounts>>(approvedView(present(WidgetFixtures.openProject())))
        val counts = view.value
        assertEquals(ApprovedCounts(338, 52, 3), counts)
        assertEquals(283, counts.notYetApproved)
        assertEquals(emptyList(), counts.rows(WidgetSize.SMALL))
        assertEquals(listOf(ApprovalRow.UNLOCKED), counts.rows(WidgetSize.MEDIUM))
        assertEquals(ApprovalRow.entries, counts.rows(WidgetSize.LARGE))
        assertEquals(listOf(52, 3, 283), ApprovalRow.entries.map(counts::count))
        assertEquals(emptyList(), ApprovedCounts(10, 4, 0).rows(WidgetSize.MEDIUM))
    }

    @Test
    fun aProjectWithNoGlyphsIsEmpty() {
        val view = assertIs<OpenProjectView.Ready<ApprovedCounts>>(approvedView(present(WidgetFixtures.openProject(0, 0, 0))))
        assertTrue(view.value.isEmpty)
    }

    @Test
    fun approvalRowsUseTheirShapes() {
        assertEquals(listOf(Mark.PASS, Mark.WARN, Mark.TODO), ApprovalRow.entries.map { it.mark })
    }

    // ---- W2

    @Test
    fun aSavedProjectKeepsItsSaveTime() {
        val view = assertIs<OpenProjectView.Ready<SavedValue>>(savedView(present(WidgetFixtures.openProject()), "writer-a"))
        assertEquals(SavedValue(SavedKind.SAVED, 1_790_000_050_000), view.value)
    }

    @Test
    fun aPendingSaveIsSavingOnlyWhileItsWriterRuns() {
        val saving = present(WidgetFixtures.openProject(save = SaveState.SAVING, savedAtMillis = null))
        val live = assertIs<OpenProjectView.Ready<SavedValue>>(savedView(saving, "writer-a"))
        assertEquals(SavedKind.SAVING, live.value.kind)
        val stopped = assertIs<OpenProjectView.Ready<SavedValue>>(savedView(saving, "a-later-process"))
        assertEquals(SavedKind.NOT_CONFIRMED, stopped.value.kind)
        assertEquals(Mark.TODO, stopped.value.kind.mark)
    }

    @Test
    fun aFinishedStateStaysTrueAfterItsWriterStops() {
        for ((state, kind) in listOf(SaveState.SAVED to SavedKind.SAVED, SaveState.FAILED to SavedKind.FAILED)) {
            val view = assertIs<OpenProjectView.Ready<SavedValue>>(savedView(present(WidgetFixtures.openProject(save = state)), "later"))
            assertEquals(kind, view.value.kind)
        }
    }

    @Test
    fun everySaveStateHasAShapeAndNoneUsesTheCurrentBlock() {
        assertEquals(Mark.PASS, SavedKind.SAVED.mark)
        assertEquals(Mark.WARN, SavedKind.SAVING.mark)
        assertEquals(Mark.FAIL, SavedKind.FAILED.mark)
        assertEquals(Mark.FAIL, SavedKind.CONFLICT.mark)
        assertEquals(Mark.INFO, SavedKind.READ_ONLY.mark)
        assertFalse(SavedKind.entries.any { it.mark == Mark.BLOCK })
    }

    // ---- W3

    @Test
    fun recentsAreMostRecentlyOpenedFirstWithOneThreeOrFiveRows() {
        val recents = (1..7).map { WidgetFixtures.recent("P$it", openedAtMillis = it * 1_000L) }.shuffled(kotlin.random.Random(4))
        val small = recentsView(recents, null, WidgetSize.SMALL, ProjectNames.SHOWN)
        assertEquals(listOf("P7"), small.rows.map { it.name })
        assertEquals(0, small.more)
        val medium = recentsView(recents, null, WidgetSize.MEDIUM, ProjectNames.SHOWN)
        assertEquals(listOf("P7", "P6", "P5"), medium.rows.map { it.name })
        assertEquals(0, medium.more)
        val large = recentsView(recents, null, WidgetSize.LARGE, ProjectNames.SHOWN)
        assertEquals(listOf("P7", "P6", "P5", "P4", "P3"), large.rows.map { it.name })
        assertEquals(2, large.more)
    }

    @Test
    fun withheldRecentRowsKeepTheirOrderCountsTimesAndKeysButNoName() {
        val recents = (1..3).map { WidgetFixtures.recent("Secret $it", openedAtMillis = it * 1_000L, glyphs = it * 10) }
        val view = recentsView(recents, recents[2].location, WidgetSize.MEDIUM, ProjectNames.WITHHELD)
        assertEquals(listOf(null, null, null), view.rows.map { it.name })
        assertEquals(listOf(30, 20, 10), view.rows.map { it.glyphs })
        assertEquals(listOf(3_000L, 2_000L, 1_000L), view.rows.map { it.openedAtMillis })
        assertEquals(listOf(true, false, false), view.rows.map { it.isOpen })
        assertEquals(WidgetLaunch.recentKey(recents[2].location), view.rows[0].key)
        assertFalse(view.toString().contains("Secret"), view.toString())
        // The widget's default.
        assertEquals(view, recentsView(recents, recents[2].location, WidgetSize.MEDIUM))
    }

    @Test
    fun theOpenProjectIsMarkedAndEveryRowCarriesItsKey() {
        val open = WidgetFixtures.recent("Open one", 2_000)
        val other = WidgetFixtures.recent("Other", 1_000, glyphs = null)
        val view = recentsView(listOf(other, open), open.location, WidgetSize.MEDIUM, ProjectNames.SHOWN)
        assertEquals(listOf(true, false), view.rows.map { it.isOpen })
        assertEquals(WidgetLaunch.recentKey(open.location), view.rows[0].key)
        assertEquals(338, view.rows[0].glyphs)
        assertNull(view.rows[1].glyphs)
        assertEquals(2_000L, view.rows[0].openedAtMillis)
    }

    @Test
    fun aRecentRowNeverCarriesItsFolder() {
        val location = ProjectLocation.SafTree("content://tree/primary%3ASecretFolder", "primary:SecretFolder/Mine")
        val row =
            recentsView(
                listOf(WidgetFixtures.recent("Mine", 1, location = location)),
                null,
                WidgetSize.LARGE,
                ProjectNames.SHOWN,
            ).rows.single()
        assertFalse(row.toString().contains("SecretFolder"), row.toString())
    }

    @Test
    fun noRecentsIsTheEmptyState() {
        assertEquals(RecentsView(emptyList(), 0), recentsView(emptyList(), null, WidgetSize.LARGE))
    }

    // ---- W4

    @Test
    fun theWorkbookViewCarriesTheSummaryOrSaysWhyItHasNone() {
        val ready = assertIs<OpenProjectView.Ready<WorkbookOutcome>>(workbookView(present(WidgetFixtures.openProject())))
        val summary = assertIs<WorkbookOutcome.Ready>(ready.value).summary
        assertEquals(4, summary.currentIndex)
        assertEquals(3, summary.done)
        assertEquals(12, summary.total)
        for (outcome in listOf(WorkbookOutcome.Failed, WorkbookOutcome.NotRun)) {
            val view =
                assertIs<OpenProjectView.Ready<WorkbookOutcome>>(workbookView(present(WidgetFixtures.openProject(workbook = outcome))))
            assertEquals(outcome, view.value)
        }
    }

    @Test
    fun theStripAndChecksUseShapes() {
        assertEquals(listOf(Mark.PASS, Mark.BLOCK, Mark.TODO), listOf(TaskState.DONE, TaskState.CURRENT, TaskState.TODO).map { it.mark() })
        assertEquals(Mark.TODO, CheckState.NOT_MEASURED.mark())
        assertEquals(Mark.FAIL, CheckState.FAIL.mark())
    }

    @Test
    fun checkCountsPutFailsFirstAndDropZeros() {
        val summary =
            WidgetFixtures.summary(
                checks = mapOf(CheckState.PASS to 1, CheckState.FAIL to 3, CheckState.WARN to 0, CheckState.NOT_MEASURED to 2),
            )
        assertEquals(listOf(CheckState.FAIL to 3, CheckState.PASS to 1, CheckState.NOT_MEASURED to 2), summary.checkCounts())
    }

    // ---- W5

    @Test
    fun lineagesTurnOneEraADayInPlayOrderAndWrapAround() {
        val scenes = LineagesResources.loadEraScenes()
        assertEquals(10, scenes.size)
        val first = lineageFor(scenes, epochDay = 0)!!
        assertEquals(1, first.position)
        assertEquals(scenes[0].title, first.title)
        assertEquals(10, first.count)
        assertEquals(scenes[3].title, lineageFor(scenes, epochDay = 3)!!.title)
        assertEquals(first, lineageFor(scenes, epochDay = 10))
        assertEquals(scenes[9].title, lineageFor(scenes, epochDay = -1)!!.title)
        assertNull(lineageFor(emptyList(), epochDay = 5))
    }

    @Test
    fun aLineageCardCarriesTheScenesOwnCaption() {
        val scenes = LineagesResources.loadEraScenes()
        for (day in scenes.indices) {
            val card = lineageFor(scenes, day.toLong())!!
            val scene = scenes[day]
            assertEquals(scene.caption.text.trim(), card.caption)
            assertEquals(scene.caption.tags, card.tags)
            assertEquals(scene.scaffold, card.scaffold)
            assertTrue(card.title.isNotBlank() && card.caption.isNotBlank(), card.title)
        }
    }

    @Test
    fun theDayTurnsAtLocalMidnight() {
        val zone = ZoneId.of("Asia/Kolkata")
        val lateEvening =
            LocalDateTime
                .of(2026, 11, 4, 23, 59)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val justAfter =
            LocalDateTime
                .of(2026, 11, 5, 0, 1)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        assertEquals(localEpochDay(lateEvening, zone) + 1, localEpochDay(justAfter, zone))
        val midnight = nextLocalMidnightMillis(lateEvening, zone)
        assertEquals(LocalDateTime.of(2026, 11, 5, 0, 0), LocalDateTime.ofInstant(Instant.ofEpochMilli(midnight), zone))
    }

    @Test
    fun nextMidnightIsAlwaysAheadAcrossADaylightSavingChange() {
        val zone = ZoneId.of("America/New_York")
        // 1 Nov 2026 is the autumn change: that day is 25 hours long.
        val start =
            LocalDateTime
                .of(2026, 11, 1, 0, 30)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val next = nextLocalMidnightMillis(start, zone)
        assertEquals(LocalDateTime.of(2026, 11, 2, 0, 0), LocalDateTime.ofInstant(Instant.ofEpochMilli(next), zone))
        assertEquals(25 * 3_600_000L - 30 * 60_000L, next - start)
    }

    // ---- W6

    @Test
    fun shortcutsNameTheProjectTaskAndEraWhenThereAreThem() {
        val card = lineageFor(LineagesResources.loadEraScenes(), epochDay = 3)
        val read = present(WidgetFixtures.openProject())
        assertEquals(ShortcutsView(true, "Hyle Deco", 4, "Control characters", card?.title), shortcutsView(read, card, ProjectNames.SHOWN))
        assertEquals(ShortcutsView(true, null, 4, "Control characters", card?.title), shortcutsView(read, card, ProjectNames.WITHHELD))
        assertEquals(ShortcutsView(false, null, null, null, null), shortcutsView(SnapshotRead.Absent, null))
        assertEquals(ShortcutsView(false, null, null, null, null), shortcutsView(SnapshotRead.Unreadable, null))
        val notRun = present(WidgetFixtures.openProject(workbook = WorkbookOutcome.NotRun))
        assertEquals(ShortcutsView(true, null, null, null, null), shortcutsView(notRun, null, ProjectNames.WITHHELD))
    }

    // ---- publishing and timed redraws

    @Test
    fun aPublishWritesOnlyForPlacedSnapshotWidgetsAndRunsTheGatesOnlyForW4OrW6() {
        assertEquals(PublishPlan(write = false, runWorkbook = false), publishPlan(emptySet()))
        assertEquals(PublishPlan(write = false, runWorkbook = false), publishPlan(setOf(WidgetKind.RECENTS, WidgetKind.LINEAGES)))
        assertEquals(PublishPlan(write = true, runWorkbook = false), publishPlan(setOf(WidgetKind.APPROVED, WidgetKind.SAVED)))
        assertEquals(PublishPlan(write = true, runWorkbook = true), publishPlan(setOf(WidgetKind.WORKBOOK)))
        assertEquals(PublishPlan(write = true, runWorkbook = true), publishPlan(setOf(WidgetKind.SHORTCUTS)))
    }

    @Test
    fun theSaveCheckRepeatsOnlyWhileThisProcessIsStillSaving() {
        val saving = present(WidgetFixtures.openProject(save = SaveState.SAVING, savedAtMillis = null))
        assertTrue(saveCheckDue(saving, "writer-a"))
        assertFalse(saveCheckDue(saving, "a-later-process"))
        assertFalse(saveCheckDue(present(WidgetFixtures.openProject()), "writer-a"))
        assertFalse(saveCheckDue(present(null), "writer-a"))
        assertFalse(saveCheckDue(SnapshotRead.Absent, "writer-a"))
        assertFalse(saveCheckDue(SnapshotRead.Unreadable, "writer-a"))
    }

    @Test
    fun theTimedRedrawsCoverEveryWidgetThatGoesOutOfDate() {
        // W6's large layout names today's era, so it turns at midnight with W5.
        assertEquals(setOf(WidgetKind.LINEAGES, WidgetKind.SHORTCUTS), MIDNIGHT_KINDS)
        assertEquals(setOf(WidgetKind.SAVED, WidgetKind.RECENTS), SAVE_CHECK_KINDS)
        assertFalse(WidgetKind.RECENTS in SNAPSHOT_KINDS)
    }
}
