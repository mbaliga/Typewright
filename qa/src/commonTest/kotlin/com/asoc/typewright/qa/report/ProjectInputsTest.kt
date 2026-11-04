// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.report

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.project.ApprovalOrigin
import com.asoc.typewright.project.BriefSource
import com.asoc.typewright.project.GlyphLock
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.LockState
import com.asoc.typewright.project.Master
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class ProjectInputsTest {
    private val at = Instant.fromEpochSeconds(0)

    private fun master(
        glyphs: List<Glyph>,
        id: String = "regular",
        family: String = "Fixture Sans",
    ) = Master(id, "$id.ufo", "Regular", true, ReportFixtures.project(glyphs, ReportFixtures.cleanInfo.copy(familyName = family)))

    private fun lock(
        origin: ApprovalOrigin,
        approved: Glyph,
    ) = GlyphLock(LockState.LOCKED, origin, at, approved, emptyList())

    private fun imported(vararg glyphs: Glyph) = glyphs.associate { GlyphRef("regular", it.name) to lock(ApprovalOrigin.IMPORT, it) }

    private val cubicT =
        with(ReportFixtures) {
            glyph("T", Contour(listOf(on(0, 0), off(0, 0), off(100, 0), on(100, 0), off(100, 100), off(0, 100)), CurveFormat.CUBIC))
        }

    @Test
    fun aProjectThatDidNotBeginFromAFontRecordsNothingAboutAFossil() {
        val m = master(ReportFixtures.cleanGlyphs)
        assertNull(importProvenanceOf(m, imported(ReportFixtures.cleanT), BriefSource.PAPER))
        assertNull(importProvenanceOf(m, imported(ReportFixtures.cleanT), BriefSource.BLANK))
    }

    @Test
    fun quadraticOutlinesImportedFromAFontAreACompiledFontsAndStillAsImported() {
        val m = master(ReportFixtures.cleanGlyphs)
        val p = importProvenanceOf(m, imported(ReportFixtures.cleanT, ReportFixtures.cleanO), BriefSource.FONT, "HyleDeco-Regular.ttf")
        assertEquals(ImportProvenance(true, 2, 2, "HyleDeco-Regular.ttf"), p)
    }

    @Test
    fun cubicOutlinesImportedFromAFontAreNotTakenForACompiledFont() {
        val m = master(listOf(cubicT))
        val p = importProvenanceOf(m, imported(cubicT), BriefSource.FONT)
        assertEquals(false, p?.fromCompiledFont)
        assertEquals(1, p?.untouchedGlyphs)
    }

    @Test
    fun aGlyphThePersonRedrewIsNoLongerAsImported() {
        // The T was approved by the import and has since been changed; the o is untouched.
        val redrawn = ReportFixtures.cleanT.copy(contours = listOf(ReportFixtures.polygon(0 to 0, 0 to 700, 400 to 700, 400 to 0)))
        val m = master(listOf(redrawn, ReportFixtures.cleanO))
        val p = importProvenanceOf(m, imported(ReportFixtures.cleanT, ReportFixtures.cleanO), BriefSource.FONT)
        assertEquals(1, p?.untouchedGlyphs)
        assertEquals(2, p?.outlineGlyphs)
        assertEquals(true, p?.fromCompiledFont)
    }

    @Test
    fun aGlyphApprovedByTheirOwnHandIsNotCountedAsImportedEvenWithTheSameOutline() {
        val m = master(ReportFixtures.cleanGlyphs)
        val locks =
            mapOf(
                GlyphRef("regular", "T") to lock(ApprovalOrigin.DRAW, ReportFixtures.cleanT),
                GlyphRef("regular", "o") to lock(ApprovalOrigin.IMPORT, ReportFixtures.cleanO),
            )
        assertEquals(1, importProvenanceOf(m, locks, BriefSource.FONT)?.untouchedGlyphs)
    }

    @Test
    fun glyphsWithNoOutlineAreNotCounted() {
        val space = ReportFixtures.glyph("space")
        val m = master(listOf(ReportFixtures.cleanT, space))
        val p = importProvenanceOf(m, imported(ReportFixtures.cleanT, space), BriefSource.FONT)
        assertEquals(1, p?.outlineGlyphs)
        assertEquals(1, p?.untouchedGlyphs)
    }

    @Test
    fun theProvenanceFeedsTheFossilCheckEndToEnd() {
        val m = master(ReportFixtures.cleanGlyphs)
        val p = importProvenanceOf(m, imported(ReportFixtures.cleanT, ReportFixtures.cleanO), BriefSource.FONT, "X.ttf")
        val report = ReportFixtures.report(provenance = p)
        assertEquals(Severity.FAIL, report.item(ReviewOrder.SOURCE_FOSSIL)?.severity)
        val drawn = importProvenanceOf(master(ReportFixtures.cleanGlyphs), emptyMap(), BriefSource.FONT)
        assertEquals(Severity.PASS, ReportFixtures.report(provenance = drawn).item(ReviewOrder.SOURCE_FOSSIL)?.severity)
    }

    @Test
    fun otherMastersAreListedByTheirNamesAndTheDefaultIsLeftOut() {
        val a = master(ReportFixtures.cleanGlyphs, id = "regular", family = "Fixture Sans")
        val b = master(ReportFixtures.cleanGlyphs, id = "bold", family = "Fixture Sans Bold")
        assertEquals(listOf(MasterNames("Fixture Sans Bold", "Regular")), otherMasterNames(listOf(a, b), a))
        assertEquals(emptyList(), otherMasterNames(listOf(a), a))
    }
}
