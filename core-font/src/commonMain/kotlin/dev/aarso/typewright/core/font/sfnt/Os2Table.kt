package dev.aarso.typewright.core.font.sfnt

/**
 * An `OS/2` table (OpenType spec, "OS/2"): Windows/CSS-facing metrics and classification.
 * Versions 0-5 share one growing layout (each version adds fields at the end); fields introduced
 * after [version] are `null` rather than guessed at. Version 0 (the minimum any conforming font
 * declares) guarantees every field up to and including [usWinDescent].
 */
data class Os2Table(
    val version: Int,
    val usWeightClass: Int,
    val usWidthClass: Int,
    val fsType: Int,
    val achVendId: String,
    val fsSelection: Int,
    val usFirstCharIndex: Int,
    val usLastCharIndex: Int,
    val sTypoAscender: Int,
    val sTypoDescender: Int,
    val sTypoLineGap: Int,
    val usWinAscent: Int,
    val usWinDescent: Int,
    // version >= 1
    val ulCodePageRange1: Long?,
    val ulCodePageRange2: Long?,
    // version >= 2
    val sxHeight: Int?,
    val sCapHeight: Int?,
    val usDefaultChar: Int?,
    val usBreakChar: Int?,
    val usMaxContext: Int?,
    // version >= 5
    val usLowerOpticalPointSize: Int?,
    val usUpperOpticalPointSize: Int?,
)

/** Parses an `OS/2` table from a cursor positioned at its start. */
fun readOs2Table(cursor: ByteCursor): Os2Table {
    val version = cursor.u16()
    cursor.skip(2) // xAvgCharWidth -- not modeled (nothing downstream needs it); skipping it is
    // load-bearing, not decorative: every OS/2 field below this line sits 2 bytes later than
    // `version` in the real table layout (OpenType spec, "OS/2"), and omitting this skip read
    // every one of them from the wrong offset -- found and fixed task P6 (Overlay tab), verified
    // against a real font's raw bytes (fontTools' own independent parse of
    // `fonts/HyleDeco-Regular.ttf`'s OS/2 table) before this fix and cross-checked after against
    // the same bytes: pre-fix this reader returned `sxHeight = 0`, `sCapHeight = 500` for that
    // font; the real, correct values (confirmed via fontTools) are `sxHeight = 500`,
    // `sCapHeight = 700` -- which, unlike the pre-fix numbers, actually match Hyle Deco's own
    // drawn ink heights for `x`/`H` (500/700). See `Os2TableTest`'s
    // `xAvgCharWidthIsSkippedSoLaterFieldsLandOnTheRealSpecOffsets` and
    // `HyleDecoCrossCheckTest`'s own OS/2 regression test for the real-data proof.
    val usWeightClass = cursor.u16()
    val usWidthClass = cursor.u16()
    val fsType = cursor.u16()
    cursor.skip(2 * 2) // ySubscriptXSize, ySubscriptYSize
    cursor.skip(2 * 2) // ySubscriptXOffset, ySubscriptYOffset
    cursor.skip(2 * 2) // ySuperscriptXSize, ySuperscriptYSize
    cursor.skip(2 * 2) // ySuperscriptXOffset, ySuperscriptYOffset
    cursor.skip(2 * 2) // yStrikeoutSize, yStrikeoutPosition
    cursor.skip(2) // sFamilyClass
    cursor.skip(10) // panose[10]
    cursor.skip(4 * 4) // ulUnicodeRange1-4
    val achVendId = cursor.tag()
    val fsSelection = cursor.u16()
    val usFirstCharIndex = cursor.u16()
    val usLastCharIndex = cursor.u16()
    val sTypoAscender = cursor.i16()
    val sTypoDescender = cursor.i16()
    val sTypoLineGap = cursor.i16()
    val usWinAscent = cursor.u16()
    val usWinDescent = cursor.u16()

    var ulCodePageRange1: Long? = null
    var ulCodePageRange2: Long? = null
    var sxHeight: Int? = null
    var sCapHeight: Int? = null
    var usDefaultChar: Int? = null
    var usBreakChar: Int? = null
    var usMaxContext: Int? = null
    var usLowerOpticalPointSize: Int? = null
    var usUpperOpticalPointSize: Int? = null

    if (version >= 1) {
        ulCodePageRange1 = cursor.u32()
        ulCodePageRange2 = cursor.u32()
    }
    if (version >= 2) {
        sxHeight = cursor.i16()
        sCapHeight = cursor.i16()
        usDefaultChar = cursor.u16()
        usBreakChar = cursor.u16()
        usMaxContext = cursor.u16()
    }
    if (version >= 5) {
        usLowerOpticalPointSize = cursor.u16()
        usUpperOpticalPointSize = cursor.u16()
    }

    return Os2Table(
        version = version,
        usWeightClass = usWeightClass,
        usWidthClass = usWidthClass,
        fsType = fsType,
        achVendId = achVendId,
        fsSelection = fsSelection,
        usFirstCharIndex = usFirstCharIndex,
        usLastCharIndex = usLastCharIndex,
        sTypoAscender = sTypoAscender,
        sTypoDescender = sTypoDescender,
        sTypoLineGap = sTypoLineGap,
        usWinAscent = usWinAscent,
        usWinDescent = usWinDescent,
        ulCodePageRange1 = ulCodePageRange1,
        ulCodePageRange2 = ulCodePageRange2,
        sxHeight = sxHeight,
        sCapHeight = sCapHeight,
        usDefaultChar = usDefaultChar,
        usBreakChar = usBreakChar,
        usMaxContext = usMaxContext,
        usLowerOpticalPointSize = usLowerOpticalPointSize,
        usUpperOpticalPointSize = usUpperOpticalPointSize,
    )
}
