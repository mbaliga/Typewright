// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.font.ufo.UfoKerning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [KerningTable] against the worked examples in the UFO 3 `kerning.plist` and `groups.plist`
 * specifications (unifiedfontobject.org/versions/ufo3), whose numbers are used here as they are
 * printed there: the group pair and its exceptions, the glyph-and-group conflict, and the lookup
 * order of the specification's own algorithm.
 */
class KerningTableTest {
    private val groups =
        mapOf(
            "public.kern1.O" to listOf("O", "D", "Q"),
            "public.kern2.E" to listOf("E", "F"),
        )

    private fun table(vararg pairs: Triple<String, String, Double>): KerningTable =
        KerningTable(
            UfoKerning(
                groups = groups,
                kerning = pairs.groupBy({ it.first }, { it.second to it.third }).mapValues { (_, seconds) -> seconds.toMap() },
            ),
        )

    private fun KerningTable.read(
        first: String,
        second: String,
    ) = resolve(first, second)

    @Test
    fun aGroupPairStandsForEveryGlyphPairOfBothGroups() {
        val kerning = table(Triple("public.kern1.O", "public.kern2.E", -100.0))
        for (first in listOf("O", "D", "Q")) {
            for (second in listOf("E", "F")) {
                val resolved = kerning.read(first, second)
                assertEquals(-100.0, resolved.value, "$first $second")
                assertEquals(KernSource.GROUP_GROUP, resolved.source)
                assertEquals(KernKey("public.kern1.O", "public.kern2.E"), resolved.key)
            }
        }
    }

    @Test
    fun aGroupWithAGlyphAppliesToTheWholeGroup() {
        val kerning =
            KerningTable(
                UfoKerning(
                    groups = mapOf("public.kern1.O" to listOf("O", "D", "Q")),
                    kerning =
                        mapOf("public.kern1.O" to mapOf("A" to -50.0)),
                ),
            )
        for (first in listOf("O", "D", "Q")) {
            assertEquals(-50.0, kerning.value(first, "A"), first)
            assertEquals(KernSource.GROUP_GLYPH, kerning.read(first, "A").source)
        }
        assertEquals(0.0, kerning.value("A", "A"))
    }

    @Test
    fun exceptionsOverrideGroupPairsAsTheSpecificationTabulates() {
        val kerning =
            table(
                Triple("public.kern1.O", "public.kern2.E", -100.0),
                Triple("public.kern1.O", "F", -200.0),
                Triple("D", "F", -300.0),
            )
        val expected =
            mapOf(
                ("O" to "E") to (-100.0 to KernSource.GROUP_GROUP),
                ("O" to "F") to (-200.0 to KernSource.GROUP_GLYPH),
                ("D" to "E") to (-100.0 to KernSource.GROUP_GROUP),
                ("D" to "F") to (-300.0 to KernSource.PAIR),
                ("Q" to "E") to (-100.0 to KernSource.GROUP_GROUP),
                ("Q" to "F") to (-200.0 to KernSource.GROUP_GLYPH),
            )
        for ((pair, want) in expected) {
            val got = kerning.read(pair.first, pair.second)
            assertEquals(want.first, got.value, "${pair.first} ${pair.second}")
            assertEquals(want.second, got.source, "${pair.first} ${pair.second}")
        }
    }

    @Test
    fun aGlyphAndGroupPairBeatsAGroupAndGlyphPairWhenTheyContradict() {
        val kerning =
            table(
                Triple("public.kern1.O", "public.kern2.E", -100.0),
                Triple("public.kern1.O", "F", -200.0),
                Triple("Q", "public.kern2.E", -250.0),
                Triple("D", "F", -300.0),
            )
        // The specification: "glyph + group pairs are given higher priority than group + glyph pairs ... the Q + F pair value would be -250."
        val qf = kerning.read("Q", "F")
        assertEquals(-250.0, qf.value)
        assertEquals(KernSource.GLYPH_GROUP, qf.source)
        assertEquals(-250.0, kerning.value("Q", "E"))
        assertEquals(-200.0, kerning.value("O", "F"))
        assertEquals(-300.0, kerning.value("D", "F"))
        assertEquals(-100.0, kerning.value("D", "E"))
    }

    @Test
    fun theSpecificationsOwnLookupExamplesComeOutAsPrinted() {
        val kerning =
            table(
                Triple("public.kern1.O", "public.kern2.E", -100.0),
                Triple("public.kern1.O", "F", -200.0),
                Triple("D", "F", -300.0),
            )
        assertEquals(-300.0, kerning.value("D", "F"))
        assertEquals(-200.0, kerning.value("O", "F"))
        assertEquals(-100.0, kerning.value("O", "E"))
        assertEquals(0.0, kerning.value("O", "O"))
        assertEquals(0.0, kerning.value("E", "E"))
        assertEquals(0.0, kerning.value("E", "O"))
        assertEquals(0.0, kerning.value("X", "X"))
    }

