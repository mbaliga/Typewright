// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.shape.preview

/**
 * Groups Skiko's per-glyph cluster array into [GlyphCluster] ranges.
 *
 * `org.jetbrains.skia.shaper.RunHandler.commitRun`'s own KDoc (Skiko 0.150.1 sources,
 * `commonMain/org/jetbrains/skia/shaper/RunHandler.kt`): "clusters[i] is an utf-16 offset
 * starting run which produced glyphs[i]" -- so [clusterUtf16Starts] is already in the same
 * code-unit index space as a Kotlin [String] (verified for real against real Devanagari and
 * Arabic text in `SkikoShaperTest`, not just trusted from that comment), and needs no UTF-8-
 * byte conversion the way a naive read of Skia's C++ side (which shapes UTF-8 internally)
 * might suggest.
 *
 * `HB_BUFFER_CLUSTER_LEVEL_MONOTONE_CHARACTERS` (SkShaper_harfbuzz.cpp) guarantees
 * [clusterUtf16Starts] is monotonic in glyph order -- non-decreasing for left-to-right runs,
 * non-increasing for right-to-left ones -- so glyphs sharing a cluster value are always
 * contiguous in [glyphCount] order. This function groups those contiguous runs and derives
 * each cluster's text end from the sorted set of distinct starts, which works the same way
 * regardless of direction (unlike deriving it from iteration order).
 */
internal fun buildSkikoClusters(
    textLength: Int,
    glyphCount: Int,
    clusterUtf16Starts: List<Int>,
): List<GlyphCluster> {
    require(clusterUtf16Starts.size == glyphCount) {
        "clusterUtf16Starts must have one entry per glyph: ${clusterUtf16Starts.size} for $glyphCount glyphs"
    }
    if (glyphCount == 0) return emptyList()

    val distinctStarts = clusterUtf16Starts.distinct().sorted()
    val textEndOf = HashMap<Int, Int>(distinctStarts.size * 2)
    for (i in distinctStarts.indices) {
        textEndOf[distinctStarts[i]] = if (i + 1 < distinctStarts.size) distinctStarts[i + 1] else textLength
    }

    val clusters = mutableListOf<GlyphCluster>()
    var groupStart = 0
    while (groupStart < glyphCount) {
        val start = clusterUtf16Starts[groupStart]
        var groupEnd = groupStart + 1
        while (groupEnd < glyphCount && clusterUtf16Starts[groupEnd] == start) groupEnd++
        clusters +=
            GlyphCluster(
                textStart = start,
                textEnd = textEndOf.getValue(start),
                glyphStart = groupStart,
                glyphEnd = groupEnd,
            )
        groupStart = groupEnd
    }
    return clusters
}
