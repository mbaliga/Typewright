// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.font.ufo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlistCodecTest {
    @Test
    fun writesThenParsesADictOfEveryScalarKind() {
        val dict =
            PlistValue.PDict(
                listOf(
                    "name" to PlistValue.PString("Hyle Deco"),
                    "count" to PlistValue.PInteger(338),
                    "angle" to PlistValue.PReal(-2.5),
                    "monospace" to PlistValue.PBoolean(false),
                    "roundTrip" to PlistValue.PBoolean(true),
                ),
            )
        val xml = writePlist(dict)
        assertTrue(xml.startsWith("<?xml"))
        val parsed = parsePlistDict(xml)
        assertEquals(dict, parsed)
    }

    @Test
    fun writesThenParsesNestedArraysAndDicts() {
        val value =
            PlistValue.PDict(
                listOf(
                    "layers" to
                        PlistValue.PArray(
                            listOf(
                                PlistValue.PArray(listOf(PlistValue.PString("public.default"), PlistValue.PString("glyphs"))),
                            ),
                        ),
                ),
            )
        val xml = writePlist(value)
        assertEquals(value, parsePlistDict(xml))
    }

    @Test
    fun parsesAnArrayRootedPlist() {
        val root = PlistValue.PArray(listOf(PlistValue.PString("a"), PlistValue.PString("b")))
        val xml = writePlist(root)
        assertEquals(root, parsePlist(xml))
    }

    @Test
    fun parsePlistDictRejectsAnArrayRoot() {
        val xml = writePlist(PlistValue.PArray(listOf(PlistValue.PString("a"))))
        assertFailsWith<IllegalArgumentException> { parsePlistDict(xml) }
    }

    @Test
    fun writingIsByteStable() {
        val dict = PlistValue.PDict(listOf("a" to PlistValue.PInteger(1), "b" to PlistValue.PString("x")))
        assertEquals(writePlist(dict), writePlist(dict))
    }

    @Test
    fun escapesTextInStringsAndKeys() {
        val dict = PlistValue.PDict(listOf("a & b" to PlistValue.PString("<value>")))
        val xml = writePlist(dict)
        assertTrue(xml.contains("a &amp; b"))
        assertTrue(xml.contains("&lt;value&gt;"))
        assertEquals(dict, parsePlistDict(xml))
    }

    @Test
    fun handlesAnEmptyDict() {
        val dict = PlistValue.PDict(emptyList())
        assertEquals(dict, parsePlistDict(writePlist(dict)))
    }

    @Test
    fun handlesAnEmptyArray() {
        val root = PlistValue.PArray(emptyList())
        assertEquals(root, parsePlist(writePlist(root)))
    }

    @Test
    fun dictGetFindsAKeyByName() {
        val dict = PlistValue.PDict(listOf("a" to PlistValue.PInteger(1), "b" to PlistValue.PInteger(2)))
        assertEquals(PlistValue.PInteger(2), dict["b"])
        assertEquals(null, dict["c"])
    }

    @Test
    fun typedAccessorsReadEachKind() {
        val dict =
            PlistValue.PDict(
                listOf(
                    "s" to PlistValue.PString("hi"),
                    "i" to PlistValue.PInteger(5),
                    "r" to PlistValue.PReal(2.5),
                    "wholeAsInteger" to PlistValue.PInteger(7),
                    "arr" to PlistValue.PArray(listOf(PlistValue.PString("x"))),
                ),
            )
        assertEquals("hi", dict.stringOrNull("s"))
        assertEquals(5L, dict.longOrNull("i"))
        assertEquals(5, dict.intOrNull("i"))
        assertEquals(2.5, dict.doubleOrNull("r"))
        assertEquals(7.0, dict.doubleOrNull("wholeAsInteger"))
        assertEquals(1, dict.arrayOrNull("arr")?.items?.size)
        assertEquals(null, dict.stringOrNull("missing"))
        assertEquals(null, dict.stringOrNull("i")) // wrong kind
    }

    @Test
    fun rejectsANonPlistRootElement() {
        assertFailsWith<IllegalArgumentException> { parsePlist("<notaplist/>") }
    }

    @Test
    fun rejectsUnsupportedDataAndDateValues() {
        val xml =
            "<?xml version=\"1.0\"?><plist version=\"1.0\"><dict><key>x</key><data>AAAA</data></dict></plist>"
        assertFailsWith<IllegalArgumentException> { parsePlist(xml) }
    }

    @Test
    fun rejectsNanOrInfiniteReals() {
        assertFailsWith<IllegalArgumentException> { writePlist(PlistValue.PDict(listOf("x" to PlistValue.PReal(Double.NaN)))) }
        assertFailsWith<IllegalArgumentException> {
            writePlist(PlistValue.PDict(listOf("x" to PlistValue.PReal(Double.POSITIVE_INFINITY))))
        }
    }
}
