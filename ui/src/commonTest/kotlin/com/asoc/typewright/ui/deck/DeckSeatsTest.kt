// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.deck

import com.asoc.typewright.ui.brief.BriefScreenTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeckSeatsTest {
    private fun everyBriefLayout() = BriefDeckPage.entries.map { it to DeckSeats.brief(it) }

    private val s02 =
        NewProjectStage.entries.map {
            it to
                DeckSeats.newProject(
                    it,
                    built = setOf(NewProjectStage.SOURCE, NewProjectStage.BRIEF),
                    valid = setOf(NewProjectStage.SOURCE),
                )
        }

    @Test
    fun everyBriefStateAndEveryS02StepObeysTheSeatRules() {
        for ((page, layout) in everyBriefLayout()) assertEquals(emptyList(), DeckSeats.problems(layout), page.name)
        for ((stage, layout) in s02) assertEquals(emptyList(), DeckSeats.problems(layout), stage.name)
    }

    @Test
    fun theBriefAlwaysOffersAskBriefGuideEvenAtTheDoors() {
        for ((page, layout) in everyBriefLayout()) {
            assertEquals(listOf("Ask", "Brief", "Guide"), layout.seats.map { it.label }, page.name)
        }
        assertEquals(DeckSeats.SEAT_ASK, DeckSeats.brief(BriefDeckPage.DOORS).pressed)
        assertEquals(DeckSeats.SEAT_BRIEF, DeckSeats.brief(BriefDeckPage.BRIEF_TAB).pressed)
        assertEquals(DeckSeats.SEAT_GUIDE, DeckSeats.brief(BriefDeckPage.GUIDE_TAB).pressed)
    }

    @Test
    fun everyBriefSeatNamesAViewTheScreenHas() {
        val tabs = BriefScreenTab.entries.map { it.name.lowercase() }
        for ((_, layout) in everyBriefLayout()) for (seat in layout.seats) assertTrue(seat.id in tabs, seat.id)
    }

    @Test
    fun theLeftCircleIsCloseOnlyWhereLeavingLosesSomething() {
        for ((page, layout) in everyBriefLayout()) assertEquals(DeckLeft.BACK, layout.left, page.name)
        for ((stage, layout) in s02) assertEquals(DeckLeft.CLOSE, layout.left, stage.name)
    }

    @Test
    fun theRightCircleFollowsTheTable() {
        val expected =
            mapOf(
                BriefDeckPage.DOORS to null,
                BriefDeckPage.SINGLE_CHOICE to null,
                BriefDeckPage.MULTI_CHOICE to DeckAction.DONE,
                BriefDeckPage.WORDMARK to DeckAction.SET_WORDMARK,
                BriefDeckPage.MIRROR_READING to DeckAction.USE_ANSWERS,
                BriefDeckPage.MIRROR_EMPTY to DeckAction.START_DRAWING,
                BriefDeckPage.FOCUS to null,
                BriefDeckPage.WRAP_UP to DeckAction.DRAW_FIRST,
                BriefDeckPage.BRIEF_TAB to null,
                BriefDeckPage.GUIDE_TAB to DeckAction.START_DRAWING,
            )
        for ((page, layout) in everyBriefLayout()) assertEquals(expected[page], layout.action, page.name)
    }

    @Test
    fun theActionIsOnTheCircleAndNotOnThePage() {
        for ((page, layout) in everyBriefLayout()) {
            for (action in DeckAction.entries) {
                assertEquals(layout.action != action, layout.pageCarries(action), "${page.name} ${action.name}")
            }
        }
        assertEquals(DeckRight.CHECK, DeckSeats.brief(BriefDeckPage.MULTI_CHOICE).right)
        assertEquals(DeckRight.NEXT, DeckSeats.brief(BriefDeckPage.GUIDE_TAB).right)
        assertEquals(DeckRight.MENU, DeckSeats.brief(BriefDeckPage.DOORS).right)
    }

    @Test
    fun anActionThatCannotRunYetSaysWhy() {
        val layout = DeckSeats.brief(BriefDeckPage.WORDMARK, canRunAction = false, whyNot = "Type the name first")
        assertEquals("Type the name first", layout.actionWhyNot)
        assertNull(DeckSeats.brief(BriefDeckPage.WORDMARK).actionWhyNot)
        assertNull(DeckSeats.brief(BriefDeckPage.DOORS, canRunAction = false, whyNot = "x").actionWhyNot, "⋯ is never off")
    }

    @Test
    fun drawFirstBecomesDrawNextOnceLettersExist() {
        assertEquals("Draw first", DeckSeats.actionLabel(DeckAction.DRAW_FIRST, lettersExist = false))
        assertEquals("Draw next", DeckSeats.actionLabel(DeckAction.DRAW_FIRST, lettersExist = true))
    }

    @Test
    fun s02PutsItsStepsOnTheArcAndCreatesOnTheLastBuiltOne() {
        val brief = s02.first { it.first == NewProjectStage.BRIEF }.second
        assertEquals(listOf("Source", "Brief", "Metrics"), brief.seats.map { it.label })
        assertEquals("brief", brief.pressed)
        assertEquals(DeckAction.CREATE, brief.action)
        assertEquals(DeckRight.CHECK, brief.right)
        val metrics = brief.seats.last()
        assertFalse(metrics.enabled)
        assertEquals("Metrics isn't built yet", metrics.why)
        val source = s02.first { it.first == NewProjectStage.SOURCE }.second
        assertEquals(DeckAction.NEXT, source.action)
    }

    @Test
    fun aLaterStepCanBeChosenOnceTheEarlierOnesAreValid() {
        val all = NewProjectStage.entries.toSet()
        val notYet = DeckSeats.newProject(NewProjectStage.SOURCE, built = all, valid = emptySet())
        assertFalse(notYet.seats[2].enabled)
        assertEquals("Finish the earlier steps first", notYet.seats[2].why)
        val ready = DeckSeats.newProject(NewProjectStage.SOURCE, built = all, valid = setOf(NewProjectStage.SOURCE, NewProjectStage.BRIEF))
        assertTrue(ready.seats[2].enabled)
    }

    @Test
    fun aRememberedSeatThePageNoLongerHasIsDroppedAndNothingReadsPressedForAnUnnamedState() {
        val layout = DeckSeats.brief(BriefDeckPage.DOORS)
        assertEquals("guide", DeckSeats.sanitise("guide", layout))
        assertNull(DeckSeats.sanitise("overlay", layout))
        assertNull(DeckSeats.resolve("somewhere else", layout))
        assertEquals("brief", DeckSeats.resolve("brief", layout))
    }

    @Test
    fun theRulesCatchABadLayout() {
        val bad =
            DeckLayout(
                seats = listOf(DeckSeat("a", "A very long label")),
                pressed = "zzz",
                left = DeckLeft.BACK,
                action = null,
            )
        assertTrue(DeckSeats.problems(bad).size >= 3)
    }
}

