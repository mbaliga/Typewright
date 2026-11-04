// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SCREENS_V1 §9.1 "Three sizes each": which layout the launcher shows at the sizes Android 12 and
 * later really give a widget. The sizes are Android's own sizing table for a typical phone: in
 * portrait a widget n cells wide and m rows high gets about (73n − 16) × (118m − 16) dp, in
 * landscape about (142n − 15) × (66m − 15) dp.
 */
class WidgetSizesTest {
    private fun portrait(
        cells: Int,
        rows: Int,
    ) = SizeDp(73f * cells - 16f, 118f * rows - 16f)

    private fun landscape(
        cells: Int,
        rows: Int,
    ) = SizeDp(142f * cells - 15f, 66f * rows - 15f)

    private fun sizeAt(
        kind: WidgetKind,
        at: SizeDp,
    ) = WidgetSizes.sizeFor(kind, at.width, at.height)

    private val projectWidgets = WidgetKind.entries - WidgetKind.SHORTCUTS

    @Test
    fun inPortraitTwoCellsAreSmallFourByTwoIsMediumAndFourByThreeIsLarge() {
        for (kind in projectWidgets) {
            assertEquals(WidgetSize.SMALL, sizeAt(kind, portrait(2, 1)), "$kind at 2×1")
            assertEquals(WidgetSize.SMALL, sizeAt(kind, portrait(2, 2)), "$kind at 2×2")
            assertEquals(WidgetSize.SMALL, sizeAt(kind, portrait(4, 1)), "$kind at 4×1")
            assertEquals(WidgetSize.MEDIUM, sizeAt(kind, portrait(3, 2)), "$kind at 3×2")
            assertEquals(WidgetSize.MEDIUM, sizeAt(kind, portrait(4, 2)), "$kind at 4×2")
            assertEquals(WidgetSize.LARGE, sizeAt(kind, portrait(4, 3)), "$kind at 4×3")
            assertEquals(WidgetSize.LARGE, sizeAt(kind, portrait(5, 4)), "$kind at 5×4")
        }
    }

    @Test
    fun shortcutsShowThreePadsFromFourCellsInOneRowAndTheirLinesFromTwoRows() {
        val kind = WidgetKind.SHORTCUTS
        assertEquals(WidgetSize.SMALL, sizeAt(kind, portrait(2, 1)))
        // Three cells can't hold WORKBOOK in a pad: it stays one RESUME pad rather than squeezing three.
        assertEquals(WidgetSize.SMALL, sizeAt(kind, portrait(3, 1)))
        assertEquals(WidgetSize.MEDIUM, sizeAt(kind, portrait(4, 1)))
        assertEquals(WidgetSize.LARGE, sizeAt(kind, portrait(4, 2)))
        // A landscape row is about 51 dp: the pads are 48 dp and Shortcuts has no top or bottom padding.
        assertEquals(WidgetSize.MEDIUM, sizeAt(kind, landscape(4, 1)))
    }

    @Test
    fun inLandscapeALayoutIsChosenOnlyWhereItFits() {
        // Landscape rows are short: a 4×2 is about 553 × 117 dp, a 4×3 about 553 × 183 dp.
        assertEquals(WidgetSize.SMALL, sizeAt(WidgetKind.APPROVED, landscape(4, 2)))
        assertEquals(WidgetSize.MEDIUM, sizeAt(WidgetKind.APPROVED, landscape(4, 3)))
        assertEquals(WidgetSize.MEDIUM, sizeAt(WidgetKind.SAVED, landscape(4, 2)))
        assertEquals(WidgetSize.SMALL, sizeAt(WidgetKind.RECENTS, landscape(4, 2)))
        assertEquals(WidgetSize.MEDIUM, sizeAt(WidgetKind.LINEAGES, landscape(4, 2)))
    }

    @Test
    fun everyLayoutStartsWhereItsContentFits() {
        for (kind in WidgetKind.entries) {
            for ((size, start) in WidgetSizes.breakpoints(kind)) {
                if (size == WidgetSize.SMALL && kind != WidgetKind.SHORTCUTS) continue // small is also the fallback below every start
                val needed = WidgetSizes.contentHeight(kind, size)
                assertTrue(start.height >= needed, "$kind $size starts at ${start.height} dp but needs $needed dp")
            }
            // Small must fit a portrait row of cells, where most small widgets sit.
            val small = WidgetSizes.contentHeight(kind, WidgetSize.SMALL)
            assertTrue(small <= portrait(2, 1).height, "$kind small needs $small dp")
        }
    }

    @Test
    fun recentProjectsRowsAreFullTouchTargets() {
        // Three 48 dp rows under the label, and five plus the "more" line, each inside its frame.
        assertTrue(WidgetSizes.breakpoints(WidgetKind.RECENTS).getValue(WidgetSize.MEDIUM).height >= 24f + 16f + 3 * 48f)
        assertTrue(WidgetSizes.breakpoints(WidgetKind.RECENTS).getValue(WidgetSize.LARGE).height >= 24f + 16f + 5 * 48f + 22f)
    }

    @Test
    fun belowTheSmallestLayoutTheWidgetStillShowsItsSummary() {
        assertEquals(WidgetSize.SMALL, WidgetSizes.sizeFor(WidgetKind.APPROVED, 40f, 40f))
    }

    @Test
    fun everyWidgetHasThreeNestedSizes() {
        for (kind in WidgetKind.entries) {
            val sizes = WidgetSizes.breakpoints(kind)
            assertEquals(WidgetSize.entries.toSet(), sizes.keys, "$kind")
            val ordered = WidgetSize.entries.map { sizes.getValue(it) }
            for ((smaller, larger) in ordered.zipWithNext()) {
                assertTrue(smaller.width <= larger.width && smaller.height <= larger.height, "$kind: $smaller then $larger")
                assertTrue(smaller != larger, "$kind: two sizes start at $smaller")
            }
        }
    }
}
