package dev.aarso.typewright.shape.preview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SkikoClustersTest {
    @Test
    fun emptyGlyphsProduceNoClusters() {
        assertEquals(emptyList(), buildSkikoClusters(textLength = 0, glyphCount = 0, clusterUtf16Starts = emptyList()))
    }

    @Test
    fun oneGlyphPerCharacterInLeftToRightOrder() {
        // "no": glyph 0 <- 'n' (chars 0..1), glyph 1 <- 'o' (chars 1..2).
        val clusters = buildSkikoClusters(textLength = 2, glyphCount = 2, clusterUtf16Starts = listOf(0, 1))
        assertEquals(
            listOf(
                GlyphCluster(textStart = 0, textEnd = 1, glyphStart = 0, glyphEnd = 1),
                GlyphCluster(textStart = 1, textEnd = 2, glyphStart = 1, glyphEnd = 2),
            ),
            clusters,
        )
    }

    @Test
    fun manyCharactersFormOneClusterOfManyGlyphsLeftToRight() {
        // A three-codepoint conjunct (e.g. Devanagari क + ् + ष) that shapes to two glyphs, both
        // reported at cluster 0 -- the real shape SkikoShaperTest observes for क्ष.
        val clusters = buildSkikoClusters(textLength = 3, glyphCount = 2, clusterUtf16Starts = listOf(0, 0))
        assertEquals(
            listOf(GlyphCluster(textStart = 0, textEnd = 3, glyphStart = 0, glyphEnd = 2)),
            clusters,
        )
    }

    @Test
    fun mixedRunOfSingleAndMultiGlyphClustersLeftToRight() {
        // "aBC" where 'a' is one glyph, "BC" ligates to one glyph: clusters [0, 1, 1].
        val clusters = buildSkikoClusters(textLength = 3, glyphCount = 3, clusterUtf16Starts = listOf(0, 1, 1))
        assertEquals(
            listOf(
                GlyphCluster(textStart = 0, textEnd = 1, glyphStart = 0, glyphEnd = 1),
                GlyphCluster(textStart = 1, textEnd = 3, glyphStart = 1, glyphEnd = 3),
            ),
            clusters,
        )
    }

    @Test
    fun rightToLeftClustersDecreaseInGlyphOrderButStillProduceForwardTextRanges() {
        // Three RTL characters, one glyph each: HarfBuzz's buffer order for RTL text puts the
        // last logical character first, so cluster values (UTF-16 starts) *decrease* as glyph
        // index increases -- still monotonic (HB_BUFFER_CLUSTER_LEVEL_MONOTONE_CHARACTERS), just
        // the other direction. The derived text ranges must still read left-to-right (0..1, 1..2,
        // 2..3), regardless of the glyph order they came from.
        val clusters = buildSkikoClusters(textLength = 3, glyphCount = 3, clusterUtf16Starts = listOf(2, 1, 0))
        assertEquals(
            listOf(
                GlyphCluster(textStart = 2, textEnd = 3, glyphStart = 0, glyphEnd = 1),
                GlyphCluster(textStart = 1, textEnd = 2, glyphStart = 1, glyphEnd = 2),
                GlyphCluster(textStart = 0, textEnd = 1, glyphStart = 2, glyphEnd = 3),
            ),
            clusters,
        )
    }

    @Test
    fun rightToLeftMultiGlyphClusterAtTheStartOfTheBuffer() {
        // RTL text whose *last* logical characters (cluster 1) ligate to two glyphs, shaped
        // first in buffer order, followed by one glyph for the first logical character
        // (cluster 0).
        val clusters = buildSkikoClusters(textLength = 3, glyphCount = 3, clusterUtf16Starts = listOf(1, 1, 0))
        assertEquals(
            listOf(
                GlyphCluster(textStart = 1, textEnd = 3, glyphStart = 0, glyphEnd = 2),
                GlyphCluster(textStart = 0, textEnd = 1, glyphStart = 2, glyphEnd = 3),
            ),
            clusters,
        )
    }

    @Test
    fun everyGlyphMustHaveAClusterEntry() {
        assertFailsWith<IllegalArgumentException> {
            buildSkikoClusters(textLength = 2, glyphCount = 2, clusterUtf16Starts = listOf(0))
        }
    }

    @Test
    fun clustersAlwaysPartitionEveryGlyphExactlyOnce() {
        val clusters = buildSkikoClusters(textLength = 3, glyphCount = 3, clusterUtf16Starts = listOf(0, 1, 1))
        val coveredGlyphs = clusters.flatMap { it.glyphStart until it.glyphEnd }
        assertEquals((0 until 3).toList(), coveredGlyphs)
        assertTrue(clusters.all { it.textStart < it.textEnd })
    }
}
