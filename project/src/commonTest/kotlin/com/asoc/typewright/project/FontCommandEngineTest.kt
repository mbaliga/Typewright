// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.geometry.Anchor
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [FontCommandEngine]: every command applied to an unlocked glyph, and the law-1 lock matrix
 * (docs/PROJECT_MODEL.md §7.1) — enforcement is by effect, so this exercises every command
 * against a LOCKED glyph rather than special-casing any of them. Runs on the JVM and Wasm.
 */
class FontCommandEngineTest {
    private val ref = GlyphRef("regular", "A")
    private val now = Fixtures.EPOCH

    private fun apply(
        font: FontState,
        command: FontCommand,
    ) = FontCommandEngine.applyWithState(font, command, now)

    private fun lockedFont(): FontState {
        val font = Fixtures.fontState()
        val (result, locked) = apply(font, Approve(ref, ApprovalOrigin.DRAW))
        assertIs<EditResult.Applied>(result)
        return locked
    }

    // ---- Ordinary edits on an unlocked glyph -------------------------------------------------

    @Test
    fun movePointsMovesOnlyTheNamedPointsAndNothingElse() {
        val font = Fixtures.fontState()
        val before = font.glyph(ref)!!
        val (result, after) = apply(font, MovePoints(ref, setOf(PointRef(0, 0)), 10, 0))
        assertIs<EditResult.Applied>(result)
        val glyph = after.glyph(ref)!!
        assertEquals(
            before.contours[0]
                .points[0]
                .point.x + 10,
            glyph.contours[0]
                .points[0]
                .point.x,
        )
        for (i in before.contours[0].points.indices) {
            if (i == 0) continue
            assertEquals(before.contours[0].points[i], glyph.contours[0].points[i])
        }
        // Every other glyph, in every other master, is untouched.
        for (name in listOf("B", "C")) {
            assertEquals(font.glyph(GlyphRef("regular", name)), after.glyph(GlyphRef("regular", name)))
        }
    }

    @Test
    fun movePointsByZeroIsANoOp() {
        val font = Fixtures.fontState()
        val (result, _) = apply(font, MovePoints(ref, setOf(PointRef(0, 0)), 0, 0))
        assertEquals(EditResult.NoChange, result)
    }

    @Test
    fun replaceOutlineReplacesTheContoursOfOneGlyph() {
        val font = Fixtures.fontState()
        val newContours = listOf(Fixtures.squareContour(0, 0, 100, 100))
        val (result, after) = apply(font, ReplaceOutline(ref, newContours, "Boolean union"))
        assertIs<EditResult.Applied>(result)
        assertEquals(newContours, after.glyph(ref)!!.contours)
    }

    @Test
    fun replaceOutlineMixingCurveFormatsIsRefused() {
        val font = Fixtures.fontState()
        val cubic = Fixtures.squareContour()
        val quadratic = Contour(listOf(ContourPoint(Point(0, 0), true), ContourPoint(Point(10, 10), false)), CurveFormat.QUADRATIC)
        val (result, _) = apply(font, ReplaceOutline(ref, listOf(cubic, quadratic), "Mixed"))
        assertEquals(EditResult.Refused(Refusal.MixedCurveFormats(ref)), result)
    }

    @Test
    fun setAnchorsReplacesAnchors() {
        val font = Fixtures.fontState()
        val anchors = listOf(Anchor("top", Point(250, 700)))
        val (result, after) = apply(font, SetAnchors(ref, anchors))
        assertIs<EditResult.Applied>(result)
        assertEquals(anchors, after.glyph(ref)!!.anchors)
    }

    @Test
    fun setAdvanceChangesOnlyTheAdvanceWidth() {
        val font = Fixtures.fontState()
        val (result, after) = apply(font, SetAdvance(ref, 600))
        assertIs<EditResult.Applied>(result)
        assertEquals(600, after.glyph(ref)!!.advanceWidth)
        assertEquals(font.glyph(ref)!!.contours, after.glyph(ref)!!.contours)
    }

