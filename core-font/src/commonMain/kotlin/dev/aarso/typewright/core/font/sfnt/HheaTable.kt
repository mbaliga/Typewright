package dev.aarso.typewright.core.font.sfnt

/**
 * The `hhea` table (OpenType spec, "hhea"): horizontal header metrics. [numberOfHMetrics] is the
 * field [readHmtxTable] actually needs; the rest is kept because it is cheap to and a caller may
 * want it for vertical-metrics UI later.
 */
data class HheaTable(
    val majorVersion: Int,
    val minorVersion: Int,
    val ascender: Int,
    val descender: Int,
    val lineGap: Int,
    val advanceWidthMax: Int,
    val minLeftSideBearing: Int,
    val minRightSideBearing: Int,
    val xMaxExtent: Int,
    val caretSlopeRise: Int,
    val caretSlopeRun: Int,
    val caretOffset: Int,
    val metricDataFormat: Int,
    val numberOfHMetrics: Int,
)

/** Parses an `hhea` table from a cursor positioned at its start. */
fun readHheaTable(cursor: ByteCursor): HheaTable {
    val majorVersion = cursor.u16()
    val minorVersion = cursor.u16()
    val ascender = cursor.i16()
    val descender = cursor.i16()
    val lineGap = cursor.i16()
    val advanceWidthMax = cursor.u16()
    val minLeftSideBearing = cursor.i16()
    val minRightSideBearing = cursor.i16()
    val xMaxExtent = cursor.i16()
    val caretSlopeRise = cursor.i16()
    val caretSlopeRun = cursor.i16()
    val caretOffset = cursor.i16()
    cursor.skip(2 * 4) // reserved x4 (int16 each)
    val metricDataFormat = cursor.i16()
    val numberOfHMetrics = cursor.u16()
    return HheaTable(
        majorVersion = majorVersion,
        minorVersion = minorVersion,
        ascender = ascender,
        descender = descender,
        lineGap = lineGap,
        advanceWidthMax = advanceWidthMax,
        minLeftSideBearing = minLeftSideBearing,
        minRightSideBearing = minRightSideBearing,
        xMaxExtent = xMaxExtent,
        caretSlopeRise = caretSlopeRise,
        caretSlopeRun = caretSlopeRun,
        caretOffset = caretOffset,
        metricDataFormat = metricDataFormat,
        numberOfHMetrics = numberOfHMetrics,
    )
}
