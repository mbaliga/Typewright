// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.deck

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The sections the deck's arc names, in order. There is no Brief section (ui/v1-screens/Deck.dc.html). */
enum class DeckSection(
    val label: String,
) {
    HOME("Home"),
    CAPTURE("Capture"),
    TRACE("Trace"),
    DRAW("Draw"),
    SPACE("Space"),
    CHECK("Check"),
    SHIP("Ship"),
    LEARN("Learn"),
    WORKBOOK("Workbook"),
}

/** The left circle: Back leaves, Close leaves without committing (red, with a white ✕), or nothing. */
enum class DeckLeft { BACK, CLOSE, NONE }

/** The right circle: the page menu, or the one primary action Next (filled), or nothing. [REFRESH] is Check's Run again. */
enum class DeckRight { MENU, NEXT, CHECK, PLUS, REFRESH, NONE }

/** How far along a station's work is, drawn beside its label on the section arc (the explorer's Home stations). */
enum class StationStatus(
    val shape: String,
    val word: String,
) {
    TO_REFIT("●", "to refit"),
    NOT_STARTED("○", "not started"),
    DONE("✓", "done"),
}

/** One toggle on the toggle arc. [groupStart] leaves a gap before it, the canvas's `||`. */
data class DeckToggle(
    val label: String,
    val on: Boolean = false,
    val groupStart: Boolean = false,
    /** False draws the seat at 35% and makes a tap explain [why] instead of going anywhere. */
    val enabled: Boolean = true,
    val why: String? = null,
    /** True for a seat that opens another page, drawn with a trailing ›. */
    val opens: Boolean = false,
)

/**
 * One label on an arc: its upper-case [text], where its centre sits as a distance along the arc
 * from the arc's left end ([centerLength]), how wide the ink band behind it is ([bandLength]), and
 * its opacity. [current] labels sit on the band.
 */
data class DeckLabel(
    val text: String,
    val centerLength: Double,
    val bandLength: Double,
    val alpha: Double,
    val current: Boolean,
    /** The section this label names, when it is on the section arc. */
    val section: DeckSection? = null,
    val enabled: Boolean = true,
    val why: String? = null,
)

/**
 * The canvas `Deck`'s geometry, in its own 390 × 172 coordinate space, taken from
 * `ui/v1-screens/Deck.dc.html` and not approximated: the section arc
 * `M -19.3 153.6 A 527 527 0 0 1 409.3 153.6` and the toggle arc `M 73.4 62.8 A 585 585 0 0 1
 * 316.6 62.8` share one centre, and span 24° and 12° either side of straight up.
 */
object DeckGeometry {
    const val WIDTH: Double = 390.0
    const val HEIGHT: Double = 172.0
    const val CENTER_X: Double = 195.0
    const val CENTER_Y: Double = 635.04
    const val SECTION_RADIUS: Double = 527.0
    const val TOGGLE_RADIUS: Double = 585.0
    const val SECTION_HALF_ANGLE: Double = 24.0 * PI / 180.0
    const val TOGGLE_HALF_ANGLE: Double = 12.0 * PI / 180.0

    /** The section arc's length, 441.5. */
    const val SECTION_LENGTH: Double = 2 * SECTION_RADIUS * SECTION_HALF_ANGLE

    /** The toggle arc's length, 245.0. */
    const val TOGGLE_LENGTH: Double = 2 * TOGGLE_RADIUS * TOGGLE_HALF_ANGLE

    /** Circles sit at (40, 71) and (350, 71): radius 25, or 26 when filled. */
    const val LEFT_CIRCLE_X: Double = 40.0
    const val RIGHT_CIRCLE_X: Double = 350.0
    const val CIRCLE_Y: Double = 71.0
    const val CIRCLE_RADIUS: Double = 25.0
    const val FILLED_CIRCLE_RADIUS: Double = 26.0

    /** The ink band behind the current section is 26 thick; behind a chosen toggle, 22. */
    const val SECTION_BAND_THICKNESS: Double = 26.0
    const val TOGGLE_BAND_THICKNESS: Double = 22.0

    private const val SECTION_CHAR_WIDTH = 7.6
    private const val SECTION_BAND_PAD = 24.0
    private const val TOGGLE_CHAR_WIDTH = 6.4
    private const val TOGGLE_BAND_PAD = 13.0
    private const val GROUP_GAP = 12.0
    private const val MAX_TOGGLES = 6

    /**
     * The section labels around [current] (two either side; blank past the ends), at 1, 0.72 and
     * 0.38. [shift] is how many sections the arc has been turned while a finger is on it (positive
     * brings later sections to the apex); the label nearest the apex is the current one. [status]
     * puts a station's shape before its label.
     */
    fun sectionLabels(
        current: DeckSection,
        shift: Double = 0.0,
        status: (DeckSection) -> StationStatus? = { null },
    ): List<DeckLabel> {
        val apex = current.ordinal + shift.roundToInt()
        return (-3..3).mapNotNull { d ->
            val pos = d - shift
            val centre = (0.5 + pos * 0.2) * SECTION_LENGTH
            if (centre < 0 || centre > SECTION_LENGTH) return@mapNotNull null
            val i = current.ordinal + d
            val section = DeckSection.entries.getOrNull(i)
            val text = section?.let { (status(it)?.shape?.plus(" ") ?: "") + it.label.uppercase() }.orEmpty()
            DeckLabel(
                text = text,
                centerLength = centre,
                bandLength = text.length * SECTION_CHAR_WIDTH + SECTION_BAND_PAD,
                alpha =
                    when {
                        text.isEmpty() -> 0.0
                        abs(pos) < 0.5 -> 1.0
                        abs(pos) < 1.5 -> 0.72
                        else -> 0.38
                    },
                current = i == apex && section != null,
                section = section,
            )
        }
    }

    /** The toggles, centred on the toggle arc (at most six), each as wide as its label plus padding. */
    fun toggleLabels(toggles: List<DeckToggle>): List<DeckLabel> {
        val shown = toggles.take(MAX_TOGGLES)
        val widths = shown.map { (it.label.length + if (it.opens) 2 else 0) * TOGGLE_CHAR_WIDTH + TOGGLE_BAND_PAD }
        val total = widths.sum() + shown.count { it.groupStart } * GROUP_GAP
        var x = TOGGLE_LENGTH / 2 - total / 2
        return shown.mapIndexed { k, toggle ->
            if (toggle.groupStart) x += GROUP_GAP
            val w = widths[k]
            DeckLabel(
                toggle.label.uppercase() + if (toggle.opens) " ›" else "",
                x + w / 2,
                w,
                alpha = if (toggle.enabled) 1.0 else 0.35,
                current = toggle.on,
                enabled = toggle.enabled,
                why = toggle.why,
            ).also { x += w }
        }
    }

    /** The angle, radians clockwise from straight up, of the point [length] along an arc of [radius] spanning ±[halfAngle]. */
    fun angleAt(
        radius: Double,
        halfAngle: Double,
        length: Double,
    ): Double = -halfAngle + length / radius

    /** The point [length] along an arc of [radius] spanning ±[halfAngle], in the deck's 390 × 172 space. */
    fun pointAt(
        radius: Double,
        halfAngle: Double,
        length: Double,
    ): Pair<Double, Double> {
        val theta = angleAt(radius, halfAngle, length)
        return CENTER_X + radius * sin(theta) to CENTER_Y - radius * cos(theta)
    }
}