    @Test
    fun setSidebearingsLeftShiftsTheOutlineAndKeepsTheRightSidebearing() {
        val font = Fixtures.fontState()
        val before = font.glyph(ref)!!
        val rightBefore = before.advanceWidth - before.contours.flatMap { it.points }.maxOf { it.point.x }
        val (result, after) = apply(font, SetSidebearings(ref, left = 80, right = null))
        assertIs<EditResult.Applied>(result)
        val glyph = after.glyph(ref)!!
        assertEquals(80, glyph.contours.flatMap { it.points }.minOf { it.point.x })
        val rightAfter = glyph.advanceWidth - glyph.contours.flatMap { it.points }.maxOf { it.point.x }
        assertEquals(rightBefore, rightAfter)
    }

    @Test
    fun setSidebearingsRightOnlyChangesTheAdvanceWidth() {
        val font = Fixtures.fontState()
        val before = font.glyph(ref)!!
        val (result, after) = apply(font, SetSidebearings(ref, left = null, right = 20))
        assertIs<EditResult.Applied>(result)
        val glyph = after.glyph(ref)!!
        assertEquals(before.contours, glyph.contours)
        assertEquals(glyph.contours.flatMap { it.points }.maxOf { it.point.x } + 20, glyph.advanceWidth)
    }

    @Test
    fun setUnicodesReplacesTheList() {
        val font = Fixtures.fontState()
        val (result, after) = apply(font, SetUnicodes(ref, listOf(0x41)))
        assertIs<EditResult.Applied>(result)
        assertEquals(listOf(0x41), after.glyph(ref)!!.unicodes)
    }

    @Test
    fun addGlyphAppendsANewGlyph() {
        val font = Fixtures.fontState()
        val (result, after) = apply(font, AddGlyph("regular", Fixtures.glyph("D")))
        assertIs<EditResult.Applied>(result)
        assertEquals(
            listOf("A", "B", "C", "D"),
            after.masters
                .single()
                .ufo.glyphs
                .map { it.name },
        )
    }

    @Test
    fun addGlyphWithAClashingNameIsRefused() {
        val font = Fixtures.fontState()
        val (result, _) = apply(font, AddGlyph("regular", Fixtures.glyph("A")))
        assertEquals(EditResult.Refused(Refusal.NameClash("A")), result)
    }

    @Test
    fun removeGlyphOnAnUnlockedGlyphRemovesIt() {
        val font = Fixtures.fontState()
        val (result, after) = apply(font, RemoveGlyph(ref))
        assertIs<EditResult.Applied>(result)
        assertNull(after.glyph(ref))
    }

    @Test
    fun removeGlyphOnAMissingGlyphIsRefused() {
        val font = Fixtures.fontState()
        val (result, _) = apply(font, RemoveGlyph(GlyphRef("regular", "Z")))
        assertEquals(EditResult.Refused(Refusal.NoSuchGlyph(GlyphRef("regular", "Z"))), result)
    }

    @Test
    fun renameGlyphRenamesTheGlyphAndItsKerningReferences() {
        val font = Fixtures.fontState()
        val (kernResult, kerned) = apply(font, SetKerning("regular", "A", "B", -40.0))
        assertIs<EditResult.Applied>(kernResult)
        val (result, after) = apply(kerned, RenameGlyph(ref, "Aacute"))
        assertIs<EditResult.Applied>(result)
        assertNull(after.glyph(ref))
        assertEquals("Aacute", after.glyph(GlyphRef("regular", "Aacute"))!!.name)
        assertEquals(
            -40.0,
            after.masters
                .single()
                .ufo.kerningInfo.kerning
                .getValue("Aacute")
                .getValue("B"),
        )
    }

    @Test
    fun renameGlyphToAnExistingNameIsRefused() {
        val font = Fixtures.fontState()
        val (result, _) = apply(font, RenameGlyph(ref, "B"))
        assertEquals(EditResult.Refused(Refusal.NameClash("B")), result)
    }

