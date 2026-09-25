// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.geometry.Anchor
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Guideline
import com.asoc.typewright.project.scrapbook.ScrapbookPin

/**
 * One edit to the font part of a project: everything Draw, Space and Trace can do, plus glyph
 * add/remove/rename, font info and locks (docs/PROJECT_MODEL.md §4, §6). Every command is a pure
 * function of [FontState]; [ProjectSession.execute] is what runs one against the open project,
 * enforcing law 1 by its effect rather than by which of these it is.
 */
sealed interface FontCommand {
    /** The undo/redo entry's label. */
    val label: String

    /** Commands sharing a non-null key inside the session's coalescing window merge into one history entry (§6). */
    val coalesceKey: Any? get() = null
}

/** Moves [points] of [glyph] by ([dx], [dy]) font units; nothing else about the glyph changes. */
data class MovePoints(
    val glyph: GlyphRef,
    val points: Set<PointRef>,
    val dx: Int,
    val dy: Int,
) : FontCommand {
    override val label: String = "Move point" + if (points.size > 1) "s" else ""
    override val coalesceKey: Any = glyph to points
}

/** Replaces [glyph]'s outline with [contours] wholesale (a tool result, a boolean, a knife cut, …). */
data class ReplaceOutline(
    val glyph: GlyphRef,
    val contours: List<Contour>,
    override val label: String,
) : FontCommand

/** Replaces [glyph]'s anchors. */
data class SetAnchors(
    val glyph: GlyphRef,
    val anchors: List<Anchor>,
) : FontCommand {
    override val label: String = "Set anchors"
}

/** Replaces [glyph]'s own guidelines (not the font-wide ones, which live in font info). */
data class SetGlyphGuidelines(
    val glyph: GlyphRef,
    val guidelines: List<Guideline>,
) : FontCommand {
    override val label: String = "Set guidelines"
}

/** Sets [glyph]'s advance width to [width]. */
data class SetAdvance(
    val glyph: GlyphRef,
    val width: Int,
) : FontCommand {
    override val label: String = "Set advance"
    override val coalesceKey: Any = glyph to "advance"
}

/**
 * Sets [glyph]'s sidebearing(s): the contour (and anchors) shift so the left edge sits at [left],
 * and the advance width is set so the right edge sits at [right] (either null leaves that side as
 * it is). A left-only change is a pure horizontal translation, so it is allowed on a locked glyph
 * (§7.1); a right-only change only touches the advance width, which is never part of "shape".
 */
data class SetSidebearings(
    val glyph: GlyphRef,
    val left: Int?,
    val right: Int?,
) : FontCommand {
    override val label: String = "Set sidebearings"
    override val coalesceKey: Any = glyph to "sidebearings"
}

/** Replaces [glyph]'s Unicode code points; the first is primary. */
data class SetUnicodes(
    val glyph: GlyphRef,
    val unicodes: List<Int>,
) : FontCommand {
    override val label: String = "Set Unicode"
}

/** Adds [glyph] to [master], at [index] (null appends). */
data class AddGlyph(
    val master: String,
    val glyph: Glyph,
    val index: Int? = null,
) : FontCommand {
    override val label: String = "Add glyph"
}

/** Removes the glyph [glyph] names. Refused if it is locked (§7.1). */
data class RemoveGlyph(
    val glyph: GlyphRef,
) : FontCommand {
    override val label: String = "Remove glyph"
}

/** Renames [glyph] to [newName]: also renames kerning/group references and, if locked, moves the lock (always allowed, D2). */
data class RenameGlyph(
    val glyph: GlyphRef,
    val newName: String,
) : FontCommand {
    override val label: String = "Rename glyph"
}

/** Sets, or (null) removes, the kerning pair ([first], [second]) of [master]. */
data class SetKerning(
    val master: String,
    val first: String,
    val second: String,
    val value: Double?,
) : FontCommand {
    override val label: String = "Set kerning"
    override val coalesceKey: Any = Triple(master, first, second)
}