    @Test
    fun aStoredZeroIsAnExceptionAndStopsTheLookup() {
        val kerning = table(Triple("public.kern1.O", "public.kern2.E", -100.0), Triple("D", "E", 0.0))
        val resolved = kerning.read("D", "E")
        assertEquals(0.0, resolved.value)
        assertEquals(KernSource.PAIR, resolved.source)
        assertEquals(-100.0, kerning.value("O", "E"))
    }

    @Test
    fun aPairWithNothingStoredIsNotKerned() {
        val resolved = table(Triple("A", "V", -50.0)).read("V", "A")
        assertEquals(KernSource.NONE, resolved.source)
        assertNull(resolved.key)
        assertEquals(0.0, resolved.value)
        assertEquals(KernSource.NONE, KerningTable(UfoKerning()).read("A", "B").source)
    }

    @Test
    fun aGlyphInTwoGroupsOfOneSideBelongsToTheFirstInFileOrder() {
        val kerning =
            KerningTable(
                UfoKerning(
                    groups = mapOf("public.kern1.A1" to listOf("A", "Aacute"), "public.kern1.A2" to listOf("A", "Acircumflex")),
                    kerning = mapOf("public.kern1.A1" to mapOf("T" to -50.0), "public.kern1.A2" to mapOf("T" to -100.0)),
                ),
            )
        assertEquals("public.kern1.A1", kerning.groupOf("A", KernSide.FIRST))
        assertEquals(-50.0, kerning.value("A", "T"))
        assertEquals(-100.0, kerning.value("Acircumflex", "T"))
    }

    @Test
    fun aGlyphIsInAFirstSideAndASecondSideGroupAtOnce() {
        val kerning = KerningTable(UfoKerning(groups = groups + ("public.kern2.O" to listOf("O", "D")), kerning = emptyMap()))
        assertEquals("public.kern1.O", kerning.groupOf("O", KernSide.FIRST))
        assertEquals("public.kern2.O", kerning.groupOf("O", KernSide.SECOND))
        assertNull(kerning.groupOf("Q", KernSide.SECOND))
    }

    @Test
    fun anOrdinaryGroupTakesNoPartInKerning() {
        val kerning =
            KerningTable(
                UfoKerning(groups = mapOf("Group1" to listOf("A", "B")), kerning = mapOf("Group1" to mapOf("B" to -30.0))),
            )
        assertNull(kerning.groupOf("A", KernSide.FIRST))
        assertEquals(0.0, kerning.value("A", "B"))
    }

    @Test
    fun ignoringAnEntryShowsWhatThePairWouldGetWithoutIt() {
        val kerning = table(Triple("public.kern1.O", "public.kern2.E", -100.0), Triple("D", "F", -300.0))
        val without = kerning.resolve("D", "F", ignoring = KernKey("D", "F"))
        assertEquals(-100.0, without.value)
        assertEquals(KernSource.GROUP_GROUP, without.source)
        assertEquals(KernSource.NONE, kerning.resolve("O", "E", ignoring = KernKey("public.kern1.O", "public.kern2.E")).source)
    }

    @Test
    fun coverageCountsTheGlyphPairsAnEntryStandsFor() {
        val kerning = table(Triple("public.kern1.O", "public.kern2.E", -100.0))
        assertEquals(6, kerning.coverage(KernKey("public.kern1.O", "public.kern2.E")))
        assertEquals(3, kerning.coverage(KernKey("public.kern1.O", "A")))
        assertEquals(1, kerning.coverage(KernKey("A", "V")))
        assertEquals(0, kerning.coverage(KernKey("public.kern1.missing", "A")))
    }

    @Test
    fun entryReadsTheStoredValueByItsKeys() {
        val kerning = table(Triple("A", "V", -50.0))
        assertEquals(-50.0, kerning.entry(KernKey("A", "V")))
        assertNull(kerning.entry(KernKey("V", "A")))
    }

    @Test
    fun groupNamesCarryTheirSidesPrefix() {
        assertEquals(KernSide.FIRST, KerningGroups.sideOf("public.kern1.O"))
        assertEquals(KernSide.SECOND, KerningGroups.sideOf("public.kern2.round"))
        assertNull(KerningGroups.sideOf("public.kern1."), "a bare prefix is not a name")
        assertNull(KerningGroups.sideOf("Group1"))
        assertEquals("O", KerningGroups.displayName("public.kern1.O"))
        assertEquals("Group1", KerningGroups.displayName("Group1"))
        assertEquals("public.kern2.x", KerningGroups.nameOn(KernSide.SECOND, "x"))
    }
}
