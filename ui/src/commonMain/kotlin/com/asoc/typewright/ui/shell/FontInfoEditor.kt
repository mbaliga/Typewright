// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.project.EditResult
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.UpdateFontInfo
import com.asoc.typewright.ui.draw.ExprScope

/** What an edit of Font info came to, in the terms the page needs to say so. */
sealed interface FontInfoOutcome {
    /** It went in; [note] says anything else it did. */
    data class Done(
        val note: String? = null,
    ) : FontInfoOutcome

    /** It would change nothing, for [reason]. */
    data class Unchanged(
        val reason: String,
    ) : FontInfoOutcome

    /** It was not done, for [message] in plain words. */
    data class Refused(
        val message: String,
    ) : FontInfoOutcome

    /** The message to show when it did not go in, or null when it did or was already so. */
    val problem: String? get() = (this as? Refused)?.message
}

/**
 * Font info's edits to the open project: one [UpdateFontInfo] through the [session] for each thing the person changes, so
 * each is one undo step, and the rules in [FontInfoRules] decide what is allowed. Font info is not a glyph, so a locked glyph
 * never stands in the way, and a project that cannot be edited (a newer format opened read-only) says why in the session's own words.
 */
class FontInfoEditor(
    private val session: ProjectSession,
    private val master: String,
) {
    /** The master's font info as it stands now. */
    fun info(): UfoFontInfo =
        session.state.value.font
            .master(master)
            ?.ufo
            ?.fontInfo ?: UfoFontInfo()

    /** What `=` can name in a number here, read from the font as it stands now. */
    fun scope(): ExprScope =
        ExprScopeOf(
            info(),
            session.state.value.font
                .master(master)
                ?.ufo
                ?.glyphs
                .orEmpty(),
        )

    /** Sets [field] to what was [typed]; a number may be `=` and an expression. */
    fun commit(
        field: FontInfoField,
        typed: String,
    ): FontInfoOutcome = apply(FontInfoRules.change(info(), field, typed, scope()))

    /** Writes the standard lines of [choice] when it has them; the person's own words ([LicenceChoice.PROPRIETARY], [LicenceChoice.OTHER]) are typed in the fields. */
    fun chooseLicence(choice: LicenceChoice): FontInfoOutcome = apply(FontInfoRules.chooseLicence(info(), choice))

    private fun apply(change: FontInfoChange): FontInfoOutcome =
        when (change) {
            is FontInfoChange.Refused -> {
                FontInfoOutcome.Refused(change.message)
            }

            is FontInfoChange.Unchanged -> {
                FontInfoOutcome.Unchanged(change.reason)
            }

            is FontInfoChange.Set -> {
                when (val result = session.execute(UpdateFontInfo(master, change.info))) {
                    is EditResult.Applied -> FontInfoOutcome.Done(change.note)
                    EditResult.NoChange -> FontInfoOutcome.Unchanged("That is the value already.")
                    is EditResult.Refused -> FontInfoOutcome.Refused(result.refusal.message)
                }
            }
        }
}
