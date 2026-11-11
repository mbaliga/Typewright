// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.ufo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XmlTextTest {
    @Test
    fun escapesTextContent() {
        assertEquals("a &amp; b &lt;c&gt; d", escapeXmlText("a & b <c> d"))
    }

    @Test
    fun leavesPlainTextAndNewlinesAlone() {
        assertEquals("plain text\nwith a newline", escapeXmlText("plain text\nwith a newline"))
    }

    @Test
    fun escapesAttributeValues() {
        // '>' is not escaped: it is only ever required inside "]]>", which cannot occur in an
        // attribute value, and escapeXmlAttribute does not attempt it (matching its own KDoc).
        assertEquals("a &amp; &lt;b&quot;c&quot;>", escapeXmlAttribute("a & <b\"c\">"))
    }

    @Test
    fun escapesControlCharactersInAttributes() {
        assertEquals("a&#10;b&#13;c&#9;d", escapeXmlAttribute("a\nb\rc\td"))
    }

    @Test
    fun dropsCharactersXmlCannotCarry() {
        // NUL, other C0 controls, U+FFFE/U+FFFF and a lone surrogate go; tab, newline, carriage
        // return and a real surrogate pair (U+1F600) stay.
        val dirty = "a\u0000b\u0001c\u000Bd\u001Fe\uFFFEf\uFFFFg\uD800h\uDC00i\tj\nk\rl\uD83D\uDE00"
        assertEquals("abcdefghi\tj\nk\rl\uD83D\uDE00", withoutXmlIllegalCharacters(dirty))
    }

    @Test
    fun cleanTextIsReturnedAsTheSameInstance() {
        val clean = "Copyright 2026 The Hyle Deco Project Authors (https://example.org)"
        assertTrue(clean === withoutXmlIllegalCharacters(clean))
    }

    @Test
    fun theWritersDropIllegalCharactersToo() {
        assertEquals("WEI", escapeXmlText("WEI\u0000"))
        assertEquals("a&quot;b", escapeXmlAttribute("a\u0000\"b\u0002"))
    }

    @Test
    fun carriageReturnsInTextSurviveAsCharacterReferences() {
        assertEquals("one&#13;&#13;two\nthree", escapeXmlText("one\r\rtwo\nthree"))
    }

    @Test
    fun aStringWithAControlCharacterWritesAPlistAParserReadsBack() {
        val text = "Work Sans\u0000 vendor"
        val plist = writePlist(PlistValue.PDict(listOf("k" to PlistValue.PString(text))))
        assertTrue('\u0000' !in plist)
        val read = parsePlistDict(plist).entries.single().second as PlistValue.PString
        assertEquals("Work Sans vendor", read.value)
    }

    @Test
    fun aCarriageReturnInAPlistStringReadsBackAsACarriageReturn() {
        val plist = writePlist(PlistValue.PDict(listOf("k" to PlistValue.PString("a\r\nb"))))
        val read = parsePlistDict(plist).entries.single().second as PlistValue.PString
        assertEquals("a\r\nb", read.value)
    }
}
