// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.project.Ethos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReferencesTest {
    private val atlas = FIXTURE_ATLAS

    @Test
    fun facesLikeTheBriefComeClosestFirst() {
        val model = resolveBrief(Ethos(levels = mapOf("contrast" to "strong", "serifs" to "bracketed")), atlas)
        val faces = facesLike(model, limit = 3)
        assertTrue(faces.all { it.family.startsWith("S") }, "$faces")
        assertEquals(0.0, faces.first().distance)
    }

    @Test
    fun aChosenGenreKeepsTheSearchInsideIt() {
        val model = resolveBrief(Ethos(genre = "display-artdeco", levels = mapOf("contrast" to "even")), atlas)
        assertTrue(facesLike(model).all { it.family.startsWith("D") })
        assertTrue(facesLike(model, anyGenre = true, limit = 20).any { it.family.startsWith("G") })
    }

    @Test
    fun namedReferencesAreNotSuggestedBack() {
        val model = resolveBrief(Ethos(references = listOf("S1")), atlas)
        assertTrue(facesLike(model, limit = 20).none { it.family == "S1" })
    }

    @Test
    fun aFaceReadsAsItsOwnGenre() {
        for (family in listOf("G3", "S4", "D1")) {
            val face = atlas.pack.faces.getValue(family)
            val reading = assertNotNull(readAs(face.asMeasurement(), atlas, exclude = family))
            assertEquals(
                face.classes.single(),
                reading.genres
                    .first()
                    .genre.key,
                "$family read as ${reading.genres}",
            )
            assertTrue(family !in reading.nearest)
        }
    }

    @Test
    fun searchingFacesMatchesNamesStartingWithTheQueryFirst() {
        assertEquals(listOf("S1", "S2", "S3", "S4", "S5", "S6"), searchFaces(atlas, "s"))
        assertTrue(searchFaces(atlas, " ").isEmpty())
    }

    @Test
    fun aFaceIsDescribedAsTheAnswersItWouldGive() {
        val text = describeFace(atlas.pack.faces.getValue("S3"), atlas, listOf(Dimension.CONTRAST, Dimension.SERIFS))
        assertEquals("high-contrast and bracketed serifs", text)
    }
}
