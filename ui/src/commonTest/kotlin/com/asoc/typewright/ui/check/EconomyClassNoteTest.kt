// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The line under the Economy page's chips says the same thing as the rows below it: what the glyphs are judged against, or that they are not. */
class EconomyClassNoteTest {
    @Test
    fun aClassWithFacesIsNamedWithWhereItCameFrom() {
        val note = assertNotNull(economyClassNote("sans-geometric", "declared by you", "sans-geometric", "Geometric sans", hasBoxes = true))
        assertTrue(note.startsWith("Judged against geometric sans: declared by you."), note)
    }

    @Test
    fun aClassWithNoFacesInTheCorpusSaysSoInsteadOfClaimingItJudgesAgainstIt() {
        val note = assertNotNull(economyClassNote("script-formal", "declared by you", "script-formal", "Formal script", hasBoxes = false))
        assertTrue("has no measured faces in the corpus" in note, note)
        assertTrue("no box to judge your glyphs against" in note, note)
        assertTrue(note.endsWith("Choose a style above to compare."), note)
        // The page below this line says "Choose a style above"; the line above it must not say "Judged against" the same class.
        assertTrue("Judged against" !in note && "declared by you" !in note, note)
    }

    @Test
    fun aChosenClassAndNoClassAreSaidInTheirOwnWords() {
        assertEquals(
            "Comparing against geometric sans instead of your own class, art deco.",
            economyClassNote("display-artdeco", "declared by you", "sans-geometric", "Art deco", hasBoxes = true),
        )
        assertTrue("which you chose here" in assertNotNull(economyClassNote(null, null, "sans-geometric", null, hasBoxes = true)))
        assertTrue(
            "Choose one to compare against" in assertNotNull(economyClassNote(null, null, null, null, hasBoxes = false)),
            "no class at all asks for one",
        )
    }
}
