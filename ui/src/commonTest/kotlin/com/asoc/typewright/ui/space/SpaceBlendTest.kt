// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.core.geometry.Glyph
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SpaceBlend]: the Before and the string as it is now put side by side letter by letter, by where each stands in the string
 * and its glyph. The Before is the string as it was, so a letter that was not drawn then is not in it, and the page says so.
 */
class SpaceBlendTest {
    private fun glyphs(
        drawn: Set<Char>,
        advance: Int = 500,
    ): List<Glyph> = "nHoOx".map { SpaceFixtures.glyph(it.toString(), it, advance, drawn = it in drawn) }

    private fun fontOf(
        drawn: String,
        kerning: UfoKerning = UfoKerning(),
        advance: Int = 500,
    ) = SpaceFixtures.font(glyphs(drawn.toSet(), advance), kerning)

    @Test
    fun lettersDrawnAtBothTimesArePairedByPlaceAndTheOthersAreNamed() {
        val before = SpaceString.set(fontOf("n"), "nHnO")
        val after = SpaceString.set(fontOf("nHO"), "nHnO")
        val blend = SpaceBlend.of(after, before)

        assertEquals(listOf(0, 1, 2, 3), blend.slots.map { it.textIndex })
        assertEquals(listOf(true, false, true, false), blend.slots.map { it.inBoth })
        assertEquals(listOf("H", "O"), blend.notCompared.map { it.char.toString() })
        assertTrue(blend.notCompared.all { it.why == NotComparedWhy.DRAWN_ONLY_NOW })
        assertEquals("H and O were not drawn when Before was made, so Before leaves them out.", blend.sentence())
        assertTrue(!blend.allCompared)
    }

    @Test
    fun aLetterInBothSlidesFromWhereItStoodToWhereItStandsAndAnotherFadesIn() {
        val before = SpaceString.set(fontOf("n"), "nHn")
        val after = SpaceString.set(fontOf("nH"), "nHn")
        val blend = SpaceBlend.of(after, before)
        val second = blend.slots[2]
        assertEquals(500.0, second.x(0f), "at Before the second n is next to the first")
        assertEquals(1000.0, second.x(1f), "now the H stands between them")
        assertEquals(750.0, second.x(0.5f), 1e-9)
        val h = blend.slots[1]
        assertEquals(0f, h.alpha(0f), "the H did not exist at Before")
        assertEquals(1f, h.alpha(1f))
        assertEquals(0.25f, h.alpha(0.25f))
        assertEquals(1f, second.alpha(0f), "a letter in both is whole at every moment")
        assertEquals(before.cells[1], second.cell(0.4f), "the first half shows the Before's letter")
        assertEquals(after.cells[2], second.cell(0.5f), "the second half shows the letter as it is now")
    }

    @Test
    fun theBeforePictureIsNeverTheAfterPictureWhenTheDrawnLettersDiffer() {
        val before = SpaceString.set(fontOf("n"), "nHnO")
        val after = SpaceString.set(fontOf("nHO"), "nHnO")
        val blend = SpaceBlend.of(after, before)
        val shownAtBefore = blend.slots.filter { it.alpha(0f) > 0f }.map { it.cell(0f).char }
        val shownNow = blend.slots.filter { it.alpha(1f) > 0f }.map { it.cell(1f).char }
        assertEquals(listOf('n', 'n'), shownAtBefore, "Before holds the letters that were drawn then, and only those")
        assertEquals(listOf('n', 'H', 'n', 'O'), shownNow)
        assertTrue(shownAtBefore != shownNow)
    }

    @Test
    fun indicesMapBetweenTheTwoPicturesWhereTheLetterIsInBoth() {
        val before = SpaceString.set(fontOf("n"), "nHnO")
        val after = SpaceString.set(fontOf("nHO"), "nHnO")
        val blend = SpaceBlend.of(after, before)
        assertEquals(0, blend.afterIndexOfBefore(0))
        assertEquals(2, blend.afterIndexOfBefore(1), "the Before's second letter is the current string's third: the H came between")
        assertEquals(1, blend.beforeIndexOfAfter(2))
        assertNull(blend.beforeIndexOfAfter(1), "the H has no Before")
        assertNull(blend.beforeIndexOfAfter(3), "nor has the O")
        assertNull(blend.afterIndexOfBefore(5), "and a letter that is not in the Before maps to nothing")
    }

