// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.core.font.ufo.writeGlif
import com.asoc.typewright.core.geometry.Glyph
import kotlin.time.Instant

/** [FontCommandEngine.apply]'s outcome before law 1 has had a chance to refuse it. */
private sealed interface RawOutcome {
    data class Applied(
        val font: FontState,
        val changed: Set<GlyphRef>,
        val renamed: Map<GlyphRef, GlyphRef> = emptyMap(),
    ) : RawOutcome

    data object NoChange : RawOutcome

    data class Refused(
        val refusal: Refusal,
    ) : RawOutcome
}

/**
 * Applies one [FontCommand] to a [FontState], pure and total: same inputs, same output, no I/O,
 * no exception. [apply] is what [ProjectSession.execute] and [ProjectSession.simulate] both run.
 *
 * **Law 1 by effect, not by command (docs/PROJECT_MODEL.md §7.1).** Every command is computed
 * first as if no glyph were locked; the result is then checked against every glyph that was
 * LOCKED before and has changed (`!==`, so an unrelated command is free): if it was removed, or
 * its shape (contours and anchors; never advance width, unicodes, guidelines or name) differs
 * from before by anything but one shared horizontal offset, the whole command is refused as
 * [Refusal.Locked] and the original [FontState] is returned untouched. A translation that does
 * pass shifts the lock's approved snapshot by the same amount, so future diffs stay minimal.
 * [RenameGlyph] is the one command that moves a glyph's lock to a new [GlyphRef]; the check
 * follows that move rather than reading the rename as a removal.
 */
internal object FontCommandEngine {
    fun apply(
        font: FontState,
        command: FontCommand,
        now: Instant,
    ): EditResult = applyWithState(font, command, now).first

    /** [apply]'s result as `(EditResult, newFontState)`; the new state equals the old one unless [EditResult.Applied]. */
    fun applyWithState(
        font: FontState,
        command: FontCommand,
        now: Instant,
    ): Pair<EditResult, FontState> {
        val outcome = applyRaw(font, command, now)
        return when (outcome) {
            is RawOutcome.NoChange -> EditResult.NoChange to font
            is RawOutcome.Refused -> EditResult.Refused(outcome.refusal) to font
            is RawOutcome.Applied -> enforceLocks(font, outcome, command.label)
        }
    }

    /** The law-1 check (this file's own KDoc): the checked [EditResult] paired with the [FontState] it actually produced (approved snapshots included). */
    private fun enforceLocks(
        before: FontState,
        outcome: RawOutcome.Applied,
        label: String,
    ): Pair<EditResult, FontState> {
        var font = outcome.font
        for ((oldRef, lock) in before.locks) {
            if (lock.state != LockState.LOCKED) continue
            val newRef = outcome.renamed[oldRef] ?: oldRef
            val beforeGlyph = before.glyph(oldRef) ?: continue
            val afterGlyph = font.glyph(newRef)
            if (beforeGlyph === afterGlyph) continue
            if (afterGlyph == null) return EditResult.Refused(Refusal.Locked(oldRef)) to before
            val dx = GlyphShape.translationDx(beforeGlyph, afterGlyph) ?: return EditResult.Refused(Refusal.Locked(oldRef)) to before
            if (dx != 0) {
                val shiftedLock = font.locks.getValue(newRef)
                font = font.copy(locks = font.locks + (newRef to shiftedLock.copy(approved = GlyphShape.shiftX(shiftedLock.approved, dx))))
            }
        }
        return EditResult.Applied(label, outcome.changed) to font
    }

