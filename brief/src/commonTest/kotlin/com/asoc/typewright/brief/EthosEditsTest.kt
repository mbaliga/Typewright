// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.project.Ethos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EthosEditsTest {
    @Test
    fun choosingAGenreAnswersItsQuestionOnce() {
        val e = Ethos().withGenre("slab").withGenre("serif-didone")
        assertEquals("serif-didone", e.genre)
        assertEquals(listOf(STEP_GENRE), e.answered)
    }

    @Test
    fun removingAUseRemovesItsOwnAnswers() {
        val watch = useById("watch-face")!!
        val aod = watch.questions.first { it.id == "aod" }
        val e = Ethos().withUseToggled("watch-face").withUseOption(watch, aod, "yes")
        assertEquals(mapOf("watch-face.aod" to "yes"), e.useOptions)
        assertTrue("use.watch-face.aod" in e.answered)
        val removed = e.withUseToggled("watch-face")
        assertTrue(removed.uses.isEmpty())
        assertTrue(removed.useOptions.isEmpty())
        assertTrue(removed.answered.none { it.startsWith("use.watch-face.") })
    }

    @Test
    fun removingTheWordmarkUseForgetsTheName() {
        val e = Ethos().withUseToggled("wordmark").withWordmark("Cells").withUseToggled("wordmark")
        assertNull(e.wordmark)
    }

    @Test
    fun feelingsAndReferencesStopAtThree() {
        var e = Ethos()
        for (k in listOf("calm", "loud", "fancy", "cute")) e = e.withFeelingToggled(k)
        assertEquals(listOf("calm", "loud", "fancy"), e.feelings)
        e = e.withFeelingToggled("loud").withFeelingToggled("cute")
        assertEquals(listOf("calm", "fancy", "cute"), e.feelings)
        var r = Ethos()
        for (f in listOf("A", "B", "C", "D")) r = r.withReferenceToggled(f)
        assertEquals(MAX_REFERENCES, r.references.size)
    }

    @Test
    fun anAnswerIsSetClearedAndMustFitItsQuestion() {
        val e = Ethos().withLevel(Dimension.CONTRAST, Level.CONTRAST_EVEN)
        assertEquals(mapOf("contrast" to "even"), e.levels)
        assertTrue(dimensionStepId(Dimension.CONTRAST) in e.answered)
        assertTrue(e.withLevel(Dimension.CONTRAST, null).levels.isEmpty())
        assertFailsWith<IllegalArgumentException> { Ethos().withLevel(Dimension.WIDTH, Level.CONTRAST_EVEN) }
    }

    @Test
    fun measuredAnswersNeverOverwriteThePersonsOwn() {
        val e =
            Ethos()
                .withLevel(Dimension.CONTRAST, Level.CONTRAST_EVEN)
                .withMeasuredLevels(mapOf(Dimension.CONTRAST to Level.CONTRAST_STRONG, Dimension.WIDTH to Level.WIDTH_NARROW))
        assertEquals(mapOf("contrast" to "even", "width" to "narrow"), e.levels)
        assertTrue(STEP_MIRROR in e.answered)
    }

    @Test
    fun aSignatureToggles() {
        val e = Ethos().withSignatureToggled(Dimension.WAIST)
        assertEquals(listOf("waist"), e.signatures)
        assertTrue(e.withSignatureToggled(Dimension.WAIST).signatures.isEmpty())
    }

    @Test
    fun goingBackReopensThatStepAndEverythingAfterIt() {
        val e = Ethos().answering("a").answering("b").answering("c")
        assertEquals(listOf("a"), e.reopening("b").answered)
        assertEquals(e, e.reopening("missing"))
        assertEquals(listOf("a", "b", "c"), e.skipping("b").answered)
    }

    @Test
    fun aBlankWordmarkClearsIt() {
        assertNull(Ethos(wordmark = "x").withWordmark("  ").wordmark)
    }
}
