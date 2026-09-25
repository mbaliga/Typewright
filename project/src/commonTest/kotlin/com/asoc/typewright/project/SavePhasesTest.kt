// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlin.test.Test
import kotlin.test.assertEquals

/** [SavePhases]: leaf files, then UFO index files, then `typewright.json` last. Runs on the JVM and Wasm. */
class SavePhasesTest {
    @Test
    fun ordersLeavesBeforeIndexFilesBeforeTheManifest() {
        val paths =
            setOf(
                "typewright.json",
                "Hyle.ufo/fontinfo.plist",
                "Hyle.ufo/glyphs/T_.glif",
                "Hyle.ufo/glyphs/contents.plist",
                "locks/regular/T_.glif",
                "scrapbook/manifest.json",
            )
        val ordered = SavePhases.order(paths)
        assertEquals(paths.size, ordered.size)
        assertEquals("typewright.json", ordered.last())
        val indexOfContents = ordered.indexOf("Hyle.ufo/glyphs/contents.plist")
        val indexOfGlif = ordered.indexOf("Hyle.ufo/glyphs/T_.glif")
        assertEquals(true, indexOfGlif < indexOfContents)
        val indexOfFontInfo = ordered.indexOf("Hyle.ufo/fontinfo.plist")
        assertEquals(true, indexOfGlif < indexOfFontInfo)
    }

    @Test
    fun sortsWithinEachPhase() {
        val ordered = SavePhases.order(setOf("b/glyphs/z.glif", "a/glyphs/a.glif"))
        assertEquals(listOf("a/glyphs/a.glif", "b/glyphs/z.glif"), ordered)
    }

    @Test
    fun aPathWithNoManifestOrder() {
        assertEquals(emptyList(), SavePhases.order(emptySet()))
    }
}
