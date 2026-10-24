// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.sidebearings
import com.asoc.typewright.project.ApprovalOrigin
import com.asoc.typewright.project.Approve
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.KernScope
import com.asoc.typewright.project.KerningGroupEdits
import com.asoc.typewright.project.LockState
import com.asoc.typewright.project.ProjectPreferences
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.ui.draw.DrawTestProjects
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [SpaceEditor] against a real [ProjectSession] over Hyle Deco's own outlines: every edit is the project's command, and one undo step. */
class SpaceEditorTest {
    private val master = "regular"
    private val groups =
        mapOf(
            "public.kern1.round" to listOf("o", "O"),
            "public.kern2.round" to listOf("o", "O"),
            "public.kern1.straight" to listOf("n", "H"),
            "public.kern2.straight" to listOf("n", "H"),
        )

    private fun session(kerning: UfoKerning = UfoKerning()): ProjectSession =
        DrawTestProjects.session(DrawTestProjects.hyleDecoQuadratic().copy(kerningInfo = kerning), coalesceWindowMillis = 0)

    private fun ProjectSession.glyph(name: String): Glyph =
        state.value.font
            .glyph(GlyphRef(master, name))!!

    private fun ProjectSession.kerning(): UfoKerning =
        state.value.font
            .master(master)!!
            .ufo.kerningInfo

    private fun ProjectSession.undoSteps() = history.value.undoLabels.size

    // ---- sidebearings and advance ------------------------------------------------------------------

    @Test
    fun leftAndRightSidebearingsAreSetAsTheDrawInspectorMeasuresThem() {
        val session = session()
        val editor = SpaceEditor(session, master)
        val before = session.glyph("n")
        val sb = before.sidebearings()!!
        assertIs<SpaceOutcome.Done>(editor.setGlyphField(SpaceField.LEFT, "n", 40))
        val moved = session.glyph("n")
        assertEquals(40, moved.sidebearings()!!.left)
        assertEquals(sb.right, moved.sidebearings()!!.right, "a left change keeps the right sidebearing, so the advance moves with it")
        assertEquals(before.advanceWidth - (sb.left - 40), moved.advanceWidth)
        assertIs<SpaceOutcome.Done>(editor.setGlyphField(SpaceField.RIGHT, "n", 60))
        assertEquals(60, session.glyph("n").sidebearings()!!.right)
        assertEquals(40, session.glyph("n").sidebearings()!!.left)
        assertIs<SpaceOutcome.Done>(editor.setGlyphField(SpaceField.ADVANCE, "n", 500))
        assertEquals(500, session.glyph("n").advanceWidth)
        assertEquals(3, session.undoSteps(), "one undo step for each of the three")
    }

    @Test
    fun theSameValueIsUnchangedAndMakesNoUndoStep() {
        val session = session()
        val editor = SpaceEditor(session, master)
        val left = session.glyph("n").sidebearings()!!.left
        assertIs<SpaceOutcome.Unchanged>(editor.setGlyphField(SpaceField.LEFT, "n", left))
        assertEquals(0, session.undoSteps())
    }

    @Test
    fun aGlyphWithNoDrawingHasNoSidebearingsToSetAndSaysSo() {
        val session = DrawTestProjects.session(DrawTestProjects.small(Glyph("empty", 400, emptyList())))
        val editor = SpaceEditor(session, master)
        val outcome = assertIs<SpaceOutcome.Refused>(editor.setGlyphField(SpaceField.LEFT, "empty", 20))
        assertTrue("no drawing" in outcome.message, outcome.message)
        assertIs<SpaceOutcome.Done>(editor.setGlyphField(SpaceField.ADVANCE, "empty", 450), "its advance can still be set")
        assertNull(editor.valueOf(SpaceField.LEFT, "empty", null))
    }

