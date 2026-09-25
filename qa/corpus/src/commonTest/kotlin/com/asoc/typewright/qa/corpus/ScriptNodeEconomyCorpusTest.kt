// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Decodes the real, checked-in Devanagari and kana sibling packs
 * (data/node-economy-devanagari.json, data/node-economy-kana.json) through
 * [NodeEconomyCorpus.loadDevanagari] / [NodeEconomyCorpus.loadKana], the same way
 * [CorpusLoaderTest] proves the Latin pack. The numbers checked here are read straight from
 * those two files (regenerated 2026-09-24; pinned to google/fonts commit
 * 23e54b51ddffbc7713c583748e3bd86f62b1fa4a) via `python3 -c "import json..."`, not invented.
 *
 * Both packs have fewer, coarser style classes than the Latin pack's ten
 * (data/scripts/build_script_node_economy_corpus.py's module doc explains why: google/fonts'
 * own volunteer style-tag project has far thinner coverage for these scripts, so this build
 * classifies by Google's own, fully-populated `category` field instead) -- `devanagari-mono`
 * and `kana-mono` are absent entirely (0 and 1 real candidate families respectively, both under
 * this generator's own MIN_FAMILIES=5), and `devanagari-handwriting` is absent too (4 real
 * candidates, also under 5) -- see docs/OPEN_QUESTIONS.md for the disclosure.
 */
class ScriptNodeEconomyCorpusTest {
    @Test
    fun devanagariPackHasTheDocumentedSourceGlyphCountAndStyleClasses() {
        val pack = loadNodeEconomyPack(DEVANAGARI_PACK_RESOURCE_PATH)
        assertTrue(pack.source.startsWith("google/fonts@23e54b51ddffbc7713c583748e3bd86f62b1fa4a"))
        // The 66 distinct Devanagari glyphs DevanagariGlyphInventory.kt lists (independent
        // vowels, anusvara, the 33 standard consonants, the 10 matras, visarga, the 10 digits).
        assertEquals(66, pack.glyphs.length)
        assertEquals(setOf("devanagari-sans", "devanagari-serif", "devanagari-display"), pack.styles.keys)
        assertEquals(
            25,
            pack.styles
                .getValue("devanagari-sans")
                .families.size,
        )
        assertEquals(
            19,
            pack.styles
                .getValue("devanagari-serif")
                .families.size,
        )
        assertEquals(
            9,
            pack.styles
                .getValue("devanagari-display")
                .families.size,
        )
    }

    @Test
    fun kanaPackHasTheDocumentedSourceGlyphCountAndStyleClasses() {
        val pack = loadNodeEconomyPack(KANA_PACK_RESOURCE_PATH)
        assertTrue(pack.source.startsWith("google/fonts@23e54b51ddffbc7713c583748e3bd86f62b1fa4a"))
        // 55 Hiragana (HiraganaGlyphs.kt) + 57 Katakana (KatakanaGlyphs.kt) = 112; one pack
        // because no google/fonts family in this corpus supports one kana script without the
        // other (see the generator's module doc).
        assertEquals(112, pack.glyphs.length)
        assertEquals(
            setOf("kana-sans", "kana-serif", "kana-display", "kana-handwriting"),
            pack.styles.keys,
        )
        assertEquals(
            18,
            pack.styles
                .getValue("kana-sans")
                .families.size,
        )
        assertEquals(
            11,
            pack.styles
                .getValue("kana-serif")
                .families.size,
        )
        assertEquals(
            11,
            pack.styles
                .getValue("kana-display")
                .families.size,
        )
        assertEquals(
            6,
            pack.styles
                .getValue("kana-handwriting")
                .families.size,
        )
    }

    @Test
    fun devanagariGlyphBoxesAreSaneAndNonDegenerate() {
        val corpus = NodeEconomyCorpus.loadDevanagari()
        // DEVANAGARI LETTER A (independent vowel) and DEVANAGARI LETTER KA (consonant),
        // devanagari-sans -- real numbers from the checked-in pack.
        assertEquals(
            Quartiles(min = 34.0, q1 = 40.0, med = 46.0, q3 = 54.0, max = 70.0, n = 25),
            corpus.onCurveBox("devanagari-sans", "अ"), // अ
        )
        assertEquals(
            Quartiles(min = 33.0, q1 = 41.0, med = 46.0, q3 = 54.0, max = 80.0, n = 25),
            corpus.onCurveBox("devanagari-sans", "क"), // क
        )
        // Every checked box is a real, ordered distribution over multiple real faces, not a
        // fabricated or collapsed one: min <= q1 <= med <= q3 <= max, and off-curve is present
        // and non-negative, for a handful of glyphs across every real style class this pack has.
        for (styleKey in corpus.styleKeys) {
            for (glyph in listOf("अ", "क", "ा", "०")) { // अ, क, ि (matra AA), ० (digit 0)
                val on = assertNotNull(corpus.onCurveBox(styleKey, glyph), "$styleKey on-curve $glyph")
                val off = assertNotNull(corpus.offCurveBox(styleKey, glyph), "$styleKey off-curve $glyph")
                assertBoxIsSane(on, styleKey, glyph, "on")
                assertBoxIsSane(off, styleKey, glyph, "off")
                assertTrue(on.n >= 5, "$styleKey $glyph on-curve n (${on.n}) should be >= MIN_FAMILIES")
            }
        }
    }

    @Test
    fun kanaGlyphBoxesAreSaneAndNonDegenerate() {
        val corpus = NodeEconomyCorpus.loadKana()
        // HIRAGANA LETTER A and KATAKANA LETTER A, kana-sans -- real numbers from the
        // checked-in pack.
        assertEquals(
            Quartiles(min = 36.0, q1 = 47.25, med = 55.5, q3 = 76.5, max = 112.0, n = 18),
            corpus.onCurveBox("kana-sans", "あ"), // あ
        )
        assertEquals(
            Quartiles(min = 10.0, q1 = 19.25, med = 26.5, q3 = 40.0, max = 48.0, n = 18),
            corpus.onCurveBox("kana-sans", "ア"), // ア
        )
        for (styleKey in corpus.styleKeys) {
            for (glyph in listOf("あ", "ア", "ん", "ー")) { // あ, ア, ん, ー
                val on = assertNotNull(corpus.onCurveBox(styleKey, glyph), "$styleKey on-curve $glyph")
                val off = assertNotNull(corpus.offCurveBox(styleKey, glyph), "$styleKey off-curve $glyph")
                assertBoxIsSane(on, styleKey, glyph, "on")
                assertBoxIsSane(off, styleKey, glyph, "off")
                assertTrue(on.n >= 5, "$styleKey $glyph on-curve n (${on.n}) should be >= MIN_FAMILIES")
            }
        }
    }

    @Test
    fun devanagariAndKanaFamilyRowsMirrorTheirGlyphBoxes() {
        // familyOnCurveCounts is the raw array the boxes were computed from (brief 8.3) -- prove
        // it agrees with onCurveBox's own n for a real (style, glyph) pair in each new pack.
        val deva = NodeEconomyCorpus.loadDevanagari()
        val devaCounts = deva.familyOnCurveCounts("devanagari-sans", "क")
        assertEquals(25, devaCounts.size)
        assertEquals(deva.onCurveBox("devanagari-sans", "क")?.n, devaCounts.size)

        val kana = NodeEconomyCorpus.loadKana()
        val kanaCounts = kana.familyOnCurveCounts("kana-sans", "あ")
        assertEquals(18, kanaCounts.size)
        assertEquals(kana.onCurveBox("kana-sans", "あ")?.n, kanaCounts.size)
    }

    private fun assertBoxIsSane(
        box: Quartiles,
        styleKey: String,
        glyph: String,
        axis: String,
    ) {
        val where = "$styleKey $axis-curve $glyph"
        assertTrue(box.min >= 0.0, "$where min should be >= 0")
        assertTrue(box.min <= box.q1, "$where min <= q1")
        assertTrue(box.q1 <= box.med, "$where q1 <= med")
        assertTrue(box.med <= box.q3, "$where med <= q3")
        assertTrue(box.q3 <= box.max, "$where q3 <= max")
        assertTrue(box.max > 0.0, "$where max should be > 0 (non-degenerate)")
        assertTrue(box.n > 0, "$where n should be > 0")
    }
}
