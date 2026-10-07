// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.project.Ethos
import com.asoc.typewright.qa.corpus.style.StyleMeasurement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuideTest {
    @Test
    fun theGuideLeadsWithTheNextThingToDraw() {
        val guide = guideFor(resolveBrief(Ethos(genre = "display-artdeco"), FIXTURE_ATLAS), Drawing.EMPTY)
        val next = guide.first()
        assertEquals(GuideKind.NEXT, next.kind)
        assertEquals("n o H O", next.glyphs)
    }

    @Test
    fun aFailedRequirementComesBeforeTensionsAndDrift() {
        val ethos = Ethos(genre = "sans-geometric", uses = listOf("watch-face"), levels = mapOf("waist" to "low", "contrast" to "even"))
        val font =
            FontView(
                1000,
                mapOf('0' to ringGlyph("zero", 600, 500, 80), '1' to barGlyph("one", 400, 80, 500)),
            )
        val drawing = Drawing(font, StyleMeasurement.EMPTY.copy(contrastRatio = 3.0))
        val kinds = guideFor(resolveBrief(ethos, FIXTURE_ATLAS), drawing).map { it.kind }
        val firstRequirement = kinds.indexOf(GuideKind.REQUIREMENT)
        assertEquals(GuideKind.NEXT, kinds.first())
        assertTrue(firstRequirement < kinds.indexOf(GuideKind.TENSION), "$kinds")
        assertTrue(kinds.indexOf(GuideKind.TENSION) < kinds.indexOf(GuideKind.DRIFT), "$kinds")
    }

    @Test
    fun aDriftItemSaysWhatTheBriefAsksAndWhy() {
        val model = resolveBrief(Ethos(levels = mapOf("contrast" to "even")), FIXTURE_ATLAS)
        val drift =
            guideFor(model, Drawing(FontView.EMPTY, StyleMeasurement.EMPTY.copy(contrastRatio = 3.0))).single { it.kind == GuideKind.DRIFT }
        assertEquals("Contrast: 3.0 : 1, off target", drift.title)
        assertTrue(drift.body.startsWith("The brief asks for 1.0 to 1.2 : 1 (your answer: Even)."), drift.body)
        assertEquals(Dimension.CONTRAST, drift.dimension)
    }

    @Test
    fun theGenreBringsItsCraftAndItsLesson() {
        val guide = guideFor(resolveBrief(Ethos(genre = "display-artdeco"), null), Drawing.EMPTY)
        assertTrue(guide.count { it.kind == GuideKind.CRAFT } >= 3)
        assertEquals("lineages.artdeco", guide.single { it.kind == GuideKind.LEARN }.sceneId)
    }
}
