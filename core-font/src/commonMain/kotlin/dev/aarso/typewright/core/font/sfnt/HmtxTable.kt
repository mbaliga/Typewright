package dev.aarso.typewright.core.font.sfnt

/** One glyph's horizontal metrics: its advance width and left side bearing, both in font units. */
data class HMetric(
    val advanceWidth: Int,
    val leftSideBearing: Int,
)

/** An `hmtx` table (OpenType spec, "hmtx"): every glyph's [HMetric], indexed by glyph id. */
data class HmtxTable(
    val metrics: List<HMetric>,
) {
    val size: Int get() = metrics.size

    operator fun get(gid: Int): HMetric = metrics[gid]
}

/**
 * Parses an `hmtx` table. `hhea.numberOfHMetrics` (`hMetrics`) explicit `(advanceWidth,
 * leftSideBearing)` pairs come first; any remaining glyphs (`numGlyphs - hMetrics`, when the font
 * is monospaced enough that its last several glyphs share one advance width) contribute only a
 * left side bearing each, repeating the final explicit advance width (OpenType spec, "hmtx").
 */
fun readHmtxTable(
    cursor: ByteCursor,
    numberOfHMetrics: Int,
    numGlyphs: Int,
): HmtxTable {
    require(numberOfHMetrics <= numGlyphs) {
        "hhea.numberOfHMetrics ($numberOfHMetrics) must not exceed maxp.numGlyphs ($numGlyphs)"
    }
    val metrics = ArrayList<HMetric>(numGlyphs)
    var lastAdvance = 0
    for (i in 0 until numberOfHMetrics) {
        val advance = cursor.u16()
        val lsb = cursor.i16()
        metrics += HMetric(advance, lsb)
        lastAdvance = advance
    }
    for (i in numberOfHMetrics until numGlyphs) {
        val lsb = cursor.i16()
        metrics += HMetric(lastAdvance, lsb)
    }
    return HmtxTable(metrics)
}
