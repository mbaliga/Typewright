// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.arabic

import dev.aarso.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArabicGlyphInventoryTest {
    @Test
    fun hasFortyNineIsolatedGlyphsMatchingTheZipsReal49Templates() {
        assertEquals(49, ArabicGlyphInventory.isolatedGlyphs.size)
    }

    @Test
    fun hasEightyFourDerivedPositionalGlyphsTwentyEightDualJoiningLettersTimesThree() {
        assertEquals(84, ArabicGlyphInventory.derivedPositionalGlyphs.size)
    }

    @Test
    fun totalInventoryIsOneHundredThirtyThreeGlyphs() {
        assertEquals(133, ArabicGlyphInventory.build().glyphs.size)
        assertEquals(
            ArabicGlyphInventory.isolatedGlyphs.size + ArabicGlyphInventory.derivedPositionalGlyphs.size,
            ArabicGlyphInventory.build().glyphs.size,
        )
    }

    @Test
    fun everyGlyphNameContainsTheArSuffix() {
        for (glyph in ArabicGlyphInventory.build().glyphs) {
            assertTrue("-ar" in glyph.name, "${glyph.name} should contain -ar")
        }
    }

    @Test
    fun isolatedFormsCarryNoPositionalSuffix() {
        for (glyph in ArabicGlyphInventory.isolatedGlyphs) {
            assertTrue(glyph.name.endsWith("-ar"), "${glyph.name} should end exactly -ar, no positional suffix")
            assertTrue(".init" !in glyph.name && ".medi" !in glyph.name && ".fina" !in glyph.name, glyph.name)
        }
    }

    @Test
    fun everyGlyphNameIsUnique() {
        val names = ArabicGlyphInventory.build().glyphs.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun isolatedGlyphsHaveARealUnicodeNameAndCodepoint() {
        for (glyph in ArabicGlyphInventory.isolatedGlyphs) {
            assertTrue(glyph.unicodeName != null, "${glyph.name} should have a Unicode name")
            assertTrue(glyph.codepoint != null, "${glyph.name} should have a codepoint")
        }
    }

    @Test
    fun derivedPositionalGlyphsHaveNoUnicodeNameOrCodepoint() {
        for (glyph in ArabicGlyphInventory.derivedPositionalGlyphs) {
            assertNull(glyph.unicodeName, "${glyph.name} is derived; should have no owning Unicode name")
            assertNull(glyph.codepoint, "${glyph.name} is derived; should have no owning codepoint")
        }
    }

    @Test
    fun everyDualJoiningLetterHasExactlyFourFormsIsolatedInitMediFina() {
        val byBase =
            ArabicGlyphInventory
                .build()
                .glyphs
                .map { it.name }
                .groupBy { it.substringBefore('.') }
        val dualJoiningIsolatedNames =
            ArabicLetters.letters
                .filter { it.joiningType == ArabicJoiningType.DUAL_JOINING }
                .map { ArabicGlyphInventory.isolatedGlyphName(it.baseName) }
        for (isolatedName in dualJoiningIsolatedNames) {
            val forms = byBase.getValue(isolatedName).toSet()
            assertEquals(
                setOf(isolatedName, "$isolatedName.init", "$isolatedName.medi", "$isolatedName.fina"),
                forms,
                "$isolatedName should have exactly isolated + init + medi + fina",
            )
        }
    }

    @Test
    fun rightJoiningAndNonJoiningLettersHaveOnlyAnIsolatedFormNoPositionalVariants() {
        val allNames =
            ArabicGlyphInventory
                .build()
                .glyphs
                .map { it.name }
                .toSet()
        val nonDualLetters = ArabicLetters.letters.filter { it.joiningType != ArabicJoiningType.DUAL_JOINING }
        assertEquals(11, nonDualLetters.size) // 10 right-joining + 1 non-joining
        for (letter in nonDualLetters) {
            val isolatedName = ArabicGlyphInventory.isolatedGlyphName(letter.baseName)
            assertTrue(isolatedName in allNames)
            assertTrue("$isolatedName.init" !in allNames, "$isolatedName should have no .init")
            assertTrue("$isolatedName.medi" !in allNames, "$isolatedName should have no .medi")
            assertTrue("$isolatedName.fina" !in allNames, "$isolatedName should have no .fina")
        }
    }

    @Test
    fun digitsHaveOnlyAnIsolatedFormNoPositionalVariants() {
        val allNames =
            ArabicGlyphInventory
                .build()
                .glyphs
                .map { it.name }
                .toSet()
        for (digit in ArabicLetters.digits) {
            val isolatedName = ArabicGlyphInventory.isolatedGlyphName(digit.baseName)
            assertTrue(isolatedName in allNames)
            assertTrue("$isolatedName.init" !in allNames)
        }
    }

    @Test
    fun positionalFormsPreserveTheDocumentedShapingOrderInitMediFina() {
        val forms = ArabicGlyphInventory.positionalGlyphNames("beh")
        assertEquals(listOf("beh-ar.init", "beh-ar.medi", "beh-ar.fina"), forms)
    }

    @Test
    fun isolatedGlyphNameAppendsArSuffixOnly() {
        assertEquals("alef-ar", ArabicGlyphInventory.isolatedGlyphName("alef"))
    }

    @Test
    fun scriptIsArabicNaskh() {
        assertEquals(WritingScript.ARABIC_NASKH, ArabicGlyphInventory.build().script)
    }

    @Test
    fun buildIsPureAndDeterministic() {
        assertEquals(ArabicGlyphInventory.build(), ArabicGlyphInventory.build())
    }
}
