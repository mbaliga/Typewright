// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Vec2
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor

/** How a finger's position picks a sector (INTERACTION_V1 §3). */
enum class RadialMapping {
    /** The sector across the hub from the finger, so the thumb never covers the choice. Used while the finger is down. */
    OPPOSITE,

    /** The sector under the pointer. Used when the ring waits open (a lifted finger taps), for a mouse and for a stylus hover. */
    DIRECT,
}

/** The numbers of the ring, in dp (UI_SPEC §3 and the V1 boards). */
object RadialGeometry {
    const val SECTORS = 8
    const val DEAD_ZONE_DP = 24.0
    const val INNER_DP = 50.0
    const val OUTER_DP = 120.0
    const val EDGE_GAP_DP = 12.0
    const val DECK_GAP_DP = 16.0
    private const val SECTOR_DEGREES = 360.0 / SECTORS

    /** The angle of [point] about [center], in degrees clockwise from straight up, in `[0, 360)`. */
    fun angleFromTop(
        center: Vec2,
        point: Vec2,
    ): Double {
        val degrees = atan2(point.x - center.x, center.y - point.y) * 180.0 / PI
        return (degrees % 360.0 + 360.0) % 360.0
    }

    /**
     * The sector (0 at the top, then clockwise) [pointer] selects about [center], or null while it is
     * inside the dead zone: moving back into it clears the choice, and releasing there cancels.
     */
    fun sectorAt(
        center: Vec2,
        pointer: Vec2,
        mapping: RadialMapping,
    ): Int? {
        if ((pointer - center).length() < DEAD_ZONE_DP) return null
        val angle = angleFromTop(center, pointer)
        val target = if (mapping == RadialMapping.OPPOSITE) (angle + 180.0) % 360.0 else angle
        return (floor(target / SECTOR_DEGREES + 0.5).toInt()) % SECTORS
    }

    /** The sector the finger itself is over, which the thumb covers: it and its two neighbours are drawn at 40%. */
    fun coveredSectors(
        center: Vec2,
        pointer: Vec2,
    ): Set<Int> {
        if ((pointer - center).length() < DEAD_ZONE_DP) return emptySet()
        val under = sectorAt(center, pointer, RadialMapping.DIRECT) ?: return emptySet()
        return setOf((under + SECTORS - 1) % SECTORS, under, (under + 1) % SECTORS)
    }

    /**
     * Where the ring is centred for a hold at [touch]: the touch point shifted inward so the whole ring
     * clears the screen edges by [EDGE_GAP_DP] and the deck (the bottom [deckHeightDp] of a canvas
     * [width] by [height] dp) by [DECK_GAP_DP]. On a canvas too small to clear both it is centred.
     */
    fun ringCenter(
        touch: Vec2,
        width: Double,
        height: Double,
        topInsetDp: Double,
        deckHeightDp: Double,
    ): Vec2 {
        val minX = EDGE_GAP_DP + OUTER_DP
        val maxX = width - EDGE_GAP_DP - OUTER_DP
        val minY = topInsetDp + EDGE_GAP_DP + OUTER_DP
        val maxY = height - deckHeightDp - DECK_GAP_DP - OUTER_DP
        val x = if (minX <= maxX) touch.x.coerceIn(minX, maxX) else width / 2.0
        val y = if (minY <= maxY) touch.y.coerceIn(minY, maxY) else (topInsetDp + (height - deckHeightDp)) / 2.0
        return Vec2(x, y.coerceIn(0.0, height))
    }
}

/** One slot of the ring: the command it runs and how it is labelled; an empty slot has no command. */
data class RadialSlot(
    val commandId: String?,
    val label: String,
    val chip: String? = null,
    val enabled: Boolean = true,
)

/** The ring's open state. */
sealed interface RadialState {
    /** The finger is down and moving: the sector across the hub is chosen ([selected] null in the dead zone). */
    data class Sweeping(
        val center: Vec2,
        val finger: Vec2,
        val selected: Int?,
    ) : RadialState

    /** The finger lifted inside the dead zone without sweeping: the ring waits, and a tap on a sector commits it. */
    data class Lifted(
        val center: Vec2,
    ) : RadialState
}

/** What a radial for each kind of object holds, by command id, clockwise from the top; null leaves the slot empty (a feature that is not built is omitted, not faked). */
object RadialLayouts {
    /** A node: the board's ring, with Split and Tangent left empty because those commands are not built. */
    val NODE: List<String?> =
        listOf("delete", "add-extremes", null, "break-handles", "reverse", "align", null, "toggle-smooth")

    /** A curve between nodes. */
    val SEGMENT: List<String?> =
        listOf("add-node", "add-extremes", null, null, "reverse", null, null, "tidy")

    /** Empty canvas, or a whole contour. */
    val GLYPH: List<String?> =
        listOf("undo", "redo", "fit", null, "lock", "correct-direction", null, "select-all")

    /** The ring for what is selected: a node or handle's, a segment's, or the glyph's when nothing is. */
    fun forSelection(selection: DrawSelection): List<String?> =
        when {
            selection.points.isNotEmpty() -> NODE
            selection.segment != null -> SEGMENT
            else -> GLYPH
        }

    fun forHit(hit: Hit): List<String?> =
        when (hit) {
            is Hit.OnPoint -> NODE
            is Hit.OnSegment -> SEGMENT
            Hit.Nothing -> GLYPH
        }
}
