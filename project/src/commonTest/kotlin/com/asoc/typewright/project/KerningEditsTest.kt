// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.font.ufo.UfoKerning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** [KerningEdits]: a kerning value lands on the right level of the specification's three, and the commands change the font as the table then reads it. */
class KerningEditsTest {
    private val master = "regular"
    private val names = listOf("O", "D", "Q", "E", "F", "A", "V")
    private val o = "public.kern1.O"
    private val e = "public.kern2.E"

    private fun kerningOf(
        vararg pairs: Triple<String, String, Double>,
        withGroups: Boolean = true,
    ): UfoKerning =
        UfoKerning(
            groups = if (withGroups) mapOf(o to listOf("O", "D", "Q"), e to listOf("E", "F")) else emptyMap(),
            kerning = pairs.groupBy({ it.first }, { it.second to it.third }).mapValues { (_, seconds) -> seconds.toMap() },
        )

    private fun font(kerning: UfoKerning): FontState =
        Fixtures.fontState(Fixtures.master(ufo = Fixtures.ufoProject(names).copy(kerningInfo = kerning)))

    private fun FontState.kerning(): UfoKerning = master(master)!!.ufo.kerningInfo

    private fun run(
        font: FontState,
        command: FontCommand?,
    ): FontState {
        assertNotNull(command, "a command was expected")
        val (result, after) = FontCommandEngine.applyWithState(font, command, Fixtures.EPOCH)
        assertIs<EditResult.Applied>(result)
        return after
    }

    private fun set(
        kerning: UfoKerning,
        first: String,
        second: String,
        value: Double,
        scope: KernScope = KernScope.GROUPS,
    ): UfoKerning = run(font(kerning), KerningEdits.set(master, KerningTable(kerning), first, second, value, scope)).kerning()

    @Test
    fun aNewPairBetweenTwoGroupedGlyphsIsWrittenAsTheGroupPair() {
        val after = set(kerningOf(), "O", "E", -80.0)
        assertEquals(mapOf(o to mapOf(e to -80.0)), after.kerning)
        val table = KerningTable(after)
        for (first in listOf("O", "D", "Q")) for (second in listOf("E", "F")) assertEquals(-80.0, table.value(first, second))
    }

    @Test
    fun thisPairOnlyWritesAnExceptionAndLeavesTheGroupsAlone() {
        val before = kerningOf(Triple(o, e, -100.0))
        val after = set(before, "D", "F", -300.0, KernScope.PAIR_ONLY)
        assertEquals(-300.0, KerningTable(after).value("D", "F"))
        assertEquals(KernSource.PAIR, KerningTable(after).resolve("D", "F").source)
        assertEquals(-100.0, KerningTable(after).value("O", "E"))
        assertEquals(-100.0, after.kerning.getValue(o).getValue(e))
    }

    @Test
    fun editingAnExistingExceptionStaysOnTheException() {
        val before = kerningOf(Triple(o, e, -100.0), Triple("D", "F", -300.0))
        val after = set(before, "D", "F", -310.0)
        assertEquals(-310.0, after.kerning.getValue("D").getValue("F"))
        assertEquals(-100.0, after.kerning.getValue(o).getValue(e))
    }

    @Test
    fun editingAPairThatReadsFromTheGroupsChangesTheGroupsNotAnException() {
        val before = kerningOf(Triple(o, e, -100.0))
        val after = set(before, "Q", "E", -120.0)
        assertEquals(setOf(o), after.kerning.keys)
        assertEquals(-120.0, KerningTable(after).value("O", "F"))
    }

    @Test
    fun aGlyphWithAGroupIsWrittenAtThatMixedLevel() {
        val before = kerningOf()
        val glyphThenGroup = set(before, "A", "F", -40.0)
        assertEquals(mapOf("A" to mapOf(e to -40.0)), glyphThenGroup.kerning)
        val groupThenGlyph = set(before, "Q", "V", -25.0)
        assertEquals(mapOf(o to mapOf("V" to -25.0)), groupThenGlyph.kerning)
    }

    @Test
    fun glyphsWithoutGroupsGetAGlyphPair() {
        val after = set(kerningOf(withGroups = false), "A", "V", -90.0)
        assertEquals(mapOf("A" to mapOf("V" to -90.0)), after.kerning)
    }

