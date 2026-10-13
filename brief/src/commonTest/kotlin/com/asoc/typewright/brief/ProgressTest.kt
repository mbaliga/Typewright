// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.project.Ethos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressTest {
    private fun model(ethos: Ethos = Ethos()) = resolveBrief(ethos, FIXTURE_ATLAS)

    private fun allAnswered(
        door: Door,
        ethos: Ethos,
        refining: Boolean = false,
    ): Ethos {
        var e = ethos
        repeat(40) {
            val step = askPosition(door, model(e), refining = refining).step ?: return e
            e = e.skipping(step.id)
        }
        error("the questions never ended")
    }

    @Test
    fun askingMoreAddsOnlyTheDimensionsTheDoorLeftOut() {
        val ethos = Ethos(genre = "display-artdeco")
        val own = questionsFor(Door.STYLE, model(ethos)).map { it.id }.toSet()
        val more = refineSteps(Door.STYLE, model(ethos)).map { it.id }
        assertTrue(more.isNotEmpty())
        assertTrue(more.none { it in own }, "$more repeats $own")
        // In the order a beginner sees them, and never stress on a monoline stroke.
        val order = TEACHING_ORDER.map { dimensionStepId(it) }
        assertEquals(more.sortedBy { order.indexOf(it) }, more)
        assertTrue(dimensionStepId(Dimension.STRESS) !in more)
    }

    @Test
    fun aDoorEndsAndAskingMoreReopensTheQuestions() {
        val done = allAnswered(Door.STYLE, Ethos())
        val end = askPosition(Door.STYLE, model(done))
        assertTrue(end.done)
        assertEquals(end.total, end.answered)
        val more = askPosition(Door.STYLE, model(done), refining = true)
        assertIs<Step.AskDimension>(more.step)
        assertTrue(more.total > end.total)
        assertTrue(askPosition(Door.STYLE, model(allAnswered(Door.STYLE, done, refining = true)), refining = true).done)
    }

    @Test
    fun aFocusedDimensionIsAskedWhateverTheDoor() {
        val done = allAnswered(Door.USE, Ethos())
        val position = askPosition(Door.USE, model(done), focus = Dimension.ROUNDNESS)
        val step = assertIs<Step.AskDimension>(position.step)
        assertEquals(Dimension.ROUNDNESS, step.dimension)
        assertTrue(position.focused)
    }

    @Test
    fun backReopensTheLastAnsweredStep() {
        val ethos = Ethos().withGenre("display-artdeco").answering(STEP_USES)
        assertEquals(STEP_USES, previousStepId(Door.STYLE, model(ethos)))
        assertNull(previousStepId(Door.STYLE, model()))
    }

    @Test
    fun everyStepSaysWhatItAsksAndWhy() {
        val ethos = Ethos(genre = "display-artdeco", uses = listOf("watch-face", "wordmark"))
        val steps = Door.entries.flatMap { questionsFor(it, model(ethos)) }
        for (step in steps) {
            assertTrue(step.prompt.endsWith("?"), "${step.id}: ${step.prompt}")
            assertTrue(step.why.isNotBlank(), step.id)
        }
        assertTrue(steps.any { it.needsConfirm } && steps.any { it.offersNotSure })
    }
}
