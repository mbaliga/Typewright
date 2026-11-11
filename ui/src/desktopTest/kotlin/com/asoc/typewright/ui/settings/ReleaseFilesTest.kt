// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** The generated constants and notices in the UI match the files they come from, so About never shows a stale version or notice. */
class ReleaseFilesTest {
    private fun repoFile(path: String): File {
        val root = System.getProperty("typewright.repoRoot") ?: fail("system property typewright.repoRoot is not set by the build")
        return File(root, path).also { assertTrue(it.isFile, "$path is missing") }
    }

    private fun versionProperties(): Map<String, String> =
        repoFile("version.properties")
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }

    @Test
    fun theAppVersionIsTheOneInTheVersionFile() {
        val properties = versionProperties()
        assertEquals(properties["VERSION_NAME"], APP_VERSION)
        assertEquals(properties["VERSION_CODE"]?.toInt(), APP_VERSION_CODE)
    }

    @Test
    fun aboutCarriesTheSkiaFreeTypeAndLibpngNoticesAndTheEngineAndApplicationLicences() {
        val text = NOTICE_SECTIONS.flatMap { it.lines + it.title }.joinToString("\n")
        for (name in listOf("Skia", "FreeType", "libpng", "HarfBuzz", "zlib", "Expat", "libjpeg-turbo", "libwebp", "ICU")) {
            assertTrue(name in text, "About's notices do not mention $name")
        }
        assertTrue("FSL-1.1-ALv2" in LICENCE_SENTENCE && "Apache-2.0" in LICENCE_SENTENCE)
    }

    @Test
    fun everyHeadingOfThirdPartyMdIsASectionOfTheNotices() {
        val headings = repoFile("THIRD_PARTY.md").readLines().filter { it.startsWith("## ") }.map { it.removePrefix("## ").trim() }
        assertTrue(headings.isNotEmpty())
        assertEquals(headings, NOTICE_SECTIONS.map { it.title })
    }
}
