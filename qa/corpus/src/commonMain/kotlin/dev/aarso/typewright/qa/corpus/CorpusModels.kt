package dev.aarso.typewright.qa.corpus

import kotlinx.serialization.Serializable

/**
 * Min/Q1/median/Q3/max of a set of node counts (brief 8.1/8.2), plus how many faces went into
 * the box. Mirrors the full pack's `dist.<glyph>.on` / `.off` shape in
 * data/node-economy-latin.json exactly; the compact pack's [CompactQuartiles] carries the same
 * five numbers without [n] (data/scripts/build_compact_corpus.py never wrote one).
 */
@Serializable
data class Quartiles(
    val min: Double,
    val q1: Double,
    val med: Double,
    val q3: Double,
    val max: Double,
    val n: Int,
)

/** On-curve and off-curve [Quartiles] for one glyph in one style class (full pack only). */
@Serializable
data class GlyphDist(
    val on: Quartiles,
    val off: Quartiles,
)

/**
 * One member face's row in a style class: its file, its outline format ("quadratic" for every
 * corpus face today, since google/fonts ships TTF only -- brief 8.1), its `/Quality/Drawing`
 * ranking score (nullable: the generator could not find a score for every candidate), and its
 * raw per-glyph counts.
 *
 * [counts] mirrors the JSON exactly: glyph -> `[onCurve, offCurve, contours]` (brief 8.1, 8.5),
 * a three-element `List<Int>`, not a named type -- read it with [onCurve], [offCurve] or
 * [contours] rather than indexing it directly.
 */
@Serializable
data class FamilyEntry(
    val family: String,
    val file: String,
    val format: String,
    val drawing: Double? = null,
    val counts: Map<String, List<Int>>,
)

/**
 * The on-curve count (TrueType on-curve points plus one implied on-curve point per
 * cyclically-consecutive off-curve pair -- brief 8.1's counting rule) for [glyph] in this face,
 * or null if this face has no box for it (rare: a genuinely contourless glyph, brief 8.1).
 */
fun FamilyEntry.onCurve(glyph: String): Int? = counts[glyph]?.getOrNull(0)

/**
 * The off-curve count for [glyph] in this face, reported in its source format (quadratic, for
 * every corpus face today), or null if this face has no box for it.
 */
fun FamilyEntry.offCurve(glyph: String): Int? = counts[glyph]?.getOrNull(1)

/** The contour count for [glyph] in this face, or null if this face has no box for it. */
fun FamilyEntry.contours(glyph: String): Int? = counts[glyph]?.getOrNull(2)

/**
 * One style class's data (brief 8.1): its Google Fonts taxonomy tags, its up-to-30 member faces
 * in rank order, and the on/off-curve [Quartiles] per glyph.
 */
@Serializable
data class StyleClass(
    val tags: List<String>,
    val families: List<FamilyEntry>,
    val dist: Map<String, GlyphDist>,
)

/**
 * The full node-economy pack (data/node-economy-latin.json, ~317 KB): per-glyph on/off-curve
 * boxes for ten style classes, plus every member face's raw counts (brief 8.1). Decode it with
 * [loadNodeEconomyPack], or query it through [NodeEconomyCorpus]. See also
 * [CompactNodeEconomyPack], the smaller, on-curve-only pack -- which pack the app ships is still
 * open (docs/OPEN_QUESTIONS.md item 8), so both are exposed here.
 */
@Serializable
data class NodeEconomyPack(
    val source: String,
    val glyphs: String,
    val styles: Map<String, StyleClass>,
)

/**
 * min/Q1/median/Q3/max only -- the compact pack's `dist` entries have no [Quartiles.n].
 * data/scripts/build_compact_corpus.py's own note on the shape: read the family count for the
 * whole style class back from [CompactStyleClass.n], or, for the exact number of faces behind
 * one glyph's box (which can be smaller when a face has no box for that glyph), count the
 * non-null entries in [CompactStyleClass.per] for that glyph.
 */
@Serializable
data class CompactQuartiles(
    val min: Double,
    val q1: Double,
    val med: Double,
    val q3: Double,
    val max: Double,
)

/**
 * One style class in the compact pack: its face count and names in rank order ([fams]), the
 * on-curve-only box per glyph ([dist]), and each face's raw on-curve count per glyph, aligned
 * index-for-index with [fams] ([per]; null where that face has no box for the glyph -- never a
 * fabricated 0, per law 5).
 */
@Serializable
data class CompactStyleClass(
    val n: Int,
    val fams: List<String>,
    val dist: Map<String, CompactQuartiles>,
    val per: Map<String, List<Int?>>,
)

/**
 * The compact node-economy pack (data/node-economy-latin.compact.json, ~87 KB): on-curve boxes
 * only, derived from [NodeEconomyPack] by data/scripts/build_compact_corpus.py. Off-curve
 * counts, contour counts, file names and drawing scores are not in this pack. Decode it with
 * [loadCompactNodeEconomyPack], or query it through [CompactNodeEconomyCorpus].
 */
@Serializable
data class CompactNodeEconomyPack(
    val glyphs: String,
    val styles: Map<String, CompactStyleClass>,
)
