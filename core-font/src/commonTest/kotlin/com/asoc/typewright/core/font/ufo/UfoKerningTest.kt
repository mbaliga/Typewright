// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.ufo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UfoKerningTest {
    @Test
    fun writesAndReadsGroupsLosslessly() {
        val groups =
            mapOf(
                "public.kern1.A" to listOf("A", "Aacute", "Acircumflex"),
                "public.kern2.O" to listOf("O", "Odieresis"),
                "Group1" to listOf("A", "A.alt"),
            )
        val xml = writeGroupsPlist(groups)
        assertEquals(groups, readGroupsPlist(xml))
    }

    @Test
    fun anEmptyGroupsMapWritesAnEmptyDict() {
        val xml = writeGroupsPlist(emptyMap())
        assertEquals(emptyMap(), readGroupsPlist(xml))
    }

    @Test
    fun readGroupsPlistRejectsANonArrayValue() {
        val xml = writePlist(PlistValue.PDict(listOf("Group1" to PlistValue.PString("A"))))
        assertFailsWith<IllegalArgumentException> { readGroupsPlist(xml) }
    }

    @Test
    fun readGroupsPlistRejectsANonStringMember() {
        val xml = writePlist(PlistValue.PDict(listOf("Group1" to PlistValue.PArray(listOf(PlistValue.PInteger(1))))))
        assertFailsWith<IllegalArgumentException> { readGroupsPlist(xml) }
    }

    @Test
    fun matchesTheUfo3SpecsOwnGroupsPlistExample() {
        // unifiedfontobject.org/versions/ufo3/groups.plist -- the spec's own example document.
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple Computer//DTD PLIST 1.0//EN"
            "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>public.kern1.A</key>
              <array>
                <string>A</string>
                <string>Aacute</string>
                <string>Acircumflex</string>
              </array>
              <key>Group1</key>
              <array>
                <string>A</string>
                <string>A.alt</string>
              </array>
            </dict>
            </plist>
            """.trimIndent()
        val groups = readGroupsPlist(xml)
        assertEquals(
            mapOf(
                "public.kern1.A" to listOf("A", "Aacute", "Acircumflex"),
                "Group1" to listOf("A", "A.alt"),
            ),
            groups,
        )
        // What this codec writes back for that same data reads back to the same map (this
        // writer's own formatting need not match the spec's byte-for-byte, only its shape).
        assertEquals(groups, readGroupsPlist(writeGroupsPlist(groups)))
    }

    @Test
    fun writesAndReadsKerningLosslessly() {
        val kerning =
            mapOf(
                "public.kern1.A" to mapOf("public.kern2.O" to 7.0, "V" to -25.0),
                "A" to mapOf("V" to -18.5),
            )
        val xml = writeKerningPlist(kerning)
        assertEquals(kerning, readKerningPlist(xml))
    }

    @Test
    fun writesWholeKerningValuesAsIntegerAndFractionalOnesAsReal() {
        val xml = writeKerningPlist(mapOf("A" to mapOf("V" to -25.0, "W" to -18.5)))
        assertTrue(xml.contains("<integer>-25</integer>"))
        assertTrue(xml.contains("<real>-18.5</real>"))
    }

    @Test
    fun readKerningPlistRejectsANonDictSecondLevelValue() {
        val xml = writePlist(PlistValue.PDict(listOf("A" to PlistValue.PInteger(5))))
        assertFailsWith<IllegalArgumentException> { readKerningPlist(xml) }
    }

    @Test
    fun readKerningPlistRejectsANonNumericValue() {
        val xml = writePlist(PlistValue.PDict(listOf("A" to PlistValue.PDict(listOf("V" to PlistValue.PString("nope"))))))
        assertFailsWith<IllegalArgumentException> { readKerningPlist(xml) }
    }

    @Test
    fun matchesTheUfo3SpecsOwnKerningPlistExample() {
        // unifiedfontobject.org/versions/ufo3/kerning.plist -- the spec's own example document.
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple Computer//DTD PLIST 1.0//EN"
            "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
              <key>public.kern1.BGroup</key>
              <dict>
                <key>public.kern2.CGroup</key>
                <integer>7</integer>
                <key>A</key>
                <integer>5</integer>
              </dict>
              <key>A</key>
              <dict>
                <key>public.kern2.CGroup</key>
                <integer>3</integer>
                <key>B</key>
                <integer>2</integer>
              </dict>
            </dict>
            </plist>
            """.trimIndent()
        val kerning = readKerningPlist(xml)
        assertEquals(
            mapOf(
                "public.kern1.BGroup" to mapOf("public.kern2.CGroup" to 7.0, "A" to 5.0),
                "A" to mapOf("public.kern2.CGroup" to 3.0, "B" to 2.0),
            ),
            kerning,
        )
        assertEquals(kerning, readKerningPlist(writeKerningPlist(kerning)))
    }
}
