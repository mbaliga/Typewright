// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.project.Ethos
import com.asoc.typewright.qa.corpus.loadStyleAtlas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Whole briefs against the real atlas, as a person would build them: the targets, tensions,
 * questions and plan they get. The counts asserted are read from data/style-atlas-latin.json.
 */
class BriefScenarioTest {
    private val atlas = loadStyleAtlas()

    @Test
    fun anArtDecoWatchFace() {
        val ethos =
            Ethos(
                genre = "display-artdeco",
                uses = listOf("watch-face"),
                useOptions = mapOf("watch-face.aod" to "yes", "watch-face.date" to "date"),
                levels =
                    mapOf(
                        "contrast" to "even",
                        "roundness" to "softSquare",
                        "width" to "narrow",
                        "waist" to "high",
                        "weight" to "bold",
                    ),
            )
        val model = resolveBrief(ethos, atlas)
        assertEquals("An art deco typeface for watch faces: monoline, squarish rounds, narrow, heavy and high-waisted.", model.sentence)
        val ids = model.tensions.map { it.id }
        // Wear OS's always-on rule first, then the one answer no deco face in the atlas gives.
        assertEquals("use.watch.ambient", ids.first())
        val squarish = model.tensions.single { it.id == "departs.roundness" }
        assertTrue(squarish.body.startsWith("None of the 8 art deco faces"), squarish.body)
        // The questions include where deco's voice lives.
        val asked = questionsFor(Door.STYLE, model).map { it.id }
        assertTrue(dimensionStepId(Dimension.WAIST) in asked && dimensionStepId(Dimension.CONTRAST) in asked, "$asked")
        // The plan starts from the figures.
        assertEquals(listOf('0', '1'), planFor(model).first().glyphs)
    }

    @Test
    fun aGroteskForAnApp() {
        val ethos = Ethos(genre = "sans-grotesque", uses = listOf("app-ui"), levels = mapOf("xHeight" to "small"))
        val model = resolveBrief(ethos, atlas)
        val departs = model.tensions.single { it.id == "departs.xHeight" }
        assertTrue(departs.body.startsWith("Only 3 of the 30 grotesque faces"), departs.body)
        assertTrue(model.tensions.any { it.id == "use.xheight" && it.source.kind == SourceKind.EVIDENCE })
        // Genres offered for an app are the ones usual for it.
        assertTrue(suggestGenres(model).all { it.genre.key in useById("app-ui")!!.suggestedGenres })
    }

    @Test
    fun startingFromNothing() {
        val ethos = Ethos(levels = mapOf("serifs" to "none", "contrast" to "even", "roundness" to "circle", "aForm" to "single"))
        val model = resolveBrief(ethos, atlas)
        assertEquals("sans-geometric", suggestGenres(model).first().genre.key)
        assertTrue(facesLike(model).isNotEmpty())
    }

    @Test
    fun feelingsThatDisagree() {
        val model = resolveBrief(Ethos(feelings = listOf("calm", "loud")), atlas)
        assertTrue(model.tensions.any { it.id == "feelings.aperture" }, "${model.tensions.map { it.id }}")
        // The feeling door then asks the aperture question first.
        val first = questionsFor(Door.FEELING, model).filterIsInstance<Step.AskDimension>().first()
        assertEquals(Dimension.APERTURE, first.dimension)
    }

    @Test
    fun referencesSetTheTargetsTheyAgreeOn() {
        val model = resolveBrief(Ethos(references = listOf("Libre Bodoni", "GFS Didot")), atlas)
        val contrast = model.targets.getValue(Feature.CONTRAST)
        assertEquals(OriginKind.REFERENCES, contrast.origin.kind)
        assertEquals(Level.CONTRAST_EXTREME, contrast.level)
    }
}
