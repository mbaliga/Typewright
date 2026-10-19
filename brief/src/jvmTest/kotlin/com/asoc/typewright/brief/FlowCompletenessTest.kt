// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.project.Ethos
import com.asoc.typewright.qa.corpus.loadStyleAtlas
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every door, walked to its end with the real atlas, as a person who answers everything and as
 * one who skips everything: the questions always end, the wrap-up always names a next step, and
 * asking for more always ends too. No door may stop in the middle.
 */
class FlowCompletenessTest {
    private val atlas = loadStyleAtlas()

    /** Hyle Deco, this repository's test font, as letters a person might have drawn. */
    private val hyleDeco: Drawing by lazy {
        // Gradle runs tests from the module's directory; a plain JVM run may start at the repository root.
        val file = listOf("../fonts/HyleDeco-Regular.ttf", "fonts/HyleDeco-Regular.ttf").map(::File).first { it.exists() }
        val font = readSfntFont(file.readBytes())
        val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        Drawing.of(FontView(font.head.unitsPerEm, chars.mapNotNull { c -> font.glyphForCodePoint(c.code)?.let { c to it } }.toMap()))
    }

    /** One answer per step, the way a person going along with the suggestions would give it. */
    private fun answer(
        step: Step,
        ethos: Ethos,
        door: Door,
    ): Ethos =
        when (step) {
            is Step.PickGenre -> {
                ethos.withGenre((step.suggestions.firstOrNull()?.genre ?: GENRES.first()).key)
            }

            Step.PickUses -> {
                (if (door == Door.USE) ethos.withUseToggled("watch-face") else ethos).answering(step.id)
            }

            is Step.AskUse -> {
                ethos.withUseOption(
                    step.use,
                    step.question,
                    step.question.options
                        .first()
                        .id,
                )
            }

            Step.EnterWordmark -> {
                ethos.withWordmark("Typewright")
            }

            is Step.PickFeelings -> {
                ethos.withFeelingToggled(FEELINGS.first().key).answering(step.id)
            }

            is Step.PickReferences -> {
                val pick = step.suggestions.firstOrNull()?.family ?: step.starters.first()
                ethos.withReferenceToggled(pick).answering(step.id)
            }

            is Step.ReadDrawing -> {
                if (step.levels.isEmpty()) ethos.skipping(step.id) else ethos.withMeasuredLevels(step.levels)
            }

            is Step.AskDimension -> {
                ethos.withLevel(step.dimension, step.suggested ?: step.options.first())
            }
        }

    private fun walk(
        door: Door,
        drawing: Drawing,
        skipAll: Boolean,
        start: Ethos = Ethos(),
        refining: Boolean = false,
    ): Ethos {
        var ethos = start
        repeat(MAX_STEPS) {
            val step = askPosition(door, resolveBrief(ethos, atlas), drawing, refining).step ?: return ethos
            val next = if (skipAll) ethos.skipping(step.id) else answer(step, ethos, door)
            assertTrue(next != ethos, "${door.id}: answering ${step.id} changed nothing")
            ethos = next
        }
        error("${door.id}: still asking after $MAX_STEPS steps")
    }

    @Test
    fun everyDoorEndsInAWrapUpWithANextStep() {
        for (door in Door.entries) {
            for (skipAll in listOf(false, true)) {
                val drawing = if (door == Door.MIRROR && !skipAll) hyleDeco else Drawing.EMPTY
                val ethos = walk(door, drawing, skipAll)
                val model = resolveBrief(ethos, atlas)
                val wrap = wrapUp(door, model, drawing)
                val label = "${door.id}${if (skipAll) ", skipping" else ""}"
                assertTrue(wrap.sentence.endsWith("."), "$label: ${wrap.sentence}")
                assertNotNull(wrap.drawFirst, "$label: no letters to draw first")
                assertTrue(guideFor(model, drawing).isNotEmpty(), "$label: empty guide")
                // Asking for more ends too, and leaves nothing more to ask.
                val refined = walk(door, drawing, skipAll, start = ethos, refining = true)
                assertEquals(0, wrapUp(door, resolveBrief(refined, atlas), drawing).moreQuestions, label)
            }
        }
    }

    @Test
    fun theReferencesDoorNeverOpensEmpty() {
        val first = askPosition(Door.REFERENCES, resolveBrief(Ethos(), atlas)).step
        val pick = assertNotNull(first as? Step.PickReferences)
        assertTrue(pick.suggestions.isEmpty())
        assertTrue(pick.starters.size >= 12, "${pick.starters}")
        assertTrue(pick.starters.all { cueFaceOf(it) != null && it in atlas.pack.faces }, "${pick.starters}")
    }

    @Test
    fun theMirrorDoorReadsDrawnLettersAndAdoptsThem() {
        val step = assertNotNull(askPosition(Door.MIRROR, resolveBrief(Ethos(), atlas), hyleDeco).step as? Step.ReadDrawing)
        assertTrue(step.levels.size >= 8, "${step.levels}")
        assertNotNull(step.reading)
        val adopted = Ethos().withMeasuredLevels(step.levels)
        assertEquals(step.levels.size, adopted.levels.size)
    }

    private companion object {
        const val MAX_STEPS = 60
    }
}