    private fun applyRaw(
        font: FontState,
        command: FontCommand,
        now: Instant,
    ): RawOutcome =
        when (command) {
            is MovePoints -> movePoints(font, command)
            is ReplaceOutline -> replaceOutline(font, command.glyph, command.contours)
            is SetAnchors -> updateGlyph(font, command.glyph) { it.copy(anchors = command.anchors) }
            is SetGlyphGuidelines -> updateGlyph(font, command.glyph) { it.copy(guidelines = command.guidelines) }
            is SetAdvance -> updateGlyph(font, command.glyph) { it.copy(advanceWidth = command.width) }
            is SetSidebearings -> setSidebearings(font, command)
            is SetUnicodes -> updateGlyphOrInvalid(font, command.glyph) { it.copy(unicodes = command.unicodes) }
            is AddGlyph -> addGlyph(font, command)
            is RemoveGlyph -> removeGlyph(font, command.glyph)
            is RenameGlyph -> renameGlyph(font, command)
            is SetKerning -> setKerning(font, command)
            is SetGroup -> setGroup(font, command)
            is SetFeatures -> setFeatures(font, command)
            is UpdateFontInfo -> updateFontInfo(font, command)
            is Approve -> approve(font, command.glyph, command.origin, now)
            is Unlock -> unlock(font, command.glyph, command.reason, now)
            is RevertToApproved -> revertToApproved(font, command.glyph, now)
            is AcceptTrace -> acceptTrace(font, command, now)
            is Batch -> batch(font, command, now)
        }

    // ---- point and outline edits --------------------------------------------------------------

    private fun movePoints(
        font: FontState,
        command: MovePoints,
    ): RawOutcome {
        if (command.dx == 0 && command.dy == 0) return RawOutcome.NoChange
        return updateGlyph(font, command.glyph) { glyph ->
            val contours = glyph.contours.toMutableList()
            for (ref in command.points) {
                if (ref.contour !in
                    contours.indices
                ) {
                    return RawOutcome.Refused(Refusal.Invalid("No contour ${ref.contour} in ${command.glyph.glyph}"))
                }
                val contour = contours[ref.contour]
                if (ref.point !in contour.points.indices) {
                    return RawOutcome.Refused(Refusal.Invalid("No point ${ref.point} in contour ${ref.contour} of ${command.glyph.glyph}"))
                }
                val points = contour.points.toMutableList()
                val point = points[ref.point]
                points[ref.point] = point.copy(point = point.point.copy(x = point.point.x + command.dx, y = point.point.y + command.dy))
                contours[ref.contour] = contour.copy(points = points)
            }
            glyph.copy(contours = contours)
        }
    }

    private fun replaceOutline(
        font: FontState,
        ref: GlyphRef,
        contours: List<com.asoc.typewright.core.geometry.Contour>,
    ): RawOutcome = updateGlyphOrInvalid(font, ref) { it.copy(contours = contours) }

    // ---- sidebearings ---------------------------------------------------------------------------

    private fun setSidebearings(
        font: FontState,
        command: SetSidebearings,
    ): RawOutcome {
        if (command.left == null && command.right == null) return RawOutcome.NoChange
        return updateGlyph(font, command.glyph) { glyph ->
            val xs = glyph.contours.flatMap { c -> c.points.map { it.point.x } }
            val minX = xs.minOrNull() ?: 0
            val maxX = xs.maxOrNull() ?: 0
            val dx = if (command.left != null) command.left - minX else 0
            val shifted = GlyphShape.shiftX(glyph, dx)
            val newMaxX = maxX + dx
            val width = if (command.right != null) newMaxX + command.right else glyph.advanceWidth + dx
            shifted.copy(advanceWidth = width)
        }
    }

    // ---- glyph list edits -------------------------------------------------------------------

    private fun addGlyph(
        font: FontState,
        command: AddGlyph,
    ): RawOutcome {
        val master = font.master(command.master) ?: return RawOutcome.Refused(Refusal.Invalid("No master \"${command.master}\""))
        if (master.ufo.glyphs.any { it.name == command.glyph.name }) return RawOutcome.Refused(Refusal.NameClash(command.glyph.name))
        val glyphs = master.ufo.glyphs.toMutableList()
        val at = command.index?.coerceIn(0, glyphs.size) ?: glyphs.size
        glyphs.add(at, command.glyph)
        return replaceMaster(font, master, master.ufo.copy(glyphs = glyphs), setOf(GlyphRef(master.id, command.glyph.name)))
    }

