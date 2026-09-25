// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.scripts.arabic

import com.asoc.typewright.scripts.WritingScript
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArabicControlCharactersTest {
    @Test
    fun hasExactlyTheFourBehDotlessFormsTheResearchNamesNoInventedExtras() {
        assertEquals(
            listOf("behDotless-ar", "behDotless-ar.init", "behDotless-ar.medi", "behDotless-ar.fina"),
            ArabicControlCharacters.controlCharacters.map { it.glyphName },
        )
    }

    @Test
    fun everyEntryHasANonBlankRationale() {
        for (entry in ArabicControlCharacters.controlCharacters) {
            assertTrue(entry.rationale.isNotBlank(), "${entry.glyphName} should have a rationale")
        }
    }

    @Test
    fun everyRationaleNamesTheSourcedSkeletonPoint() {
        for (entry in ArabicControlCharacters.controlCharacters) {
            assertTrue("behDotless-ar" in entry.rationale, entry.rationale)
        }
    }

    @Test
    fun behDotlessArIsNotAGlyphInventoryMember() {
        val inventoryNames =
            ArabicGlyphInventory
                .build()
                .glyphs
                .map { it.name }
                .toSet()
        for (entry in ArabicControlCharacters.controlCharacters) {
            assertTrue(entry.glyphName !in inventoryNames, "${entry.glyphName} should not be a base-inventory member")
        }
    }

    @Test
    fun scriptIsArabicNaskh() {
        assertEquals(WritingScript.ARABIC_NASKH, ArabicControlCharacters.build().script)
    }

    @Test
    fun buildIsPureAndDeterministic() {
        assertEquals(ArabicControlCharacters.build(), ArabicControlCharacters.build())
    }
}
