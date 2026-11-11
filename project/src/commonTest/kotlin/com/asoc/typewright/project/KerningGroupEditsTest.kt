// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.font.ufo.UfoKerning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [KerningGroupEdits]: the UFO group rules, and the kerning entries that follow a group when it is renamed or deleted. */
class KerningGroupEditsTest {
    private val master = "regular"
    private val o = "public.kern1.O"
    private val h = "public.kern1.H"
    private val round2 = "public.kern2.round"

    private val kerning =
        UfoKerning(
            groups =
                mapOf(
                    o to listOf("O", "C"),
                    h to listOf("H", "n"),
                    round2 to listOf("o", "c"),
                    "Group1" to listOf("A", "B"),
                ),
            kerning = mapOf(o to mapOf(round2 to -20.0, "V" to -10.0), "T" to mapOf(round2 to -40.0, "o" to -60.0)),
        )

    /** Applies [edit]'s command to a font over [from] and returns the kerning it leaves. */
    private fun applied(
        edit: GroupEdit,
        from: UfoKerning = kerning,
    ): UfoKerning {
        val run = assertIs<GroupEdit.Run>(edit)
        val font = Fixtures.fontState(Fixtures.master(ufo = Fixtures.ufoProject().copy(kerningInfo = from)))
        val (result, after) = FontCommandEngine.applyWithState(font, run.command, Fixtures.EPOCH)
        assertIs<EditResult.Applied>(result)
        return after.master(master)!!.ufo.kerningInfo
    }

    @Test
    fun cleanNameKeepsOneLineOfPrintableCharacters() {
        assertEquals("round", KerningGroupEdits.cleanName("  round "))
        assertNull(KerningGroupEdits.cleanName(""))
        assertNull(KerningGroupEdits.cleanName("   "))
        assertNull(KerningGroupEdits.cleanName("a\nb"))
        assertNull(KerningGroupEdits.cleanName("a\u0000b"))
        assertNull(KerningGroupEdits.cleanName("x".repeat(KerningGroupEdits.MAX_NAME_LENGTH + 1)))
        assertEquals(
            "x".repeat(KerningGroupEdits.MAX_NAME_LENGTH),
            KerningGroupEdits.cleanName("x".repeat(KerningGroupEdits.MAX_NAME_LENGTH)),
        )
    }

    @Test
    fun createMakesAGroupWithTheSidesPrefix() {
        val after = applied(KerningGroupEdits.create(master, kerning, KernSide.SECOND, "straight", listOf("n", "h")))
        assertEquals(listOf("n", "h"), after.groups["public.kern2.straight"])
        assertEquals(kerning.kerning, after.kerning)
    }

    @Test
    fun createRefusesANameAlreadyUsedOnThatSideButNotOnTheOther() {
        assertIs<GroupEdit.Refused>(KerningGroupEdits.create(master, kerning, KernSide.FIRST, "O"))
        assertIs<GroupEdit.Run>(KerningGroupEdits.create(master, kerning, KernSide.SECOND, "O"))
        assertIs<GroupEdit.Refused>(KerningGroupEdits.create(master, kerning, KernSide.FIRST, " "))
    }

    @Test
    fun createMovesAGlyphOutOfTheOtherGroupOnItsSide() {
        val edit = KerningGroupEdits.create(master, kerning, KernSide.FIRST, "round", listOf("C"))
        val after = applied(edit)
        assertEquals(listOf("C"), after.groups["public.kern1.round"])
        assertEquals(listOf("O"), after.groups[o], "C left O: a glyph is in one first-side group")
        assertTrue(assertIs<GroupEdit.Run>(edit).note!!.contains("C left O"))
    }

    @Test
    fun addingAGlyphAlreadyInAnotherGroupOnThatSideMovesIt() {
        val edit = KerningGroupEdits.addMember(master, kerning, o, "H")
        val after = applied(edit)
        assertEquals(listOf("O", "C", "H"), after.groups[o])
        assertEquals(listOf("n"), after.groups[h])
        assertTrue(assertIs<GroupEdit.Run>(edit).note!!.contains("H left H"))
    }

    @Test
    fun aGlyphMayBeInOneGroupOnEachSideAtOnce() {
        val after = applied(KerningGroupEdits.addMember(master, kerning, "public.kern2.round", "O"))
        assertEquals(listOf("o", "c", "O"), after.groups[round2])
        assertEquals(listOf("O", "C"), after.groups[o], "the first-side group is untouched")
        assertNull(assertIs<GroupEdit.Run>(KerningGroupEdits.addMember(master, kerning, round2, "O")).note)
    }

