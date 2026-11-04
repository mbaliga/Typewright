// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.imports

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.font.ufo.readUfoProject
import com.asoc.typewright.core.font.ufo.writeUfoProject
import com.asoc.typewright.project.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportRenameTest {
    private val reserved = listOf("Josefin Sans")

    /** A font info the way a compiler-ready UFO from the web would hold it: every name key a compiler would emit, all using the old name. */
    private fun josefin(): UfoProject {
        val records =
            PlistValue.PArray(
                listOf(
                    record(1, "Josefin Sans Light"),
                    record(3, "1.0;JOSF;Josefin Sans Light"),
                    record(13, "Licensed under the OFL"),
                    record(16, "Josefin Sans"),
                ),
            )
        val info =
            UfoFontInfo(
                familyName = "Josefin Sans",
                styleName = "Light",
                copyright = "Copyright 2010 The Josefin Sans Project Authors, with Reserved Font Name \"Josefin Sans\".",
                trademark = "Josefin Sans is a trademark of Typemade.",
                openTypeNameLicense = "This Font Software is licensed under the SIL Open Font License, Version 1.1.",
                openTypeNamePreferredFamilyName = "Josefin Sans",
                postscriptFontName = "JosefinSans-Light",
                other =
                    listOf(
                        "styleMapFamilyName" to PlistValue.PString("Josefin Sans Light"),
                        "postscriptFullName" to PlistValue.PString("Josefin Sans Light"),
                        "openTypeNameUniqueID" to PlistValue.PString("1.0;JOSF;JosefinSans-Light"),
                        "openTypeNameCompatibleFullName" to PlistValue.PString("Josefin Sans Light"),
                        "openTypeNameRecords" to records,
                        "openTypeNameDescription" to PlistValue.PString("Josefin Sans is a vintage geometric"),
                    ),
            )
        return Fixtures.ufoProject().copy(fontInfo = info)
    }

    private fun record(
        id: Long,
        text: String,
    ) = PlistValue.PDict(
        listOf(
            "nameID" to PlistValue.PInteger(id),
            "platformID" to PlistValue.PInteger(3),
            "encodingID" to PlistValue.PInteger(1),
            "languageID" to PlistValue.PInteger(1033),
            "string" to PlistValue.PString(text),
        ),
    )

    @Test
    fun theFixtureReallyUsesTheReservedNameInEveryKeyACompilerWouldEmit() {
        assertEquals(
            listOf(
                "familyName",
                "openTypeNamePreferredFamilyName",
                "postscriptFontName",
                "styleMapFamilyName",
                "postscriptFullName",
                "openTypeNameUniqueID",
                "openTypeNameCompatibleFullName",
                "openTypeNameRecords (name ID 1)",
                "openTypeNameRecords (name ID 3)",
                "openTypeNameRecords (name ID 16)",
            ),
            nameTableKeysMentioning(josefin().fontInfo, reserved),
        )
    }

    @Test
    fun renamingChangesEveryNameKeyAndLeavesNoneUsingTheOldName() {
        val renamed = renameFamily(josefin(), "Lumen Script", reserved)
        val info = renamed.fontInfo
        assertEquals(emptyList(), nameTableKeysMentioning(info, reserved))
        assertEquals("Lumen Script", info.familyName)
        assertEquals("Lumen Script", info.openTypeNamePreferredFamilyName)
        assertEquals("LumenScript-Light", info.postscriptFontName)
        assertEquals("Light", info.styleName)
        val keys = info.other.map { it.first }
        for (derived in listOf("styleMapFamilyName", "postscriptFullName", "openTypeNameUniqueID", "openTypeNameCompatibleFullName")) {
            assertTrue(derived !in keys, "$derived is left to the compiler to rebuild from the new family")
        }
        // The name table records for the family are gone; the licence record (13) stays.
        val records = info.other.single { it.first == "openTypeNameRecords" }.second as PlistValue.PArray
        assertEquals(1, records.items.size)
    }

    @Test
    fun renamingKeepsTheCopyrightTheTrademarkTheLicenceAndTheDescription() {
        val before = josefin().fontInfo
        val after = renameFamily(josefin(), "Lumen Script", reserved).fontInfo
        assertEquals(before.copyright, after.copyright)
        assertEquals(before.trademark, after.trademark)
        assertEquals(before.openTypeNameLicense, after.openTypeNameLicense)
        assertEquals(
            before.other.single { it.first == "openTypeNameDescription" },
            after.other.single {
                it.first ==
                    "openTypeNameDescription"
            },
        )
    }

    @Test
    fun renamingLeavesTheGlyphsAndTheLibAlone() {
        val before = josefin()
        val after = renameFamily(before, "Lumen Script", reserved)
        assertEquals(before.glyphs, after.glyphs)
        assertEquals(before.lib, after.lib)
    }

    @Test
    fun theRenamedProjectSurvivesWritingAndReadingTheUfo() {
        val renamed = renameFamily(josefin(), "Lumen Script", reserved)
        assertEquals(renamed, readUfoProject(writeUfoProject(renamed)))
    }

    @Test
    fun aKeyNotPresentIsNotInvented() {
        val bare = Fixtures.ufoProject().copy(fontInfo = UfoFontInfo(familyName = "Josefin Sans", styleName = "Regular"))
        val info = renameFamily(bare, "Lumen", reserved).fontInfo
        assertNull(info.postscriptFontName)
        assertNull(info.openTypeNamePreferredFamilyName)
        assertEquals(emptyList(), info.other)
    }

    @Test
    fun aStyleNameThatStillUsesTheReservedNameMakesTheRenameRefuse() {
        val bad = josefin().let { it.copy(fontInfo = it.fontInfo.copy(styleName = "Josefin Sans Italic")) }
        val message = assertFailsWith<IllegalArgumentException> { renameFamily(bad, "Lumen Script", reserved) }.message
        assertTrue("styleName" in message.orEmpty(), message)
    }

    @Test
    fun aNewNameMustBeOneAndMustNotUseTheReservedName() {
        assertNotNull(validateFamilyName("   ", reserved))
        assertNotNull(validateFamilyName("Josefin Sans", reserved, oldFamily = "Josefin Sans"))
        assertNotNull(validateFamilyName("Josefin Sans Redraw", reserved))
        assertNotNull(validateFamilyName("my JOSEFIN sans", reserved))
        assertNotNull(validateFamilyName("JosefinSans", reserved))
        assertNotNull(validateFamilyName("x".repeat(MAX_FAMILY_NAME_LENGTH + 1), reserved))
        assertNotNull(validateFamilyName("Bad\u0001Name", reserved))
        assertNull(validateFamilyName("Lumen Script", reserved))
        assertNull(validateFamilyName("Josephine Sans", reserved))
        assertNull(validateFamilyName("Resource", listOf("Source")), "'Resource' is not the word 'Source'")
        assertNotNull(validateFamilyName("Source Redraw", listOf("Source")))
    }

    @Test
    fun theMessageNamesTheReservedNameItClashesWith() {
        assertTrue("“Josefin Sans”" in validateFamilyName("Josefin Sans Redraw", reserved).orEmpty())
    }

    @Test
    fun postScriptNamesAreAsciiAndShort() {
        assertEquals("LumenScript-Light", postScriptName("Lumen Script", "Light"))
        assertEquals("Caf-BoldItalic", postScriptName("Café", "Bold Italic"))
        assertNull(postScriptName("éè", "Regular"))
        assertEquals(63, postScriptName("A".repeat(80), "Regular")?.length)
    }

    @Test
    fun folderNamesLoseTheCharactersAFilesystemRefuses() {
        assertEquals("Hyle Deco", projectFolderName("Hyle Deco"))
        assertEquals("a_b_c_d_e_f_g_h_i_j", projectFolderName("a/b\\c:d*e?f\"g<h>i|j"))
        assertEquals("_hidden", projectFolderName(".hidden"))
        assertEquals("Untitled", projectFolderName("   "))
        assertEquals("a_b", projectFolderName("a\u0001b"))
        assertEquals("Fira Sans", projectFolderName("  Fira Sans  "))
    }
}