    private fun removeGlyph(
        font: FontState,
        ref: GlyphRef,
    ): RawOutcome {
        val master = font.master(ref.master) ?: return RawOutcome.Refused(Refusal.NoSuchGlyph(ref))
        if (master.ufo.glyphs.none { it.name == ref.glyph }) return RawOutcome.Refused(Refusal.NoSuchGlyph(ref))
        val glyphs = master.ufo.glyphs.filterNot { it.name == ref.glyph }
        val locks = font.locks - ref
        val newFont =
            replaceMaster(font, master, master.ufo.copy(glyphs = glyphs), setOf(ref)) as? RawOutcome.Applied
                ?: return RawOutcome.Refused(Refusal.Invalid("Could not remove ${ref.glyph}"))
        return newFont.copy(font = newFont.font.copy(locks = locks))
    }

    private fun renameGlyph(
        font: FontState,
        command: RenameGlyph,
    ): RawOutcome {
        val ref = command.glyph
        val master = font.master(ref.master) ?: return RawOutcome.Refused(Refusal.NoSuchGlyph(ref))
        val glyph = master.ufo.glyphs.find { it.name == ref.glyph } ?: return RawOutcome.Refused(Refusal.NoSuchGlyph(ref))
        if (command.newName == ref.glyph) return RawOutcome.NoChange
        if (master.ufo.glyphs.any { it.name == command.newName }) return RawOutcome.Refused(Refusal.NameClash(command.newName))
        val glyphs = master.ufo.glyphs.map { if (it.name == ref.glyph) it.copy(name = command.newName) else it }
        val kerning = renameInKerning(master.ufo.kerningInfo, ref.glyph, command.newName)
        val newRef = GlyphRef(ref.master, command.newName)
        val existingLock = font.locks[ref]
        val locks =
            if (existingLock != null) {
                (font.locks - ref) + (newRef to existingLock.copy(approved = existingLock.approved.copy(name = command.newName)))
            } else {
                font.locks
            }
        val newMaster = master.copy(ufo = master.ufo.copy(glyphs = glyphs, kerningInfo = kerning))
        val newMasters = font.masters.map { if (it.id == master.id) newMaster else it }
        val newFont = font.copy(masters = newMasters, locks = locks)
        val renamed = if (existingLock != null) mapOf(ref to newRef) else emptyMap()
        return RawOutcome.Applied(newFont, setOf(ref, newRef), renamed)
    }

    private fun renameInKerning(
        kerning: UfoKerning,
        from: String,
        to: String,
    ): UfoKerning {
        fun rename(name: String) = if (name == from) to else name
        val groups = kerning.groups.entries.associate { (name, members) -> rename(name) to members.map(::rename) }
        val pairs =
            kerning.kerning.entries.associate { (first, seconds) ->
                rename(first) to seconds.entries.associate { (second, value) -> rename(second) to value }
            }
        return kerning.copy(groups = groups, kerning = pairs)
    }

    // ---- kerning, groups, features, font info -----------------------------------------------

    private fun setKerning(
        font: FontState,
        command: SetKerning,
    ): RawOutcome {
        val master = font.master(command.master) ?: return RawOutcome.Refused(Refusal.Invalid("No master \"${command.master}\""))
        val current =
            master.ufo.kerningInfo.kerning[command.first]
                ?.get(command.second)
        if (current == command.value) return RawOutcome.NoChange
        val seconds = (master.ufo.kerningInfo.kerning[command.first] ?: emptyMap()).toMutableMap()
        if (command.value == null) seconds.remove(command.second) else seconds[command.second] = command.value
        val pairs =
            master.ufo.kerningInfo.kerning
                .toMutableMap()
        if (seconds.isEmpty()) pairs.remove(command.first) else pairs[command.first] = seconds
        return replaceMaster(font, master, master.ufo.copy(kerningInfo = master.ufo.kerningInfo.copy(kerning = pairs)), emptySet())
    }

    private fun setGroup(
        font: FontState,
        command: SetGroup,
    ): RawOutcome {
        val master = font.master(command.master) ?: return RawOutcome.Refused(Refusal.Invalid("No master \"${command.master}\""))
        if (master.ufo.kerningInfo.groups[command.name] == command.members) return RawOutcome.NoChange
        val groups =
            master.ufo.kerningInfo.groups
                .toMutableMap()
        if (command.members == null) groups.remove(command.name) else groups[command.name] = command.members
        return replaceMaster(font, master, master.ufo.copy(kerningInfo = master.ufo.kerningInfo.copy(groups = groups)), emptySet())
    }

