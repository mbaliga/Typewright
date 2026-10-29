// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.ship

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.project.imports.LicenceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShipFactsTest {
    private val facts = ShipFacts.of(ShipFixtures.completeInfo)

    @Test
    fun namesVersionAndPeopleAreTheProjectsOwn() {
        assertEquals("Hyle Deco", facts.family)
        assertEquals("Regular", facts.style)
        assertEquals("1.024", facts.version)
        assertEquals("A. Designer", facts.designer)
        assertEquals("HyleDeco-Regular", facts.postScriptName)
        assertEquals("HYLE", facts.vendorId)
        assertEquals(1000, facts.unitsPerEm)
    }

    @Test
    fun theFileStemAndFullNameAreMadeFromFamilyAndStyle() {
        assertEquals("HyleDeco-Regular", facts.fileStem)
        assertEquals("Hyle Deco Regular", facts.fullName)
        assertEquals("hyle-deco-gf", facts.folderName)
    }

    @Test
    fun theCopyrightLineGivesTheYearTheHolderAndTheRepositoryAddress() {
        assertEquals(2026, facts.copyrightYear)
        assertEquals("The Hyle Deco Project Authors", facts.copyrightHolder)
        assertEquals("https://github.com/example/hyle-deco", facts.repositoryUrl)
    }

    @Test
    fun aCopyrightLineWithoutAnAddressGivesNone() {
        val info = ShipFixtures.completeInfo.copy(copyright = "Copyright 2020 A. Person")
        val bare = ShipFacts.of(info)
        assertNull(bare.repositoryUrl)
        assertEquals(2020, bare.copyrightYear)
        assertEquals("A. Person", bare.copyrightHolder)
    }

    @Test
    fun aProjectThatStatesNothingGivesNullsNotGuesses() {
        val empty = ShipFacts.of(UfoFontInfo())
        assertNull(empty.family)
        assertNull(empty.style)
        assertNull(empty.version)
        assertNull(empty.designer)
        assertNull(empty.copyright)
        assertNull(empty.copyrightYear)
        assertNull(empty.repositoryUrl)
        assertNull(empty.vendorId)
        assertNull(empty.weight)
        assertNull(empty.fileStem)
        assertNull(empty.fullName)
        assertNull(empty.folderName)
        assertEquals(LicenceKind.NONE, empty.licence)
    }

    @Test
    fun blankFieldsAreNotStated() {
        val blank = ShipFacts.of(UfoFontInfo(familyName = "  ", styleName = "", openTypeNameDesigner = " ", copyright = ""))
        assertNull(blank.family)
        assertNull(blank.style)
        assertNull(blank.designer)
        assertNull(blank.copyright)
    }

    @Test
    fun theLicenceIsReadFromTheProjectsOwnWords() {
        assertEquals(LicenceKind.OFL, facts.licence)
        val own =
            ShipFacts.of(
                ShipFixtures.completeInfo.copy(
                    openTypeNameLicense = "Proprietary. Do not redistribute.",
                    openTypeNameLicenseURL = null,
                    copyright = "Copyright 2026 A",
                ),
            )
        assertEquals(LicenceKind.PROPRIETARY, own.licence)
        assertEquals("Proprietary. Do not redistribute.", own.licenceText)
    }

    @Test
    fun aReservedFontNameIsReadFromTheCopyrightLine() {
        val reserved =
            ShipFacts.of(ShipFixtures.completeInfo.copy(copyright = "Copyright 2026 The Hyle Authors, with Reserved Font Name “Hyle Deco”"))
        assertTrue(reserved.reservedFontNames.isNotEmpty(), "the Reserved Font Name should be found")
        assertTrue(facts.reservedFontNames.isEmpty())
    }

    @Test
    fun theWeightIsTheProjectsClassElseTheStyleNamesWeight() {
        assertEquals(400, facts.weight)
        val bold = ShipFacts.of(UfoFontInfo(familyName = "F", styleName = "Bold"))
        assertEquals(700, bold.weight)
        val classed =
            ShipFacts.of(
                UfoFontInfo(
                    familyName = "F",
                    styleName = "Bold",
                    other =
                        listOf("openTypeOS2WeightClass" to PlistValue.PInteger(650)),
                ),
            )
        assertEquals(650, classed.weight, "a weight the project states beats the one the name stands for")
        assertNull(ShipFacts.of(UfoFontInfo(familyName = "F", styleName = "Wobbly")).weight)
    }

    @Test
    fun anItalicIsFoundInTheStyleName() {
        assertTrue(ShipFacts.of(UfoFontInfo(styleName = "Bold Italic")).italic)
        assertTrue(ShipFacts.of(UfoFontInfo(styleName = "Light Oblique")).italic)
        assertFalse(facts.italic)
    }

    @Test
    fun styleNamesMapToTheWeightsOpenTypeAndGoogleFontsUse() {
        val expected =
            mapOf(
                "Thin" to 100,
                "ExtraLight" to 200,
                "Extra Light" to 200,
                "Light" to 300,
                "Regular" to 400,
                "Italic" to 400,
                "Medium" to 500,
                "SemiBold" to 600,
                "Bold" to 700,
                "Bold Italic" to 700,
                "ExtraBold" to 800,
                "Black" to 900,
            )
        for ((name, weight) in expected) assertEquals(weight, weightOfStyleName(name), name)
        assertNull(weightOfStyleName("Condensed"))
    }

    @Test
    fun aFileStemKeepsNoCharacterAFileNameCannotHold() {
        val odd = ShipFacts.of(UfoFontInfo(familyName = "A/B: C*", styleName = "Re?gular"))
        assertEquals("ABC-Regular", odd.fileStem)
    }

    @Test
    fun aFileStemIsPlainAsciiWhateverTheFamilyIsCalledAndTheFamilyKeepsItsLetters() {
        val accented = ShipFacts.of(UfoFontInfo(familyName = "Hylé Deco", styleName = "Regular"))
        assertEquals("HyleDeco-Regular", accented.fileStem)
        assertEquals("Hylé Deco", accented.family, "the name itself is the person's")
        assertEquals("Hylé Deco Regular", accented.fullName)
        assertEquals("hyle-deco-gf", accented.folderName)
        val styled = ShipFacts.of(UfoFontInfo(familyName = "Julián", styleName = "Bold Itálic"))
        assertEquals("Julian-BoldItalic", styled.fileStem)
        assertNull(ShipFacts.of(UfoFontInfo(familyName = "あいう", styleName = "Regular")).fileStem)
    }

    @Test
    fun theEmbeddingIsStatedOnlyWhenTheProjectHasTheKey() {
        assertEquals(false, ShipFacts.of(UfoFontInfo()).embeddingStated)
        val restricted = UfoFontInfo(other = listOf("openTypeOS2Type" to PlistValue.PArray(listOf(PlistValue.PInteger(1)))))
        assertEquals(true, ShipFacts.of(restricted).embeddingStated)
        val installable = UfoFontInfo(other = listOf("openTypeOS2Type" to PlistValue.PArray(emptyList())))
        assertEquals(true, ShipFacts.of(installable).embeddingStated, "an empty list is still a statement")
        val unrelated = UfoFontInfo(other = listOf("openTypeOS2WeightClass" to PlistValue.PInteger(400)))
        assertEquals(false, ShipFacts.of(unrelated).embeddingStated)
    }
}
