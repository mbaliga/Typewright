// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.ui.sheet.MAX_ZOOM
import com.asoc.typewright.ui.sheet.MIN_ZOOM
import com.asoc.typewright.ui.sheet.Room
import com.asoc.typewright.ui.sheet.SheetCamera
import com.asoc.typewright.ui.sheet.worldBounds
import kotlin.math.max
import kotlin.math.min

/** Where the glyph being drawn sits in the Draw room's world, and how big one font unit is there. */
object DrawWorld {
    /** World dp per font unit at camera zoom 1: a 500-unit x-height is 270 dp, the size the V1 Draw board uses. */
    const val UNIT_DP: Double = 0.54

    /** How far inside the Draw room's left edge the glyph's origin (its x = 0) sits, in world dp. */
    const val ORIGIN_X_DP: Double = 120.0

    /** The world x of the glyph origin. */
    val originX: Double get() = Room.DRAW.worldBounds().left + ORIGIN_X_DP

    /** The most screen dp one font unit may cover when a glyph is fitted: a full stop is shown at a drawable size, not blown up to fill the screen. */
    const val MAX_FIT_DP_PER_UNIT: Double = 0.9
}

/** What a glyph is fitted to: the heights worth keeping in view while it is drawn. */
object DrawFit {
    /**
     * The lowest and highest font y to fit to for a glyph with ink spanning [inkBottom]..[inkTop] (null for
     * an empty glyph): from the baseline, or the lowest descender, up to the taller of the glyph and the
     * x-height (the cap height when the font gives no x-height, 70% of the em when it gives neither), so a
     * small glyph still shows where it sits against the x-height line. The ascender and the descender are
     * not forced into view: they stay on the sheet and a pan reaches them.
     */
    fun heights(
        inkBottom: Int?,
        inkTop: Int?,
        metrics: DrawMetrics,
    ): Pair<Int, Int> {
        val reference =
            metrics.lines.firstOrNull { it.kind == MetricKind.X_HEIGHT }?.y
                ?: metrics.lines.firstOrNull { it.kind == MetricKind.CAP_HEIGHT }?.y
                ?: (metrics.unitsPerEm * DEFAULT_REFERENCE_FRACTION).toInt()
        return minOf(0, inkBottom ?: 0) to maxOf(inkTop ?: 0, reference)
    }

    private const val DEFAULT_REFERENCE_FRACTION = 0.7
}

/**
 * The map between a glyph's font units (y up) and screen dp (y down) for one camera. [originScreen]
 * is where font point (0, 0) lands and [dpPerUnit] how many screen dp one font unit covers; the two
 * are everything a hit test, a snap or a drawing needs, so none of them reads the camera itself.
 * Built from a [SheetCamera] so the glyph pans and zooms with the sheet.
 */
data class DrawSpace(
    val originScreen: Vec2,
    val dpPerUnit: Double,
) {
    init {
        require(dpPerUnit > 0.0) { "dpPerUnit must be positive, was $dpPerUnit" }
    }

    fun toScreen(font: Vec2): Vec2 = Vec2(originScreen.x + font.x * dpPerUnit, originScreen.y - font.y * dpPerUnit)

    fun toFont(screen: Vec2): Vec2 = Vec2((screen.x - originScreen.x) / dpPerUnit, (originScreen.y - screen.y) / dpPerUnit)

    /** A screen length in dp as font units. */
    fun unitsFor(dp: Double): Double = dp / dpPerUnit

    companion object {
        /** The space the Draw room's glyph occupies under [camera]. */
        fun of(camera: SheetCamera): DrawSpace {
            val originWorldX = DrawWorld.originX
            return DrawSpace(
                originScreen = Vec2((originWorldX - camera.offset.x) * camera.zoom, -camera.offset.y * camera.zoom),
                dpPerUnit = DrawWorld.UNIT_DP * camera.zoom,
            )
        }
    }
}

/** The part of the screen a glyph can use: below the header and above the inspector row and deck. */
data class FitArea(
    val widthDp: Double,
    val heightDp: Double,
    val topDp: Double,
    val bottomDp: Double,
    val leftDp: Double,
    val rightDp: Double,
)

/**
 * The camera that puts a glyph of [advanceWidth] by [lowest]..[highest] font units in the middle of
 * [area], as large as fits with a margin (and no larger than [DrawWorld.MAX_FIT_DP_PER_UNIT]), within the zoom range the sheet allows. Only a person asking
 * (fit glyph, double-tap on empty canvas) or a different glyph calls this; a resize never does, so
 * the view the person set is not thrown away when a bar appears.
 */
fun fitCamera(
    advanceWidth: Int,
    lowest: Int,
    highest: Int,
    area: FitArea,
): SheetCamera {
    val usableWidth = max(1.0, area.widthDp - area.leftDp - area.rightDp)
    val usableHeight = max(1.0, area.heightDp - area.topDp - area.bottomDp)
    val boxWidth = max(1, advanceWidth).toDouble()
    val boxHeight = max(1, highest - lowest).toDouble()
    val dpPerUnit = min(min(usableWidth / boxWidth, usableHeight / boxHeight) * FIT_MARGIN, DrawWorld.MAX_FIT_DP_PER_UNIT)
    val zoom = (dpPerUnit / DrawWorld.UNIT_DP).coerceIn(MIN_ZOOM, MAX_ZOOM)
    // The box centre, in font units, lands at the centre of the usable area.
    val centreUnitsX = boxWidth / 2.0
    val centreUnitsY = (lowest + highest) / 2.0
    val centreScreenX = area.leftDp + usableWidth / 2.0
    val centreScreenY = area.topDp + usableHeight / 2.0
    // screenX = (originX + fx*UNIT - offX)*zoom  ->  offX = originX + fx*UNIT - screenX/zoom
    val offsetX = DrawWorld.originX + centreUnitsX * DrawWorld.UNIT_DP - centreScreenX / zoom
    // screenY = (-fy*UNIT - offY)*zoom  ->  offY = -fy*UNIT - screenY/zoom
    val offsetY = -centreUnitsY * DrawWorld.UNIT_DP - centreScreenY / zoom
    return SheetCamera(Vec2(offsetX, offsetY), zoom)
}

private const val FIT_MARGIN = 0.92