    private fun setFeatures(
        font: FontState,
        command: SetFeatures,
    ): RawOutcome {
        val master = font.master(command.master) ?: return RawOutcome.Refused(Refusal.Invalid("No master \"${command.master}\""))
        if (master.ufo.kerningInfo.features == command.text) return RawOutcome.NoChange
        return replaceMaster(font, master, master.ufo.copy(kerningInfo = master.ufo.kerningInfo.copy(features = command.text)), emptySet())
    }

    private fun updateFontInfo(
        font: FontState,
        command: UpdateFontInfo,
    ): RawOutcome {
        val master = font.master(command.master) ?: return RawOutcome.Refused(Refusal.Invalid("No master \"${command.master}\""))
        if (master.ufo.fontInfo == command.info) return RawOutcome.NoChange
        return replaceMaster(font, master, master.ufo.copy(fontInfo = command.info), emptySet())
    }

    // ---- locks --------------------------------------------------------------------------------

    private fun approve(
        font: FontState,
        ref: GlyphRef,
        origin: ApprovalOrigin,
        now: Instant,
    ): RawOutcome {
        val glyph = font.glyph(ref) ?: return RawOutcome.Refused(Refusal.NoSuchGlyph(ref))
        val existing = font.locks[ref]
        val episodes = freezeOpenEpisode(existing, glyph, now)
        val lock = GlyphLock(LockState.LOCKED, origin, now, glyph, episodes)
        return RawOutcome.Applied(font.copy(locks = font.locks + (ref to lock)), emptySet())
    }

    private fun unlock(
        font: FontState,
        ref: GlyphRef,
        reason: String?,
        now: Instant,
    ): RawOutcome {
        val lock = font.locks[ref] ?: return RawOutcome.Refused(Refusal.NoSuchGlyph(ref))
        if (lock.state != LockState.LOCKED) return RawOutcome.NoChange
        val episode = UnlockEpisode(now, UnlockCause.USER, reason, relockedAt = null, frozenDiff = null)
        val updated = lock.copy(state = LockState.UNLOCKED, episodes = lock.episodes + episode)
        return RawOutcome.Applied(font.copy(locks = font.locks + (ref to updated)), emptySet())
    }

    private fun revertToApproved(
        font: FontState,
        ref: GlyphRef,
        now: Instant,
    ): RawOutcome {
        val lock = font.locks[ref] ?: return RawOutcome.Refused(Refusal.NoSuchGlyph(ref))
        val current = font.glyph(ref) ?: return RawOutcome.Refused(Refusal.NoSuchGlyph(ref))
        val restored = current.copy(contours = lock.approved.contours, anchors = lock.approved.anchors)
        val episodes = freezeOpenEpisode(lock, restored, now)
        val newLock = lock.copy(state = LockState.LOCKED, episodes = episodes)
        val outcome = updateGlyph(font, ref) { restored } as? RawOutcome.Applied ?: return RawOutcome.NoChange
        return outcome.copy(font = outcome.font.copy(locks = outcome.font.locks + (ref to newLock)))
    }

    private fun acceptTrace(
        font: FontState,
        command: AcceptTrace,
        now: Instant,
    ): RawOutcome {
        val glyph = font.glyph(command.glyph) ?: return RawOutcome.Refused(Refusal.NoSuchGlyph(command.glyph))
        val replaced = glyph.copy(contours = command.contours, advanceWidth = command.advanceWidth ?: glyph.advanceWidth)
        val replacedOutcome = updateGlyphOrInvalid(font, command.glyph) { replaced }
        if (replacedOutcome !is RawOutcome.Applied) return replacedOutcome
        val existing = replacedOutcome.font.locks[command.glyph]
        val episodes = freezeOpenEpisode(existing, replaced, now)
        val lock = GlyphLock(LockState.LOCKED, ApprovalOrigin.TRACE, now, replaced, episodes)
        return replacedOutcome.copy(font = replacedOutcome.font.copy(locks = replacedOutcome.font.locks + (command.glyph to lock)))
    }

