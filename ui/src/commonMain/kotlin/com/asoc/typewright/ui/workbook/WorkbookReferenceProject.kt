// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.workbook

import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.ui.learn.HyleDecoProjectFontBytes

/**
 * The real [UfoProject] [WorkbookScreen] runs every task's real gate against -- this build's own
 * Hyle Deco reference font, the same honestly-disclosed "project" stand-in
 * [com.asoc.typewright.ui.learn.HyleDecoProjectFontBytes]'s own KDoc already establishes for the
 * Overlay and Lens tabs (there is no current-project flow wired into `ui` yet), and the same
 * precedent `campaign`'s own `HyleDecoTask4GateTest.kt` (`jvmTest`) set for task 4's gate
 * specifically -- this function is that same idea, but reachable from every KMP target `ui`
 * builds for (not `jvmTest`-only): [HyleDecoProjectFontBytes.bytes] is plain embedded common-code
 * bytes, and [readSfntFont] is pure common Kotlin, so parsing this font works identically on the
 * JVM, Android and Kotlin/Wasm.
 *
 * Every [UfoFontInfo] field here is read from the real font, not guessed: [UfoFontInfo.unitsPerEm]/
 * [UfoFontInfo.ascender]/[UfoFontInfo.descender] from `head`/`hhea`, and -- because `OS/2` is
 * present on this font and version >= 2 -- [UfoFontInfo.xHeight]/[UfoFontInfo.capHeight] from its
 * real `sxHeight`/`sCapHeight` (`500`/`700`, per `Os2Table.kt`'s own KDoc on the real bug this
 * build found and fixed reading them: "which, unlike the pre-fix numbers, actually match Hyle
 * Deco's own drawn ink heights for x/H"). The `500`/`700` fallback below only matters if a future
 * change to [HyleDecoProjectFontBytes] ever swapped in a font whose `OS/2` table is absent or
 * pre-version-2 -- not the case for the font actually embedded today.
 */
fun hyleDecoReferenceProject(): UfoProject {
    val font = readSfntFont(HyleDecoProjectFontBytes.bytes)
    val fontInfo =
        UfoFontInfo(
            familyName = "Hyle Deco",
            styleName = "Regular",
            unitsPerEm = font.head.unitsPerEm,
            ascender = font.hhea.ascender,
            descender = font.hhea.descender,
            xHeight = font.os2?.sxHeight ?: 500,
            capHeight = font.os2?.sCapHeight ?: 700,
        )
    return UfoProject(fontInfo, font.allGlyphs())
}
