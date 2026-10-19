// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.deck

import kotlin.math.abs
import kotlin.math.roundToInt

/** What a key does to the deck. */
sealed interface DeckKeyCommand {
    data class StepSection(
        val delta: Int,
    ) : DeckKeyCommand

    data class GoToSection(
        val section: DeckSection,
    ) : DeckKeyCommand

    data object OpenMap : DeckKeyCommand

    /** Escape, and system back: exactly what the left circle does. */
    data object Leave : DeckKeyCommand
}

/** The deck's keys and the arc's swipe, as pure functions so they can be tested off-device. */
object DeckKeys {
    /**
     * What [key] means for the deck, or null. ← → step between sections, digits 1–9 jump to one, M
     * opens the Map. All of them are ignored while a text field, a list or a radio group has focus
     * ([focusOwnsKeys]), or while a modifier is held, so the deck never steals a key something else
     * uses; Escape always leaves.
     */
    fun interpret(
        key: String,
        focusOwnsKeys: Boolean,
        modified: Boolean = false,
    ): DeckKeyCommand? {
        if (key == "Escape") return DeckKeyCommand.Leave
        if (focusOwnsKeys || modified) return null
        return when (key) {
            "ArrowLeft" -> {
                DeckKeyCommand.StepSection(-1)
            }

            "ArrowRight" -> {
                DeckKeyCommand.StepSection(1)
            }

            "m", "M" -> {
                DeckKeyCommand.OpenMap
            }

            else -> {
                key.singleOrNull()?.digitToIntOrNull()?.takeIf { it in 1..DeckSection.entries.size }?.let {
                    DeckKeyCommand.GoToSection(DeckSection.entries[it - 1])
                }
            }
        }
    }

    /** The section [delta] places from [from], or null past either end. */
    fun step(
        from: DeckSection,
        delta: Int,
    ): DeckSection? = DeckSection.entries.getOrNull(from.ordinal + delta)

    /**
     * How many sections a drag of [dragLength] along the arc has turned it: one per label spacing
     * (a fifth of the arc), dragging left bringing later sections to the apex.
     */
    fun turned(dragLength: Double): Double = -dragLength / (0.2 * DeckGeometry.SECTION_LENGTH)

    /** The section at the apex when a drag of [dragLength] is released from [from], clamped to the ends. */
    fun settle(
        from: DeckSection,
        dragLength: Double,
    ): DeckSection {
        val index = (from.ordinal + turned(dragLength).roundToInt()).coerceIn(0, DeckSection.entries.lastIndex)
        return DeckSection.entries[index]
    }

    /** True when a drag is long enough to be a turn and not a tap that wobbled. */
    fun isTurn(dragLength: Double): Boolean = abs(dragLength) >= TURN_SLOP

    private const val TURN_SLOP = 8.0
}
