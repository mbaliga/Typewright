package dev.aarso.typewright.qa.corpus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Decodes the real, checked-in data packs (data/node-economy-latin.json and its compact
 * derivative) through [readCorpusResourceText] and the `expect`/`actual` resource reader, on
 * whichever target this test runs under -- `jvmTest` via the classpath, `wasmJsNodeTest` via
 * Node's `fs` (see CorpusResources.kt). The numbers checked here are read straight from
 * data/node-economy-latin.json (regenerated 2026-09-24, P0c; pinned to google/fonts commit
 * b5efa9c32e8f9b63005f5cdb1ad5527a77d2cd04) and cross-checked with a `python3 -c "import
 * json..."` read of the same file; they are not invented.
 */
class CorpusLoaderTest {
    @Test
    fun fullPackHasTheDocumentedSourceAndGlyphString() {
        val pack = loadNodeEconomyPack()
        assertTrue(pack.source.startsWith("google/fonts@b5efa9c32e8f9b63005f5cdb1ad5527a77d2cd04"))
        assertEquals(
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789",
            pack.glyphs,
        )
    }

    @Test
    fun fullPackHasTheTenDocumentedStyleClassesAtTheirRealSizes() {
        val pack = loadNodeEconomyPack()
        // brief 8.1's class list and sizes, corrected 2026-09-24 (P0c;
        // docs/OPEN_QUESTIONS.md item 17): didone and blackletter run under 30 because their
        // candidate pools genuinely run out, not from a bug.
        val expectedSizes =
            mapOf(
                "sans-geometric" to 30,
                "sans-grotesque" to 30,
                "sans-neogrotesque" to 30,
                "sans-humanist" to 30,
                "serif-garalde" to 30,
                "serif-transitional" to 30,
                "serif-didone" to 15,
                "slab" to 30,
                "display-artdeco" to 8,
                "blackletter" to 15,
            )
        assertEquals(expectedSizes.keys, pack.styles.keys)
        for ((styleKey, expectedSize) in expectedSizes) {
            assertEquals(
                expectedSize,
                pack.styles
                    .getValue(styleKey)
                    .families.size,
                "style class $styleKey",
            )
        }
    }

    @Test
    fun fullPackSansGeometricOBoxMatchesTheRegeneratedCorpus() {
        // docs/OPEN_QUESTIONS.md item 17: "the corrected sans-geometric 'o' box is min 20 ·
        // Q1 23.25 · med 24 · Q3 24 · max 100 on-curve (30 families; off-curve is min 0 ·
        // Q1 23 · med 24 · Q3 24 · max 100)".
        val pack = loadNodeEconomyPack()
        val oBox =
            pack.styles
                .getValue("sans-geometric")
                .dist
                .getValue("o")
        assertEquals(Quartiles(min = 20.0, q1 = 23.25, med = 24.0, q3 = 24.0, max = 100.0, n = 30), oBox.on)
        assertEquals(Quartiles(min = 0.0, q1 = 23.0, med = 24.0, q3 = 24.0, max = 100.0, n = 30), oBox.off)
    }

    @Test
    fun fullPackFamilyCountsMirrorTheJsonTripleShape() {
        val pack = loadNodeEconomyPack()
        val geometric = pack.styles.getValue("sans-geometric")
        val googleSansFlex = geometric.families.first { it.family == "Google Sans Flex" }
        assertEquals("quadratic", googleSansFlex.format)
        // [on, off, contours] straight from the JSON, per the family sample checked while
        // building the loader.
        assertEquals(listOf(26, 12, 2), googleSansFlex.counts.getValue("A"))
        assertEquals(26, googleSansFlex.onCurve("A"))
        assertEquals(12, googleSansFlex.offCurve("A"))
        assertEquals(2, googleSansFlex.contours("A"))
    }

    @Test
    fun compactPackIsOnCurveOnlyAndAgreesWithTheFullPackOBox() {
        val full = loadNodeEconomyPack()
        val compact = loadCompactNodeEconomyPack()
        assertEquals(full.glyphs, compact.glyphs)
        assertEquals(full.styles.keys, compact.styles.keys)

        val fullGeometric = full.styles.getValue("sans-geometric")
        val compactGeometric = compact.styles.getValue("sans-geometric")
        assertEquals(fullGeometric.families.size, compactGeometric.n)
        assertEquals(fullGeometric.families.map { it.family }, compactGeometric.fams)

        val fullOnBox = fullGeometric.dist.getValue("o").on
        val compactOBox = compactGeometric.dist.getValue("o")
        assertEquals(fullOnBox.min, compactOBox.min)
        assertEquals(fullOnBox.q1, compactOBox.q1)
        assertEquals(fullOnBox.med, compactOBox.med)
        assertEquals(fullOnBox.q3, compactOBox.q3)
        assertEquals(fullOnBox.max, compactOBox.max)

        // "per" is the raw on-curve count per family, aligned index-for-index with "fams", and
        // every geometric family has an "o" (no nulls in this glyph's row for this class).
        val perO = compactGeometric.per.getValue("o")
        assertEquals(compactGeometric.fams.size, perO.size)
        assertTrue(perO.none { it == null })
        assertEquals(
            fullGeometric.families.map { it.onCurve("o") },
            perO,
        )
    }
}
