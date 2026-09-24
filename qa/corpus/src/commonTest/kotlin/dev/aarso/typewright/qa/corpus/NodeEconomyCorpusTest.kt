package dev.aarso.typewright.qa.corpus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A small, hand-built pack -- not the real corpus -- so wrapper behaviour (missing keys,
 *  family ordering, per-glyph absence) is checked against numbers chosen for the test, not
 *  numbers that could drift if the corpus is regenerated. */
private fun fixturePack(): NodeEconomyPack =
    NodeEconomyPack(
        source = "test fixture",
        glyphs = "on",
        styles =
            mapOf(
                "test-style" to
                    StyleClass(
                        tags = listOf("/Test"),
                        families =
                            listOf(
                                FamilyEntry(
                                    family = "Alpha",
                                    file = "alpha.ttf",
                                    format = "quadratic",
                                    drawing = 90.0,
                                    counts = mapOf("o" to listOf(20, 10, 2), "n" to listOf(14, 8, 1)),
                                ),
                                FamilyEntry(
                                    family = "Beta",
                                    file = "beta.ttf",
                                    format = "quadratic",
                                    drawing = null,
                                    // "n" has no box for this family (a genuinely contourless
                                    // glyph, brief 8.1) -- deliberately absent from counts.
                                    counts = mapOf("o" to listOf(24, 12, 2)),
                                ),
                                FamilyEntry(
                                    family = "Gamma",
                                    file = "gamma.ttf",
                                    format = "quadratic",
                                    drawing = 70.0,
                                    counts = mapOf("o" to listOf(28, 14, 2), "n" to listOf(22, 8, 1)),
                                ),
                            ),
                        dist =
                            mapOf(
                                "o" to
                                    GlyphDist(
                                        on = Quartiles(min = 20.0, q1 = 22.0, med = 24.0, q3 = 26.0, max = 28.0, n = 3),
                                        off = Quartiles(min = 10.0, q1 = 11.0, med = 12.0, q3 = 13.0, max = 14.0, n = 3),
                                    ),
                                "n" to
                                    GlyphDist(
                                        on = Quartiles(min = 14.0, q1 = 16.0, med = 18.0, q3 = 20.0, max = 22.0, n = 2),
                                        off = Quartiles(min = 8.0, q1 = 8.0, med = 8.0, q3 = 8.0, max = 8.0, n = 2),
                                    ),
                            ),
                    ),
            ),
    )

class NodeEconomyCorpusTest {
    @Test
    fun exposesTheOnAndOffCurveBoxesForAKnownPair() {
        val corpus = NodeEconomyCorpus(fixturePack())
        assertEquals(Quartiles(20.0, 22.0, 24.0, 26.0, 28.0, 3), corpus.onCurveBox("test-style", "o"))
        assertEquals(Quartiles(10.0, 11.0, 12.0, 13.0, 14.0, 3), corpus.offCurveBox("test-style", "o"))
    }

    @Test
    fun returnsNullForAnUnknownStyleOrGlyph() {
        val corpus = NodeEconomyCorpus(fixturePack())
        assertNull(corpus.onCurveBox("no-such-style", "o"))
        assertNull(corpus.onCurveBox("test-style", "z"))
        assertNull(corpus.offCurveBox("test-style", "z"))
    }

    @Test
    fun familyCountsAreInRankOrderAndSkipAFamilyWithNoBoxForThatGlyph() {
        val corpus = NodeEconomyCorpus(fixturePack())
        assertEquals(listOf(20, 24, 28), corpus.familyOnCurveCounts("test-style", "o"))
        assertEquals(listOf(10, 12, 14), corpus.familyOffCurveCounts("test-style", "o"))
        // "Beta" has no box for "n" -- skipped, not a fabricated 0 (law 5).
        assertEquals(listOf(14, 22), corpus.familyOnCurveCounts("test-style", "n"))
        assertEquals(listOf(8, 8), corpus.familyOffCurveCounts("test-style", "n"))
    }

    @Test
    fun familyCountsAreEmptyForAnUnknownStyle() {
        val corpus = NodeEconomyCorpus(fixturePack())
        assertEquals(emptyList(), corpus.familyOnCurveCounts("no-such-style", "o"))
    }

    @Test
    fun familiesReturnsTheFullRowsInRankOrder() {
        val corpus = NodeEconomyCorpus(fixturePack())
        assertEquals(listOf("Alpha", "Beta", "Gamma"), corpus.families("test-style").map { it.family })
    }

    @Test
    fun styleKeysMirrorsThePacksStyles() {
        val corpus = NodeEconomyCorpus(fixturePack())
        assertEquals(setOf("test-style"), corpus.styleKeys)
    }

    @Test
    fun loadReadsTheRealEmbeddedPack() {
        // Cross-checks the box-statistics wrapper against the same real numbers
        // CorpusLoaderTest checks at the loader level, so the wrapper is proven end to end
        // through the actual embedded resource, not just against the fixture above.
        val corpus = NodeEconomyCorpus.load()
        assertEquals(
            Quartiles(min = 20.0, q1 = 23.25, med = 24.0, q3 = 24.0, max = 100.0, n = 30),
            corpus.onCurveBox("sans-geometric", "o"),
        )
        assertEquals(30, corpus.familyOnCurveCounts("sans-geometric", "o").size)
    }
}
