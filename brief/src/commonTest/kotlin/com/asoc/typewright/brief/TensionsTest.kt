// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.project.Ethos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TensionsTest {
    private fun tensions(
        ethos: Ethos,
        withAtlas: Boolean = true,
    ) = resolveBrief(ethos, if (withAtlas) FIXTURE_ATLAS else null).tensions

    @Test
    fun anAnswerFewOfTheGenresFacesGiveIsADeparture() {
        // One geometric face in six has a low bar.
        val t = tensions(Ethos(genre = "sans-geometric", levels = mapOf("waist" to "low"))).single { it.id == "departs.waist" }
        assertTrue("Only 1 of the 6" in t.body, t.body)
        assertEquals(SourceKind.MEASURED, t.source.kind)
        assertEquals(Dimension.WAIST, t.signature)
    }

    @Test
    fun noGenreFaceGivingTheAnswerIsSaidPlainly() {
        val t = tensions(Ethos(genre = "sans-geometric", levels = mapOf("serifs" to "bracketed"))).single { it.id == "departs.serifs" }
        assertTrue(t.body.startsWith("None of the 6"), t.body)
    }

    @Test
    fun anOrdinaryAnswerIsNoDeparture() {
        // Two geometric faces in six have a two-storey a: a third, above the quarter it takes.
        assertTrue(tensions(Ethos(genre = "sans-geometric", levels = mapOf("aForm" to "double"))).none { it.id.startsWith("departs.") })
    }

    @Test
    fun markingASignatureSettlesTheDeparture() {
        val ethos = Ethos(genre = "sans-geometric", levels = mapOf("waist" to "low"), signatures = listOf("waist"))
        assertTrue(tensions(ethos).none { it.id == "departs.waist" })
    }

    @Test
    fun aHeavyWatchFaceInAlwaysOnModeMeetsWearOsRule() {
        val ethos = Ethos(uses = listOf("watch-face"), useOptions = mapOf("watch-face.aod" to "yes"), levels = mapOf("weight" to "bold"))
        val t = tensions(ethos, withAtlas = false).single { it.id == "use.watch.ambient" }
        assertEquals(SourceKind.RULE, t.source.kind)
        assertTrue("WO-P7" in t.source.citation)
        // Not in always-on mode: no tension.
        assertTrue(
            tensions(ethos.copy(useOptions = mapOf("watch-face.aod" to "no")), withAtlas = false).none { it.id == "use.watch.ambient" },
        )
    }

    @Test
    fun accessibleSignsRuleOutScriptAndLightWeights() {
        val script = tensions(Ethos(genre = "script-formal", uses = listOf("signage")), withAtlas = false)
        assertTrue(script.any { it.id == "use.signage.forms" && "703.5.1" in it.source.citation })
        val light = tensions(Ethos(uses = listOf("signage"), levels = mapOf("weight" to "light")), withAtlas = false)
        assertTrue(light.any { it.id == "use.signage.stroke" && "703.5.7" in it.source.citation })
    }

    @Test
    fun codeNeedsAMonospace() {
        assertTrue(tensions(Ethos(genre = "sans-humanist", uses = listOf("code")), withAtlas = false).any { it.id == "use.code.pitch" })
        assertFalse(tensions(Ethos(genre = "monospace", uses = listOf("code")), withAtlas = false).any { it.id == "use.code.pitch" })
    }

    @Test
    fun hairlinesAtSmallSizes() {
        val t = tensions(Ethos(uses = listOf("app-ui"), levels = mapOf("contrast" to "extreme")), withAtlas = false)
        assertTrue(t.any { it.id == "use.hairlines" })
    }

    @Test
    fun answersThatContradictEachOther() {
        val t = tensions(Ethos(levels = mapOf("serifs" to "hairline", "contrast" to "even")), withAtlas = false)
        assertTrue(t.any { it.id == "answers.hairline.even" })
    }

    @Test
    fun withoutTheAtlasAConventionStandsInForTheMeasuredDeparture() {
        val ethos = Ethos(genre = "sans-grotesque", levels = mapOf("waist" to "high"))
        assertTrue(tensions(ethos, withAtlas = false).any { it.id == "answers.waist" })
    }

    @Test
    fun rulesComeBeforeMeasurementsBeforeConventions() {
        val ethos =
            Ethos(
                genre = "sans-geometric",
                uses = listOf("watch-face"),
                useOptions = mapOf("watch-face.aod" to "yes"),
                levels = mapOf("weight" to "bold", "waist" to "low", "aperture" to "closed"),
            )
        val kinds = tensions(ethos).map { it.source.kind }
        assertEquals(kinds.sortedBy { it.ordinal }, kinds)
        assertEquals(SourceKind.RULE, kinds.first())
        assertTrue(SourceKind.CONVENTION in kinds)
    }
}