    @Test
    fun anApprovedGlyphIsSpacedWithoutBeingRedrawnOrUnlocked() {
        val session = session()
        val editor = SpaceEditor(session, master)
        val ref = GlyphRef(master, "n")
        session.execute(Approve(ref, ApprovalOrigin.TRACE))
        val drawn = session.glyph("n")
        assertIs<SpaceOutcome.Done>(editor.setGlyphField(SpaceField.LEFT, "n", 30))
        val spaced = session.glyph("n")
        assertEquals(LockState.LOCKED, session.lockOf(ref)?.state, "spacing does not unlock")
        val dx = 30 - drawn.sidebearings()!!.left
        assertEquals(
            drawn.contours.map { c -> c.points.map { it.point.x + dx to it.point.y } },
            spaced.contours.map { c -> c.points.map { it.point.x to it.point.y } },
            "the drawing is the same drawing, moved sideways",
        )
        assertEquals(spaced.contours, session.lockOf(ref)!!.approved.contours, "the approved copy moved with it, so a diff stays empty")
        assertTrue(editor.isLocked("n"))
        assertFalse(editor.isLocked("o"))
    }

    @Test
    fun unlockOpensAnEpisodeForAnApprovedGlyph() {
        val session = session()
        val editor = SpaceEditor(session, master)
        session.execute(Approve(GlyphRef(master, "n"), ApprovalOrigin.DRAW))
        assertIs<SpaceOutcome.Done>(editor.unlock("n"))
        assertEquals(LockState.UNLOCKED, session.lockOf(GlyphRef(master, "n"))?.state)
        assertIs<SpaceOutcome.Unchanged>(editor.unlock("n"))
    }

    // ---- typed values and arrows ---------------------------------------------------------------------

    @Test
    fun anExpressionMatchesOneGlyphsSidebearingToAnothers() {
        val session = session()
        val editor = SpaceEditor(session, master)
        val oLeft = session.glyph("o").sidebearings()!!.left
        assertNull(editor.commit(SpaceField.LEFT, "=o.lsb", "n", null, KernScope.GROUPS))
        assertEquals(oLeft, session.glyph("n").sidebearings()!!.left)
        val hRight = session.glyph("H").sidebearings()!!.right
        assertNull(editor.commit(SpaceField.RIGHT, "=H.rsb+4", "n", null, KernScope.GROUPS))
        assertEquals(hRight + 4, session.glyph("n").sidebearings()!!.right)
        // A bare name is the glyph being spaced.
        val advance = session.glyph("n").advanceWidth
        assertNull(editor.commit(SpaceField.ADVANCE, "=adv+10", "n", null, KernScope.GROUPS))
        assertEquals(advance + 10, session.glyph("n").advanceWidth)
    }

    @Test
    fun aMistakeInAFieldComesBackAsAReasonAndChangesNothing() {
        val session = session()
        val editor = SpaceEditor(session, master)
        val steps = session.undoSteps()
        assertTrue("not a number" in editor.commit(SpaceField.LEFT, "forty", "n", null, KernScope.GROUPS).orEmpty())
        assertTrue("zz" in editor.commit(SpaceField.LEFT, "=zz.lsb", "n", null, KernScope.GROUPS).orEmpty())
        assertNotNull(editor.commit(SpaceField.LEFT, "=1/0", "n", null, KernScope.GROUPS))
        assertEquals(steps, session.undoSteps())
        val xHeight =
            checkNotNull(
                session.state.value.font
                    .master(master)!!
                    .ufo.fontInfo.xHeight,
            )
        assertEquals(SpaceValue.Units(floor(xHeight / 10.0 + 0.5).toInt()), editor.evaluate("=xheight/10", "n"))
    }

    @Test
    fun theMetricNamesAreTheFontsOwn() {
        val session = session()
        val editor = SpaceEditor(session, master)
        val xHeight =
            session.state.value.font
                .master(master)!!
                .ufo.fontInfo.xHeight
        assertNotNull(xHeight)
        assertEquals(SpaceValue.Units(xHeight), editor.evaluate("=xheight", null))
        assertEquals(SpaceValue.Units(1000), editor.evaluate("=upm", null))
        assertEquals(SpaceValue.Units(0), editor.evaluate("=baseline", null))
    }

