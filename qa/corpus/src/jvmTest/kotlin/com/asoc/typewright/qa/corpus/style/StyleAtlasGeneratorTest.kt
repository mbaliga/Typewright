// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus.style

import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regenerates data/style-atlas-latin.json when the atlas fonts have been fetched, and skips
 * otherwise (a fresh checkout, CI): the fonts are third-party binaries this repository does not
 * commit. To regenerate:
 *
 *     python3 data/scripts/fetch_style_atlas_fonts.py
 *     ./gradlew :qa:corpus:jvmTest --tests "*StyleAtlasGeneratorTest*"
 *
 * Faces that fail to parse are listed in style-atlas-failures.txt next to the fonts.
 */
class StyleAtlasGeneratorTest {
    @Test
    fun regenerateTheStyleAtlas() {
        val fontsDir = File("build/style-atlas-fonts")
        assumeTrue(
            "SKIPPED StyleAtlasGeneratorTest: ${fontsDir.absolutePath}/manifest.tsv not found; run data/scripts/fetch_style_atlas_fonts.py first.",
            File(fontsDir, "manifest.tsv").exists(),
        )
        val (json, failures) = StyleAtlasGenerator.generate(fontsDir)
        File(fontsDir, "style-atlas-failures.txt").writeText(failures.joinToString("\n"))
        File("../../data/style-atlas-latin.json").writeText(json)
        assertTrue(json.contains("\"classes\""), "the generated pack has no classes")
    }
}
