// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [adaptiveThreshold] against a raster split into two very differently lit halves -- the "uneven
 * lighting" case docs/ARCHITECTURE_REVIEW.md section 3 `:engine-trace` names as the reason to use a
 * local threshold rather than one global cutoff. Both halves carry the *same* ink/paper contrast
 * (80 levels) but at very different absolute brightness, chosen so that **no single global cutoff
 * value can classify both halves correctly** -- proven directly in
 * [aSingleGlobalCutoffCannotClassifyBothHalvesCorrectly] below, not merely asserted.
 */
class AdaptiveThresholdTest {
    companion object {
        private const val WIDTH = 100
        private const val HEIGHT = 60

        // Left half: bright background (220), a darker mark (140) -- contrast 80.
        private const val LEFT_BACKGROUND = 220
        private const val LEFT_INK = 140

        // Right half: dim background (120), an even darker mark (40) -- the same 80 contrast, but
        // an absolute brightness a single global cutoff tuned for the left half cannot also cover.
        private const val RIGHT_BACKGROUND = 120
        private const val RIGHT_INK = 40

        private const val MARK_SIZE = 14 // small relative to the default window (radius 15, side 31)

        private fun unevenlyLitRaster(): GrayscaleRaster {
            val pixels = IntArray(WIDTH * HEIGHT)
            for (y in 0 until HEIGHT) {
                for (x in 0 until WIDTH) {
                    val inLeftHalf = x < WIDTH / 2
                    val background = if (inLeftHalf) LEFT_BACKGROUND else RIGHT_BACKGROUND
                    pixels[y * WIDTH + x] = background
                }
            }

            fun paintMark(
                x0: Int,
                y0: Int,
                value: Int,
            ) {
                for (y in y0 until y0 + MARK_SIZE) for (x in x0 until x0 + MARK_SIZE) pixels[y * WIDTH + x] = value
            }
            val markY = HEIGHT / 2 - MARK_SIZE / 2
            paintMark(WIDTH / 4 - MARK_SIZE / 2, markY, LEFT_INK)
            paintMark(3 * WIDTH / 4 - MARK_SIZE / 2, markY, RIGHT_INK)
            return GrayscaleRaster(WIDTH, HEIGHT, pixels)
        }
    }

    @Test
    fun findsTheDarkMarkOnTheBrightLeftHalf() {
        val binary = adaptiveThreshold(unevenlyLitRaster())
        assertTrue(binary[WIDTH / 4, HEIGHT / 2], "the left-half mark's own centre should be classified as ink")
        assertFalse(binary[5, 5], "plain bright background, far from any mark, should stay paper")
    }

    @Test
    fun findsTheDarkMarkOnTheDimRightHalfDespiteItsLowerAbsoluteBrightness() {
        val binary = adaptiveThreshold(unevenlyLitRaster())
        assertTrue(binary[3 * WIDTH / 4, HEIGHT / 2], "the right-half mark's own centre should be classified as ink")
        assertFalse(
            binary[WIDTH - 5, 5],
            "plain dim background, far from any mark, should stay paper despite being darker than the left-half mark",
        )
    }

    @Test
    fun aSingleGlobalCutoffCannotClassifyBothHalvesCorrectly() {
        // A global rule "ink if value < cutoff" would need cutoff > LEFT_INK (140) and
        // cutoff <= LEFT_BACKGROUND (220) to find the left mark without flagging the left
        // background, and cutoff > RIGHT_INK (40) and cutoff <= RIGHT_BACKGROUND (120) to find the
        // right mark without flagging the right background as ink. The second constraint's own
        // upper bound (120) is already below the first constraint's lower bound (140): no cutoff
        // satisfies both, so a single global threshold is provably unable to get both halves right
        // at once -- exactly the failure adaptiveThreshold's local, per-window comparison avoids.
        val needsToExceed = LEFT_INK
        val mustNotExceed = RIGHT_BACKGROUND
        assertTrue(needsToExceed >= mustNotExceed, "the two halves' constraints must actually conflict for this test to mean anything")
    }
}