    @Test
    fun theArrowsNudgeByOneAndShiftByTen() {
        val session = session()
        val editor = SpaceEditor(session, master)
        val start = session.glyph("n").sidebearings()!!.right
        assertIs<SpaceOutcome.Done>(editor.nudge(SpaceField.RIGHT, 1, "n", null, KernScope.GROUPS))
        assertEquals(start + 1, session.glyph("n").sidebearings()!!.right)
        assertIs<SpaceOutcome.Done>(editor.nudge(SpaceField.RIGHT, -10, "n", null, KernScope.GROUPS))
        assertEquals(start - 9, session.glyph("n").sidebearings()!!.right)
        val left = session.glyph("n").sidebearings()!!.left
        assertIs<SpaceOutcome.Done>(editor.nudge(SpaceField.LEFT, 10, "n", null, KernScope.GROUPS))
        assertEquals(left + 10, session.glyph("n").sidebearings()!!.left)
    }

    @Test
    fun nudgingKerningNeedsAPairAndAGlyphNeedsADrawing() {
        val session = session()
        val editor = SpaceEditor(session, master)
        assertIs<SpaceOutcome.Refused>(editor.nudge(SpaceField.KERN, 1, "n", null, KernScope.GROUPS))
        assertIs<SpaceOutcome.Refused>(editor.nudge(SpaceField.LEFT, 1, null, null, KernScope.GROUPS))
    }

    // ---- kerning -------------------------------------------------------------------------------

    @Test
    fun aPairBetweenGroupedGlyphsIsWrittenToTheGroupsAndTheNoteSaysHowManyFollow() {
        val session = session(UfoKerning(groups = groups))
        val editor = SpaceEditor(session, master)
        val outcome = assertIs<SpaceOutcome.Done>(editor.setKern("o", "n", -30, KernScope.GROUPS))
        assertEquals(mapOf("public.kern1.round" to mapOf("public.kern2.straight" to -30.0)), session.kerning().kerning)
        assertTrue("group pair round – straight (4 glyph pairs)" in outcome.note.orEmpty(), outcome.note)
        assertEquals(-30.0, editor.valueOf(SpaceField.KERN, null, "O" to "H"), "every letter of both groups follows")
    }

    @Test
    fun thisPairOnlyWritesAnExceptionAndSaysNothingExtra() {
        val session = session(UfoKerning(groups = groups, kerning = mapOf("public.kern1.round" to mapOf("public.kern2.straight" to -30.0))))
        val editor = SpaceEditor(session, master)
        val outcome = assertIs<SpaceOutcome.Done>(editor.setKern("o", "n", -50, KernScope.PAIR_ONLY))
        assertNull(outcome.note)
        assertEquals(
            -50.0,
            session
                .kerning()
                .kerning
                .getValue("o")
                .getValue("n"),
        )
        assertEquals(-30.0, editor.valueOf(SpaceField.KERN, null, "O" to "H"), "the others keep the group's value")
    }

    @Test
    fun removingAPairSaysWhatTheGroupsGiveItNow() {
        val session =
            session(
                UfoKerning(
                    groups = groups,
                    kerning = mapOf("public.kern1.round" to mapOf("public.kern2.straight" to -30.0), "o" to mapOf("n" to -50.0)),
                ),
            )
        val editor = SpaceEditor(session, master)
        val outcome = assertIs<SpaceOutcome.Done>(editor.removeKern("o", "n", KernScope.GROUPS))
        assertEquals("The group value -30 applies to this pair now.", outcome.note)
        assertEquals(setOf("public.kern1.round"), session.kerning().kerning.keys)
        val gone = assertIs<SpaceOutcome.Done>(editor.removeKern("o", "n", KernScope.GROUPS))
        assertTrue("Removed group pair round – straight" in gone.note.orEmpty(), gone.note)
        assertEquals(emptyMap(), session.kerning().kerning)
        assertIs<SpaceOutcome.Unchanged>(editor.removeKern("o", "n", KernScope.GROUPS))
    }

