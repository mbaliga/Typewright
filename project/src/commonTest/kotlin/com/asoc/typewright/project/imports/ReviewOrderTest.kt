// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.imports

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.font.ufo.writeUfoProject
import com.asoc.typewright.project.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The audit comes first: a UFO whose licence withholds modification is refused from its font info
 * alone, so its glyphs are never parsed. The proof is a glyph file that cannot be parsed: with a
 * licence that allows the import it makes the UFO unreadable, with one that does not it makes no
 * difference.
 */
class ReviewOrderTest {
    private fun filesWith(
        info: UfoFontInfo,
        brokenGlif: Boolean,
    ): Map<String, String> {
        val project = Fixtures.ufoProject(listOf("A", "B")).let { UfoProject(info, it.glyphs) }
        val files = writeUfoProject(project).toMutableMap()
        if (brokenGlif) {
            val glif = files.keys.first { it.startsWith("glyphs/") && it.endsWith(".glif") }
            files[glif] = "<glyph name=\"A\""
        }
        return files
    }

    private val proprietary =
        UfoFontInfo(
            familyName = "Locked",
            styleName = "Regular",
            unitsPerEm = 1000,
            openTypeNameLicense = "This font is proprietary software of Example Foundry. It may not be modified or redistributed.",
        )

    private val open =
        UfoFontInfo(
            familyName = "Open",
            styleName = "Regular",
            unitsPerEm = 1000,
            openTypeNameLicense = "This Font Software is licensed under the SIL Open Font License, Version 1.1.",
        )

    @Test
    fun aRefusedUfoIsRefusedWithoutItsGlyphsBeingParsed() {
        val review = reviewUfoFolder("Locked.ufo", filesWith(proprietary, brokenGlif = true))
        val refused = assertIs<ImportReview.Refused>(review)
        assertEquals(ImportVerdict.REFUSED, refused.audit.verdict)
        assertEquals("Locked", refused.audit.familyName)
    }

    @Test
    fun aUfoThatMayBeImportedIsReadInFullSoABrokenGlyphMakesItUnreadable() {
        val review = reviewUfoFolder("Open.ufo", filesWith(open, brokenGlif = true))
        assertIs<ImportReview.Unreadable>(review)
        assertTrue("Nothing was imported" in review.message, review.message)
    }

    @Test
    fun theSameUfoWithItsGlyphsIntactIsReadyOrRefusedByItsLicenceAlone() {
        assertIs<ImportReview.Ready>(reviewUfoFolder("Open.ufo", filesWith(open, brokenGlif = false)))
        assertIs<ImportReview.Refused>(reviewUfoFolder("Locked.ufo", filesWith(proprietary, brokenGlif = false)))
    }

    @Test
    fun aUfoWithNoMetaInfoIsUnreadableNotRefused() {
        val files = filesWith(proprietary, brokenGlif = false).toMutableMap()
        files.remove("metainfo.plist")
        assertIs<ImportReview.Unreadable>(reviewUfoFolder("Locked.ufo", files))
    }
}
