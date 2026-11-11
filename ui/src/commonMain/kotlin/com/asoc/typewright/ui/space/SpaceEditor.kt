// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.sidebearings
import com.asoc.typewright.project.EditResult
import com.asoc.typewright.project.FontCommand
import com.asoc.typewright.project.Gesture
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.GroupEdit
import com.asoc.typewright.project.KernKey
import com.asoc.typewright.project.KernScope
import com.asoc.typewright.project.KerningEdits
import com.asoc.typewright.project.KerningTable
import com.asoc.typewright.project.LockState
import com.asoc.typewright.project.MetaChange
import com.asoc.typewright.project.ProjectPreferences
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.Refusal
import com.asoc.typewright.project.SetAdvance
import com.asoc.typewright.project.SetFeatures
import com.asoc.typewright.project.SetSidebearings
import com.asoc.typewright.project.Unlock
import com.asoc.typewright.ui.draw.ExprResult
import com.asoc.typewright.ui.draw.ExprScope
import com.asoc.typewright.ui.draw.NumericExpr
import kotlin.math.round
import kotlin.math.roundToInt

/** The numbers Space edits: the selected glyph's sidebearings and advance, and the selected pair's kerning. */
enum class SpaceField(
    val label: String,
) {
    LEFT("left"),
    RIGHT("right"),
    ADVANCE("advance"),
    KERN("kern"),
}

/** What an edit came to, in the terms the screen needs to say so. */
sealed interface SpaceOutcome {
    /** It went in; [note] says anything the person should know it did. */
    data class Done(
        val note: String? = null,
    ) : SpaceOutcome

    /** It would change nothing. */
    data class Unchanged(
        val reason: String,
    ) : SpaceOutcome

    /** It was not done, for [message] in plain words. */
    data class Refused(
        val message: String,
    ) : SpaceOutcome

    /** The project refused it because [glyph] is approved and locked (law 1); the screen offers Unlock. */
    data class Locked(
        val glyph: String,
        val message: String,
    ) : SpaceOutcome
}

/** Whether the project is as the edit asked: it went in, or was so already. A refused edit left it as it was, so what was typed to ask for it must stay. */
val SpaceOutcome.went: Boolean get() = this is SpaceOutcome.Done || this is SpaceOutcome.Unchanged

/**
 * What was typed into a number field: the number it comes to, or why it does not come to one. [units] is the whole
 * number a sidebearing or advance takes (integers at rest); [exact] keeps the fraction a kerning value may carry,
 * so typing -12.5 into a kern field writes -12.5 and not -13.
 */
sealed interface SpaceValue {
    data class Units(
        val units: Int,
        val exact: Double = units.toDouble(),
    ) : SpaceValue {
        /** The number to write to [field]: whole for a sidebearing or advance, as typed (to two decimals) for kerning. */
        fun forField(field: SpaceField): Double = if (field == SpaceField.KERN) exact else units.toDouble()
    }

    data class Bad(
        val message: String,
    ) : SpaceValue
}

/**
 * Space's edits to the open project, each through the [session] so it is one undo step: the
 * sidebearings and advance of a glyph, the kerning of a pair at the level the groups put it, kerning
 * groups, `features.fea` and the person's own control strings. Sidebearings are measured the way the
 * Draw inspector measures them (core-geometry's [sidebearings]) and set with the same
 * [SetSidebearings] command, so a number set here reads back identically there. A glyph that is
 * approved can still be spaced: spacing slides or widens a drawing without redrawing it (the project's
 * lock compares shape modulo one horizontal shift), and if the project ever refuses, the outcome is
 * [SpaceOutcome.Locked] and the screen offers Unlock.
 *
 * A drag is a session [Gesture]: [beginScrub], any number of [scrubTo], then [endScrub]. Whatever
 * ends a drag must call [endScrub]; calling it again, or with no drag open, does nothing.
 */