    @Test
    fun aKerningNudgeKeepsTheLevelThePairReadsFrom() {
        val session = session(UfoKerning(groups = groups, kerning = mapOf("public.kern1.round" to mapOf("public.kern2.straight" to -30.0))))
        val editor = SpaceEditor(session, master)
        assertIs<SpaceOutcome.Done>(editor.nudge(SpaceField.KERN, 10, "o", "o" to "n", KernScope.GROUPS))
        assertEquals(
            -20.0,
            session
                .kerning()
                .kerning
                .getValue("public.kern1.round")
                .getValue("public.kern2.straight"),
        )
        assertNull(session.kerning().kerning["o"])
    }

    // ---- a drag is one undo step ---------------------------------------------------------------------

    @Test
    fun aDragOfASidebearingIsOneUndoStepHoweverLongItRuns() {
        val session = session()
        val editor = SpaceEditor(session, master)
        val start = session.glyph("n").sidebearings()!!.right
        assertNull(editor.beginScrub(SpaceField.RIGHT, "n", null, KernScope.GROUPS))
        assertTrue(editor.scrubbing)
        for (units in listOf(1, 2, 5, 9, 14, 20)) assertTrue(editor.scrubTo(units))
        assertEquals(start + 20, session.glyph("n").sidebearings()!!.right, "the page follows the finger while it is down")
        assertEquals(0, session.undoSteps(), "and nothing is in the history until it lifts")
        editor.endScrub(commit = true)
        assertFalse(editor.scrubbing)
        assertEquals(1, session.undoSteps())
        assertTrue(session.undo())
        assertEquals(start, session.glyph("n").sidebearings()!!.right)
    }

    @Test
    fun aCancelledDragPutsTheGlyphBackAndLeavesNoStep() {
        val session = session()
        val editor = SpaceEditor(session, master)
        val before = session.glyph("n")
        assertNull(editor.beginScrub(SpaceField.LEFT, "n", null, KernScope.GROUPS))
        editor.scrubTo(25)
        assertTrue(session.glyph("n") != before)
        editor.endScrub(commit = false)
        assertEquals(before, session.glyph("n"))
        assertEquals(0, session.undoSteps())
        editor.endScrub(commit = false)
        assertFalse(editor.scrubTo(3), "a drag that is over takes no more movement")
    }

    @Test
    fun aKerningDragWritesToTheEntryItBeganOnAndCrossingZeroKeepsIt() {
        val session = session(UfoKerning(groups = groups, kerning = mapOf("public.kern1.round" to mapOf("public.kern2.straight" to -10.0))))
        val editor = SpaceEditor(session, master)
        assertNull(editor.beginScrub(SpaceField.KERN, "o", "o" to "n", KernScope.GROUPS))
        for (units in listOf(5, 10, 15, 8)) editor.scrubTo(units)
        assertEquals(-2.0, editor.valueOf(SpaceField.KERN, null, "o" to "n"))
        editor.endScrub(commit = true)
        assertEquals(mapOf("public.kern1.round" to mapOf("public.kern2.straight" to -2.0)), session.kerning().kerning)
        assertEquals(1, session.undoSteps())
        session.undo()
        assertEquals(
            -10.0,
            session
                .kerning()
                .kerning
                .getValue("public.kern1.round")
                .getValue("public.kern2.straight"),
        )
    }

    @Test
    fun beginningAnotherEditEndsAnOpenDrag() {
        val session = session()
        val editor = SpaceEditor(session, master)
        editor.beginScrub(SpaceField.RIGHT, "n", null, KernScope.GROUPS)
        editor.scrubTo(7)
        assertIs<SpaceOutcome.Done>(editor.setGlyphField(SpaceField.ADVANCE, "o", 480))
        assertFalse(editor.scrubbing)
        assertEquals(2, session.undoSteps(), "the drag was kept as its own step, then the advance")
    }

    @Test
    fun aDragNeedsSomethingToDrag() {
        val session = session()
        val editor = SpaceEditor(session, master)
        assertIs<SpaceOutcome.Refused>(editor.beginScrub(SpaceField.KERN, "n", null, KernScope.GROUPS))
        assertFalse(editor.scrubbing)
        assertIs<SpaceOutcome.Refused>(editor.beginScrub(SpaceField.LEFT, null, null, KernScope.GROUPS))
    }

    // ---- kerning that is not a whole number ------------------------------------------------------------