    /** Episodes for a (re)lock: an already-open episode is frozen with the diff from its snapshot to [current], and dropped if that diff is empty. */
    private fun freezeOpenEpisode(
        existing: GlyphLock?,
        current: Glyph,
        now: Instant,
    ): List<UnlockEpisode> {
        if (existing == null) return emptyList()
        val open = existing.episodes.lastOrNull() ?: return existing.episodes
        if (open.relockedAt != null) return existing.episodes
        val oldText = writeGlif(existing.approved)
        val newText = writeGlif(current)
        if (oldText == newText) return existing.episodes.dropLast(1)
        val diff = UnifiedDiff.diff(oldText, newText, "approved", "current", null, null)
        val frozen = open.copy(relockedAt = now, frozenDiff = diff)
        return existing.episodes.dropLast(1) + frozen
    }

    // ---- batch ----------------------------------------------------------------------------------

    private fun batch(
        font: FontState,
        command: Batch,
        now: Instant,
    ): RawOutcome {
        var current = font
        val changed = mutableSetOf<GlyphRef>()
        val renamed = mutableMapOf<GlyphRef, GlyphRef>()
        for (sub in command.commands) {
            when (val outcome = applyRaw(current, sub, now)) {
                is RawOutcome.NoChange -> {}

                is RawOutcome.Refused -> {
                    return outcome
                }

                is RawOutcome.Applied -> {
                    current = outcome.font
                    changed += outcome.changed
                    for ((from, to) in outcome.renamed) {
                        val originalFrom = renamed.entries.find { it.value == from }?.key ?: from
                        renamed[originalFrom] = to
                    }
                }
            }
        }
        if (current == font) return RawOutcome.NoChange
        return RawOutcome.Applied(current, changed, renamed)
    }

    // ---- shared helpers -------------------------------------------------------------------------

    private inline fun updateGlyph(
        font: FontState,
        ref: GlyphRef,
        transform: (Glyph) -> Glyph,
    ): RawOutcome {
        val master = font.master(ref.master) ?: return RawOutcome.Refused(Refusal.NoSuchGlyph(ref))
        val glyph = master.ufo.glyphs.find { it.name == ref.glyph } ?: return RawOutcome.Refused(Refusal.NoSuchGlyph(ref))
        val updated = transform(glyph)
        if (updated == glyph) return RawOutcome.NoChange
        val glyphs = master.ufo.glyphs.map { if (it.name == ref.glyph) updated else it }
        return replaceMaster(font, master, master.ufo.copy(glyphs = glyphs), setOf(ref))
    }

    /** Like [updateGlyph], but a mixed-curve-format result (writeGlif's own check) is reported as [Refusal.MixedCurveFormats]. */
    private fun updateGlyphOrInvalid(
        font: FontState,
        ref: GlyphRef,
        transform: (Glyph) -> Glyph,
    ): RawOutcome {
        val master = font.master(ref.master) ?: return RawOutcome.Refused(Refusal.NoSuchGlyph(ref))
        val glyph = master.ufo.glyphs.find { it.name == ref.glyph } ?: return RawOutcome.Refused(Refusal.NoSuchGlyph(ref))
        val updated = transform(glyph)
        if (updated == glyph) return RawOutcome.NoChange
        val validation = runCatching { writeGlif(updated, master.ufo.lib.lineContourFormat) }
        if (validation.isFailure) return RawOutcome.Refused(Refusal.MixedCurveFormats(ref))
        val glyphs = master.ufo.glyphs.map { if (it.name == ref.glyph) updated else it }
        return replaceMaster(font, master, master.ufo.copy(glyphs = glyphs), setOf(ref))
    }

    private fun replaceMaster(
        font: FontState,
        master: Master,
        newUfo: com.asoc.typewright.core.font.ufo.UfoProject,
        changed: Set<GlyphRef>,
    ): RawOutcome {
        if (newUfo == master.ufo) return RawOutcome.NoChange
        val masters = font.masters.map { if (it.id == master.id) it.copy(ufo = newUfo) else it }
        return RawOutcome.Applied(font.copy(masters = masters), changed)
    }
}
