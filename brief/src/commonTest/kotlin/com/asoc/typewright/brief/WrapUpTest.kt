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
    fun drawnLettersMoveTheFirstStepOn() {
        val model = resolveBrief(Ethos(uses = listOf("watch-face")), FIXTURE_ATLAS)
        assertEquals(listOf('0', '1'), wrapUp(Door.USE, model).drawFirst?.glyphs)
        val font = FontView(1000, mapOf('0' to ringGlyph("zero", 600, 500, 80), '1' to barGlyph("one", 400, 80, 500)))
        val drawn = wrapUp(Door.USE, model, Drawing(font, Drawing.EMPTY.measurement))
        assertTrue('0' !in assertNotNull(drawn.drawFirst).glyphs)
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