    @Test
    fun theSameValueIsNoCommand() {
        val kerning = kerningOf(Triple(o, e, -100.0))
        assertNull(KerningEdits.set(master, KerningTable(kerning), "O", "E", -100.0, KernScope.GROUPS))
        assertNull(KerningEdits.set(master, KerningTable(kerning), "A", "V", 0.0, KernScope.GROUPS), "no entry and zero changes nothing")
    }

    @Test
    fun zeroOnAGroupPairRemovesTheEntryRatherThanStoringAZero() {
        val kerning = kerningOf(Triple(o, e, -100.0))
        val after = set(kerning, "O", "E", 0.0)
        assertEquals(emptyMap(), after.kerning)
    }

    @Test
    fun zeroOnAnExceptionIsKeptWhileAGroupValueWouldOtherwiseApply() {
        val kerning = kerningOf(Triple(o, e, -100.0))
        val after = set(kerning, "D", "F", 0.0, KernScope.PAIR_ONLY)
        assertEquals(0.0, after.kerning.getValue("D").getValue("F"), "the zero is what keeps the group's -100 off this pair")
        assertEquals(0.0, KerningTable(after).value("D", "F"))
        assertEquals(-100.0, KerningTable(after).value("D", "E"))
    }

    @Test
    fun zeroOnAnExceptionIsDroppedWhenNothingBelowItKerns() {
        val kerning = kerningOf(Triple("A", "V", -90.0), withGroups = false)
        val after = set(kerning, "A", "V", 0.0)
        assertEquals(emptyMap(), after.kerning)
    }

    @Test
    fun removingAPairRemovesTheEntryItReadsFrom() {
        val kerning = kerningOf(Triple(o, e, -100.0), Triple("D", "F", -300.0))
        val table = KerningTable(kerning)
        val exception = run(font(kerning), KerningEdits.remove(master, table, "D", "F", KernScope.GROUPS)).kerning()
        assertEquals(setOf(o), exception.kerning.keys, "the exception goes and the group value is what shows")
        assertEquals(-100.0, KerningTable(exception).value("D", "F"))
        val group = run(font(kerning), KerningEdits.remove(master, table, "Q", "E", KernScope.GROUPS)).kerning()
        assertEquals(setOf("D"), group.kerning.keys, "the group pair goes for every glyph that read from it")
        assertEquals(0.0, KerningTable(group).value("O", "E"))
    }

    @Test
    fun removingThisPairOnlyKeepsTheGroupPair() {
        val kerning = kerningOf(Triple(o, e, -100.0), Triple("D", "F", -300.0))
        val after = run(font(kerning), KerningEdits.remove(master, KerningTable(kerning), "D", "F", KernScope.PAIR_ONLY)).kerning()
        assertEquals(-100.0, KerningTable(after).value("D", "F"))
        assertNull(KerningEdits.remove(master, KerningTable(after), "D", "F", KernScope.PAIR_ONLY), "there is no exception left to remove")
    }

    @Test
    fun removingAPairThatIsNotKernedIsNoCommand() {
        assertNull(KerningEdits.remove(master, KerningTable(kerningOf()), "A", "V", KernScope.GROUPS))
    }

    @Test
    fun theTargetKeyIsTheLevelAWriteWouldGoTo() {
        val table = KerningTable(kerningOf(Triple("public.kern1.O", "F", -200.0)))
        assertEquals(KernKey(o, "F"), KerningEdits.targetKey(table, "Q", "F", KernScope.GROUPS))
        assertEquals(KernKey("Q", "F"), KerningEdits.targetKey(table, "Q", "F", KernScope.PAIR_ONLY))
        assertEquals(KernKey(o, e), KerningEdits.targetKey(table, "O", "E", KernScope.GROUPS))
        assertEquals(KernKey("A", "V"), KerningEdits.targetKey(table, "A", "V", KernScope.GROUPS))
    }

    @Test
    fun setAtKeepsWritingToTheEntryItWasGiven() {
        val kerning = kerningOf(Triple(o, e, -100.0))
        val table = KerningTable(kerning)
        val key = KernKey(o, e)
        val after = run(font(kerning), KerningEdits.setAt(master, table, "O", "E", key, -105.0)).kerning()
        assertEquals(-105.0, after.kerning.getValue(o).getValue(e))
        assertEquals(1, after.kerning.size)
    }
}
