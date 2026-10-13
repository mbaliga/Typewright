// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.project.Ethos
import com.asoc.typewright.qa.corpus.style.StyleMeasurement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowTest {
    private fun ids(
        door: Door,
        ethos: Ethos,
        withAtlas: Boolean = false,
    ) = questionsFor(door, resolveBrief(ethos, if (withAtlas) FIXTURE_ATLAS else null)).map { it.id }

    @Test
    fun everyDoorStartsWithWhatThePersonKnows() {
        val empty = Ethos()
        assertEquals(STEP_GENRE, ids(Door.STYLE, empty).first())
        assertEquals(STEP_USES, ids(Door.USE, empty).first())
        assertEquals(STEP_REFERENCES, ids(Door.REFERENCES, empty).first())
        assertEquals(STEP_FEELINGS, ids(Door.FEELING, empty).first())
        assertEquals(STEP_MIRROR, ids(Door.MIRROR, empty).first())
        assertEquals(STEP_USES, ids(Door.BLANK, empty).first())
    }

    @Test
    fun theBlankDoorAsksEveryDimensionAndStressOnlyOnAContrastedStroke() {
        val plain = ids(Door.BLANK, Ethos())
        assertTrue(dimensionStepId(Dimension.STRESS) !in plain)
        assertEquals(Dimension.entries.size - 1, plain.count { it.startsWith("dim.") })
        val contrasted = ids(Door.BLANK, Ethos(levels = mapOf("contrast" to "strong")))
        assertTrue(dimensionStepId(Dimension.STRESS) in contrasted)
    }

    @Test
    fun aGenreAddsWhereItsVoiceLivesWithoutTheAtlas() {
        val deco = ids(Door.STYLE, Ethos(genre = "display-artdeco"))
        for (d in genreByKey("display-artdeco")!!.askAbout) assertTrue(dimensionStepId(d) in deco, "$d missing from $deco")
    }

    @Test
    fun aGenreAddsTheQuestionsItsFacesSplitOnWithTheAtlas() {
        // Deco's five faces split three, one and one on the bar: an open question.
        val open = openDimensions(genreByKey("display-artdeco")!!, FIXTURE_ATLAS).map { it.first }
        assertTrue(Dimension.WAIST in open, "$open")
        // Every garalde face has serifs: a closed one.
        assertTrue(Dimension.SERIFS !in openDimensions(genreByKey("serif-garalde")!!, FIXTURE_ATLAS).map { it.first })
    }

    @Test
    fun aUseAddsItsOwnQuestionsAndTheWordmarkItsLetters() {
        val watch = ids(Door.USE, Ethos(uses = listOf("watch-face")))
        assertTrue(listOf("use.watch-face.aod", "use.watch-face.date", "use.watch-face.dial").all { it in watch }, "$watch")
        assertTrue(STEP_WORDMARK in ids(Door.USE, Ethos(uses = listOf("wordmark"))))
    }

    @Test
    fun theNextStepIsTheFirstOneNotYetAnswered() {
        val ethos = Ethos(genre = "display-artdeco", answered = listOf(STEP_GENRE))
        val next = nextStep(Door.STYLE, resolveBrief(ethos, null))
        assertEquals(Step.PickUses, next)
        val done = ethos.answering(STEP_USES)
        assertIs<Step.AskDimension>(nextStep(Door.STYLE, resolveBrief(done, null)))
    }

    @Test
    fun aQuestionShowsHowTheGenresFacesAnswerIt() {
        val model = resolveBrief(Ethos(genre = "display-artdeco"), FIXTURE_ATLAS)
        val waist = questionsFor(Door.STYLE, model).filterIsInstance<Step.AskDimension>().single { it.dimension == Dimension.WAIST }
        assertEquals(mapOf(Level.WAIST_HIGH to 3, Level.WAIST_MIDDLE to 1, Level.WAIST_LOW to 1), waist.genreCounts)
        assertEquals(5, waist.genreTotal)
        assertEquals(Dimension.WAIST.levels, waist.options)
    }

    @Test
    fun aQuestionSuggestsWhatTheBriefSoFarPointsTo() {
        val model = resolveBrief(Ethos(genre = "serif-garalde"), FIXTURE_ATLAS)
        val contrast = questionsFor(Door.BLANK, model).filterIsInstance<Step.AskDimension>().single { it.dimension == Dimension.CONTRAST }
        assertEquals(Level.CONTRAST_STRONG, contrast.suggested)
        assertEquals(OriginKind.GENRE, assertNotNull(contrast.suggestedBy).kind)
        // Once answered, nothing is suggested over the person's own answer.
        val answered = resolveBrief(Ethos(genre = "serif-garalde", levels = mapOf("contrast" to "even")), FIXTURE_ATLAS)
        val again = questionsFor(Door.BLANK, answered).filterIsInstance<Step.AskDimension>().single { it.dimension == Dimension.CONTRAST }
        assertNull(again.suggested)
    }

    @Test
    fun genresAreSuggestedByHowManyOfTheirFacesFit() {
        val model = resolveBrief(Ethos(levels = mapOf("serifs" to "none", "aForm" to "single")), FIXTURE_ATLAS)
        assertEquals("sans-geometric", suggestGenres(model).first().genre.key)
        // With a use, only the genres usual for it are offered.
        val watch = resolveBrief(Ethos(uses = listOf("watch-face"), levels = mapOf("serifs" to "none")), FIXTURE_ATLAS)
        assertTrue(suggestGenres(watch).all { it.genre.key in useById("watch-face")!!.suggestedGenres })
        // Without anything measured to go on, a use's usual genres are offered as they are.
        val bare = resolveBrief(Ethos(uses = listOf("code")), null)
        assertEquals(listOf("monospace"), suggestGenres(bare).map { it.genre.key })
    }

    @Test
    fun theMirrorReadsTheDrawing() {
        val face =
            FIXTURE_ATLAS.pack.faces
                .getValue("S3")
                .asMeasurement()
        val drawing = Drawing(FontView.EMPTY, face)
        val read = questionsFor(Door.MIRROR, resolveBrief(Ethos(), FIXTURE_ATLAS), drawing).first()
        assertIs<Step.ReadDrawing>(read)
        assertEquals(Level.SERIFS_BRACKETED, read.levels[Dimension.SERIFS])
        assertEquals("onageHTcsx", read.toDraw)
        assertEquals(
            "serif-garalde",
            assertNotNull(read.reading)
                .genres
                .first()
                .genre.key,
        )
    }

    @Test
    fun nothingMeasuredReadsAsNothing() {
        assertNull(readAs(StyleMeasurement.EMPTY, FIXTURE_ATLAS))
        assertTrue(levelsOf(StyleMeasurement.EMPTY, FIXTURE_ATLAS).isEmpty())
    }
}
