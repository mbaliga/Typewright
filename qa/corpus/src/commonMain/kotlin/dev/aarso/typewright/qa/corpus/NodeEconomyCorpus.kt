package dev.aarso.typewright.qa.corpus

/**
 * Box statistics over the full node-economy pack (brief 8.1): the on/off-curve [Quartiles] and
 * the raw per-family counts for one (style class, glyph) pair. Build with [load] to read and
 * decode [loadNodeEconomyPack], or wrap an already-decoded [NodeEconomyPack] directly (for
 * example in a test, against a hand-built fixture pack).
 */
class NodeEconomyCorpus(
    private val pack: NodeEconomyPack,
) {
    /** The pack's style-class keys, e.g. "sans-geometric" (brief 8.1's ten classes). */
    val styleKeys: Set<String> get() = pack.styles.keys

    /**
     * The on-curve [Quartiles] box for [glyph] in [styleKey] -- the primary axis brief 8.2
     * discusses -- or null if [styleKey] is unknown, or no member family had a count for
     * [glyph].
     */
    fun onCurveBox(
        styleKey: String,
        glyph: String,
    ): Quartiles? =
        pack.styles[styleKey]
            ?.dist
            ?.get(glyph)
            ?.on

    /** The off-curve [Quartiles] box for [glyph] in [styleKey] (brief 8.1's secondary axis). */
    fun offCurveBox(
        styleKey: String,
        glyph: String,
    ): Quartiles? =
        pack.styles[styleKey]
            ?.dist
            ?.get(glyph)
            ?.off

    /**
     * Every member family's raw on-curve count for [glyph] in [styleKey], in the pack's rank
     * order, skipping a family with no count for that glyph. This is the raw array [onCurveBox]
     * was computed from -- brief 8.3's "tapping a row opens it to show the 30 faces as points"
     * reads from here.
     */
    fun familyOnCurveCounts(
        styleKey: String,
        glyph: String,
    ): List<Int> = pack.styles[styleKey]?.families?.mapNotNull { it.onCurve(glyph) } ?: emptyList()

    /** Every member family's raw off-curve count for [glyph] in [styleKey], in rank order. */
    fun familyOffCurveCounts(
        styleKey: String,
        glyph: String,
    ): List<Int> = pack.styles[styleKey]?.families?.mapNotNull { it.offCurve(glyph) } ?: emptyList()

    /**
     * Every member family's full row for [styleKey], in rank order, for a caller that needs a
     * family's name, file or drawing score rather than just one glyph's count.
     */
    fun families(styleKey: String): List<FamilyEntry> = pack.styles[styleKey]?.families ?: emptyList()

    companion object {
        /** Loads and decodes the full pack ([loadNodeEconomyPack]), then wraps it. */
        fun load(): NodeEconomyCorpus = NodeEconomyCorpus(loadNodeEconomyPack())
    }
}

/**
 * Box statistics over the compact node-economy pack: on-curve only (see [CompactNodeEconomyPack]).
 * [Quartiles.n] here is not carried by the pack itself -- it is recovered as the count of
 * non-null entries in [CompactStyleClass.per] for that glyph, per
 * data/scripts/build_compact_corpus.py's own note on the shape, so it can be smaller than
 * [CompactStyleClass.n] (the style class's total family count) when a family has no count for
 * that particular glyph.
 */
class CompactNodeEconomyCorpus(
    private val pack: CompactNodeEconomyPack,
) {
    /** The pack's style-class keys, e.g. "sans-geometric" (brief 8.1's ten classes). */
    val styleKeys: Set<String> get() = pack.styles.keys

    /**
     * The on-curve [Quartiles] box for [glyph] in [styleKey], or null if [styleKey] is unknown,
     * or no member family had a count for [glyph]. The compact pack carries no off-curve data
     * (see [CompactNodeEconomyPack]).
     */
    fun onCurveBox(
        styleKey: String,
        glyph: String,
    ): Quartiles? {
        val style = pack.styles[styleKey] ?: return null
        val box = style.dist[glyph] ?: return null
        val n = style.per[glyph]?.count { it != null } ?: 0
        return Quartiles(min = box.min, q1 = box.q1, med = box.med, q3 = box.q3, max = box.max, n = n)
    }

    /**
     * Every member family's raw on-curve count for [glyph] in [styleKey], in the pack's rank
     * order (aligned with [CompactStyleClass.fams]), skipping a family with no count for that
     * glyph (`null` in [CompactStyleClass.per]).
     */
    fun familyOnCurveCounts(
        styleKey: String,
        glyph: String,
    ): List<Int> =
        pack.styles[styleKey]
            ?.per
            ?.get(glyph)
            ?.filterNotNull() ?: emptyList()

    companion object {
        /** Loads and decodes the compact pack ([loadCompactNodeEconomyPack]), then wraps it. */
        fun load(): CompactNodeEconomyCorpus = CompactNodeEconomyCorpus(loadCompactNodeEconomyPack())
    }
}