/** Sets, or (null members) removes, the group [name] of [master]. */
data class SetGroup(
    val master: String,
    val name: String,
    val members: List<String>?,
) : FontCommand {
    override val label: String = "Set group"
}

/** Sets, or (null) clears, [master]'s `features.fea` text. */
data class SetFeatures(
    val master: String,
    val text: String?,
) : FontCommand {
    override val label: String = "Set features"
    override val coalesceKey: Any = master to "features"
}

/** Replaces [master]'s font info wholesale. */
data class UpdateFontInfo(
    val master: String,
    val info: UfoFontInfo,
) : FontCommand {
    override val label: String = "Update font info"
    override val coalesceKey: Any = master to "font info" to changedFieldKey(info)
}

/** Approves (locks) [glyph] at its current outline, recording [origin]. */
data class Approve(
    val glyph: GlyphRef,
    val origin: ApprovalOrigin,
) : FontCommand {
    override val label: String = "Approve"
}

/** Unlocks [glyph] for editing, opening a new unlock episode. */
data class Unlock(
    val glyph: GlyphRef,
    val reason: String? = null,
) : FontCommand {
    override val label: String = "Unlock"
}

/** Restores [glyph] to its approved outline and relocks it. */
data class RevertToApproved(
    val glyph: GlyphRef,
) : FontCommand {
    override val label: String = "Revert to approved"
}

/** S10's accept: replaces [glyph]'s outline (and, if given, [advanceWidth]) and approves it, as one history entry. */
data class AcceptTrace(
    val glyph: GlyphRef,
    val contours: List<Contour>,
    val advanceWidth: Int? = null,
) : FontCommand {
    override val label: String = "Accept trace"
}

/** Several commands applied as one all-or-nothing history entry, labelled [label]. */
data class Batch(
    override val label: String,
    val commands: List<FontCommand>,
) : FontCommand

// UpdateFontInfo coalesces per changed field so scrubbing one number doesn't merge with another
// scrubbed a moment before or after it.
private fun changedFieldKey(info: UfoFontInfo): List<Any?> =
    listOf(
        info.familyName,
        info.styleName,
        info.unitsPerEm,
        info.ascender,
        info.descender,
        info.xHeight,
        info.capHeight,
        info.versionMajor,
        info.versionMinor,
        info.guidelines,
        info.copyright,
        info.trademark,
        info.openTypeNameDesigner,
        info.openTypeNameDesignerURL,
        info.openTypeNameManufacturer,
        info.openTypeNameManufacturerURL,
        info.openTypeNameLicense,
        info.openTypeNameLicenseURL,
        info.openTypeNamePreferredFamilyName,
        info.openTypeNamePreferredSubfamilyName,
        info.postscriptFontName,
        info.openTypeOS2VendorID,
        info.openTypeHheaAscender,
        info.openTypeHheaDescender,
        info.openTypeHheaLineGap,
        info.openTypeOS2TypoAscender,
        info.openTypeOS2TypoDescender,
        info.openTypeOS2TypoLineGap,
        info.openTypeOS2WinAscent,
        info.openTypeOS2WinDescent,
        info.other,
    )

/**
 * A change to the metadata half of a project: the scrapbook, the lessons, workbook confirmations,
 * the brief, scripts, comparison fonts and preferences. Never in the font undo/redo history
 * (§6); autosaved like everything else (§8).
 */
sealed interface MetaChange {
    /** Pins [pin] to the scrapbook; [image]/[imageExtension] are its bytes when [pin] is a photo, else null. */
    data class AddPin(
        val pin: ScrapbookPin,
        val image: ByteArray?,
        val imageExtension: String?,
    ) : MetaChange

    /** Replaces an existing pin (matched by id) with [pin]. */
    data class UpdatePin(
        val pin: ScrapbookPin,
    ) : MetaChange