    private val fractional = UfoKerning(kerning = mapOf("n" to mapOf("o" to -12.5)))

    private fun ProjectSession.stored(): Double? = kerning().kerning["n"]?.get("o")

    @Test
    fun aKerningValueWithAFractionIsReadExactlyAndNudgedFromWhereItIsStored() {
        val session = session(fractional)
        val editor = SpaceEditor(session, master)
        assertEquals(-12.5, editor.valueOf(SpaceField.KERN, null, "n" to "o"), "read back as stored, not rounded to -12")
        assertIs<SpaceOutcome.Done>(editor.nudge(SpaceField.KERN, 1, null, "n" to "o", KernScope.GROUPS))
        assertEquals(-11.5, session.stored(), "one unit up from -12.5, not -11 or -12 from a rounded copy")
        assertIs<SpaceOutcome.Done>(editor.nudge(SpaceField.KERN, -10, null, "n" to "o", KernScope.GROUPS))
        assertEquals(-21.5, session.stored())
    }

    @Test
    fun aDragThatComesBackToWhereItBeganWritesWhatWasThereFractionAndAll() {
        val session = session(fractional)
        val editor = SpaceEditor(session, master)
        assertNull(editor.beginScrub(SpaceField.KERN, null, "n" to "o", KernScope.GROUPS))
        assertTrue(editor.scrubTo(7))
        assertEquals(-5.5, session.stored(), "the drag adds its distance to the stored value")
        assertTrue(editor.scrubTo(0))
        assertEquals(-12.5, session.stored(), "and back at its start the value is the one that was there, not -12")
        editor.endScrub(commit = true)
        assertEquals(-12.5, session.stored())
        session.undo()
        assertEquals(-12.5, session.stored())

        val second = session(fractional)
        val other = SpaceEditor(second, master)
        assertNull(other.beginScrub(SpaceField.KERN, null, "n" to "o", KernScope.GROUPS))
        other.scrubTo(3)
        other.endScrub(commit = true)
        assertEquals(-9.5, second.stored())
    }

    @Test
    fun typingAFractionWritesItAndTypingWhatTheInspectorShowsChangesNothing() {
        val session = session(fractional)
        val editor = SpaceEditor(session, master)
        val pair = "n" to "o"
        assertNull(editor.commit(SpaceField.KERN, "-12.5", null, pair, KernScope.GROUPS), "the displayed value, typed back, is accepted")
        assertEquals(0, session.undoSteps(), "and rewrites nothing")
        assertNull(editor.commit(SpaceField.KERN, "7.25", null, pair, KernScope.GROUPS))
        assertEquals(7.25, session.stored(), "a fraction typed is a fraction written")
        assertNull(editor.commit(SpaceField.KERN, "7.256", null, pair, KernScope.GROUPS))
        assertEquals(7.26, session.stored(), "to two decimals")
        assertNull(editor.commit(SpaceField.KERN, "-12", null, pair, KernScope.GROUPS))
        assertEquals(-12.0, session.stored())
    }

    @Test
    fun aSidebearingIsStillAWholeNumberWhateverIsTyped() {
        val session = session()
        val editor = SpaceEditor(session, master)
        assertNull(editor.commit(SpaceField.LEFT, "40.6", "n", null, KernScope.GROUPS))
        assertEquals(41, session.glyph("n").sidebearings()!!.left)
        assertNull(editor.commit(SpaceField.RIGHT, "=60.4", "n", null, KernScope.GROUPS))
        assertEquals(60, session.glyph("n").sidebearings()!!.right)
    }

    @Test
    fun removingAnExceptionSaysWhatTheGroupsGiveThePairNowWithItsFraction() {
        val session =
            session(
                UfoKerning(
                    kerning = mapOf("n" to mapOf("o" to -3.0), "public.kern1.straight" to mapOf("public.kern2.round" to -7.5)),
                    groups = groups,
                ),
            )
        val editor = SpaceEditor(session, master)
        assertTrue(editor.canRemoveKern("n", "o", KernScope.PAIR_ONLY))
        val outcome = assertIs<SpaceOutcome.Done>(editor.removeKern("n", "o", KernScope.PAIR_ONLY))
        assertEquals("The group value -7.5 applies to this pair now.", outcome.note)
        assertFalse(editor.canRemoveKern("n", "o", KernScope.PAIR_ONLY), "the exception is gone, so there is none to remove")
        assertTrue(editor.canRemoveKern("n", "o", KernScope.GROUPS), "the group value is what is left to remove")
    }

