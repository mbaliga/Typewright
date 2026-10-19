// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.project.Ethos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WrapUpTest {
    private val lowWaistedDeco = Ethos(genre = "display-artdeco", levels = mapOf("waist" to "low"))

    @Test
    fun theWrapUpNamesWhatToSettleAndWhatToDrawFirst() {
        val model = resolveBrief(lowWaistedDeco, FIXTURE_ATLAS)
        val wrap = wrapUp(Door.STYLE, model)
        assertEquals(model.sentence, wrap.sentence)
        assertTrue(wrap.toSettle.any { it.id == "departs.waist" }, "${wrap.toSettle.map { it.id }}")
        assertEquals(listOf('n', 'o', 'H', 'O'), assertNotNull(wrap.drawFirst).glyphs)
        assertTrue(wrap.targets > 0)
    }

    @Test
    fun aKeptSignatureIsSettled() {
        val kept = lowWaistedDeco.withSignatureToggled(Dimension.WAIST)
        val wrap = wrapUp(Door.STYLE, resolveBrief(kept, FIXTURE_ATLAS))
        assertTrue(wrap.toSettle.none { it.signature == Dimension.WAIST })
    }

    @Test
    fun theControlSetIsAlwaysDrawnFirstWhateverTheDoorOrUse() {
        val model = resolveBrief(Ethos(uses = listOf("watch-face")), FIXTURE_ATLAS)
        for (door in Door.entries) {
            assertEquals(listOf('n', 'o', 'H', 'O'), assertNotNull(wrapUp(door, model).drawFirst).glyphs, door.name)
        }
        val deco = wrapUp(Door.STYLE, resolveBrief(lowWaistedDeco, FIXTURE_ATLAS))
        assertEquals("art deco", deco.thenGenre?.genreName)
        assertEquals("A E M R S 0 8", deco.thenGenre?.letters)
    }

    @Test
    fun drawnLettersMoveTheFirstStepOn() {
        val model = resolveBrief(Ethos(uses = listOf("watch-face")), FIXTURE_ATLAS)
        val control = FontView(1000, "noHO".associate { it to ringGlyph("g$it", 600, 500, 80) })
        val drawn = wrapUp(Door.USE, model, Drawing(control, Drawing.EMPTY.measurement))
        assertEquals(listOf('0', '1'), assertNotNull(drawn.drawFirst).glyphs)
        assertEquals(null, drawn.thenGenre, "the genre's letters are named only while the control set is still to draw")
    }

    @Test
    fun theWrapUpCountsTheQuestionsLeftToRefine() {
        val model = resolveBrief(Ethos(), FIXTURE_ATLAS)
        val left = refineSteps(Door.STYLE, model).size
        assertEquals(left, wrapUp(Door.STYLE, model).moreQuestions)
        val oneMore = Ethos().withLevel(refineSteps(Door.STYLE, model).first().dimension, null)
        assertEquals(left - 1, wrapUp(Door.STYLE, resolveBrief(oneMore, FIXTURE_ATLAS)).moreQuestions)
    }
}
