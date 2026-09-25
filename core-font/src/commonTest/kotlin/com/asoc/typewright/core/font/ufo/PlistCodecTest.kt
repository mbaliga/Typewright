// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.ufo

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
    fun dataAndDateValuesReadAndWriteBackUnchanged() {
        // <data> wrapped over lines and indented, as plistlib writes it.
        val xml =
            "<?xml version=\"1.0\"?><plist version=\"1.0\"><dict>" +
                "<key>blob</key><data>\n    AAECAwQF\n    Bgc=\n  </data>" +
                "<key>empty</key><data></data>" +
                "<key>when</key><date>2026-09-25T12:00:00Z</date>" +
                "<key>day</key><date> 2026-09-25Z </date>" +
                "</dict></plist>"
        val expected =
            PlistValue.PDict(
                listOf(
                    "blob" to PlistValue.PData("AAECAwQFBgc="),
                    "empty" to PlistValue.PData(""),
                    "when" to PlistValue.PDate("2026-09-25T12:00:00Z"),
                    "day" to PlistValue.PDate("2026-09-25Z"),
                ),
            )
        val parsed = parsePlistDict(xml)
        assertEquals(expected, parsed)
        val written = writePlist(parsed)
        assertTrue("  <data>AAECAwQFBgc=</data>\n" in written, written)
        assertTrue("  <date>2026-09-25T12:00:00Z</date>\n" in written, written)
        assertEquals(expected, parsePlistDict(written))
        assertEquals(written, writePlist(parsePlistDict(written)))
    }

    @Test
    fun rejectsMalformedDataAndDateValues() {
        fun plist(value: String) = "<?xml version=\"1.0\"?><plist version=\"1.0\"><dict><key>x</key>$value</dict></plist>"
        assertFailsWith<IllegalArgumentException> { parsePlist(plist("<data>AAE</data>")) } // unpadded
        assertFailsWith<IllegalArgumentException> { parsePlist(plist("<data>AA*=</data>")) } // not the alphabet
        assertFailsWith<IllegalArgumentException> { parsePlist(plist("<date>2026-09Z</date>")) } // plistlib needs a day
        assertFailsWith<IllegalArgumentException> { parsePlist(plist("<date>2026-09-25 12:00</date>")) }
        // The types hold only the canonical form, so equal bytes are always an equal value.
        assertFailsWith<IllegalArgumentException> { PlistValue.PData("AAEC Aw==") }
        assertFailsWith<IllegalArgumentException> { PlistValue.PDate("yesterday") }
    }

    @Test
    fun realsAreWrittenAsPythonReprWritesThemOnEveryPlatform() {
        // Python's repr(float), which fontTools uses for every <real>. The JVM's and Wasm's own
        // Double.toString disagree on most of these (1.0E-5 against 0.00001), so pinning the exact
        // text here, in commonTest, fails the build on whichever target drifts.
        val expected =
            listOf(
                0.1 to "0.1",
                0.5 to "0.5",
                -12.5 to "-12.5",
                750.0 to "750.0",
                100.0 to "100.0",
                0.0 to "0.0",
                -0.0 to "-0.0",
                0.039625 to "0.039625",
                0.0001 to "0.0001",
                0.00012345 to "0.00012345",
                1e-5 to "1e-05",
                5e-5 to "5e-05",
                1e-7 to "1e-07",
                1.5e-10 to "1.5e-10",
                -1e-300 to "-1e-300",
                1e7 to "10000000.0",
                12345678.9 to "12345678.9",
                123456789.0 to "123456789.0",
                1e15 to "1000000000000000.0",
                9007199254740993.0 to "9007199254740992.0",
                1e16 to "1e+16",
                1.5e20 to "1.5e+20",
                1e21 to "1e+21",
                1e22 to "1e+22",
                1.0 / 3.0 to "0.3333333333333333",
                0.1 + 0.2 to "0.30000000000000004",
                Double.MIN_VALUE to "5e-324",
                2.2250738585072014e-308 to "2.2250738585072014e-308",
                Double.MAX_VALUE to "1.7976931348623157e+308",
            )
        for ((value, text) in expected) {
            assertEquals(text, formatReal(value), "formatReal($value)")
            assertEquals(value, text.toDouble(), "$text reads back as $value")
            assertTrue("<real>$text</real>" in writePlist(PlistValue.PReal(value)), "writePlist(PReal($value))")
        }
        // Reading Python's exponent form back gives the same value.
        val dict = PlistValue.PDict(listOf("small" to PlistValue.PReal(1e-5), "large" to PlistValue.PReal(1.5e20)))
        assertEquals(dict, parsePlistDict(writePlist(dict)))
    }

    @Test
    fun rejectsNanOrInfiniteReals() {
        assertFailsWith<IllegalArgumentException> { writePlist(PlistValue.PDict(listOf("x" to PlistValue.PReal(Double.NaN)))) }
        assertFailsWith<IllegalArgumentException> {
            writePlist(PlistValue.PDict(listOf("x" to PlistValue.PReal(Double.POSITIVE_INFINITY))))
        }
    }
}
