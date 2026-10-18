// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.project.Brief
import com.asoc.typewright.project.BriefSource
import com.asoc.typewright.project.ContrastModel
import com.asoc.typewright.project.Ethos
import com.asoc.typewright.project.StyleClass
import com.asoc.typewright.project.Use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BriefEditsTest {
    private val blank = Brief(source = BriefSource.BLANK)

    @Test
    fun aCorpusGenreDeclaresItself() {
        val next = blank.withEthos(Ethos(genre = "sans-geometric"))
        assertEquals("sans-geometric", next.styleClass.declared)
        assertEquals(ContrastModel.SANS, next.model)
    }

    @Test
    fun aNonCorpusGenreDeclaresNothing() {
        val next = blank.withEthos(Ethos(genre = "sans-rounded"))
        assertNull(next.styleClass.declared)
        assertEquals(ContrastModel.SANS, next.model, "still a SANS group genre, just not a corpus one")
    }

    @Test
    fun theSerifsAnswerOverridesTheGenresOwnGroup() {
        val serifGenreButNoSerifs = blank.withEthos(Ethos(genre = "serif-garalde"))
        assertEquals(ContrastModel.SERIF, serifGenreButNoSerifs.model)

        val serifGenreButSansAnswer = blank.withEthos(Ethos(genre = "serif-garalde", levels = mapOf("serifs" to "none")))
        assertEquals(ContrastModel.SANS, serifGenreButSansAnswer.model)

        for (hasSerifs in listOf("bracketed", "slab", "hairline")) {
            val next = blank.withEthos(Ethos(levels = mapOf("serifs" to hasSerifs)))
            assertEquals(ContrastModel.SERIF, next.model, hasSerifs)
        }
    }

    @Test
    fun noGenreAndNoSerifsAnswerLeavesModelNull() {
        assertNull(blank.withEthos(Ethos()).model)
        assertNull(blank.withEthos(Ethos(genre = "display-stencil")).model, "a display genre answers neither chip")
    }

    @Test
    fun theFirstUseDecidesTextOrDisplay() {
        assertEquals(Use.TEXT, blank.withEthos(Ethos(uses = listOf("body-text"))).use)
        for (display in listOf("headline", "signage", "wordmark", "watch-face")) {
            assertEquals(Use.DISPLAY, blank.withEthos(Ethos(uses = listOf(display))).use, display)
        }
        assertNull(blank.withEthos(Ethos(uses = listOf("code"))).use)
        assertNull(blank.withEthos(Ethos()).use)
        assertEquals(
            Use.DISPLAY,
            blank.withEthos(Ethos(uses = listOf("headline", "body-text"))).use,
            "the most important (first) use decides, not any use",
        )
    }

    @Test
    fun confirmedAndNoteAreNeverTouched() {
        val declared =
            blank.copy(styleClass = StyleClass(declared = "slab", confirmed = "slab"), note = "a note the person wrote")
        val next = declared.withEthos(Ethos(genre = "sans-geometric"))
        assertEquals("slab", next.styleClass.confirmed)
        assertEquals("a note the person wrote", next.note)
        assertEquals("sans-geometric", next.styleClass.declared, "declared still re-derives from the new ethos")
    }

    @Test
    fun theEthosItselfRoundTrips() {
        val ethos = Ethos(genre = "slab", uses = listOf("signage"))
        assertEquals(ethos, blank.withEthos(ethos).ethos)
    }
}
