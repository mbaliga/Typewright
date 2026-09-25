// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.devanagari

import com.asoc.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevanagariControlCharactersTest {
    @Test
    fun everyEntryHasANonBlankGlyphNameAndRationale() {
        for (entry in DevanagariControlCharacters.build().controlCharacters) {
            assertTrue(entry.glyphName.isNotBlank())
            assertTrue(entry.rationale.isNotBlank())
        }
    }

    @Test
    fun stageOneSpellsOutPaavInOrder() {
        val entries = DevanagariControlCharacters.build().controlCharacters
        val stageOne = entries.filter { "Stage 1" in it.rationale }
        assertEquals(listOf("pa-deva", "aaMatra-deva", "va-deva"), stageOne.map { it.glyphName })
    }

    @Test
    fun stageTwoCoversAllFourSyllablesOfKimiinuphuu() {
        val entries = DevanagariControlCharacters.build().controlCharacters
        val stageTwo = entries.filter { "Stage 2" in it.rationale }.map { it.glyphName }
        assertEquals(
            listOf("ka-deva", "iMatra-deva", "ma-deva", "iiMatra-deva", "na-deva", "uMatra-deva", "pha-deva", "uuMatra-deva"),
            stageTwo,
        )
    }

    @Test
    fun stageThreeCoversAllSixBareConsonantsOfBharasagadaha() {
        val entries = DevanagariControlCharacters.build().controlCharacters
        val stageThree = entries.filter { "Stage 3" in it.rationale }.map { it.glyphName }
        assertEquals(listOf("bha-deva", "ra-deva", "sa-deva", "ga-deva", "da-deva", "ha-deva"), stageThree)
    }

    @Test
    fun stageFourCoversTheUpperAndLowerHeightExtremes() {
        val entries = DevanagariControlCharacters.build().controlCharacters
        val stageFour = entries.filter { "Stage 4" in it.rationale }.map { it.glyphName }
        assertEquals(listOf("reph-deva", "ma-deva", "oMatra-deva", "anusvara-deva", "dda_dda-deva", "uuMatra-deva"), stageFour)
    }

    @Test
    fun stageFourConjunctIsUnderscoreJoinedPerTheResearchsOwnConvention() {
        val entries = DevanagariControlCharacters.build().controlCharacters
        assertTrue(entries.any { it.glyphName == "dda_dda-deva" })
    }

    @Test
    fun everyEntryNamesWhichStageItBelongsTo() {
        for (entry in DevanagariControlCharacters.build().controlCharacters) {
            assertTrue(
                Regex("Stage [1-4]").containsMatchIn(entry.rationale),
                "${entry.glyphName}'s rationale should name a stage: ${entry.rationale}",
            )
        }
    }

    @Test
    fun scriptIsDevanagari() {
        assertEquals(WritingScript.DEVANAGARI, DevanagariControlCharacters.build().script)
    }

    @Test
    fun buildIsPureAndDeterministic() {
        assertEquals(DevanagariControlCharacters.build(), DevanagariControlCharacters.build())
    }
}
