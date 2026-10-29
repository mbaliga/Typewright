// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.project.KernKey
import com.asoc.typewright.project.KernScope
import com.asoc.typewright.project.KernSource
import com.asoc.typewright.project.KerningTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [formatUnits] and [kernSourceSentence]: how a kerning value and its source are put in words. */
class KernTextTest {
    @Test
    fun aWholeValueHasNoDecimalPointAndAFractionKeepsUpToTwoPlaces() {
        assertEquals("12", formatUnits(12.0))
        assertEquals("-12", formatUnits(-12.0))
        assertEquals("-12.5", formatUnits(-12.5))
        assertEquals("3.33", formatUnits(3.333))
        assertEquals("100.13", formatUnits(100.126))
        assertEquals("0", formatUnits(0.004), "a hundredth of a unit is not worth a digit")
        assertEquals("0", formatUnits(-0.0))
        assertEquals("0.05", formatUnits(0.05))
        assertEquals("10000000", formatUnits(1.0e7), "no exponent")
        assertEquals("-7", formatUnits(-6.999), "rounds to the nearest hundredth first, so a value 0.001 short of whole reads whole")
    }

    private val table =
        KerningTable(
            UfoKerning(
                groups = mapOf("public.kern1.round" to listOf("o"), "public.kern2.straight" to listOf("n")),
                kerning = mapOf("public.kern1.round" to mapOf("public.kern2.straight" to -24.0), "n" to mapOf("o" to -12.0)),
            ),
        )

    @Test
    fun anUnkernedGroupedPairSaysWhereATypedValueWouldGoUnderTheChosenScope() {
        val none = table.resolve("x", "y")
        assertEquals(
            "Not kerned yet.",
            kernSourceSentence(table, grouped = false, resolved = none, scope = KernScope.GROUPS, removable = false),
        )
        val toGroups = kernSourceSentence(table, grouped = true, resolved = none, scope = KernScope.GROUPS, removable = false)
        assertTrue("goes to the groups" in toGroups, toGroups)
        val toPair = kernSourceSentence(table, grouped = true, resolved = none, scope = KernScope.PAIR_ONLY, removable = false)
        assertTrue("this pair only" in toPair && "groups" in toPair && "goes to the groups" !in toPair, toPair)
    }

    @Test
    fun aValueFromTheGroupsUnderThisPairOnlySaysThereIsNothingOfItsOwnToRemove() {
        val fromGroups = table.resolve("o", "n")
        assertEquals(KernSource.GROUP_GROUP, fromGroups.source)
        val groupsScope = kernSourceSentence(table, true, fromGroups, KernScope.GROUPS, removable = true)
        assertTrue(groupsScope.startsWith("The value comes from group pair round"), groupsScope)
        assertTrue("no exception of its own to remove" !in groupsScope, groupsScope)
        val pairScope = kernSourceSentence(table, true, fromGroups, KernScope.PAIR_ONLY, removable = false)
        assertTrue(pairScope.startsWith("The value comes from group pair round"), pairScope)
        assertTrue("no exception of its own to remove" in pairScope && "choose Groups" in pairScope, pairScope)
    }

    @Test
    fun anExceptionIsDescribedAsThisPairWhateverTheScope() {
        val exception = table.resolve("n", "o")
        assertEquals(KernKey("n", "o"), exception.key)
        for (scope in KernScope.entries) {
            assertEquals("The value comes from this pair.", kernSourceSentence(table, true, exception, scope, removable = true))
        }
    }
}
