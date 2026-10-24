// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.project.KerningTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [SpaceInspectorModel]: the numbers the inspector row shows for a selected letter or pair, and which of them can be edited. */
class SpaceInspectorModelTest {
    private val kerning =
        UfoKerning(
            groups =
                mapOf(
                    "public.kern1.round" to listOf("o", "O"),
                    "public.kern2.round" to listOf("o", "O"),
                    "public.kern1.straight" to listOf("n", "H"),
                ),
            kerning = mapOf("public.kern1.round" to mapOf("n" to -24.0), "n" to mapOf("o" to -12.0)),
        )
    private val font = SpaceFixtures.noho(kerning)
    private val table: KerningTable = font.kerningTable

    private fun cell(letter: String) = SpaceString.set(font, letter).cells.single()

    @Test
    fun spacingShowsLeftRightAdvanceAndGroupsAndTheActiveOneIsTheInkBlock() {
        val fields = SpaceInspectorModel.spacing(cell("n"), table, SpaceField.RIGHT, editable = true)
        assertEquals(listOf("left", "right", "advance", "group"), fields.map { it.label })
        assertEquals(listOf("46", "51", "500", "straight ·"), fields.map { it.value })
        assertEquals(listOf(false, true, false, false), fields.map { it.emphasize })
        assertEquals(listOf("left", "right", "advance", null), fields.map { it.key })
    }

    @Test
    fun beforeIsReadOnlyAndAGlyphWithNoDrawingHasNoSidebearingsToEdit() {
        val readOnly = SpaceInspectorModel.spacing(cell("n"), table, SpaceField.LEFT, editable = false)
        assertEquals(listOf(null, null, null, null), readOnly.map { it.key })
        val empty =
            SpaceFixtures
                .font(listOf(SpaceFixtures.glyph("a", 'a', 480, drawn = false)))
                .let { SpaceInspectorModel.spacing(SpaceString.set(it, "a").cells.firstOrNull(), it.kerningTable, SpaceField.LEFT, true) }
        assertEquals(listOf("left", "right", "advance", "group"), empty.map { it.label })
        assertEquals("—", empty.first().value)
        val noCell = SpaceInspectorModel.spacing(null, table, SpaceField.LEFT, true)
        assertEquals(listOf("—", "—", "—", "—"), noCell.map { it.value })
    }

    @Test
    fun groupsShowFirstThenSecondWithADashForASideWithNone() {
        assertEquals("straight ·", SpaceInspectorModel.groupText("n", table), "n is in a first-side group only")
        assertEquals(
            "· bowl",
            SpaceInspectorModel.groupText("c", KerningTable(UfoKerning(groups = mapOf("public.kern2.bowl" to listOf("c"))))),
        )
        assertEquals("round", SpaceInspectorModel.groupText("o", table), "the same name on both sides is written once")
        assertEquals("—", SpaceInspectorModel.groupText("A", table))
        val mixed =
            KerningTable(
                UfoKerning(groups = mapOf("public.kern1.round" to listOf("o"), "public.kern2.bowl" to listOf("o"))),
            )
        assertEquals("round · bowl", SpaceInspectorModel.groupText("o", mixed))
    }

    @Test
    fun kerningShowsThePairItsValueWhereItComesFromAndWhatItAppliesTo() {
        val exception = SpaceInspectorModel.kerning("n", "o", table, editable = true)
        assertEquals(listOf("pair", "kern", "from", "applies to"), exception.map { it.label })
        assertEquals(listOf("n o", "-12", "pair", "this pair"), exception.map { it.value })
        assertEquals(listOf(null, "kern", null, null), exception.map { it.key })
        val group = SpaceInspectorModel.kerning("o", "n", table, editable = true)
        assertEquals(listOf("o n", "-24", "group", "2 pairs"), group.map { it.value })
        val none = SpaceInspectorModel.kerning("H", "O", table, editable = true)
        assertEquals(listOf("H O", "0", "none", "—"), none.map { it.value })
    }

    @Test
    fun kerningHasNothingToEditWhileBeforeIsShownOrWithNoPair() {
        assertNull(SpaceInspectorModel.kerning("n", "o", table, editable = false)[1].key)
        assertEquals(listOf("—", "—", "—", "—"), SpaceInspectorModel.kerning(null, null, table, true).map { it.value })
    }

    @Test
    fun keysAndFieldsRoundTrip() {
        for (field in SpaceField.entries) assertEquals(field, SpaceInspectorModel.fieldOf(SpaceInspectorModel.keyOf(field)))
        assertNull(SpaceInspectorModel.fieldOf("group"))
    }
}