    @Test
    fun setKerningSetsAndRemovesAPair() {
        val font = Fixtures.fontState()
        val (setResult, withKerning) = apply(font, SetKerning("regular", "A", "B", -40.0))
        assertIs<EditResult.Applied>(setResult)
        assertEquals(
            -40.0,
            withKerning.masters
                .single()
                .ufo.kerningInfo.kerning
                .getValue("A")
                .getValue("B"),
        )
        val (removeResult, cleared) = apply(withKerning, SetKerning("regular", "A", "B", null))
        assertIs<EditResult.Applied>(removeResult)
        assertTrue(
            cleared.masters
                .single()
                .ufo.kerningInfo.kerning
                .isEmpty(),
        )
    }

    @Test
    fun updateFontInfoReplacesFontInfo() {
        val font = Fixtures.fontState()
        val newInfo =
            font.masters
                .single()
                .ufo.fontInfo
                .copy(openTypeNameDesigner = "Golden Path")
        val (result, after) = apply(font, UpdateFontInfo("regular", newInfo))
        assertIs<EditResult.Applied>(result)
        assertEquals(
            "Golden Path",
            after.masters
                .single()
                .ufo.fontInfo.openTypeNameDesigner,
        )
    }

    @Test
    fun batchAppliesAllOrNothing() {
        val font = Fixtures.fontState()
        val batch = Batch("Two moves", listOf(MovePoints(ref, setOf(PointRef(0, 0)), 1, 0), RemoveGlyph(GlyphRef("regular", "Z"))))
        val (result, after) = apply(font, batch)
        assertEquals(EditResult.Refused(Refusal.NoSuchGlyph(GlyphRef("regular", "Z"))), result)
        assertEquals(font, after)
    }

    @Test
    fun batchThatSucceedsAppliesEveryCommand() {
        val font = Fixtures.fontState()
        val batch = Batch("Two edits", listOf(SetAdvance(ref, 600), SetUnicodes(ref, listOf(0x41))))
        val (result, after) = apply(font, batch)
        assertIs<EditResult.Applied>(result)
        assertEquals(600, after.glyph(ref)!!.advanceWidth)
        assertEquals(listOf(0x41), after.glyph(ref)!!.unicodes)
    }

    // ---- Approve, unlock, revert, accept-trace ------------------------------------------------

    @Test
    fun approveLocksTheGlyphAtItsCurrentOutline() {
        val font = Fixtures.fontState()
        val (result, after) = apply(font, Approve(ref, ApprovalOrigin.DRAW))
        assertIs<EditResult.Applied>(result)
        val lock = after.locks.getValue(ref)
        assertEquals(LockState.LOCKED, lock.state)
        assertEquals(ApprovalOrigin.DRAW, lock.origin)
        assertEquals(font.glyph(ref), lock.approved)
        assertTrue(lock.episodes.isEmpty())
    }

    @Test
    fun unlockOpensAnEpisodeAndUnlocksTheGlyph() {
        val locked = lockedFont()
        val (result, after) = apply(locked, Unlock(ref, reason = null))
        assertIs<EditResult.Applied>(result)
        val lock = after.locks.getValue(ref)
        assertEquals(LockState.UNLOCKED, lock.state)
        assertEquals(1, lock.episodes.size)
        assertEquals(UnlockCause.USER, lock.episodes.single().cause)
        assertNull(lock.episodes.single().relockedAt)
    }

    @Test
    fun unlockOnAnAlreadyUnlockedGlyphIsANoOp() {
        val locked = lockedFont()
        val (_, unlocked) = apply(locked, Unlock(ref))
        val (result, _) = apply(unlocked, Unlock(ref))
        assertEquals(EditResult.NoChange, result)
    }

