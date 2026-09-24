package dev.aarso.typewright.core.font.ufo

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
