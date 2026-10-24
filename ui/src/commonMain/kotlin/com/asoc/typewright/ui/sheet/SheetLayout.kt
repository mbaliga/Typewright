// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.sheet

import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.ui.deck.DeckGeometry
import com.asoc.typewright.ui.draw.DrawInsets
import com.asoc.typewright.ui.puck.PuckEdge
import kotlin.math.min

/**
 * Where the sheet's fixed glass sits and what that leaves free for the glyph, in dp. A phone has the
 * header over the glyph and the inspector row and the deck under it; at [WIDE_DP] and above the frame is
 * the explorer's desktop one (a top bar, a tool rail on the left, an inspector column on the right) with
 * the deck centred at the foot. Pure, so the free area a glyph is fitted into is a number a test can
 * check against the glass it must not sit under.
 */
data class SheetLayout(
    val wide: Boolean,
    /** The deck as drawn: its 172 dp frame scaled down on a screen narrower than the 390 dp it is drawn for. */
    val deckVisibleDp: Double,
    /** How far above the bottom the inspector row's lower edge sits: 12 dp above the deck (SCREENS_V1 §2.5). */
    val inspectorBottomDp: Double,
    /** What the glyph may use. */
    val free: DrawInsets,
) {
    companion object {
        /** The width from which the desktop frame replaces the phone one (SCREENS_V1 §2.6). */
        const val WIDE_DP: Double = 900.0

        const val TOP_BAR_DP: Double = 46.0
        const val TOOL_RAIL_DP: Double = 210.0
        const val INSPECTOR_COLUMN_DP: Double = 320.0

        /** The header block of a phone: 12 above, a 14 sp title and an 11 sp line, 16 below. */
        const val PHONE_HEADER_DP: Double = 62.0

        /** One inspector row: a 9.5 sp label over a 15 sp value, with room for the line that says why a typed value was refused. */
        const val INSPECTOR_ROW_DP: Double = 44.0

        const val DECK_TO_INSPECTOR_DP: Double = 12.0
        const val INSPECTOR_TO_GLYPH_DP: Double = 8.0

        /** How much of the glyph's side the puck covers (its radius and a little more) when it is pinned there. */
        const val PUCK_SIDE_INSET_DP: Double = 44.0

        fun of(
            canvasSizeDp: Vec2,
            statusBarDp: Double = 0.0,
            puckEdge: PuckEdge? = null,
        ): SheetLayout {
            val wide = canvasSizeDp.x >= WIDE_DP
            val deckVisible = DeckGeometry.HEIGHT * min(1.0, canvasSizeDp.x / DeckGeometry.WIDTH)
            val inspectorBottom = deckVisible + DECK_TO_INSPECTOR_DP
            return if (wide) {
                SheetLayout(
                    wide = true,
                    deckVisibleDp = deckVisible,
                    inspectorBottomDp = inspectorBottom,
                    free = DrawInsets(top = TOP_BAR_DP, bottom = inspectorBottom, left = TOOL_RAIL_DP, right = INSPECTOR_COLUMN_DP),
                )
            } else {
                SheetLayout(
                    wide = false,
                    deckVisibleDp = deckVisible,
                    inspectorBottomDp = inspectorBottom,
                    free =
                        DrawInsets(
                            top = statusBarDp + PHONE_HEADER_DP,
                            bottom = inspectorBottom + INSPECTOR_ROW_DP + INSPECTOR_TO_GLYPH_DP,
                            left = if (puckEdge == PuckEdge.LEFT) PUCK_SIDE_INSET_DP else 0.0,
                            right = if (puckEdge == PuckEdge.RIGHT) PUCK_SIDE_INSET_DP else 0.0,
                        ),
                )
            }
        }
    }
}