    @Test
    fun aLetterThatIsNoLongerDrawnIsInTheBeforeAlone() {
        val before = SpaceString.set(fontOf("nH"), "nHn")
        val after = SpaceString.set(fontOf("n"), "nHn")
        val blend = SpaceBlend.of(after, before)
        val gone = blend.slots[1]
        assertNull(gone.after)
        assertNotNull(gone.before)
        assertEquals(1f, gone.alpha(0f))
        assertEquals(0f, gone.alpha(1f))
        assertEquals("H is not drawn now, so After leaves it out.", blend.sentence())
    }

    @Test
    fun theSameCharacterGivenAnotherGlyphIsTwoPlacesAndNamed() {
        val was = SpaceFixtures.font(listOf(SpaceFixtures.glyph("n", 'n', 500)))
        val now = SpaceFixtures.font(listOf(SpaceFixtures.glyph("n.alt", 'n', 520)))
        val blend = SpaceBlend.of(SpaceString.set(now, "n"), SpaceString.set(was, "n"))
        assertEquals(2, blend.slots.size)
        assertEquals(listOf(NotCompared('n', NotComparedWhy.DIFFERENT_GLYPH)), blend.notCompared)
        assertEquals("n is a different glyph now than at Before.", blend.sentence())
    }

    @Test
    fun withNoBeforeEveryPlaceHoldsTheCurrentLetterAlone() {
        val after = SpaceString.set(fontOf("nH"), "nHn")
        val blend = SpaceBlend.of(after, null)
        assertEquals(3, blend.slots.size)
        assertTrue(blend.slots.all { it.before == null && it.alpha(1f) == 1f })
        assertNull(blend.sentence())
        assertTrue(blend.allCompared)
    }

    @Test
    fun theSameLettersEverywhereAreAllComparedAndNothingIsSaid() {
        val font = fontOf("nHoO")
        val blend = SpaceBlend.of(SpaceString.set(font, "nHoO"), SpaceString.set(font, "nHoO"))
        assertTrue(blend.allCompared)
        assertNull(blend.sentence())
        assertEquals(listOf(0, 1, 2, 3), blend.slots.map { blend.afterIndexOfBefore(it.before!!.index) })
    }

    @Test
    fun twoKindsOfAbsenceAreEachNamedOnceWithTheRightVerb() {
        val before = SpaceString.set(fontOf("nHx"), "nHOx")
        val after = SpaceString.set(fontOf("nOx"), "nHOx")
        // H is drawn only at Before, O only now.
        assertEquals(
            "O was not drawn when Before was made, so Before leaves it out. H is not drawn now, so After leaves it out.",
            SpaceBlend.of(after, before).sentence(),
        )
    }

    @Test
    fun overManyRandomStringsEveryLetterIsInOnePlaceAndThePlacesFollowTheString() {
        val letters = "nHoOx"
        repeat(300) { seed ->
            val random = Random(seed)
            val text =
                (0 until random.nextInt(1, 12)).joinToString("") {
                    (
                        if (random.nextInt(6) ==
                            0
                        ) {
                            ' '
                        } else {
                            letters[random.nextInt(letters.length)]
                        }
                    ).toString()
                }
            val drawnThen = letters.filter { random.nextBoolean() }
            val drawnNow = letters.filter { random.nextBoolean() }
            val before = SpaceString.set(fontOf(drawnThen, advance = 400), text)
            val after = SpaceString.set(fontOf(drawnNow, advance = 600), text)
            val blend = SpaceBlend.of(after, before)

            assertEquals(
                after.cells.map { it.index },
                blend.slots.mapNotNull { it.after?.index },
                "seed $seed: every current letter once, in order",
            )
            assertEquals(
                before.cells.map { it.index },
                blend.slots.mapNotNull { it.before?.index },
                "seed $seed: every Before letter once, in order",
            )
            assertEquals(
                blend.slots.map { it.textIndex }.sorted(),
                blend.slots.map { it.textIndex },
                "seed $seed: places follow the string",
            )
            for (slot in blend.slots.filter { it.inBoth }) {
                assertEquals(slot.before!!.glyph.name, slot.after!!.glyph.name, "seed $seed: a place holds one glyph")
                assertEquals(slot.before!!.textIndex, slot.after!!.textIndex)
            }
            val expectedNotCompared =
                (after.cells.map { it.textIndex } + before.cells.map { it.textIndex }).groupBy { it }.count {
                    it.value.size ==
                        1
                }
            assertEquals(expectedNotCompared, blend.notCompared.size, "seed $seed: each letter in only one picture is named")
            assertEquals(blend.notCompared.isEmpty(), blend.sentence() == null, "seed $seed")
        }
    }
}
