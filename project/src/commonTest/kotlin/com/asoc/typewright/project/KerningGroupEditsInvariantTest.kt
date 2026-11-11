// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.font.ufo.UfoKerning
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rule [KerningGroupEdits] exists to keep, checked over many generated fonts rather than the one or two a
 * hand-written case thinks of: after an edit a glyph is in at most one group per side, every glyph that was asked
 * for is where it was asked to be, and nobody else was moved. Also the names a group member may have.
 */
class KerningGroupEditsInvariantTest {
    private val master = "regular"
    private val glyphs = ('A'..'L').map { it.toString() }
    private val glyphSet = glyphs.toSet()

    private fun applied(
        edit: GroupEdit,
        from: UfoKerning,
    ): UfoKerning {
        val run = assertIs<GroupEdit.Run>(edit)
        val font = Fixtures.fontState(Fixtures.master(ufo = Fixtures.ufoProject().copy(kerningInfo = from)))
        val (result, after) = FontCommandEngine.applyWithState(font, run.command, Fixtures.EPOCH)
        assertIs<EditResult.Applied>(result)
        return after.master(master)!!.ufo.kerningInfo
    }

    /** Groups as the specification allows them: on each side, every glyph is in at most one group. */
    private fun randomGroups(random: Random): Map<String, List<String>> {
        val groups = linkedMapOf<String, List<String>>()
        for (side in KernSide.entries) {
            val buckets = Array(3) { mutableListOf<String>() }
            for (glyph in glyphs) {
                val pick = random.nextInt(0, 4)
                if (pick < 3) buckets[pick] += glyph
            }
            buckets.forEachIndexed { i, members -> if (members.isNotEmpty()) groups["${side.prefix}old$i"] = members }
        }
        return groups
    }

    private fun assertOneGroupPerGlyphPerSide(
        groups: Map<String, List<String>>,
        label: String,
    ) {
        for (side in KernSide.entries) {
            val seen = HashMap<String, String>()
            for ((group, members) in groups) {
                if (KerningGroups.sideOf(group) != side) continue
                for (member in members) {
                    val other = seen.put(member, group)
                    assertTrue(other == null || other == group, "$label: $member is in both $other and $group on the ${side.name} side")
                }
            }
        }
    }

    @Test
    fun afterCreateNoGlyphIsInTwoGroupsOnASideAndNobodyElseMoved() {
        var severalFromOneGroup = 0
        repeat(400) { seed ->
            val random = Random(seed)
            val groups = randomGroups(random)
            val kerning = UfoKerning(groups = groups)
            val side = if (random.nextBoolean()) KernSide.FIRST else KernSide.SECOND
            val requested = glyphs.shuffled(random).take(random.nextInt(1, 7))
            val inOneOldGroup = groups.filterKeys { KerningGroups.sideOf(it) == side }.values.map { old -> requested.count { it in old } }
            if (inOneOldGroup.any { it >= 2 }) severalFromOneGroup++

            val after = applied(KerningGroupEdits.create(master, kerning, side, "fresh", requested, glyphSet), kerning)

            val fresh = side.prefix + "fresh"
            assertEquals(requested, after.groups[fresh], "seed $seed: every requested member is in the new group, in order")
            assertOneGroupPerGlyphPerSide(after.groups, "seed $seed")
            for ((group, members) in groups) {
                val expected = if (KerningGroups.sideOf(group) == side) members.filter { it !in requested } else members
                assertEquals(expected, after.groups[group], "seed $seed: $group keeps every member that was not asked for")
            }
        }
        assertTrue(
            severalFromOneGroup > 100,
            "the cases that broke it - two or more members from one old group - are common here: $severalFromOneGroup",
        )
    }

    @Test
    fun twoMembersOfOneOldGroupBothLeaveItInASingleCommand() {
        val old = "public.kern1.O"
        val kerning = UfoKerning(groups = mapOf(old to listOf("O", "C", "D")))
        val edit = KerningGroupEdits.create(master, kerning, KernSide.FIRST, "x", listOf("O", "C"))
        val after = applied(edit, kerning)
        assertEquals(listOf("D"), after.groups[old], "O and C both left; D stayed")
        assertEquals(listOf("O", "C"), after.groups["public.kern1.x"])
        val note = assertIs<GroupEdit.Run>(edit).note.orEmpty()
        assertTrue("O left O" in note && "C left O" in note, "the page can name both moves: $note")
    }

