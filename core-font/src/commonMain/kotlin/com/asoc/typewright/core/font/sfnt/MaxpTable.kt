// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.sfnt

/**
 * The `maxp` table (OpenType spec, "maxp"): [numGlyphs] plus, for version 1.0 (glyf-outline
 * fonts), the profiling maxima a hinting VM would need. CFF fonts (out of `core-font`'s v1 scope;
 * see [SfntTableDirectory]'s `sfntVersion`) use version 0.5, which stops after [numGlyphs].
 */
data class MaxpTable(
    val version: Double,
    val numGlyphs: Int,
    val maxPoints: Int?,
    val maxContours: Int?,
    val maxCompositePoints: Int?,
    val maxCompositeContours: Int?,
    val maxZones: Int?,
    val maxTwilightPoints: Int?,
    val maxStorage: Int?,
    val maxFunctionDefs: Int?,
    val maxInstructionDefs: Int?,
    val maxStackElements: Int?,
    val maxSizeOfInstructions: Int?,
    val maxComponentElements: Int?,
    val maxComponentDepth: Int?,
)

/** Parses a `maxp` table from a cursor positioned at its start. */
fun readMaxpTable(cursor: ByteCursor): MaxpTable {
    val version = cursor.fixed()
    val numGlyphs = cursor.u16()
    if (version < 1.0) {
        return MaxpTable(
            version = version,
            numGlyphs = numGlyphs,
            maxPoints = null,
            maxContours = null,
            maxCompositePoints = null,
            maxCompositeContours = null,
            maxZones = null,
            maxTwilightPoints = null,
            maxStorage = null,
            maxFunctionDefs = null,
            maxInstructionDefs = null,
            maxStackElements = null,
            maxSizeOfInstructions = null,
            maxComponentElements = null,
            maxComponentDepth = null,
        )
    }
    return MaxpTable(
        version = version,
        numGlyphs = numGlyphs,
        maxPoints = cursor.u16(),
        maxContours = cursor.u16(),
        maxCompositePoints = cursor.u16(),
        maxCompositeContours = cursor.u16(),
        maxZones = cursor.u16(),
        maxTwilightPoints = cursor.u16(),
        maxStorage = cursor.u16(),
        maxFunctionDefs = cursor.u16(),
        maxInstructionDefs = cursor.u16(),
        maxStackElements = cursor.u16(),
        maxSizeOfInstructions = cursor.u16(),
        maxComponentElements = cursor.u16(),
        maxComponentDepth = cursor.u16(),
    )
}
