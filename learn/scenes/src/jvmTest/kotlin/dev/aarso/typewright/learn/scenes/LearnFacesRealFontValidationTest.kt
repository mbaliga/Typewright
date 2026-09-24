package dev.aarso.typewright.learn.scenes

import dev.aarso.typewright.core.font.sfnt.readSfntFont
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * P6 (Learn faces fetch): proves the font files `data/scripts/fetch_learn_faces.py` wrote under
 * `data/learn-faces/` (manifest at `data/learn-faces/manifest.json`) are real, loadable
 * TrueType-outline fonts -- not HTML error pages or truncated downloads -- by running each one
 * through `core-font`'s own production reader ([readSfntFont]), the same reader every other
 * sfnt in this codebase goes through and the one the Learn scene renderer will use to draw these
 * faces. This is JVM-only (`jvmTest`, not `commonTest`) purely because it touches the filesystem
 * with `java.io.File`; `core-font` itself stays platform-free (CLAUDE.md law 2).
 *
 * Unlike `qa/corpus`'s `StyleDetectorRealFontValidationTest` (which skips when its fonts are
 * absent, because those binaries are deliberately *not* committed), this test always runs: the
 * Learn faces *are* committed, per docs/LESSONS_SCAFFOLD.md's "every example is a real font
 * loaded at build time" and this repository's own `data/` convention for checked-in,
 * generator-produced assets. A missing or corrupt face here is a real regression, not an
 * expected fresh-checkout state.
 *
 * The file list below is every face `fetch_learn_faces.py`'s `FACES` table names (the ten
 * Lineages on-stage faces plus the seven exercise-bank faces), hand-kept in sync with it rather
 * than parsed from `manifest.json` -- this module has no JSON dependency yet, and duplicating a
 * short, stable filename list here is cheaper than adding one for a test.
 */
class LearnFacesRealFontValidationTest {
    @Test
    fun everyFetchedFaceParsesAsARealSfntFontWithLatinCoverage() {
        val dataDir = findLearnFacesDir()
        assertNotNull(
            dataDir,
            "could not find data/learn-faces/ above ${File(".").absolutePath} -- " +
                "run data/scripts/fetch_learn_faces.py from the repository root first",
        )

        var checked = 0
        for ((key, relativePath) in FACE_FILES) {
            val file = File(dataDir, relativePath)
            assertTrue(
                file.exists(),
                "$key: missing ${file.path} -- re-run data/scripts/fetch_learn_faces.py",
            )

            val bytes = file.readBytes()
            assertTrue(
                bytes.size > 3_000,
                "$key: ${file.path} is too small (${bytes.size} bytes) to be a real webfont",
            )

            val font = readSfntFont(bytes)
            assertTrue(font.numGlyphs > 0, "$key: ${file.path} parsed but has 0 glyphs")

            // A Lineages scene crossfades the sample "ago" (docs/LESSONS_SCAFFOLD.md section 2);
            // every face must at least cover those three letters.
            for (ch in "ago") {
                val gid = font.glyphIdForCodePoint(ch.code)
                assertNotNull(gid, "$key: ${file.path} has no glyph for '$ch'")
                val glyph = font.glyph(gid)
                assertTrue(glyph.contours.isNotEmpty(), "$key: ${file.path} glyph '$ch' has no contours")
            }
            checked++
        }
        assertEquals(FACE_FILES.size, checked, "expected to check every face in FACE_FILES")
    }
}

/** Walks up from the working directory to find the repository's `data/learn-faces/` directory. */
private fun findLearnFacesDir(): File? {
    var dir: File? = File(".").absoluteFile
    repeat(6) {
        val candidate = File(dir, "data/learn-faces")
        if (candidate.isDirectory) return candidate
        dir = dir?.parentFile
    }
    return null
}

/** Kept in sync with `data/scripts/fetch_learn_faces.py`'s `FACES` table. */
private val FACE_FILES =
    listOf(
        "blackletter" to "unifrakturmaguntia/UnifrakturMaguntia-Book.ttf",
        "garalde" to "ebgaramond/EBGaramond[wght].ttf",
        "transitional" to "librebaskerville/LibreBaskerville[wght].ttf",
        "didone" to "playfairdisplay/PlayfairDisplay[wght].ttf",
        "slab" to "zillaslab/ZillaSlab-Regular.ttf",
        "grotesque" to "worksans/WorkSans[wght].ttf",
        "artdeco" to "limelight/Limelight-Regular.ttf",
        "geometric" to "jost/Jost[wght].ttf",
        "humanist" to "sourcesans3/SourceSans3[wght].ttf",
        "neogrotesque" to "inter/Inter[opsz,wght].ttf",
        "librebodoni" to "librebodoni/LibreBodoni[wght].ttf",
        "poppins" to "poppins/Poppins-Regular.ttf",
        "librefranklin" to "librefranklin/LibreFranklin[wght].ttf",
        "robotoslab" to "robotoslab/RobotoSlab[wght].ttf",
        "cormorant" to "cormorant/Cormorant[wght].ttf",
        "opensans" to "opensans/OpenSans[wdth,wght].ttf",
        "josefinsans" to "josefinsans/JosefinSans[wght].ttf",
    )