    @Test
    fun aGlyphThatWasAlreadyInTwoGroupsOfASideLeavesBothWhenItIsMoved() {
        val kerning = UfoKerning(groups = mapOf("public.kern1.a" to listOf("A", "B"), "public.kern1.b" to listOf("B", "C")))
        val after = applied(KerningGroupEdits.create(master, kerning, KernSide.FIRST, "n", listOf("B")), kerning)
        assertEquals(listOf("A"), after.groups["public.kern1.a"])
        assertEquals(listOf("C"), after.groups["public.kern1.b"])
        assertOneGroupPerGlyphPerSide(after.groups, "malformed start")
        val added = applied(KerningGroupEdits.addMember(master, kerning, "public.kern1.a", "C"), kerning)
        assertEquals(listOf("B"), added.groups["public.kern1.b"], "adding C to a takes it out of b")
    }

    @Test
    fun addingMembersOneAfterAnotherKeepsTheInvariant() {
        repeat(200) { seed ->
            val random = Random(seed)
            var kerning = UfoKerning(groups = randomGroups(random))
            repeat(6) {
                val group =
                    kerning.groups.keys
                        .toList()
                        .random(random)
                val glyph = glyphs.random(random)
                val edit = KerningGroupEdits.addMember(master, kerning, group, glyph, glyphSet)
                if (edit is GroupEdit.Run) {
                    kerning = applied(edit, kerning)
                    assertTrue(glyph in kerning.groups.getValue(group), "seed $seed: $glyph is in $group")
                }
                assertOneGroupPerGlyphPerSide(kerning.groups, "seed $seed")
            }
        }
    }

    // ---- the names a member may have -------------------------------------------------------------------

    @Test
    fun aMemberNameIsOneWordOfPrintableCharactersThatTheFontHas() {
        assertEquals(KerningGroupEdits.GlyphNameCheck.Ok("n"), KerningGroupEdits.checkGlyphName("  n \n", setOf("n")))
        for (bad in listOf("", "   ", "n o", "n\u0007", "n\u0000", "n\u007F", "n\u0085", "n\to")) {
            assertIs<KerningGroupEdits.GlyphNameCheck.Bad>(KerningGroupEdits.checkGlyphName(bad), "'${bad.replace("\u0007", "<BEL>")}'")
        }
        assertIs<KerningGroupEdits.GlyphNameCheck.Bad>(
            KerningGroupEdits.checkGlyphName(
                "x".repeat(KerningGroupEdits.MAX_GLYPH_NAME_LENGTH + 1),
            ),
        )
        val unknown = assertIs<KerningGroupEdits.GlyphNameCheck.Bad>(KerningGroupEdits.checkGlyphName("zzz", setOf("n")))
        assertTrue("no glyph called zzz" in unknown.reason, unknown.reason)
        assertEquals(
            KerningGroupEdits.GlyphNameCheck.Ok("zzz"),
            KerningGroupEdits.checkGlyphName("zzz"),
            "without the font's names only the form is checked",
        )
    }

    @Test
    fun addAndCreateRefuseABadMemberWithTheReasonAndChangeNothing() {
        val group = "public.kern1.O"
        val kerning = UfoKerning(groups = mapOf(group to listOf("A")))
        for ((raw, word) in listOf("B C" to "one word", "B\u0007" to "control", "Q" to "no glyph called Q")) {
            val add = assertIs<GroupEdit.Refused>(KerningGroupEdits.addMember(master, kerning, group, raw, glyphSet))
            assertTrue(word in add.reason, "add '$raw': ${add.reason}")
            val create =
                assertIs<GroupEdit.Refused>(KerningGroupEdits.create(master, kerning, KernSide.SECOND, "x", listOf("B", raw), glyphSet))
            assertTrue(word in create.reason, "create with '$raw': ${create.reason}")
        }
        assertNull((KerningGroupEdits.addMember(master, kerning, group, "B", glyphSet) as? GroupEdit.Refused)?.reason)
    }
}
