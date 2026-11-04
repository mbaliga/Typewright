// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.imports

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The brief's §4.5 rules: OFL and Apache are fine, a reserved name must be renamed, no licence is a warning, a proprietary licence is refused. */
class LicenceAuditTest {
    private val oflText =
        "This Font Software is licensed under the SIL Open Font License, Version 1.1. " +
            "This license is available with a FAQ at: https://scripts.sil.org/OFL"

    private fun audit(
        info: UfoFontInfo,
        files: List<String> = emptyList(),
    ) = auditLicence("Test.ttf", info, AuditSource.FONT_FILE, files)

    private fun info(
        licence: String? = null,
        url: String? = null,
        copyright: String? = "Copyright 2020 The Test Project Authors (https://example.org)",
        restricted: Boolean = false,
    ) = UfoFontInfo(
        familyName = "Test",
        copyright = copyright,
        openTypeNameLicense = licence,
        openTypeNameLicenseURL = url,
        other = if (restricted) listOf("openTypeOS2Type" to PlistValue.PArray(listOf(PlistValue.PInteger(1)))) else emptyList(),
    )

    @Test
    fun anOflFontIsFineToModifyAndTheLicenceIsNamedWithItsVersion() {
        val audit = audit(info(oflText, "https://scripts.sil.org/OFL"))
        assertEquals(LicenceKind.OFL, audit.licence)
        assertEquals("SIL Open Font License 1.1", audit.licenceName)
        assertEquals(ImportVerdict.FINE, audit.verdict)
        assertTrue(audit.canImport)
        assertFalse(audit.mustRename)
        assertTrue(audit.verdictWords.startsWith("OFL lets you change and redistribute this font."))
    }

    @Test
    fun theOflIsRecognisedByItsUrlAloneAndByAShortSentence() {
        assertEquals(LicenceKind.OFL, audit(info(null, "http://scripts.sil.org/OFL")).licence)
        val zilla = audit(info("Licensed under the Open Font License, version 1.1 or later."))
        assertEquals(LicenceKind.OFL, zilla.licence)
        assertEquals("SIL Open Font License 1.1", zilla.licenceName)
        assertEquals(LicenceKind.OFL, audit(info("OFL-1.1")).licence)
    }

    @Test
    fun anApacheFontIsFineToModify() {
        val audit = audit(info("Licensed under the Apache License, Version 2.0", "http://www.apache.org/licenses/LICENSE-2.0"))
        assertEquals(LicenceKind.APACHE, audit.licence)
        assertEquals("Apache License 2.0", audit.licenceName)
        assertEquals(ImportVerdict.FINE, audit.verdict)
    }

    @Test
    fun aReservedFontNameMeansRenameAndTheVerdictNamesIt() {
        val audit =
            audit(
                info(
                    oflText,
                    copyright =
                        "Copyright 2017 The Playfair Display Project Authors (https://github.com/clauseggers/Playfair-Display), " +
                            "with Reserved Font Name \"Playfair Display\".",
                ),
            )
        assertEquals(ImportVerdict.RENAME, audit.verdict)
        assertTrue(audit.canImport, "a reserved name does not stop the import; it makes the new version carry another name")
        assertTrue(audit.mustRename)
        assertEquals(listOf("Playfair Display"), audit.reservedFontNames)
        assertTrue("“Playfair Display”" in audit.verdictWords)
    }

    @Test
    fun aReservedNameDeclaredOnlyInTheLicenceFileBesideAUfoCounts() {
        val file =
            "Copyright (c) 2010, Foo (http://foo.example), with Reserved Font Name \"Foo Sans\".\n\n" +
                "SIL OPEN FONT LICENSE Version 1.1"
        val audit = auditLicence("Foo.ufo", info(null, null), AuditSource.UFO, listOf(file))
        assertEquals(LicenceKind.OFL, audit.licence)
        assertEquals(listOf("Foo Sans"), audit.reservedFontNames)
        assertEquals(ImportVerdict.RENAME, audit.verdict)
    }

    @Test
    fun noLicenceAtAllIsAWarningNotARefusal() {
        val audit = audit(info(null, null, copyright = "Copyright 2020 Somebody"))
        assertEquals(LicenceKind.NONE, audit.licence)
        assertEquals(ImportVerdict.UNCERTAIN, audit.verdict)
        assertTrue(audit.canImport)
        assertTrue("states no licence" in audit.verdictWords)
    }

    @Test
    fun aLicenceTypewrightDoesNotRecogniseIsAWarningToo() {
        val audit = audit(info("Released under the Foo Public Terms, see foo.example/terms."))
        assertEquals(LicenceKind.OTHER, audit.licence)
        assertEquals(ImportVerdict.UNCERTAIN, audit.verdict)
        assertTrue("doesn't recognise" in audit.verdictWords)
    }

    @Test
    fun aProprietaryLicenceIsRefused() {
        for (
        text in
        listOf(
            "This font is proprietary software of Example Foundry.",
            "Use of this font is governed by the End User License Agreement at example.com/eula.",
            "The font may not be modified or redistributed.",
            "Not for redistribution.",
            "Creative Commons Attribution-NoDerivatives 4.0 International",
            "Licensed under CC BY-NC-ND 4.0.",
        )
        ) {
            val audit = audit(info(text))
            assertEquals(LicenceKind.PROPRIETARY, audit.licence, text)
            assertEquals(ImportVerdict.REFUSED, audit.verdict, text)
            assertFalse(audit.canImport, text)
        }
    }

