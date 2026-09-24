package dev.aarso.typewright.core.font.sfnt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StandardMacGlyphNamesTest {
    @Test
    fun hasExactlyTheSpecsTwoHundredAndFiftyEightNames() {
        assertEquals(258, STANDARD_MAC_GLYPH_NAMES.size)
    }

    @Test
    fun startsAndIndexesAsTheSpecDefines() {
        assertEquals(".notdef", STANDARD_MAC_GLYPH_NAMES[0])
        assertEquals(".null", STANDARD_MAC_GLYPH_NAMES[1])
        assertEquals("space", STANDARD_MAC_GLYPH_NAMES[3])
        assertEquals("A", STANDARD_MAC_GLYPH_NAMES[36])
        assertEquals("a", STANDARD_MAC_GLYPH_NAMES[68])
        assertEquals("dcroat", STANDARD_MAC_GLYPH_NAMES[257])
    }

    @Test
    fun everyNameIsUnique() {
        assertEquals(STANDARD_MAC_GLYPH_NAMES.size, STANDARD_MAC_GLYPH_NAMES.toSet().size)
        assertTrue(STANDARD_MAC_GLYPH_NAMES.all { it.isNotEmpty() })
    }
}