    /** Removes the pin with this id. */
    data class RemovePin(
        val id: String,
    ) : MetaChange

    /** Saves a Workbook reflection on [task] of [workbook]. */
    data class AddReflection(
        val workbook: String,
        val task: Int,
        val text: String,
    ) : MetaChange

    /** Confirms or unconfirms [task] of [workbook] (§10.3). */
    data class SetTaskConfirmed(
        val workbook: String,
        val task: Int,
        val confirmed: Boolean,
    ) : MetaChange

    /** Replaces the project's brief. */
    data class SetBrief(
        val brief: Brief,
    ) : MetaChange

    /** Replaces the project's script list. */
    data class SetScripts(
        val scripts: List<String>,
    ) : MetaChange

    /** Replaces the comparison font list. */
    data class SetComparisonFonts(
        val fonts: List<ComparisonFont>,
    ) : MetaChange

    /** Replaces the working preferences. */
    data class SetPreferences(
        val preferences: ProjectPreferences,
    ) : MetaChange

    /** Renames the project ([ProjectManifest.name]; not the folder). */
    data class Rename(
        val name: String,
    ) : MetaChange
}

/** What running a [FontCommand] or [MetaChange] did. */
sealed interface EditResult {
    /** The command applied; [changed] is every glyph it touched (empty for a meta-only or font-wide command). */
    data class Applied(
        val label: String,
        val changed: Set<GlyphRef>,
    ) : EditResult

    /** The command was a no-op: it would not have changed the state. Nothing was recorded. */
    data object NoChange : EditResult

    /** The command was refused; state, history and autosave are untouched. */
    data class Refused(
        val refusal: Refusal,
    ) : EditResult
}

/** Why a command could not be applied. */
sealed interface Refusal {
    /** One plain English sentence; the UI may restyle it. */
    val message: String

    /** [glyph] is approved and locked; the effect would have changed its shape or removed it (§7.1). */
    data class Locked(
        val glyph: GlyphRef,
    ) : Refusal {
        override val message: String = "${glyph.glyph} is approved and locked. Unlock it to edit; the change is kept as a diff."
    }

    /** [glyph] does not exist in its master. */
    data class NoSuchGlyph(
        val glyph: GlyphRef,
    ) : Refusal {
        override val message: String = "There is no glyph named \"${glyph.glyph}\"."
    }

    /** A glyph already named [name] exists where one is being added or renamed to it. */
    data class NameClash(
        val name: String,
    ) : Refusal {
        override val message: String = "A glyph named \"$name\" already exists."
    }

    /** [glyph]'s new contours mix curve formats a `.glif` cannot keep straight (§9.2). */
    data class MixedCurveFormats(
        val glyph: GlyphRef,
    ) : Refusal {
        override val message: String = "${glyph.glyph}'s contours mix curve formats; one glyph holds one curve format."
    }

    /** A command's own data was invalid. */
    data class Invalid(
        override val message: String,
    ) : Refusal

    /** The session is read-only (a newer format, or a store that could not be written). */
    data class ReadOnly(
        override val message: String,
    ) : Refusal
}

/**
 * A live, coalesced edit in progress (a drag): every [update] applies immediately to [ProjectSession.state]
 * so the UI tracks it, but only one history entry is recorded, on [commit]. [cancel] restores the
 * state from before the gesture began. Autosave waits while a gesture is open (§8.1).
 */
interface Gesture {
    /** Applies [command] to the gesture's working state; refusals behave as [ProjectSession.execute]'s. */
    fun update(command: FontCommand): EditResult

    /** Records one history entry for the whole gesture and resumes autosave. */
    fun commit()

    /** Discards every update since [ProjectSession.beginGesture]; no history entry. */
    fun cancel()
}

/** The default coalescing window (docs/PROJECT_MODEL.md §6): 1,000 ms. */
internal const val DEFAULT_COALESCE_WINDOW_MS: Long = 1_000
