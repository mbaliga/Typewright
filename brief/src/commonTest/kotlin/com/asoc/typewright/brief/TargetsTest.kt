// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.project.Ethos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TargetsTest {
    private val atlas = FIXTURE_ATLAS
    private val maker = TargetMaker.of(atlas)

    @Test
    fun anAnswerBecomesTheQuantileBandOfItsPooledGenres() {
        // Even: the geometric faces from their minimum to their upper quartile (1.0 .. 1.1875).
        val even = assertNotNull(maker.range(Level.CONTRAST_EVEN.anchors.single()))
        assertEquals(1.0, even.start, 1e-9)
        assertEquals(1.1875, even.endInclusive, 1e-9)
        // Strong: the garalde faces' middle half (2.25 .. 2.75).
        val strong = assertNotNull(maker.range(Level.CONTRAST_STRONG.anchors.single()))
        assertEquals(2.25, strong.start, 1e-9)
        assertEquals(2.75, strong.endInclusive, 1e-9)
    }

    @Test
    fun theMakerIsSharedPerAtlas() {
        assertTrue(TargetMaker.of(atlas) === TargetMaker.of(atlas))
    }

    @Test
    fun aValueMapsBackToTheAnswerWhoseBandHoldsIt() {
        assertEquals(Level.CONTRAST_EVEN, maker.levelHolding(Feature.CONTRAST, 1.1))
        assertEquals(Level.CONTRAST_STRONG, maker.levelHolding(Feature.CONTRAST, 2.5))
        // Between bands: the nearest one.
        assertEquals(Level.CONTRAST_EVEN, maker.levelHolding(Feature.CONTRAST, 1.25))
        assertEquals(Level.A_SINGLE, maker.levelWithValue(Feature.A_STOREYS, "single"))
        // Three answers share serif "yes", so the value alone names none of them.
        assertNull(maker.levelWithValue(Feature.SERIF, "yes"))
    }

    @Test
    fun aFaceAnswersEachDimension() {
        val garalde = atlas.pack.faces.getValue("S3")
        val deco = atlas.pack.faces.getValue("D1")

        fun level(
            d: Dimension,
            face: com.asoc.typewright.qa.corpus.AtlasFace,
        ) = maker.levelFor(d, { f -> f.numericValue(face) }, { f -> f.categoricalValue(face) })
        assertEquals(Level.SERIFS_BRACKETED, level(Dimension.SERIFS, garalde))
        assertEquals(Level.SERIFS_NONE, level(Dimension.SERIFS, deco))
        assertEquals(Level.WAIST_HIGH, level(Dimension.WAIST, deco))
        // A monoline face has no stress worth reading.
        assertNull(level(Dimension.STRESS, deco))
    }

    @Test
    fun yourAnswerBeatsYourReferencesWhichBeatTheGenre() {
        val model =
            resolveBrief(
                Ethos(genre = "sans-geometric", references = listOf("S1"), levels = mapOf("contrast" to "even")),
                atlas,
            )
        val contrast = assertNotNull(model.targets[Feature.CONTRAST])
        assertEquals(OriginKind.ANSWER, contrast.origin.kind)
        assertTrue(contrast.firm)
        val serif = assertNotNull(model.targets[Feature.SERIF])
        assertEquals(OriginKind.REFERENCES, serif.origin.kind)
        assertEquals(setOf("yes"), serif.values)
        assertFalse(serif.firm)
    }

    @Test
    fun aFeelingBeatsTheGenre() {
        // Calm's faces (three garaldes) are more open than the corpus; geometric faces are not.
        val model = resolveBrief(Ethos(genre = "sans-geometric", feelings = listOf("calm")), atlas)
        val aperture = assertNotNull(model.targets[Feature.APERTURE])
        assertEquals(OriginKind.FEELING, aperture.origin.kind)
        assertEquals(Level.APERTURE_OPEN, aperture.level)
        val serif = assertNotNull(model.targets[Feature.SERIF])
        // Calm's faces all have serifs: a categorical leaning, also above the genre.
        assertEquals(OriginKind.FEELING, serif.origin.kind)
    }

    @Test
    fun theGenreFillsWhatNothingElseSpokeTo() {
        val model = resolveBrief(Ethos(genre = "serif-garalde"), atlas)
        val contrast = assertNotNull(model.targets[Feature.CONTRAST])
        assertEquals(OriginKind.GENRE, contrast.origin.kind)
        assertEquals(2.25, assertNotNull(contrast.range).start, 1e-9)
        assertEquals(setOf("yes"), model.targets.getValue(Feature.SERIF).values)
    }

    @Test
    fun stressIsDroppedOnAMonolineStroke() {
        val model = resolveBrief(Ethos(genre = "sans-geometric", levels = mapOf("contrast" to "even")), atlas)
        assertNull(model.targets[Feature.STRESS])
        // Garalde faces measure a bracket, but a sans-serif answer leaves nothing to bracket.
        val serifless = resolveBrief(Ethos(genre = "serif-garalde", levels = mapOf("serifs" to "none")), atlas)
        assertNull(serifless.targets[Feature.BRACKET])
        assertNotNull(resolveBrief(Ethos(genre = "serif-garalde"), atlas).targets[Feature.BRACKET])
    }

    @Test
    fun withoutTheAtlasOnlyCategoricalAnswersBecomeTargets() {
        val model = resolveBrief(Ethos(levels = mapOf("contrast" to "even", "aForm" to "single")), atlas = null)
        assertNull(model.targets[Feature.CONTRAST])
        assertEquals(setOf("single"), model.targets.getValue(Feature.A_STOREYS).values)
        assertTrue(model.feelings.isEmpty())
    }

    @Test
    fun referencesThatDisagreeAreReported() {
        val model = resolveBrief(Ethos(references = listOf("G1", "D3")), atlas)
        // Contrast 1.0 against 4.0 is far more than one and a half interquartile ranges apart.
        assertTrue(Feature.CONTRAST in model.referenceSpread)
        assertTrue(model.tensions.any { it.id == "references.contrast" })
    }

    @Test
    fun aTargetDescribesItselfInWords() {
        val t = Target(Feature.X_HEIGHT, 0.70..0.74, emptySet(), Origin(OriginKind.GENRE, "x", 1), firm = false)
        assertEquals("0.70 to 0.74 of the cap height", t.describe())
        val c = Target(Feature.A_STOREYS, null, setOf("double"), Origin(OriginKind.GENRE, "x", 1), firm = false)
        assertEquals("two-storey", c.describe())
        assertTrue(c.contains("double"))
        assertFalse(c.contains("single"))
    }
}
