// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which removals carry a cross, and the words that say how many. */
class TidyCrossesTest {
    private fun space(dpPerUnit: Double) = DrawSpace(originScreen = Vec2(0.0, 0.0), dpPerUnit = dpPerUnit)

    @Test
    fun crossesAreOneToEveryGapOfScreenAndCoarserWhenZoomedOut() {
        // 100 removed nodes, 1 unit apart along x.
        val removed = (0 until 100).map { Point(it, 0) }
        val zoomedOut = TidyCrosses.shown(removed, space(1.0))
        val zoomedIn = TidyCrosses.shown(removed, space(11.0))
        // At 1 dp per unit a cross every 22 units: 0, 22, 44, 66, 88.
        assertEquals(listOf(0, 22, 44, 66, 88), zoomedOut.map { it.x })
        // At 11 dp per unit a cross every 2 units.
        assertEquals((0 until 100 step 2).toList(), zoomedIn.map { it.x })
        assertTrue(zoomedIn.size > zoomedOut.size)
    }

    @Test
    fun theCaptionNamesHowManyCarryACrossAndIsAbsentWhenAllDo() {
        val dense = (0 until 100).map { Point(it, 0) }
        assertEquals("crosses on 5 of 100; zoom in for the rest", TidyCrosses.caption(dense, space(1.0)))
        val sparse = listOf(Point(0, 0), Point(500, 0), Point(900, 0))
        assertNull(TidyCrosses.caption(sparse, space(1.0)))
        assertEquals(sparse, TidyCrosses.shown(sparse, space(1.0)))
        assertEquals(emptyList(), TidyCrosses.shown(emptyList(), space(1.0)))
    }
}