    @Test
    fun withThisPairOnlyChosenAValueFromTheGroupsHasNothingToRemove() {
        val session = session(UfoKerning(kerning = mapOf("public.kern1.straight" to mapOf("public.kern2.round" to -7.5)), groups = groups))
        val editor = SpaceEditor(session, master)
        assertFalse(editor.canRemoveKern("n", "o", KernScope.PAIR_ONLY))
        assertIs<SpaceOutcome.Unchanged>(editor.removeKern("n", "o", KernScope.PAIR_ONLY))
        assertEquals(0, session.undoSteps())
    }

    // ---- groups, features, strings ---------------------------------------------------------------------

    @Test
    fun aGroupEditIsOneUndoStepEvenWhenItRewritesPairs() {
        val session =
            session(
                UfoKerning(groups = groups, kerning = mapOf("public.kern1.round" to mapOf("public.kern2.straight" to -30.0, "H" to -4.0))),
            )
        val editor = SpaceEditor(session, master)
        val kerning = session.kerning()
        val outcome =
            editor.applyGroupEdit(KerningGroupEdits.rename(master, kerning, "public.kern2.straight", "stems"))
        assertIs<SpaceOutcome.Done>(outcome)
        assertEquals(1, session.undoSteps())
        assertEquals(mapOf("public.kern1.round" to mapOf("H" to -4.0, "public.kern2.stems" to -30.0)), session.kerning().kerning)
        session.undo()
        assertEquals(kerning, session.kerning(), "undo puts the groups and the pairs back as they were")
        assertIs<SpaceOutcome.Refused>(editor.applyGroupEdit(KerningGroupEdits.rename(master, kerning, "public.kern2.straight", "round")))
        assertIs<SpaceOutcome.Unchanged>(editor.applyGroupEdit(KerningGroupEdits.addMember(master, kerning, "public.kern1.round", "o")))
    }

    @Test
    fun aGroupEditThatMovesAGlyphSaysSo() {
        val session = session(UfoKerning(groups = groups))
        val editor = SpaceEditor(session, master)
        val outcome =
            assertIs<SpaceOutcome.Done>(
                editor.applyGroupEdit(KerningGroupEdits.addMember(master, session.kerning(), "public.kern1.round", "n")),
            )
        assertTrue("n left straight" in outcome.note.orEmpty(), outcome.note)
        assertEquals(listOf("H"), session.kerning().groups["public.kern1.straight"])
    }

    @Test
    fun featuresAreSetAndClearedThroughTheSessionAndUndone() {
        val session = session()
        val editor = SpaceEditor(session, master)
        val text = "feature kern {\n    pos n o -12;\n} kern;\n"
        assertIs<SpaceOutcome.Done>(editor.setFeatures(text))
        assertEquals(text, session.kerning().features)
        assertIs<SpaceOutcome.Unchanged>(editor.setFeatures(text))
        assertIs<SpaceOutcome.Done>(editor.setFeatures(null))
        assertNull(session.kerning().features)
        session.undo()
        assertEquals(text, session.kerning().features)
        session.undo()
        assertNull(session.kerning().features)
    }

    @Test
    fun theirOwnControlStringsLiveInTheProjectsPreferences() {
        val session = session()
        val editor = SpaceEditor(session, master)
        assertIs<SpaceOutcome.Done>(editor.setPreferences(ProjectPreferences(spaceControlStrings = listOf("HOHOOHH"))))
        assertEquals(listOf("HOHOOHH"), editor.preferences().spaceControlStrings)
        assertEquals(listOf("HOHOOHH"), session.state.value.meta.manifest.preferences.spaceControlStrings)
        assertIs<SpaceOutcome.Unchanged>(editor.setPreferences(editor.preferences()))
    }
}
