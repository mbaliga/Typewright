// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Deliberately gives "n" fewer non-null [CompactStyleClass.per] entries than
 *  [CompactStyleClass.n], the case data/scripts/build_compact_corpus.py's own note calls out:
 *  a family with no count for a glyph leaves a `null` in `per`, not a fabricated 0. */
private fun fixturePack(): CompactNodeEconomyPack =
    CompactNodeEconomyPack(
        glyphs = "on",
        styles =
            mapOf(
                "test-style" to
                    CompactStyleClass(
                        n = 3,
                        fams = listOf("Alpha", "Beta", "Gamma"),
                        dist =
                            mapOf(
                                "o" to CompactQuartiles(min = 20.0, q1 = 22.0, med = 24.0, q3 = 26.0, max = 28.0),
                                "n" to CompactQuartiles(min = 14.0, q1 = 16.0, med = 18.0, q3 = 20.0, max = 22.0),
                            ),
                        per =
                            mapOf(
                                "o" to listOf(20, 24, 28),
                                "n" to listOf(14, null, 22),
                            ),
                    ),
            ),
    )

class CompactNodeEconomyCorpusTest {
    @Test
    fun onCurveBoxUsesTheDistQuartilesWithNRecoveredFromPer() {
        val corpus = CompactNodeEconomyCorpus(fixturePack())
        assertEquals(
            Quartiles(min = 20.0, q1 = 22.0, med = 24.0, q3 = 26.0, max = 28.0, n = 3),
            corpus.onCurveBox("test-style", "o"),
        )
    }

    @Test
    fun onCurveBoxNIsSmallerThanTheStyleClassNWhenAFamilyHasNoCountForThatGlyph() {
        val corpus = CompactNodeEconomyCorpus(fixturePack())
        // style n is 3, but "n" (the glyph) has only 2 non-null entries in "per".
        assertEquals(
            Quartiles(min = 14.0, q1 = 16.0, med = 18.0, q3 = 20.0, max = 22.0, n = 2),
            corpus.onCurveBox("test-style", "n"),
        )
    }

    @Test
    fun returnsNullForAnUnknownStyleOrGlyph() {
        val corpus = CompactNodeEconomyCorpus(fixturePack())
        assertNull(corpus.onCurveBox("no-such-style", "o"))
        assertNull(corpus.onCurveBox("test-style", "z"))
    }

    @Test
    fun familyOnCurveCountsSkipsNullEntries() {
        val corpus = CompactNodeEconomyCorpus(fixturePack())
        assertEquals(listOf(20, 24, 28), corpus.familyOnCurveCounts("test-style", "o"))
        assertEquals(listOf(14, 22), corpus.familyOnCurveCounts("test-style", "n"))
    }

    @Test
    fun styleKeysMirrorsThePacksStyles() {
        val corpus = CompactNodeEconomyCorpus(fixturePack())
        assertEquals(setOf("test-style"), corpus.styleKeys)
    }

    @Test
    fun loadReadsTheRealEmbeddedCompactPack() {
        val corpus = CompactNodeEconomyCorpus.load()
        assertEquals(
            Quartiles(min = 20.0, q1 = 23.25, med = 24.0, q3 = 24.0, max = 100.0, n = 30),
            corpus.onCurveBox("sans-geometric", "o"),
        )
    }
}