    @Test
    fun approvingAgainAfterAnEditFreezesTheOpenEpisodeWithANonEmptyDiff() {
        val locked = lockedFont()
        val (_, unlocked) = apply(locked, Unlock(ref))
        val (_, edited) = apply(unlocked, MovePoints(ref, setOf(PointRef(0, 0)), 0, -1))
        val (result, reapproved) = apply(edited, Approve(ref, ApprovalOrigin.DRAW))
        assertIs<EditResult.Applied>(result)
        val lock = reapproved.locks.getValue(ref)
        assertEquals(LockState.LOCKED, lock.state)
        assertEquals(1, lock.episodes.size)
        val episode = lock.episodes.single()
        assertEquals(edited.glyph(ref), lock.approved)
        assertTrue(episode.frozenDiff!!.isNotEmpty())
        assertEquals(reapproved.locks.getValue(ref).approvedAt, episode.relockedAt)
    }

    @Test
    fun approvingAgainWithNoEditDropsTheEpisode() {
        val locked = lockedFont()
        val (_, unlocked) = apply(locked, Unlock(ref))
        val (result, reapproved) = apply(unlocked, Approve(ref, ApprovalOrigin.DRAW))
        assertIs<EditResult.Applied>(result)
        assertTrue(
            reapproved.locks
                .getValue(ref)
                .episodes
                .isEmpty(),
        )
    }

    @Test
    fun revertToApprovedRestoresTheOutlineAndRelocks() {
        val locked = lockedFont()
        val (_, unlocked) = apply(locked, Unlock(ref))
        val (_, edited) = apply(unlocked, MovePoints(ref, setOf(PointRef(0, 0)), 5, 0))
        val (result, reverted) = apply(edited, RevertToApproved(ref))
        assertIs<EditResult.Applied>(result)
        assertEquals(locked.glyph(ref), reverted.glyph(ref))
        assertEquals(LockState.LOCKED, reverted.locks.getValue(ref).state)
        assertTrue(
            reverted.locks
                .getValue(ref)
                .episodes
                .isEmpty(),
        )
    }

    @Test
    fun acceptTraceReplacesAndApprovesInOneStep() {
        val font = Fixtures.fontState()
        val newContours = listOf(Fixtures.squareContour(0, 0, 200, 200))
        val (result, after) = apply(font, AcceptTrace(ref, newContours, advanceWidth = 300))
        assertIs<EditResult.Applied>(result)
        assertEquals(newContours, after.glyph(ref)!!.contours)
        assertEquals(300, after.glyph(ref)!!.advanceWidth)
        val lock = after.locks.getValue(ref)
        assertEquals(LockState.LOCKED, lock.state)
        assertEquals(ApprovalOrigin.TRACE, lock.origin)
    }

    // ---- The lock matrix (§7.1): every command against a LOCKED glyph -----------------------

    @Test
    fun movePointsOnALockedGlyphIsRefused() {
        val locked = lockedFont()
        val (result, after) = apply(locked, MovePoints(ref, setOf(PointRef(0, 0)), 1, 0))
        assertEquals(EditResult.Refused(Refusal.Locked(ref)), result)
        assertEquals(locked, after)
    }

    @Test
    fun replaceOutlineOnALockedGlyphIsRefused() {
        val locked = lockedFont()
        val (result, _) = apply(locked, ReplaceOutline(ref, listOf(Fixtures.squareContour(0, 0, 10, 10)), "Replace"))
        assertEquals(EditResult.Refused(Refusal.Locked(ref)), result)
    }

    @Test
    fun acceptTraceOnALockedGlyphIsRefused() {
        val locked = lockedFont()
        val (result, _) = apply(locked, AcceptTrace(ref, listOf(Fixtures.squareContour(0, 0, 10, 10)), null))
        assertEquals(EditResult.Refused(Refusal.Locked(ref)), result)
    }

    @Test
    fun setAnchorsOnALockedGlyphIsRefusedWhenItMovesAnAnchor() {
        val locked = lockedFont()
        val (result, _) = apply(locked, SetAnchors(ref, listOf(Anchor("top", Point(250, 700)))))
        assertEquals(EditResult.Refused(Refusal.Locked(ref)), result)
    }