class DeckKeysTest {
    @Test
    fun arrowsStepDigitsJumpAndMOpensTheMap() {
        assertEquals(DeckKeyCommand.StepSection(-1), DeckKeys.interpret("ArrowLeft", focusOwnsKeys = false))
        assertEquals(DeckKeyCommand.StepSection(1), DeckKeys.interpret("ArrowRight", focusOwnsKeys = false))
        assertEquals(DeckKeyCommand.GoToSection(DeckSection.HOME), DeckKeys.interpret("1", focusOwnsKeys = false))
        assertEquals(DeckKeyCommand.GoToSection(DeckSection.WORKBOOK), DeckKeys.interpret("9", focusOwnsKeys = false))
        assertEquals(DeckKeyCommand.OpenMap, DeckKeys.interpret("m", focusOwnsKeys = false))
    }

    @Test
    fun keysAreIgnoredWhileATextFieldOrListOwnsFocusOrAModifierIsHeld() {
        for (key in listOf("ArrowLeft", "ArrowRight", "1", "m")) {
            assertNull(DeckKeys.interpret(key, focusOwnsKeys = true), key)
            assertNull(DeckKeys.interpret(key, focusOwnsKeys = false, modified = true), key)
        }
        assertEquals(DeckKeyCommand.Leave, DeckKeys.interpret("Escape", focusOwnsKeys = true))
        assertNull(DeckKeys.interpret("0", focusOwnsKeys = false))
        assertNull(DeckKeys.interpret("x", focusOwnsKeys = false))
    }

    @Test
    fun aSwipeTurnsTheArcAndSettlesOnTheSectionAtTheApex() {
        val spacing = 0.2 * DeckGeometry.SECTION_LENGTH
        assertEquals(DeckSection.TRACE, DeckKeys.settle(DeckSection.CAPTURE, -spacing))
        assertEquals(DeckSection.CAPTURE, DeckKeys.settle(DeckSection.TRACE, spacing))
        assertEquals(DeckSection.HOME, DeckKeys.settle(DeckSection.HOME, spacing * 3), "clamped at the start")
        assertEquals(DeckSection.WORKBOOK, DeckKeys.settle(DeckSection.LEARN, -spacing * 5), "clamped at the end")
        assertEquals(DeckSection.DRAW, DeckKeys.settle(DeckSection.DRAW, -spacing * 0.3), "less than half a step stays put")
        assertTrue(DeckKeys.isTurn(20.0))
        assertFalse(DeckKeys.isTurn(2.0))
    }

    @Test
    fun stepStopsAtTheEnds() {
        assertNull(DeckKeys.step(DeckSection.HOME, -1))
        assertEquals(DeckSection.CAPTURE, DeckKeys.step(DeckSection.HOME, 1))
    }
}