    @Test
    fun aLicenceThatOnlyLimitsUseIsReadTheLicenceNotRefused() {
        for (
        text in
        listOf(
            "Free for personal use only.",
            "Creative Commons Attribution-NonCommercial 4.0 International",
            "This font is licensed CC BY-NC-SA 4.0 for non-commercial projects.",
            "Free for personal use. Contact us for a commercial licence.",
        )
        ) {
            val audit = audit(info(text))
            assertEquals(LicenceKind.USE_LIMITED, audit.licence, text)
            assertEquals(ImportVerdict.UNCERTAIN, audit.verdict, text)
            assertEquals("Read the licence first", audit.verdict.title, text)
            assertTrue(audit.canImport, "$text: such a licence may well allow changes, so it is not refused")
            assertFalse("doesn't let you change it" in audit.verdictWords, "$text: that sentence would be false for CC BY-NC")
            assertTrue("Read the licence" in audit.verdictWords, audit.verdictWords)
        }
    }

    @Test
    fun aNonCommercialLicenceThatAlsoForbidsChangesIsStillRefused() {
        val audit = audit(info("Creative Commons Attribution-NonCommercial-NoDerivatives 4.0, non-commercial use only."))
        assertEquals(LicenceKind.PROPRIETARY, audit.licence)
        assertFalse(audit.canImport)
    }

    @Test
    fun aReservedNameAndALimitedUseLicenceAskForBothTheRenameAndAReading() {
        val audit =
            audit(
                info(
                    "Free for non-commercial use.",
                    copyright = "Copyright 2012 Foo, with the Reserved Font Name Bar",
                ),
            )
        assertEquals(ImportVerdict.RENAME, audit.verdict)
        assertEquals(listOf("Bar"), audit.reservedFontNames)
        assertTrue("read it too" in audit.verdictWords, audit.verdictWords)
    }

    @Test
    fun aReservedNameDeclaredInTheShapesTheOflFaqAllowsIsStillFoundByTheAudit() {
        for (
        copyright in
        listOf(
            "Copyright 2011 Foo, with the Reserved Font Name Bar",
            "Copyright 2012 Foo. Reserved Font Name \"Bar\"",
            "Foo, Reserved Font Name Bar",
        )
        ) {
            val audit = audit(info(oflText, copyright = copyright))
            assertEquals(ImportVerdict.RENAME, audit.verdict, copyright)
            assertEquals(listOf("Bar"), audit.reservedFontNames, copyright)
        }
    }

    @Test
    fun aRestrictedEmbeddingFlagWithNoOpenLicenceIsRefused() {
        assertEquals(ImportVerdict.REFUSED, audit(info(null, null, restricted = true)).verdict)
        assertEquals(ImportVerdict.REFUSED, audit(info("Some other terms.", restricted = true)).verdict)
    }

    @Test
    fun anOpenLicenceOutweighsARestrictedFlagAndAnAllRightsReservedCopyright() {
        val audit = audit(info(oflText, copyright = "Copyright 2010 Adobe. All Rights Reserved.", restricted = true))
        assertEquals(ImportVerdict.FINE, audit.verdict)
    }

    @Test
    fun theFullOflTextWithItsOwnBoilerplateIsNotMistakenForAProprietaryOrReservedNameDeclaration() {
        val full =
            "Copyright (c) 2010, j. 'mach' wust, with Reserved Font Name UnifrakturMaguntia.\n\n" +
                "This Font Software is licensed under the SIL Open Font License, Version 1.1.\n\n" +
                "\"Reserved Font Name\" refers to any names specified as such after the copyright statement(s).\n" +
                "The fonts and derivatives, however, cannot be released under any other type of license.\n" +
                "No Modified Version of the Font Software may use the Reserved Font Name(s) unless explicit written permission is granted."
        val audit = audit(info(full, copyright = "Copyright (c) 2010 j. 'mach' wust with Reserved Font Name UnifrakturMaguntia."))
        assertEquals(LicenceKind.OFL, audit.licence)
        assertEquals(listOf("UnifrakturMaguntia"), audit.reservedFontNames)
    }

    @Test
    fun thePeopleAndTheirLinksAreCarriedIntoTheAudit() {
        val audit =
            audit(
                info(oflText).copy(
                    openTypeNameDesigner = "Ninad Kale",
                    openTypeNameDesignerURL = "https://designer.example",
                    openTypeNameManufacturerURL = "https://indiantypefoundry.com",
                    trademark = "Poppins is a trademark",
                ),
            )
        assertEquals("Ninad Kale", audit.designer)
        assertEquals("https://designer.example", audit.designerUrl)
        assertEquals("https://indiantypefoundry.com", audit.vendorUrl)
        assertEquals("Poppins is a trademark", audit.trademark)
        assertEquals("Test", audit.familyName)
    }

    @Test
    fun aMissingDesignerIsNull() {
        assertEquals(null, audit(info(oflText)).designer)
    }

    @Test
    fun classifyLicenceReadsTheCopyrightLineWhenTheLicenceFieldIsEmpty() {
        val (kind, name) = classifyLicence(emptyList(), "Copyright 2010 Foo. Licensed under the SIL Open Font License, Version 1.1.", false)
        assertEquals(LicenceKind.OFL, kind)
        assertEquals("SIL Open Font License 1.1", name)
    }
}