    @Test
    fun removeGlyphOnALockedGlyphIsRefused() {
        val locked = lockedFont()
        val (result, after) = apply(locked, RemoveGlyph(ref))
        assertEquals(EditResult.Refused(Refusal.Locked(ref)), result)
        assertEquals(locked, after)
    }

    @Test
    fun setAdvanceOnALockedGlyphIsAllowed() {
        val locked = lockedFont()
        val (result, after) = apply(locked, SetAdvance(ref, 700))
        assertIs<EditResult.Applied>(result)
        assertEquals(700, after.glyph(ref)!!.advanceWidth)
        assertEquals(LockState.LOCKED, after.locks.getValue(ref).state)
    }

    @Test
    fun setUnicodesOnALockedGlyphIsAllowed() {
        val locked = lockedFont()
        val (result, _) = apply(locked, SetUnicodes(ref, listOf(0x41)))
        assertIs<EditResult.Applied>(result)
    }

    @Test
    fun setGroupAndSetKerningOnAMasterWithALockedGlyphAreAllowed() {
        val locked = lockedFont()
        val (groupResult, withGroup) = apply(locked, SetGroup("regular", "public.kern1.A", listOf("A")))
        assertIs<EditResult.Applied>(groupResult)
        val (kernResult, _) = apply(withGroup, SetKerning("regular", "A", "B", -30.0))
        assertIs<EditResult.Applied>(kernResult)
    }

    @Test
    fun renameGlyphOnALockedGlyphIsAllowedAndMovesTheLock() {
        val locked = lockedFont()
        val (result, after) = apply(locked, RenameGlyph(ref, "Aacute"))
        assertIs<EditResult.Applied>(result)
        val newRef = GlyphRef("regular", "Aacute")
        assertNull(after.locks[ref])
        val lock = after.locks.getValue(newRef)
        assertEquals(LockState.LOCKED, lock.state)
        assertEquals("Aacute", lock.approved.name)
        assertEquals(after.glyph(newRef), lock.approved)
    }

    @Test
    fun setGlyphGuidelinesOnALockedGlyphIsAllowedSinceGuidelinesAreNotShape() {
        val locked = lockedFont()
        val (result, after) =
            apply(
                locked,
                SetGlyphGuidelines(
                    ref,
                    listOf(
                        com.asoc.typewright.core.geometry
                            .Guideline(y = 500.0),
                    ),
                ),
            )
        assertIs<EditResult.Applied>(result)
        assertEquals(LockState.LOCKED, after.locks.getValue(ref).state)
    }

    @Test
    fun setSidebearingsLeftOnALockedGlyphShiftsBothTheGlyphAndTheApprovedSnapshot() {
        val locked = lockedFont()
        val approvedBefore = locked.locks.getValue(ref).approved
        val (result, after) = apply(locked, SetSidebearings(ref, left = 80, right = null))
        assertIs<EditResult.Applied>(result)
        val lock = after.locks.getValue(ref)
        assertEquals(LockState.LOCKED, lock.state)
        assertEquals(after.glyph(ref)!!.contours, lock.approved.contours)
        assertFalse(approvedBefore.contours == lock.approved.contours)
    }

    @Test
    fun setSidebearingsRightOnlyOnALockedGlyphIsAllowedAndDoesNotShiftTheSnapshot() {
        val locked = lockedFont()
        val approvedBefore = locked.locks.getValue(ref).approved
        val (result, after) = apply(locked, SetSidebearings(ref, left = null, right = 20))
        assertIs<EditResult.Applied>(result)
        assertEquals(approvedBefore, after.locks.getValue(ref).approved)
    }

    @Test
    fun simulateReturnsTheSameRefusalAsExecuteWithoutChangingAnything() {
        val locked = lockedFont()
        val (result, after) = apply(locked, MovePoints(ref, setOf(PointRef(0, 0)), 1, 0))
        assertEquals(EditResult.Refused(Refusal.Locked(ref)), result)
        assertEquals(locked, after)
    }
}
