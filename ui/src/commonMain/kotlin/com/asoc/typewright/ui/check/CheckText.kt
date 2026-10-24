// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import com.asoc.typewright.qa.report.EconomySort
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckToggle

/**
 * The sentences the Check pages print that depend on the state of a run, kept apart from the screens so
 * what a person is told can be tested without drawing anything.
 */
object CheckText {
    /** The Economy page's title (the board's `Economy · on-curve points`). */
    const val ECONOMY_TITLE = "Economy · on-curve points"

    /** What layer one says before it has been looked for. */
    private const val LAYER_TWO_ONLY = "Layer two here"

    /** The Report page's title: `Check · Hyle Deco Regular`. */
    fun title(name: String): String = "Check · $name"

    /**
     * The mono line under the Report page's title: which tool layer one runs here, or why it does not, and
     * that layer two is always here. A device with no Python says "Layer one needs the desktop app", as the board does.
     */
    fun runtimeLine(state: CheckRunState): String {
        if (!state.probed) return LAYER_TWO_ONLY
        val layerOne = state.layerOne
        return when {
            layerOne is LayerOneState.Unavailable && layerOne.reason.startsWith(NEEDS_DESKTOP) -> "$NEEDS_DESKTOP · layer two here"
            layerOne is LayerOneState.Unavailable -> "Layer one is not available on this computer · layer two here"
            state.runtime != null -> "Layer one: ${state.runtime.description} · layer two here"
            else -> "Layer one runs on this computer · layer two here"
        }
    }

    /** The Economy page's subtitle: `Your glyph against 30 faces · worst first`, and where the class came from when it is only our reading. */
    fun economySubtitle(
        faces: Int,
        sort: EconomySort,
        classIsHeuristic: Boolean,
    ): String {
        val base = "Your glyph against $faces faces · ${sort.label.lowercase()}"
        return if (classIsHeuristic) "$base · class read by our heuristic" else base
    }

    /** `00:08`: whole seconds as minutes and seconds, as the build page prints elapsed time. */
    fun elapsed(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** `6 s` for a stage that took [seconds] whole seconds, `under 1 s` for less than one. */
    fun duration(seconds: Int): String = if (seconds < 1) "under 1 s" else "$seconds s"

    /** The words the platform checkers begin with when layer one cannot run on a phone or in a browser. */
    const val NEEDS_DESKTOP = "Layer one needs the desktop app"
}

/** What the Economy page's subtitle needs to know: how many faces the chosen class has, and whether the class is only our reading. */
internal data class EconomyHeader(
    val faces: Int,
    val classIsHeuristic: Boolean,
)

/**
 * What the page puts on the glass: its title and subtitle, the deck's toggle arc and what the right circle is. With no
 * project, or a project with no glyphs, there are no toggles and no right circle (a circle with no job is hidden); Plain and Raw
 * show only in the Report; the right circle is dimmed while a run is going.
 */
internal data class CheckChrome(
    val title: String,
    val subtitle: String,
    val toggles: List<DeckToggle>,
    val right: DeckRight,
    val rightDimmed: Boolean,
)

internal fun checkChrome(
    name: String?,
    hasGlyphs: Boolean,
    run: CheckRunState,
    view: CheckView,
    raw: Boolean,
    sort: EconomySort,
    economy: EconomyHeader?,
): CheckChrome {
    val economyView = view == CheckView.ECONOMY
    val live = name != null && hasGlyphs
    return CheckChrome(
        title =
            when {
                name == null -> "Check"
                economyView -> CheckText.ECONOMY_TITLE
                else -> CheckText.title(name)
            },
        subtitle =
            when {
                name == null -> {
                    ""
                }

                economyView -> {
                    economy?.let { CheckText.economySubtitle(it.faces, sort, it.classIsHeuristic) }
                        ?: "No style class to compare against yet"
                }

                else -> {
                    CheckText.runtimeLine(run)
                }
            },
        toggles =
            when {
                !live -> {
                    emptyList()
                }

                economyView -> {
                    listOf(DeckToggle("Report", on = false), DeckToggle("Economy", on = true))
                }

                else -> {
                    listOf(
                        DeckToggle("Report", on = true),
                        DeckToggle("Economy", on = false),
                        DeckToggle("Plain", on = !raw, groupStart = true),
                        DeckToggle("Raw", on = raw),
                    )
                }
            },
        right = if (live) DeckRight.REFRESH else DeckRight.NONE,
        rightDimmed = run.isRunning,
    )
}
