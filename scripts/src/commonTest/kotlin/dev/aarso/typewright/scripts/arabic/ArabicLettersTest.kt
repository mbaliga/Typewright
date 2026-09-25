// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.arabic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArabicLettersTest {
    @Test
    fun hasThirtyNineLettersAndTenDigitsMatchingTheZipsReal49Templates() {
        assertEquals(39, ArabicLetters.letters.size)
        assertEquals(10, ArabicLetters.digits.size)
        assertEquals(49, ArabicLetters.letters.size + ArabicLetters.digits.size)
    }

    @Test
    fun hasTwentyEightDualJoiningTenRightJoiningAndOneNonJoiningLetter() {
        val byType = ArabicLetters.letters.groupingBy { it.joiningType }.eachCount()
        assertEquals(28, byType[ArabicJoiningType.DUAL_JOINING])
        assertEquals(10, byType[ArabicJoiningType.RIGHT_JOINING])
        assertEquals(1, byType[ArabicJoiningType.NON_JOINING])
    }

    @Test
    fun hamzaIsTheOneNonJoiningLetter() {
        val nonJoining = ArabicLetters.letters.filter { it.joiningType == ArabicJoiningType.NON_JOINING }
        assertEquals(listOf("hamza"), nonJoining.map { it.baseName })
    }

    @Test
    fun everyLetterBaseNameIsUnique() {
        val names = ArabicLetters.letters.map { it.baseName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun everyDigitBaseNameIsUnique() {
        val names = ArabicLetters.digits.map { it.baseName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun everyLetterCodepointIsInTheArabicOrArabicExtendedBlocks() {
        for (letter in ArabicLetters.letters) {
            assertTrue(
                letter.codepoint in 0x0600..0x06FF,
                "${letter.baseName}: codepoint U+${letter.codepoint.toString(16)} should be in the Arabic block",
            )
        }
    }

    @Test
    fun everyDigitCodepointIsAnArabicIndicDigit() {
        for (digit in ArabicLetters.digits) {
            assertTrue(
                digit.codepoint in 0x0660..0x0669,
                "${digit.baseName}: codepoint U+${digit.codepoint.toString(16)} should be an Arabic-Indic digit",
            )
        }
    }

    @Test
    fun aKnownCodepointAndJoiningTypeAreCorrect() {
        val alef = ArabicLetters.letters.first { it.baseName == "alef" }
        assertEquals(0x0627, alef.codepoint)
        assertEquals(ArabicJoiningType.RIGHT_JOINING, alef.joiningType)

        val beh = ArabicLetters.letters.first { it.baseName == "beh" }
        assertEquals(0x0628, beh.codepoint)
        assertEquals(ArabicJoiningType.DUAL_JOINING, beh.joiningType)
    }

    @Test
    fun everyTemplateFileNameStartsWithItsThreeDigitZipIndexAndIsUnique() {
        val all = ArabicLetters.letters.map { it.templateFileName } + ArabicLetters.digits.map { it.templateFileName }
        assertEquals(all.size, all.toSet().size)
        for ((i, fileName) in all.withIndex()) {
            val expectedIndex = i.toString().padStart(3, '0')
            assertTrue(fileName.startsWith("${expectedIndex}_"), "entry $i ($fileName) should start with $expectedIndex\\_")
        }
    }

    @Test
    fun everyLetterUnicodeNameStartsWithArabicLetter() {
        for (letter in ArabicLetters.letters) {
            assertTrue(letter.unicodeName.startsWith("ARABIC LETTER "), letter.unicodeName)
        }
    }

    @Test
    fun everyDigitUnicodeNameStartsWithArabicIndicDigit() {
        for (digit in ArabicLetters.digits) {
            assertTrue(digit.unicodeName.startsWith("ARABIC-INDIC DIGIT "), digit.unicodeName)
        }
    }
}