class SpaceEditor(
    private val session: ProjectSession,
    private val master: String,
) {
    private var gesture: Gesture? = null
    private var scrub: Scrub? = null

    private class Scrub(
        val field: SpaceField,
        val glyph: String,
        val pair: Pair<String, String>?,
        val key: KernKey?,
        /** The value the drag began on, exactly as stored: a drag adds to it and never rewrites it from a rounded copy. */
        val start: Double,
    )

    private val ufo: UfoProject? get() =
        session.state.value.font
            .master(master)
            ?.ufo

    /** The glyph called [name] in the master, or null. */
    fun glyph(name: String): Glyph? = ufo?.glyphs?.firstOrNull { it.name == name }

    /** The master's kerning as it stands now. */
    fun kerning(): KerningTable = KerningTable(ufo?.kerningInfo ?: UfoKerning())

    /** Whether [name] is approved and locked. */
    fun isLocked(name: String): Boolean = session.lockOf(GlyphRef(master, name))?.state == LockState.LOCKED

    /** The preferences the project holds. */
    fun preferences(): ProjectPreferences = session.state.value.meta.manifest.preferences

    /**
     * The value [field] has now for [glyph] (and [pair], for kerning), exactly as the project stores it, or null where it has
     * none (a glyph with no drawing has no sidebearings). Kerning may be fractional, so this is a Double: nudges and drags
     * add to it, and rounding is only ever for showing it.
     */
    fun valueOf(
        field: SpaceField,
        glyph: String?,
        pair: Pair<String, String>?,
    ): Double? =
        when (field) {
            SpaceField.KERN -> {
                pair?.let { kerning().value(it.first, it.second) }
            }

            else -> {
                val g = glyph?.let(::glyph)
                when (field) {
                    SpaceField.LEFT -> g?.sidebearings()?.left?.toDouble()
                    SpaceField.RIGHT -> g?.sidebearings()?.right?.toDouble()
                    else -> g?.advanceWidth?.toDouble()
                }
            }
        }

    // ---- one value at a time -----------------------------------------------------------------------

    /** Sets [field] of [glyph] to [units] (for a sidebearing, as core-geometry measures it). */
    fun setGlyphField(
        field: SpaceField,
        glyph: String,
        units: Int,
    ): SpaceOutcome {
        endScrub(commit = true)
        val ref = GlyphRef(master, glyph)
        val command: FontCommand =
            when (field) {
                SpaceField.LEFT -> {
                    if (glyph(glyph)?.sidebearings() == null) return noDrawing(glyph)
                    SetSidebearings(ref, left = units, right = null)
                }

                SpaceField.RIGHT -> {
                    if (glyph(glyph)?.sidebearings() == null) return noDrawing(glyph)
                    SetSidebearings(ref, left = null, right = units)
                }

                SpaceField.ADVANCE -> {
                    SetAdvance(ref, units)
                }

                SpaceField.KERN -> {
                    return SpaceOutcome.Refused("Kerning belongs to a pair of glyphs.")
                }
            }
        return outcome(session.execute(command), glyph, "That is the value already.")
    }

    /**
     * Sets the kerning of ([first], [second]) to [units] at the level [scope] chooses. When that level is a
     * group, the note says so and how many glyph pairs follow, so a value never lands on more than was meant unseen.
     */
    fun setKern(
        first: String,
        second: String,
        units: Int,
        scope: KernScope,
    ): SpaceOutcome = setKern(first, second, units.toDouble(), scope)

    /** [setKern] for a value that may carry a fraction, as stored kerning may. */
    fun setKern(
        first: String,
        second: String,
        value: Double,
        scope: KernScope,
    ): SpaceOutcome {
        endScrub(commit = true)
        val table = kerning()
        val key = KerningEdits.targetKey(table, first, second, scope)
        val command =
            KerningEdits.setAt(master, table, first, second, key, value)
                ?: return SpaceOutcome.Unchanged("That is the value already.")
        val result = outcome(session.execute(command), first, "That is the value already.")
        if (result !is SpaceOutcome.Done) return result
        return SpaceOutcome.Done(if (table.isGroupLevel(key)) "Written to ${table.describe(key)}." else null)
    }

    /** Removes the kerning entry ([first], [second]) reads from, under [scope]; the note says what the pair gets instead. */
    fun removeKern(
        first: String,
        second: String,
        scope: KernScope,
    ): SpaceOutcome {
        endScrub(commit = true)
        val table = kerning()
        val none = "There is no kerning to remove here."
        val command = KerningEdits.remove(master, table, first, second, scope) ?: return SpaceOutcome.Unchanged(none)
        val removed = table.resolve(first, second).key
        val result = outcome(session.execute(command), first, none)
        if (result !is SpaceOutcome.Done) return result
        val after = kerning().resolve(first, second)
        val gone = if (removed != null && table.isGroupLevel(removed)) "Removed ${table.describe(removed)}. " else ""
        val now =
            if (after.value != 0.0) {
                "The ${after.sourceWord()} value ${formatUnits(after.value)} applies to this pair now."
            } else {
                "This pair is not kerned now."
            }
        return SpaceOutcome.Done(gone + now)
    }

    /** Whether [removeKern] for ([first], [second]) under [scope] would take an entry away: false where the pair gets nothing from that level. */
    fun canRemoveKern(
        first: String,
        second: String,
        scope: KernScope,
    ): Boolean = KerningEdits.remove(master, kerning(), first, second, scope) != null

    /** What [text] comes to as a number of font units: a number, or `=` and an expression (`=n.lsb`, `=xheight/10`); a bare `lsb` is [glyph]'s. */
    fun evaluate(
        text: String,
        glyph: String?,
    ): SpaceValue =
        when (val result = NumericExpr.evaluate(text, exprScope(glyph))) {
            is ExprResult.Error -> SpaceValue.Bad(result.message)
            is ExprResult.Value -> SpaceValue.Units(result.units, exact = round(result.value * HUNDRED) / HUNDRED)
        }

    /**
     * What was typed into the field [field]. Null when it went in, otherwise the plain-words reason it did
     * not (for an expression that does not come to a number, or an edit the project refused).
     */
    fun commit(
        field: SpaceField,
        text: String,
        glyph: String?,
        pair: Pair<String, String>?,
        scope: KernScope,
    ): String? =
        when (val value = evaluate(text, glyph)) {
            is SpaceValue.Bad -> {
                value.message
            }

            is SpaceValue.Units -> {
                when (val done = apply(field, value.forField(field), glyph, pair, scope)) {
                    is SpaceOutcome.Done, is SpaceOutcome.Unchanged -> null
                    is SpaceOutcome.Refused -> done.message
                    is SpaceOutcome.Locked -> done.message
                }
            }
        }

    /** Moves [field] by [delta] units from where it is stored, so a kerning value with a fraction keeps it. */
    fun nudge(
        field: SpaceField,
        delta: Int,
        glyph: String?,
        pair: Pair<String, String>?,
        scope: KernScope,
    ): SpaceOutcome {
        val now = valueOf(field, glyph, pair) ?: return if (field == SpaceField.KERN) noPair() else noDrawing(glyph ?: "This glyph")
        return apply(field, now + delta, glyph, pair, scope)
    }

    /** Sets [field] to [units] on [glyph] (or [pair], for kerning) at [scope]'s level. */
    fun apply(
        field: SpaceField,
        units: Int,
        glyph: String?,
        pair: Pair<String, String>?,
        scope: KernScope,
    ): SpaceOutcome = apply(field, units.toDouble(), glyph, pair, scope)

    /** [apply] for a value that may carry a fraction. A sidebearing or advance is a whole number, so it is rounded to one. */
    fun apply(
        field: SpaceField,
        value: Double,
        glyph: String?,
        pair: Pair<String, String>?,
        scope: KernScope,
    ): SpaceOutcome =
        when (field) {
            SpaceField.KERN -> pair?.let { setKern(it.first, it.second, value, scope) } ?: noPair()
            else -> glyph?.let { setGlyphField(field, it, value.roundToInt()) } ?: SpaceOutcome.Refused("Pick a glyph first.")
        }

    /** What `=` expressions in a field can name: the font's metrics, and any glyph's advance and sidebearings (`n.lsb`); a bare `lsb` is [current]'s. */
    fun exprScope(current: String?): ExprScope =
        object : ExprScope {
            override fun metric(name: String): Double? {
                val info = ufo?.fontInfo ?: return null
                return when (name) {
                    "xheight" -> info.xHeight?.toDouble()
                    "capheight" -> info.capHeight?.toDouble()
                    "ascender" -> info.ascender?.toDouble()
                    "descender" -> info.descender?.toDouble()
                    "baseline" -> 0.0
                    "upm" -> (info.unitsPerEm ?: DEFAULT_UPM).toDouble()
                    else -> null
                }
            }

            override fun glyphValue(
                glyph: String?,
                property: String,
            ): Double? {
                val g = (glyph ?: current)?.let(::glyph) ?: return null
                return when (property) {
                    "adv" -> g.advanceWidth.toDouble()
                    "lsb" -> g.sidebearings()?.left?.toDouble()
                    "rsb" -> g.sidebearings()?.right?.toDouble()
                    else -> null
                }
            }
        }

    // ---- a drag --------------------------------------------------------------------------------

    /** Whether a drag is open. */
    val scrubbing: Boolean get() = scrub != null

    /**
     * Opens a drag of [field] on [glyph] (or of the kerning of [pair] at [scope]'s level): one session gesture,
     * so everything dragged until [endScrub] is a single undo step. Null means it began; anything else is why not.
     */
    fun beginScrub(
        field: SpaceField,
        glyph: String?,
        pair: Pair<String, String>?,
        scope: KernScope,
    ): SpaceOutcome? {
        endScrub(commit = true)
        val start = valueOf(field, glyph, pair)
        if (start == null) return if (field == SpaceField.KERN) noPair() else noDrawing(glyph ?: "This glyph")
        val key = if (field == SpaceField.KERN) pair?.let { KerningEdits.targetKey(kerning(), it.first, it.second, scope) } else null
        gesture = session.beginGesture(if (field == SpaceField.KERN) "Set kerning" else "Set ${field.label}")
        scrub = Scrub(field, glyph ?: pair?.first.orEmpty(), pair, key, start)
        return null
    }

    /** The drag is now [units] from where it began. False when the project refused and the drag is over. */
    fun scrubTo(units: Int): Boolean {
        val open = gesture
        val drag = scrub
        if (open == null || drag == null || !open.isOpen) {
            endScrub(commit = false)
            return false
        }
        // Added to the value as it is stored: a drag that comes back to where it began writes what was there, fraction and all.
        val target = drag.start + units
        val ref = GlyphRef(master, drag.glyph)
        val command: FontCommand? =
            when (drag.field) {
                SpaceField.LEFT -> {
                    SetSidebearings(ref, left = target.roundToInt(), right = null)
                }

                SpaceField.RIGHT -> {
                    SetSidebearings(ref, left = null, right = target.roundToInt())
                }

                SpaceField.ADVANCE -> {
                    SetAdvance(ref, target.roundToInt())
                }

                SpaceField.KERN -> {
                    val pair = drag.pair
                    val key = drag.key
                    if (pair == null ||
                        key == null
                    ) {
                        null
                    } else {
                        KerningEdits.setAt(master, kerning(), pair.first, pair.second, key, target)
                    }
                }
            }
        if (command != null && open.update(command) is EditResult.Refused) {
            endScrub(commit = false)
            return false
        }
        return true
    }

    /** Ends the drag: one undo step when [commit], otherwise as if it never began. Does nothing if no drag is open. */
    fun endScrub(commit: Boolean) {
        val open = gesture
        gesture = null
        scrub = null
        if (open == null || !open.isOpen) return
        if (commit) open.commit() else open.cancel()
    }

    // ---- groups, features, strings, locks --------------------------------------------------------------

    /** Runs a group edit made by `KerningGroupEdits`. */
    fun applyGroupEdit(edit: GroupEdit): SpaceOutcome {
        endScrub(commit = true)
        return when (edit) {
            is GroupEdit.Refused -> SpaceOutcome.Refused(edit.reason)
            is GroupEdit.Unchanged -> SpaceOutcome.Unchanged(edit.reason)
            is GroupEdit.Run -> outcome(session.execute(edit.command), "", "Nothing changed.", note = edit.note)
        }
    }

    /** Replaces `features.fea` with [text]; null removes the file. */
    fun setFeatures(text: String?): SpaceOutcome {
        endScrub(commit = true)
        return outcome(session.execute(SetFeatures(master, text)), "", "features.fea is the same as it was.")
    }

    /** Replaces the project's preferences (the control strings live there). */
    fun setPreferences(preferences: ProjectPreferences): SpaceOutcome =
        if (preferences == preferences()) {
            SpaceOutcome.Unchanged("Nothing changed.")
        } else {
            when (val result = session.update(MetaChange.SetPreferences(preferences))) {
                is EditResult.Applied -> SpaceOutcome.Done()
                is EditResult.NoChange -> SpaceOutcome.Unchanged("Nothing changed.")
                is EditResult.Refused -> SpaceOutcome.Refused(result.refusal.message)
            }
        }

    /** Unlocks [glyph] for editing; the project records the unlock and keeps the diff. */
    fun unlock(glyph: String): SpaceOutcome =
        when (val result = session.execute(Unlock(GlyphRef(master, glyph)))) {
            is EditResult.Applied -> SpaceOutcome.Done("$glyph is unlocked. Edits are kept as a diff against the approved outline.")
            is EditResult.NoChange -> SpaceOutcome.Unchanged("$glyph is not locked.")
            is EditResult.Refused -> SpaceOutcome.Refused(result.refusal.message)
        }

    private fun noDrawing(glyph: String) = SpaceOutcome.Refused("$glyph has no drawing yet, so it has no sidebearings to set.")

    private fun noPair() = SpaceOutcome.Refused("Pick two letters set side by side to kern.")

    private fun outcome(
        result: EditResult,
        glyph: String,
        unchanged: String,
        note: String? = null,
    ): SpaceOutcome =
        when (result) {
            is EditResult.Applied -> {
                SpaceOutcome.Done(note)
            }

            is EditResult.NoChange -> {
                SpaceOutcome.Unchanged(unchanged)
            }

            is EditResult.Refused -> {
                if (result.refusal is Refusal.Locked) {
                    SpaceOutcome.Locked(glyph, result.refusal.message)
                } else {
                    SpaceOutcome.Refused(result.refusal.message)
                }
            }
        }

    private companion object {
        const val DEFAULT_UPM = 1000
        const val HUNDRED = 100.0
    }
}
