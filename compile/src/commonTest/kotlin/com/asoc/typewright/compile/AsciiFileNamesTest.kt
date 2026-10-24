// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.parsePlistDict
import com.asoc.typewright.core.font.ufo.writePlist
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The copy fontmake reads has plain ASCII file names however the project's glyphs are named. */
class AsciiFileNamesTest {
    private fun contents(vararg entries: Pair<String, String>): ByteArray =
        writePlist(PlistValue.PDict(entries.map { (glyph, file) -> glyph to PlistValue.PString(file) })).encodeToByteArray()

    private fun ufo(vararg glyphFiles: Pair<String, String>): Map<String, ByteArray> =
        linkedMapOf<String, ByteArray>().apply {
            put("metainfo.plist", "meta".encodeToByteArray())
            put("glyphs/contents.plist", contents(*glyphFiles))
            for ((glyph, file) in glyphFiles) put("glyphs/$file", "outline of $glyph".encodeToByteArray())
        }

    private fun mapping(files: Map<String, ByteArray>): Map<String, String> =
        parsePlistDict(files.getValue("glyphs/contents.plist").decodeToString()).entries.associate { (k, v) ->
            k to
                (v as PlistValue.PString).value
        }

    @Test
    fun aUfoWhoseFileNamesAreAlreadyAsciiIsReturnedAsItIs() {
        val files = ufo("A" to "A_.glif", "b" to "b.glif")
        val safe = withAsciiFileNames(files)
        assertEquals(files.keys.toList(), safe.keys.toList())
        assertSame(files.getValue("glyphs/contents.plist"), safe.getValue("glyphs/contents.plist"), "an untouched file is the same bytes")
    }

    @Test
    fun everyNonAsciiGlyphFileIsRenamedAndContentsPointsAtTheNewName() {
        val files = ufo("é" to "é.glif", "あ" to "あ.glif", "A" to "A_.glif")
        val safe = withAsciiFileNames(files)

        assertTrue(safe.keys.all { isPlainAscii(it) }, safe.keys.toString())
        val names = mapping(safe)
        // Two different glyphs keep two different files, and each file is the glyph's own outline.
        assertEquals(3, names.values.toSet().size)
        assertEquals("A_.glif", names.getValue("A"), "a file that was already ASCII keeps its name")
        assertContentEquals("outline of é".encodeToByteArray(), safe.getValue("glyphs/${names.getValue("é")}"))
        assertContentEquals("outline of あ".encodeToByteArray(), safe.getValue("glyphs/${names.getValue("あ")}"))
        // The glyph names themselves are not touched: they are inside the files and in contents.plist, as the project has them.
        assertEquals(setOf("é", "あ", "A"), names.keys)
        assertNull(safe["glyphs/é.glif"], "the old file is gone")
    }

    @Test
    fun aNewNameNeverTakesAFileThatIsAlreadyThere() {
        // A project that already has a glyph file with the first name the renamer would pick (compared without regard to case).
        val files = ufo("x" to "Typewright-Glyph-1.glif", "é" to "é.glif")
        val names = mapping(withAsciiFileNames(files))
        assertEquals("Typewright-Glyph-1.glif", names.getValue("x"))
        assertEquals("typewright-glyph-2.glif", names.getValue("é"))
    }

    @Test
    fun otherFilesAndALayerWithNoGlyphsAreLeftAlone() {
        val files = ufo("é" to "é.glif") + mapOf("features.fea" to "feature kern;".encodeToByteArray())
        val safe = withAsciiFileNames(files)
        assertContentEquals("feature kern;".encodeToByteArray(), safe.getValue("features.fea"))
        assertContentEquals("meta".encodeToByteArray(), safe.getValue("metainfo.plist"))
        // Something that is not a plist is not rewritten, so fontmake reports it in its own words.
        val broken = mapOf("glyphs/contents.plist" to "not a plist".encodeToByteArray(), "glyphs/é.glif" to byteArrayOf(1))
        assertEquals(broken.keys, withAsciiFileNames(broken).keys)
    }

    @Test
    fun plainAsciiMeansPrintableAsciiOnly() {
        assertTrue(isPlainAscii("HyleDeco-Regular.ufo"))
        assertTrue(isPlainAscii("with space.glif"))
        assertFalse(isPlainAscii("Hylé.ufo"))
        assertFalse(isPlainAscii("tab\there"))
        assertFalse(isPlainAscii("あ"))
    }
}
