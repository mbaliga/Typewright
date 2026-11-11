// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.polygonContour
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The feedback of the Pen, the Knife and Tidy as a person sees it on the phone-sized sheet: each state is written as a PNG under
 * `ui/build/screenshots`, and what is asserted is that the state changed the picture where its feedback is drawn (a rendering
 * that ignored the state would fail), not that a PNG exists. Nothing here is a touch screen; touch is owner-verified on a device only.
 */
class DrawToolsScreenshotTest {
    private fun differingPixels(
        a: org.jetbrains.skia.Image,
        b: org.jetbrains.skia.Image,
    ): Int {
        val pa = a.toComposeImageBitmap().toPixelMap()
        val pb = b.toComposeImageBitmap().toPixelMap()
        var n = 0
        for (y in 0 until pa.height) {
            for (x in 0 until pa.width) {
                val c = pa[x, y]
                val d = pb[x, y]
                if (abs(c.red - d.red) + abs(c.green - d.green) + abs(c.blue - d.blue) > 0.3f) n++
            }
        }
        return n
    }

    private fun slab(): Glyph =
        Glyph("I", 400, listOf(polygonContour(listOf(Point(50, 0), Point(350, 0), Point(350, 400), Point(50, 400)), CurveFormat.CUBIC)))

    @Test
    fun penWithAPointBeingPulledOut() =
        withDrawSheet(DrawTestProjects.small(Glyph("a", 500, emptyList())), "a", PHONE_W, PHONE_H, PHONE_DENSITY) {
            key(Keys.P)
            val empty = snapshot("draw-pen-0-empty")
            click(Vec2(110.0, 330.0))
            click(Vec2(250.0, 330.0))
            press(Vec2(250.0, 470.0))
            move(Vec2(280.0, 470.0))
            move(Vec2(300.0, 440.0))
            val pulling = snapshot("draw-pen-phone")
            assertTrue(
                differingPixels(empty, pulling) > MIN_CHANGED_PIXELS,
                "the path, its corners and the handles being pulled are drawn",
            )
            release(Vec2(300.0, 440.0))
        }

    @Test
    fun knifeWithItsFirstEndPlacedAndTheHintShowing() =
        withDrawSheet(DrawTestProjects.small(slab()), "I", PHONE_W, PHONE_H, PHONE_DENSITY) {
            val plain = snapshot("draw-knife-0-plain")
            key(Keys.K, ctrl = true, shift = true)
            click(Vec2((screenOf(0, 0).x + screenOf(0, 3).x) / 2.0, screenOf(0, 0).y))
            val armed = snapshot("draw-knife-phone")
            assertTrue(differingPixels(plain, armed) > MIN_CHANGED_PIXELS, "the first end's mark and the hint are drawn")
        }

    @Test
    fun tidyWithTheCountShownAndTheNodesThatWouldGoMarked() =
        withDrawSheet(DrawTestProjects.hyleDecoCubic(), "H", PHONE_W, PHONE_H, PHONE_DENSITY) {
            val before = snapshot("draw-tidy-0-before")
            key(Keys.Y, ctrl = true, shift = true)
            val proposal = snapshot("draw-tidy-phone")
            assertTrue(differingPixels(before, proposal) > MIN_CHANGED_PIXELS, "the count bar and the removal marks are drawn")
        }

    private companion object {
        const val PHONE_W = 720
        const val PHONE_H = 1560
        const val PHONE_DENSITY = 2f
        const val MIN_CHANGED_PIXELS = 400
    }
}
