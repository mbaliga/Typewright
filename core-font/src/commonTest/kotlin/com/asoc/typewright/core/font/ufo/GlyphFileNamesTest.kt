// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.ufo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Examples reproduced from `fontTools.ufoLib.filenames.userNameToFileName`'s own doctring (MIT; see THIRD_PARTY.md). */
class GlyphFileNamesTest {
    @Test
    fun matchesTheReferenceImplementationsDoctringExamples() {
        assertEquals("a", userNameToFileName("a"))
        assertEquals("A_", userNameToFileName("A"))
        assertEquals("A_E_", userNameToFileName("AE"))
        assertEquals("A_e", userNameToFileName("Ae"))
        assertEquals("ae", userNameToFileName("ae"))
        assertEquals("aE_", userNameToFileName("aE"))
        assertEquals("a.alt", userNameToFileName("a.alt"))
        assertEquals("A_.alt", userNameToFileName("A.alt"))
        assertEquals("A_.A_lt", userNameToFileName("A.Alt"))
        assertEquals("A_.aL_t", userNameToFileName("A.aLt"))
        assertEquals("A_.alT_", userNameToFileName("A.alT"))
        assertEquals("T__H_", userNameToFileName("T_H"))
        assertEquals("T__h", userNameToFileName("T_h"))
        assertEquals("t_h", userNameToFileName("t_h"))
        assertEquals("F__F__I_", userNameToFileName("F_F_I"))
        assertEquals("f_f_i", userNameToFileName("f_f_i"))
        assertEquals("A_acute_V_.swash", userNameToFileName("Aacute_V.swash"))
        assertEquals("_notdef", userNameToFileName(".notdef"))
        assertEquals("_con", userNameToFileName("con"))
        assertEquals("C_O_N_", userNameToFileName("CON"))
        assertEquals("_con.alt", userNameToFileName("con.alt"))
        assertEquals("alt._con", userNameToFileName("alt.con"))
    }

    @Test
    fun appendsTheSuffix() {
        assertEquals("A_.glif", userNameToFileName("A", suffix = ".glif"))
        assertEquals("space.glif", userNameToFileName("space", suffix = ".glif"))
    }

    @Test
    fun resolvesACaseInsensitiveClashWithAnExistingName() {
        val existing = setOf("a_.glif")
        val name = userNameToFileName("A", existing, suffix = ".glif")
        assertTrue(name != "A_.glif")
        assertEquals(false, existing.contains(name.lowercase()))
    }

    @Test
    fun handleFileNameClash1AppendsAZeroPaddedCounter() {
        val prefix = "0".repeat(5) + "."
        val suffix = "." + "0".repeat(10)
        val existing = setOf("a".repeat(5))
        val name = handleFileNameClash1("A".repeat(5), existing, prefix, suffix)
        assertEquals("00000.AAAAA000000000000001.0000000000", name)
    }

    @Test
    fun handleFileNameClash1SkipsAnAlreadyTakenCounter() {
        val prefix = "0".repeat(5) + "."
        val suffix = "." + "0".repeat(10)
        val existing = setOf("a".repeat(5), prefix + "aaaaa" + "1".padStart(15, '0') + suffix)
        val name = handleFileNameClash1("A".repeat(5), existing, prefix, suffix)
        assertEquals("00000.AAAAA000000000000002.0000000000", name)
    }

    @Test
    fun handleFileNameClash2FindsTheFirstUnusedCounter() {
        val prefix = "0".repeat(5) + "."
        val suffix = "." + "0".repeat(10)
        val existing = (0 until 100).map { prefix + it + suffix }.toSet()
        assertEquals("00000.100.0000000000", handleFileNameClash2(existing, prefix, suffix))
    }

    @Test
    fun rejectsAnEmptyGlyphName() {
        assertFailsWith<IllegalArgumentException> { userNameToFileName("") }
    }
}
