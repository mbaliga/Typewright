// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.theme

import com.asoc.typewright.ui.tokens.Argb
import com.asoc.typewright.ui.tokens.CloseToken
import com.asoc.typewright.ui.tokens.ThemeRole
import kotlin.math.PI

/**
 * One thing wrong with a theme, in one line, and which [roles] it is about. [suggestion] is the
 * nearest colour that would be allowed, where there is one.
 */
data class ThemeProblem(
    val roles: List<ThemeRole>,
    val message: String,
    val suggestion: Argb? = null,
)

/**
 * The guardrails of SCREENS_V1 §3.2, enforced and each explained in a line:
 * - text (ink and muted) reaches 4.5:1 against the canvas, and geometry colours 3:1;
 * - the selected node, the snap target and the accent differ from each other and from the node
 *   colours by at least 25 in OKLab lightness × 100 or by ΔE 30 (CIE76);
 * - no role sits within ΔE 20 of the close red, and the nearest allowed colour is offered.
 */
object ThemeGuardrails {
    const val TEXT_CONTRAST = 4.5
    const val GEOMETRY_CONTRAST = 3.0
    const val MIN_LIGHTNESS_GAP = 25.0
    const val MIN_DELTA_E = 30.0
    const val RED_DELTA_E = 20.0

    /** The close red a theme's roles must keep clear of: the dark-ground red on a dark ground. */
    fun closeRed(dark: Boolean): Argb = CloseToken.red(dark)

    private val distinctSelection = listOf(ThemeRole.ACCENT, ThemeRole.SELECTED_NODE, ThemeRole.SNAP_TARGET)
    private val nodeColours = listOf(ThemeRole.CORNER_NODE, ThemeRole.SMOOTH_NODE, ThemeRole.OFF_CURVE, ThemeRole.HANDLE_LINE)

    /** Everything wrong with [theme]: empty means it can be saved. */
    fun check(theme: Theme): List<ThemeProblem> =
        buildList {
            val canvas = theme.color(ThemeRole.CANVAS)
            for (role in ThemeRole.entries) {
                if (role == ThemeRole.CANVAS) continue
                val ratio = contrastRatio(theme.color(role), canvas)
                val floor = if (role.text) TEXT_CONTRAST else GEOMETRY_CONTRAST
                if (ratio < floor) {
                    val what = if (role.text) "text needs" else "lines and nodes need"
                    add(ThemeProblem(listOf(role), "${role.label} is ${format(ratio)}:1 on the canvas; $what ${format(floor)}:1."))
                }
            }
            // A selected node that is the accent colour is "following the accent" (the default), not a collision with it.
            val selection =
                distinctSelection.filter {
                    it != ThemeRole.SELECTED_NODE || theme.color(ThemeRole.SELECTED_NODE) != theme.color(ThemeRole.ACCENT)
                }
            for ((i, a) in selection.withIndex()) {
                for (b in selection.drop(i + 1)) addIfAlike(theme, a, b)
                for (n in nodeColours) addIfAlike(theme, a, n)
            }
            val red = closeRed(theme.dark)
            for (role in ThemeRole.entries) {
                val color = theme.color(role)
                if (deltaE(color, red) < RED_DELTA_E) {
                    add(
                        ThemeProblem(
                            listOf(role),
                            "${role.label} is too close to the close red; nothing but the close circle may be red.",
                            nearestAllowed(color, red),
                        ),
                    )
                }
            }
        }

    private fun MutableList<ThemeProblem>.addIfAlike(
        theme: Theme,
        a: ThemeRole,
        b: ThemeRole,
    ) {
        val x = theme.color(a)
        val y = theme.color(b)
        if (lightnessGap(x, y) >= MIN_LIGHTNESS_GAP || deltaE(x, y) >= MIN_DELTA_E) return
        add(ThemeProblem(listOf(a, b), "${a.label} and ${b.label} are too alike: they need 25 apart in lightness, or ΔE 30."))
    }

    /** The problems that concern [role], so the editor can show them under its row. */
    fun forRole(
        problems: List<ThemeProblem>,
        role: ThemeRole,
    ): List<ThemeProblem> = problems.filter { role in it.roles }

    /**
     * The colour nearest [color] that is at least [RED_DELTA_E] (plus a small margin) from [red]: tried
     * by turning the hue, shifting the lightness and easing the chroma, the smallest change winning.
     */
    fun nearestAllowed(
        color: Argb,
        red: Argb,
    ): Argb {
        val lab = color.toOkLab()
        var best: Argb? = null
        var bestCost = Double.MAX_VALUE
        for (turn in -36..36) {
            val hue = lab.hue + turn * (5.0 * PI / 180.0)
            for (scale in CHROMA_SCALES) {
                for (step in -4..4) {
                    val candidate = lab.moved(hue, scale, step * 0.025).toArgb()
                    if (deltaE(candidate, red) < RED_DELTA_E + RED_MARGIN) continue
                    val cost = deltaE(candidate, color)
                    if (cost < bestCost) {
                        bestCost = cost
                        best = candidate
                    }
                }
            }
        }
        return best ?: color
    }

    private val CHROMA_SCALES = listOf(1.0, 0.85, 0.7, 0.5)
    private const val RED_MARGIN = 2.0

    private fun format(value: Double): String = ((value * 10).toInt() / 10.0).toString()
}
