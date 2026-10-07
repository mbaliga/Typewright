// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.project.Ethos
import kotlin.test.Test
import kotlin.test.assertEquals

class SentenceTest {
    private fun sentence(ethos: Ethos) = resolveBrief(ethos, null).sentence

    @Test
    fun nothingChosenSaysSo() {
        assertEquals("Nothing chosen yet.", sentence(Ethos()))
    }

    @Test
    fun theGenreTheUsesAndTheAnswersInQuestionOrder() {
        val ethos =
            Ethos(
                genre = "display-artdeco",
                uses = listOf("watch-face"),
                levels = mapOf("width" to "narrow", "contrast" to "even", "roundness" to "softSquare", "waist" to "high"),
            )
        assertEquals("An art deco typeface for watch faces: monoline, squarish rounds, narrow and high-waisted.", sentence(ethos))
    }

    @Test
    fun feelingsComeFirstAsAdjectives() {
        val ethos = Ethos(genre = "sans-geometric", uses = listOf("app-ui", "signage"), feelings = listOf("calm", "business"))
        assertEquals("A calm, businesslike geometric sans typeface for app interfaces and signs.", sentence(ethos))
    }

    @Test
    fun aSansSerifGenreDoesNotRepeatThatItIsSansSerif() {
        val ethos = Ethos(genre = "sans-grotesque", levels = mapOf("serifs" to "none", "aForm" to "double"))
        assertEquals("A grotesque typeface: a two-storey a.", sentence(ethos))
    }

    @Test
    fun aWordmarkIsNamed() {
        assertEquals("A typeface for the wordmark “Cells”.", sentence(Ethos(uses = listOf("wordmark"), wordmark = " Cells ")))
        assertEquals("A typeface for a wordmark.", sentence(Ethos(uses = listOf("wordmark"))))
    }

    @Test
    fun wordsJoinTheEnglishWay() {
        assertEquals("", joinWords(emptyList()))
        assertEquals("a", joinWords(listOf("a")))
        assertEquals("a and b", joinWords(listOf("a", "b")))
        assertEquals("a, b and c", joinWords(listOf("a", "b", "c")))
        assertEquals("An art deco face", withArticle("art deco face"))
        assertEquals("A geometric face", withArticle("geometric face"))
    }
}
