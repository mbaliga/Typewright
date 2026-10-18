// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.brief

import com.asoc.typewright.brief.Dimension
import com.asoc.typewright.brief.Door
import com.asoc.typewright.brief.Drawing
import com.asoc.typewright.brief.Level
import com.asoc.typewright.brief.Step
import com.asoc.typewright.project.Ethos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BriefScreenStateTest {
    @Test
    fun startsOnTheAskTabWithNoDoorChosen() {
        val state = BriefScreenUiState()
        assertEquals(BriefScreenTab.ASK, state.tab)
        assertNull(state.door)
        assertNull(state.askPosition())
        assertEquals(listOf("Ask", "Brief", "Guide"), BriefScreenTab.ORDERED.map { it.label })
    }

    @Test
    fun answersChangeTheSentence() {
        val state = BriefScreenUiState()
        assertEquals("Nothing chosen yet.", state.model.sentence)

        state.answerLevel(Dimension.CONTRAST, Level.CONTRAST_EVEN)
        assertTrue(state.model.sentence.contains("monoline"), state.model.sentence)

        state.chooseGenre("sans-geometric")
        assertTrue(state.model.sentence.contains("geometric sans"), state.model.sentence)
        assertTrue(state.model.sentence.contains("monoline"), state.model.sentence)
    }

    @Test
    fun clearingAnAnswerRemovesItFromTheSentenceButKeepsTheQuestionAnswered() {
        val state = BriefScreenUiState()
        state.answerLevel(Dimension.CONTRAST, Level.CONTRAST_EVEN)
        state.answerLevel(Dimension.CONTRAST, null)
        assertFalse(state.model.sentence.contains("monoline"))
        assertTrue("dim.contrast" in state.ethos.answered)
    }

    @Test
    fun skippingAStepAnswersItWithoutChoosingAnything() {
        val state = BriefScreenUiState(initialDoor = Door.STYLE)
        val first = assertNotNull(state.askPosition()?.step)
        assertTrue(first is Step.PickGenre, "the style door asks for a genre first")

        state.skip(first)

        assertNull(state.model.genre)
        assertTrue(first.id in state.ethos.answered)
        assertEquals(Step.PickUses, state.askPosition()?.step)
        assertEquals(1, state.askPosition()?.answered)
    }

    @Test
    fun goingBackAsksTheLastQuestionAgain() {
        val state = BriefScreenUiState(initialDoor = Door.STYLE)
        val first = assertNotNull(state.askPosition()?.step)
        state.skip(first)
        state.goBack()
        assertEquals(first, state.askPosition()?.step)
        assertEquals(0, state.askPosition()?.answered)
    }

    @Test
    fun aFourthFeelingIsRefusedAndLeavesTheAnswersAlone() {
        val state = BriefScreenUiState()
        listOf("calm", "sincere", "business", "loud").forEach { state.toggleFeeling(it) }
        assertEquals(listOf("calm", "sincere", "business"), state.ethos.feelings)
    }

    @Test
    fun withNoProjectTheAnswersStayInMemoryAndSayWhy() {
        val state = BriefScreenUiState()
        assertFalse(state.saved)
        assertEquals(BRIEF_NOT_SAVED_NOTE, state.saveNote)
        assertEquals("Not saved: no project is open", BRIEF_NOT_SAVED_NOTE)

        state.chooseGenre("sans-geometric")
        assertEquals("sans-geometric", state.ethos.genre)
        assertEquals(BRIEF_NOT_SAVED_NOTE, state.saveNote)
    }

    @Test
    fun withAProjectEveryChangeIsHandedToItAndSaysSo() {
        val saved = mutableListOf<Ethos>()
        val state =
            BriefScreenUiState(
                persist = {
                    saved += it
                    true
                },
            )
        assertTrue(state.saved)

        state.toggleUse("watch-face")
        assertEquals(listOf("watch-face"), saved.single().uses)
        assertEquals(BRIEF_SAVED_NOTE, state.saveNote)

        // A change that changes nothing is not saved again.
        state.toggleFeeling("calm")
        state.toggleFeeling("sincere")
        state.toggleFeeling("business")
        val count = saved.size
        state.toggleFeeling("loud")
        assertEquals(count, saved.size)
    }

    @Test
    fun aProjectThatRefusesTheAnswersIsSaidToBeReadOnly() {
        val state = BriefScreenUiState(persist = { false })
        state.chooseGenre("sans-geometric")
        assertEquals(BRIEF_READ_ONLY_NOTE, state.saveNote)
        assertEquals("sans-geometric", state.ethos.genre, "the answer still counts this session")
    }

    @Test
    fun withoutTheAtlasThereAreNoMeasuredRangesButTheAnswersStillMakeTargets() {
        val state = BriefScreenUiState(atlas = null)
        state.chooseGenre("sans-geometric")
        assertEquals("Geometric sans", state.model.genre?.name)
        assertTrue(
            state.model.targets.values
                .none { it.range != null },
        )
    }

    @Test
    fun noProjectFontIsAnEmptyDrawing() {
        assertEquals(Drawing.EMPTY, briefDrawingOf(null))
        assertEquals(Drawing.EMPTY, BriefScreenUiState().drawing)
    }

    private fun BriefScreenUiState.skipToTheEnd() {
        repeat(60) {
            val step = askPosition()?.step ?: return
            skip(step)
        }
        error("the questions never ended")
    }

    @Test
    fun aDoorEndsInAWrapUpThatLeadsOn() {
        val state = BriefScreenUiState(initialDoor = Door.USE)
        state.toggleUse("watch-face")
        state.skipToTheEnd()
        val wrap = assertNotNull(state.wrapUp())
        assertEquals(listOf('0', '1'), assertNotNull(wrap.drawFirst).glyphs)
        assertTrue(wrap.moreQuestions > 0)

        state.askMore()
        assertIs<Step.AskDimension>(state.askPosition()?.step)
        state.skipToTheEnd()
        assertEquals(0, state.wrapUp()?.moreQuestions)
    }

    @Test
    fun aTensionIsSettledByAskingItsQuestionAgain() {
        val state = BriefScreenUiState(initialTab = BriefScreenTab.BRIEF)
        state.askAbout(Dimension.ROUNDNESS)
        assertEquals(BriefScreenTab.ASK, state.tab)
        val position = assertNotNull(state.askPosition())
        assertTrue(position.focused)
        assertEquals(Dimension.ROUNDNESS, assertIs<Step.AskDimension>(position.step).dimension)

        state.answerLevel(Dimension.ROUNDNESS, Level.ROUND_SQUARE)
        assertNull(state.focus)
        assertNull(state.askPosition(), "with no door chosen, the doors show again")
    }

    @Test
    fun backAtTheFirstQuestionReturnsToTheDoors() {
        val state = BriefScreenUiState(initialDoor = Door.FEELING)
        state.goBack()
        assertNull(state.door)
    }

    @Test
    fun startingOverClearsEveryAnswer() {
        val state = BriefScreenUiState(initialDoor = Door.STYLE)
        state.chooseGenre("sans-geometric")
        state.startOver()
        assertTrue(state.ethos.isEmpty)
        assertNull(state.door)
        assertEquals(BriefScreenTab.ASK, state.tab)
    }

    @Test
    fun aBriefWithAnswersOpensOnTheGuide() {
        assertEquals(BriefScreenTab.GUIDE, BriefScreenUiState(initialEthos = Ethos(genre = "sans-geometric")).tab)
    }

    @Test
    fun withNoProjectConfirmingAStyleClassIsANoOp() {
        val state = BriefScreenUiState()
        state.confirmStyleClass("slab")
        assertNull(state.confirmedStyleClass)
    }

    @Test
    fun withAProjectConfirmingAStyleClassSavesItAndUnconfirmingClearsIt() {
        val saved = mutableListOf<String?>()
        val state =
            BriefScreenUiState(persistConfirmedStyleClass = {
                saved += it
                true
            })
        assertNull(state.confirmedStyleClass)

        state.confirmStyleClass("slab")
        assertEquals("slab", state.confirmedStyleClass)
        assertEquals(listOf<String?>("slab"), saved)

        state.confirmStyleClass(null)
        assertNull(state.confirmedStyleClass)
        assertEquals(listOf("slab", null), saved)
    }

    @Test
    fun aProjectThatRefusesAConfirmationLeavesItUnconfirmed() {
        val state = BriefScreenUiState(persistConfirmedStyleClass = { false })
        state.confirmStyleClass("slab")
        assertNull(state.confirmedStyleClass)
    }

    @Test
    fun aBriefOpensWithItsProjectsOwnConfirmedStyleClass() {
        assertEquals("slab", BriefScreenUiState(initialConfirmedStyleClass = "slab").confirmedStyleClass)
    }

    @Test
    fun theKeeperHandsBackTheSameStateWhileTheProjectIsTheSame() {
        val keeper = BriefStateKeeper()
        val first = keeper.stateFor(null) { BriefScreenUiState() }
        first.chooseGenre("sans-geometric")
        assertSame(first, keeper.stateFor(null) { BriefScreenUiState() })
    }
}
