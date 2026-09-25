// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import kotlin.test.Test
import kotlin.test.assertFailsWith

/** [StemPrimitive]: "a rectangle whose width is the font's stem value, grid-snapped" -- change the stem value and every stem updates. */
class StemTest {
    @Test
    fun realizeProducesARectangleOfExactlyTheStemWidthAndHeight() {
        val stem = StemPrimitive(centerX = 100.0, bottomY = 0.0, topY = 700.0, stemValue = 80.0)
        val box = stem.realize().boundingBox()
        assertApprox(80.0, box.maxX - box.minX, tolerance = 1.0)
        assertApprox(700.0, box.maxY - box.minY, tolerance = 1.0)
        assertApprox(60.0, box.minX, tolerance = 1.0) // centred on x=100, width 80 -> minX = 60
        assertApprox(140.0, box.maxX, tolerance = 1.0)
    }

    @Test
    fun changingTheStemValueChangesEveryRealizeCallImmediately() {
        // "Every construction stays parametric until baked... change the stem value and every stem
        // updates" (brief section 10): calling .copy(stemValue = ...) and realizing again must produce
        // a genuinely different width, with nothing cached from the first realize() call.
        val original = StemPrimitive(centerX = 0.0, bottomY = 0.0, topY = 500.0, stemValue = 60.0)
        val firstWidth = original.realize().boundingBox().let { it.maxX - it.minX }
        val changed = original.copy(stemValue = 120.0)
        val secondWidth = changed.realize().boundingBox().let { it.maxX - it.minX }
        assertApprox(60.0, firstWidth, tolerance = 1.0)
        assertApprox(120.0, secondWidth, tolerance = 1.0)
        // The original instance itself is unaffected (a data class, not mutable shared state).
        assertApprox(60.0, original.realize().boundingBox().let { it.maxX - it.minX }, tolerance = 1.0)
    }

    @Test
    fun gridSnappingRoundsToTheNearestGridUnit() {
        val stem = StemPrimitive(centerX = 0.0, bottomY = 0.0, topY = 100.0, stemValue = 47.0, gridUnit = 10.0)
        val box = stem.realize().boundingBox()
        assertApprox(50.0, box.maxX - box.minX, tolerance = 1.0) // 47 snaps to the nearest 10 -> 50
    }

    @Test
    fun realizeMatchesAPlainRectangleOfTheSameSnappedSize() {
        val stem = StemPrimitive(centerX = 200.0, bottomY = 10.0, topY = 710.0, stemValue = 90.0)
        val stemBox = stem.realize().boundingBox()
        val rectangleBox =
            RectanglePrimitive
                .CentreSize(
                    com.asoc.typewright.core.geometry
                        .Point(200, 360),
                    width = 90.0,
                    height = 700.0,
                ).realize()
                .boundingBox()
        assertApprox(rectangleBox.minX, stemBox.minX, tolerance = 1.0)
        assertApprox(rectangleBox.maxX, stemBox.maxX, tolerance = 1.0)
        assertApprox(rectangleBox.minY, stemBox.minY, tolerance = 1.0)
        assertApprox(rectangleBox.maxY, stemBox.maxY, tolerance = 1.0)
    }

    @Test
    fun rejectsATopYNotAboveBottomY() {
        assertFailsWith<IllegalArgumentException> { StemPrimitive(centerX = 0.0, bottomY = 100.0, topY = 100.0, stemValue = 10.0) }
    }

    @Test
    fun rejectsANonPositiveStemValue() {
        assertFailsWith<IllegalArgumentException> { StemPrimitive(centerX = 0.0, bottomY = 0.0, topY = 100.0, stemValue = 0.0) }
    }
}
