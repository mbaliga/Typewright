// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.project.ProjectPreferences

/** One control string offered on the Space page, and whether the person added it ([own]) or it comes with the Workbook. */
data class ControlString(
    val text: String,
    val own: Boolean,
)

/**
 * The strings Space sets: the two the Workbook's Spacing task names (`nnonnonon` for round against
 * straight, `HHOHOHOH` for capitals; docs/TYPEWRIGHT_HANDOFF.md M5 and `workbook-latin.yaml`, tasks 4
 * and 7), then the ones the person added, which live in the project's preferences. A string is only
 * ever text: Space sets whichever of its letters the font has drawn.
 */
object ControlStrings {
    /** The Workbook's own control strings, in the order the task names them. */
    val WORKBOOK: List<String> = listOf("nnonnonon", "HHOHOHOH")

    /** The longest string accepted, so a pasted paragraph is not taken for a control string. */
    const val MAX_LENGTH = 60

    /** The strings to offer: the Workbook's, then [preferences]' own, without repeats. */
    fun all(preferences: ProjectPreferences): List<ControlString> {
        val own = preferences.spaceControlStrings.filter { it !in WORKBOOK }.distinct()
        return WORKBOOK.map { ControlString(it, own = false) } + own.map { ControlString(it, own = true) }
    }

    /** [raw] as a control string: trimmed, inner runs of spaces made single; null where empty, too long or holding a control character. */
    fun clean(raw: String): String? {
        val text =
            raw
                .trim()
                .split(' ')
                .filter { it.isNotEmpty() }
                .joinToString(" ")
        if (text.isEmpty() || text.length > MAX_LENGTH) return null
        if (text.any { it.code < SPACE || it.code == DELETE }) return null
        return text
    }

    /** What adding [raw] comes to. */
    sealed interface Add {
        data class Added(
            val preferences: ProjectPreferences,
            val text: String,
        ) : Add

        data class Refused(
            val reason: String,
        ) : Add
    }

    /** [preferences] with [raw] added as the person's own string, or why it cannot be. */
    fun add(
        preferences: ProjectPreferences,
        raw: String,
    ): Add {
        val text = clean(raw) ?: return Add.Refused("Type up to $MAX_LENGTH letters on one line.")
        if (all(preferences).any { it.text == text }) return Add.Refused("$text is in the list already.")
        return Add.Added(preferences.copy(spaceControlStrings = preferences.spaceControlStrings + text), text)
    }

    /** [preferences] without the person's string [text]; the Workbook's cannot be removed. */
    fun remove(
        preferences: ProjectPreferences,
        text: String,
    ): ProjectPreferences = preferences.copy(spaceControlStrings = preferences.spaceControlStrings - text)

    private const val SPACE = 0x20
    private const val DELETE = 0x7F
}
