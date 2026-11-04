// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android.widget

import com.asoc.typewright.campaign.DEFAULT_TASK4_STYLE_KEY
import com.asoc.typewright.campaign.WorkbookLatinContent
import com.asoc.typewright.project.StyleClass
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** W4's summary: the Workbook's real twelve tasks and gates, run on a project, in order. */
class WorkbookSummariesTest {
    private val corpus by lazy { NodeEconomyCorpus.load() }
    private val summaries = WorkbookSummaries({ WorkbookLatinContent.load() }, { corpus })

    @Test
    fun aNewProjectIsOnTaskOneWithNothingDone() {
        val summary = summaries.summarise(WidgetFixtures.font(listOf("n", "o")), WidgetFixtures.manifest())
        assertEquals(12, summary.total)
        assertEquals(0, summary.done)
        assertEquals(1, summary.currentIndex)
        assertEquals(TaskState.CURRENT, summary.states.first())
        assertTrue(summary.states.drop(1).all { it == TaskState.TODO })
        assertEquals(WorkbookLatinContent.load().first { it.index == 1 }.title, summary.currentTitle)
    }

    @Test
    fun confirmedJudgmentTasksAreDoneInOrder() {
        val summary = summaries.summarise(WidgetFixtures.font(listOf("n", "o")), WidgetFixtures.manifest(confirmedTasks = setOf(1, 2)))
        assertEquals(listOf(TaskState.DONE, TaskState.DONE), summary.states.take(2))
        assertTrue(summary.currentIndex >= 3, "current ${summary.currentIndex}")
        assertEquals(summary.currentIndex - 1, summary.done)
    }

    @Test
    fun aConfirmationLaterInTheBookDoesNotSkipAhead() {
        val summary = summaries.summarise(WidgetFixtures.font(listOf("n")), WidgetFixtures.manifest(confirmedTasks = setOf(2)))
        assertEquals(1, summary.currentIndex)
        assertEquals(0, summary.done)
    }

    @Test
    fun theCurrentTasksChecksAreCounted() {
        val summary = summaries.summarise(WidgetFixtures.font(listOf("n")), WidgetFixtures.manifest())
        assertTrue(summary.currentChecks.values.sum() > 0)
        assertTrue(summary.currentGateSummary.isNotBlank())
    }

    @Test
    fun theStyleClassIsTheProjectsOwnWhenTheCorpusKnowsIt() {
        val keys = setOf("sans-geometric", "serif-old-style")
        assertEquals("serif-old-style", WorkbookSummaries.styleKeyFor(StyleClass("sans-geometric", "serif-old-style"), keys))
        assertEquals("sans-geometric", WorkbookSummaries.styleKeyFor(StyleClass("sans-geometric", "not-a-class"), keys))
        assertEquals(DEFAULT_TASK4_STYLE_KEY, WorkbookSummaries.styleKeyFor(StyleClass("nope", null), keys))
        assertEquals(DEFAULT_TASK4_STYLE_KEY, WorkbookSummaries.styleKeyFor(StyleClass(null, null), keys))
    }

    @Test
    fun aProjectWithNoMasterCannotBeSummarised() {
        val empty = WidgetFixtures.font(emptyList()).copy(masters = emptyList())
        assertFailsWith<NoSuchElementException> { summaries.summarise(empty, WidgetFixtures.manifest()) }
    }
}
