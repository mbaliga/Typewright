// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EconomyModelTest {
    private val names =
        mapOf(
            "sans-geometric" to "Geometric sans",
            "display-artdeco" to "Art deco",
            "sans-grotesque" to "Grotesque",
            "slab" to "Slab",
            "blackletter" to "Blackletter",
            "zzz-new" to "Zzz",
        )

    private fun chips(
        projectKey: String?,
        selected: String?,
    ) = styleChips(
        keys = names.keys.toList().reversed(),
        faces = { if (it == "display-artdeco") 8 else 30 },
        label = { names.getValue(it) },
        projectKey = projectKey,
        selectedKey = selected,
    )

    @Test
    fun theProjectsOwnClassComesFirstThenTheExplorersOrderThenTheRest() {
        assertEquals(
            listOf("Geometric sans", "Art deco", "Grotesque", "Slab", "Blackletter", "Zzz"),
            chips("sans-geometric", "sans-geometric").map { it.label },
        )
        assertEquals("Grotesque", chips("sans-grotesque", null).first().label)
        assertEquals(
            listOf("Grotesque", "Geometric sans", "Art deco", "Slab", "Blackletter", "Zzz"),
            chips("sans-grotesque", null).map { it.label },
        )
    }

    @Test
    fun aChipSaysItsClassAndHowManyFacesTheBoxIsBuiltFrom() {
        val all = chips("sans-geometric", "display-artdeco")
        assertEquals("Geometric sans · 30", all[0].text)
        assertEquals("Art deco · 8", all[1].text)
        assertEquals(listOf(false, true), all.take(2).map { it.selected })
        assertEquals(listOf(true, false), all.take(2).map { it.isProjectsClass })
    }

    @Test
    fun withNoClassOfItsOwnNoChipIsMarkedTheProjects() {
        assertEquals(emptyList(), chips(null, null).filter { it.isProjectsClass })
        assertEquals(emptyList(), chips(null, null).filter { it.selected })
    }

    @Test
    fun theFirstRowIsOpenUntilTheyTapOneAndThenItFollowsThem() {
        val rows = listOf("T", "H", "o")
        val start = OpenRow()
        assertEquals("T", start.resolve(rows))
        val h = start.tapped("H", start.resolve(rows))
        assertEquals("H", h.resolve(rows))
        val closed = h.tapped("H", h.resolve(rows))
        assertNull(closed.resolve(rows), "tapping the open row closes it, and the first row does not spring open again")
    }

    @Test
    fun tappingTheFirstRowWhileItIsOpenByDefaultClosesIt() {
        val rows = listOf("T", "H")
        assertNull(OpenRow().tapped("T", OpenRow().resolve(rows)).resolve(rows))
    }

    @Test
    fun aChosenRowThatNoLongerExistsOpensNothing() {
        val chosen = OpenRow().tapped("o", "T")
        assertNull(chosen.resolve(listOf("T", "H")))
        assertEquals(null, OpenRow().resolve(emptyList()))
    }
}
