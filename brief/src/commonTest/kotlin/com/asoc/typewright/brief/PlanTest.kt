// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.project.Ethos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanTest {
    private fun plan(ethos: Ethos) = planFor(resolveBrief(ethos, null))

    @Test
    fun theControlCharactersComeFirst() {
        val steps = plan(Ethos())
        assertEquals("control", steps.first().id)
        assertEquals("noHO".toList(), steps.first().glyphs)
    }

    @Test
    fun everyGlyphIsPlannedOnce() {
        for (ethos in listOf(
            Ethos(),
            Ethos(
                genre = "display-artdeco",
                uses = listOf("watch-face"),
                useOptions =
                    mapOf("watch-face.date" to "date"),
            ),
        )) {
            val glyphs = plan(ethos).flatMap { it.glyphs }
            assertEquals(glyphs.distinct(), glyphs)
        }
        val all = plan(Ethos()).flatMap { it.glyphs }.toSet()
        assertTrue(('a'..'z').all { it in all } && ('A'..'Z').all { it in all } && ('0'..'9').all { it in all })
    }

    @Test
    fun theGenresVoiceFollowsTheControlCharacters() {
        val steps = plan(Ethos(genre = "display-artdeco"))
        assertEquals(listOf("control", "genre.voice"), steps.take(2).map { it.id })
        assertEquals("AEMRS08".toList(), steps[1].glyphs)
    }

    @Test
    fun aWatchFaceStartsFromItsFigures() {
        val steps = plan(Ethos(uses = listOf("watch-face")))
        assertEquals(listOf("watch.bones", "watch.figures"), steps.take(2).map { it.id })
        assertEquals(listOf('0', '1'), steps[0].glyphs)
        val dated = plan(Ethos(uses = listOf("watch-face"), useOptions = mapOf("watch-face.date" to "date")))
        assertEquals(listOf("watch.bones", "watch.figures", "watch.capitals", "watch.date"), dated.take(4).map { it.id })
        assertTrue(DATE_CAPITALS.all { c -> dated.take(4).any { c in it.glyphs } })
    }

    @Test
    fun theDayAndMonthNamesNeedTwentyTwoCapitals() {
        assertEquals("ABCDEFGHIJLMNOPRSTUVWY".toList(), DATE_CAPITALS)
    }

    @Test
    fun aWordmarkIsOnlyItsOwnLetters() {
        val steps = plan(Ethos(uses = listOf("wordmark"), wordmark = "Cells"))
        assertEquals(listOf("wordmark.letters", "wordmark.space"), steps.map { it.id })
        assertEquals("Cels".toList(), steps.first().glyphs)
    }

    @Test
    fun signsAndHeadlinesDrawCapitalsBeforeLowercase() {
        val ids = plan(Ethos(uses = listOf("signage"))).map { it.id }
        assertTrue(ids.indexOf("upper.straight") < ids.indexOf("lower.straight"), "$ids")
        val text = plan(Ethos(uses = listOf("body-text"))).map { it.id }
        assertTrue(text.indexOf("lower.straight") < text.indexOf("upper.straight"), "$text")
        assertEquals("latin.core", text.last())
    }

    @Test
    fun codeSettlesTheLookAlikesEarly() {
        val ids = plan(Ethos(uses = listOf("code"))).map { it.id }
        assertEquals("code.lookalikes", ids[1])
    }

    @Test
    fun aStepKnowsWhatIsStillUndrawn() {
        val step = plan(Ethos()).first()
        val font = FontView(1000, mapOf('n' to barGlyph("n", 600, 80, 500), 'o' to Glyph("o", 600, emptyList())))
        assertEquals(listOf('o', 'H', 'O'), step.remaining(font))
    }
}
