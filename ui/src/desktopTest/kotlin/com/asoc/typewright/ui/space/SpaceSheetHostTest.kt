// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.asoc.typewright.core.font.ufo.UfoKerning
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The router hands Space to the sheet, so what a person meets on Space is the sheet hosting the page. These render
 * the whole sheet on Space over a real project and the page alone over the same project, and hold them to be the same
 * picture: the sheet adds no second deck and no Draw content under or over the page, and a press on the deck reaches
 * the page through the sheet exactly as it does without it.
 */
class SpaceSheetHostTest {
    private val kerning =
        UfoKerning(
            groups = SpaceTestProjects.GROUPS,
            kerning = mapOf("public.kern1.round" to mapOf("public.kern2.straight" to -24.0), "n" to mapOf("o" to -12.0)),
        )
    private val ufo get() = SpaceTestProjects.hyleDeco(kerning)

    private fun org.jetbrains.skia.Image.pixels(): IntArray = toComposeImageBitmap().toPixelMap().buffer

    /** The share (0..1) of pixels in which [a] and [b] differ by more than a rounding error in any channel. */
    private fun differing(
        a: IntArray,
        b: IntArray,
    ): Double {
        require(a.size == b.size) { "the two pictures are not the same size" }
        var different = 0
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            val far =
                (0..SHIFT_MAX step CHANNEL_BITS).any { shift -> abs(((x shr shift) and CHANNEL) - ((y shr shift) and CHANNEL)) > TOLERANCE }
            if (far) different++
        }
        return different.toDouble() / a.size
    }

    @Test
    fun theSheetOnSpaceIsTheSpacePageAndNothingElse() {
        var sheet = IntArray(0)
        var page = IntArray(0)
        withSpace(ufo) { sheet = snapshot("space-host-sheet-spacing").pixels() }
        withSpacePage(ufo) { page = rig.snapshot("space-host-page-spacing").pixels() }
        val share = differing(sheet, page)
        assertTrue(share < SAME, "the sheet's Space is the page's picture, but $share of its pixels differ")
    }

    @Test
    fun aPressOnTheDeckReachesThePageThroughTheSheet() {
        var sheetSpacing = IntArray(0)
        var sheetKerning = IntArray(0)
        var pageKerning = IntArray(0)
        withSpace(ufo) {
            sheetSpacing = snapshot("space-host-sheet-spacing").pixels()
            click(DeckPoints(PHONE_DP_W, PHONE_DP_H).seat("Kerning", SPACING_SEATS))
            frames(6)
            sheetKerning = snapshot("space-host-sheet-kerning").pixels()
        }
        withSpacePage(ufo) {
            rig.click(DeckPoints(widthDp, heightDp).seat("Kerning", SPACING_SEATS))
            rig.frames(6)
            pageKerning = rig.snapshot("space-host-page-kerning").pixels()
        }
        assertTrue(differing(sheetSpacing, sheetKerning) > CHANGED, "pressing Kerning through the sheet changed what is shown")
        val share = differing(sheetKerning, pageKerning)
        assertTrue(share < SAME, "through the sheet Kerning looks as it does on the page alone, but $share of its pixels differ")
    }

    private companion object {
        const val CHANNEL = 0xFF
        const val CHANNEL_BITS = 8
        const val SHIFT_MAX = 16
        const val TOLERANCE = 6
        const val SAME = 0.002
        const val CHANGED = 0.005
    }
}