    @Test
    fun addingAMemberAnOrdinaryGroupAlreadyHasNeverMovesIt() {
        val edit = KerningGroupEdits.addMember(master, kerning, "Group1", "H")
        val after = applied(edit)
        assertEquals(listOf("A", "B", "H"), after.groups["Group1"])
        assertEquals(listOf("H", "n"), after.groups[h])
    }

    @Test
    fun addingAMemberThatIsThereOrAGroupThatIsNotIsNotARun() {
        assertIs<GroupEdit.Unchanged>(KerningGroupEdits.addMember(master, kerning, o, "O"))
        assertIs<GroupEdit.Refused>(KerningGroupEdits.addMember(master, kerning, "public.kern1.none", "O"))
        assertIs<GroupEdit.Refused>(KerningGroupEdits.addMember(master, kerning, o, "  "))
    }

    @Test
    fun removeTakesTheGlyphOutAndKeepsTheGroupEvenWhenEmpty() {
        val after = applied(KerningGroupEdits.removeMember(master, kerning, o, "C"))
        assertEquals(listOf("O"), after.groups[o])
        val empty = applied(KerningGroupEdits.removeMember(master, after, o, "O"), from = after)
        assertEquals(emptyList(), empty.groups[o], "an empty group is allowed by the specification")
        assertIs<GroupEdit.Unchanged>(KerningGroupEdits.removeMember(master, kerning, o, "Z"))
    }

    @Test
    fun renameMovesTheGroupAndEveryPairThatNamesItOnEitherSide() {
        val after = applied(KerningGroupEdits.rename(master, kerning, round2, "bowl"))
        val renamed = "public.kern2.bowl"
        assertEquals(listOf("o", "c"), after.groups[renamed])
        assertNull(after.groups[round2])
        assertEquals(-20.0, after.kerning.getValue(o).getValue(renamed))
        assertEquals(-40.0, after.kerning.getValue("T").getValue(renamed))
        assertEquals(-10.0, after.kerning.getValue(o).getValue("V"), "pairs that do not name it are untouched")
        assertEquals(-60.0, after.kerning.getValue("T").getValue("o"))
        assertTrue(after.kerning.values.none { round2 in it.keys }, "nothing is left pointing at the old name")
        // The table reads the same glyph pairs as before, under the new name.
        assertEquals(KerningTable(kerning).value("O", "c"), KerningTable(after).value("O", "c"))
        assertEquals(KerningTable(kerning).value("T", "c"), KerningTable(after).value("T", "c"))
    }

    @Test
    fun renameAFirstSideGroupRewritesTheRowKey() {
        val after = applied(KerningGroupEdits.rename(master, kerning, o, "caps"))
        assertEquals(mapOf(round2 to -20.0, "V" to -10.0), after.kerning["public.kern1.caps"])
        assertNull(after.kerning[o])
    }

    @Test
    fun renameRefusesATakenNameAndAcceptsTheSameName() {
        assertIs<GroupEdit.Refused>(KerningGroupEdits.rename(master, kerning, o, "H"))
        assertIs<GroupEdit.Unchanged>(KerningGroupEdits.rename(master, kerning, o, "O"))
        assertIs<GroupEdit.Refused>(KerningGroupEdits.rename(master, kerning, "public.kern1.none", "x"))
        assertIs<GroupEdit.Refused>(KerningGroupEdits.rename(master, kerning, o, ""))
    }

    @Test
    fun deleteRemovesTheGroupAndTheEntriesThatNameIt() {
        val edit = KerningGroupEdits.delete(master, kerning, round2)
        val after = applied(edit)
        assertNull(after.groups[round2])
        assertEquals(mapOf(o to mapOf("V" to -10.0), "T" to mapOf("o" to -60.0)), after.kerning)
        assertEquals("Also removed 2 kerning pairs that named it.", assertIs<GroupEdit.Run>(edit).note)
        assertEquals(2, KerningGroupEdits.entriesNaming(kerning, round2))
        assertEquals(2, KerningGroupEdits.entriesNaming(kerning, o))
        assertEquals(0, KerningGroupEdits.entriesNaming(kerning, h))
    }

    @Test
    fun deletingAGroupNothingNamesSaysNothingExtra() {
        val edit = KerningGroupEdits.delete(master, kerning, h)
        assertNull(assertIs<GroupEdit.Run>(edit).note)
        assertEquals(kerning.kerning, applied(edit).kerning)
        assertIs<GroupEdit.Refused>(KerningGroupEdits.delete(master, kerning, "public.kern1.none"))
    }
}
