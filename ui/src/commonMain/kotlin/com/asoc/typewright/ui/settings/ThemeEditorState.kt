// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.asoc.typewright.ui.theme.Theme
import com.asoc.typewright.ui.theme.ThemeCodec
import com.asoc.typewright.ui.theme.ThemeGuardrails
import com.asoc.typewright.ui.theme.ThemeProblem
import com.asoc.typewright.ui.theme.ThemeRead
import com.asoc.typewright.ui.tokens.Argb
import com.asoc.typewright.ui.tokens.ThemeRole

/**
 * The custom theme editor's state (S22): the [draft] being edited, what it started as
 * ([original], for "reset role" and for knowing whether anything would be lost), and the text in
 * each hex field, which may be half-typed. The guardrails run on every change; saving is blocked
 * while any [problems] remain.
 *
 * Built-in themes are read-only, so the editor always starts from a copy: a new theme is a
 * duplicate of [base] under [freshId]; an existing custom theme is edited under its own id.
 */
class ThemeEditorState(
    private val base: Theme,
    freshId: String,
    existing: Theme? = null,
) {
    /** What the editor opened with. */
    val original: Theme = existing ?: base.duplicate(freshId, "${base.name} copy")

    var draft: Theme by mutableStateOf(original)
        private set

    private var typed: Map<ThemeRole, String> by mutableStateOf(emptyMap())

    /** What is in [role]'s hex field: what was typed, or the colour as hex. */
    fun hexText(role: ThemeRole): String = typed[role] ?: ThemeCodec.hex(draft.color(role))

    /** Everything wrong with the draft; saving waits until this is empty. */
    val problems: List<ThemeProblem> get() = ThemeGuardrails.check(draft)

    /** True when saving or closing would change or lose something. */
    val dirty: Boolean get() = draft != original

    /** The first reason saving is blocked, in one line, or null when it is not. */
    val blockedBecause: String? get() = problems.firstOrNull()?.message ?: nameProblem()

    private fun nameProblem(): String? = if (draft.name.isBlank()) "Give the theme a name." else null

    /** Types into [role]'s hex field: the colour changes as soon as the text is a whole #RRGGBB. */
    fun typeHex(
        role: ThemeRole,
        text: String,
    ) {
        typed = typed + (role to text)
        ThemeCodec.parseHex(text)?.let { draft = draft.with(role, it) }
    }

    fun setColor(
        role: ThemeRole,
        color: Argb,
    ) {
        typed = typed - role
        draft = draft.with(role, color)
    }

    /** Puts [role] back to what the editor opened with. */
    fun resetRole(role: ThemeRole) {
        typed = typed - role
        draft = draft.reset(role, original)
    }

    /** True when [role] differs from what the editor opened with. */
    fun differs(role: ThemeRole): Boolean = draft.color(role) != original.color(role)

    /** Puts the whole theme back to what the editor opened with, keeping its name. */
    fun resetTheme() {
        typed = emptyMap()
        draft = original.copy(name = draft.name)
    }

    fun rename(name: String) {
        draft = draft.copy(name = name)
    }

    fun setDark(dark: Boolean) {
        draft = draft.copy(dark = dark)
    }

    fun setGrain(grain: Double) {
        draft = draft.copy(grain = grain.coerceIn(0.0, Theme.MAX_GRAIN))
    }

    fun setGridOpacity(opacity: Double) {
        draft = draft.copy(gridOpacity = opacity.coerceIn(0.0, Theme.MAX_GRID_OPACITY))
    }

    /** The draft as the JSON file that exports it. */
    fun export(): String = ThemeCodec.encode(draft)

    /**
     * Replaces the draft with the theme in [json], keeping the id the editor has so an import
     * never overwrites another theme. Returns why it could not be read, or null.
     */
    fun import(json: String): String? =
        when (val read = ThemeCodec.decode(json)) {
            is ThemeRead.Bad -> {
                read.reason
            }

            is ThemeRead.Ok -> {
                typed = emptyMap()
                draft = read.theme.copy(id = original.id)
                null
            }
        }
}
