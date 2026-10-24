// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.core.geometry.sidebearings
import com.asoc.typewright.project.KernSide
import com.asoc.typewright.project.KernSource
import com.asoc.typewright.project.KerningGroups
import com.asoc.typewright.project.KerningTable
import com.asoc.typewright.ui.glass.InspectorField

/**
 * What the Space inspector row shows (SCREENS_V1 S13: left, right, kern, group), as plain data a test
 * can read. In Spacing it is the selected letter: its sidebearings and advance, and its groups. In
 * Kerning it is the selected pair: the two letters, the kerning between them, where that value comes from
 * (this pair on its own, or the groups) and how many glyph pairs it stands for. A value that can be typed
 * or dragged has a key; the one named by [active] is the ink block.
 */
object SpaceInspectorModel {
    const val KEY_LEFT = "left"
    const val KEY_RIGHT = "right"
    const val KEY_ADVANCE = "advance"
    const val KEY_KERN = "kern"

    /** The field a key names, or null. */
    fun fieldOf(key: String): SpaceField? =
        when (key) {
            KEY_LEFT -> SpaceField.LEFT
            KEY_RIGHT -> SpaceField.RIGHT
            KEY_ADVANCE -> SpaceField.ADVANCE
            KEY_KERN -> SpaceField.KERN
            else -> null
        }

    /** The key for [field]. */
    fun keyOf(field: SpaceField): String =
        when (field) {
            SpaceField.LEFT -> KEY_LEFT
            SpaceField.RIGHT -> KEY_RIGHT
            SpaceField.ADVANCE -> KEY_ADVANCE
            SpaceField.KERN -> KEY_KERN
        }

    /** The fields for the selected letter [cell] of the string in Spacing. [editable] is false while Before is shown. */
    fun spacing(
        cell: SpaceCell?,
        kerning: KerningTable,
        active: SpaceField,
        editable: Boolean,
    ): List<InspectorField> {
        if (cell == null) {
            return listOf("left", "right", "advance", "group").map { InspectorField(it, "—") }
        }
        val sb = cell.glyph.sidebearings()

        fun field(
            field: SpaceField,
            value: Int?,
        ) = InspectorField(
            label = field.label,
            value = value?.toString() ?: "—",
            emphasize = active == field,
            key = if (value != null && editable) keyOf(field) else null,
        )
        return listOf(
            field(SpaceField.LEFT, sb?.left),
            field(SpaceField.RIGHT, sb?.right),
            field(SpaceField.ADVANCE, cell.glyph.advanceWidth),
            InspectorField("group", groupText(cell.glyph.name, kerning)),
        )
    }

    /** The fields for the selected pair ([first], [second]) in Kerning, or dashes where no pair is selected. */
    fun kerning(
        first: String?,
        second: String?,
        kerning: KerningTable,
        editable: Boolean,
    ): List<InspectorField> {
        if (first == null || second == null) {
            return listOf("pair", "kern", "from", "applies to").map { InspectorField(it, "—") }
        }
        val resolved = kerning.resolve(first, second)
        val applies =
            when (resolved.source) {
                KernSource.NONE -> "—"
                KernSource.PAIR -> "this pair"
                else -> resolved.key?.let { key -> kerning.coverage(key).let { "$it ${if (it == 1) "pair" else "pairs"}" } } ?: "—"
            }
        return listOf(
            InspectorField("pair", "$first $second"),
            InspectorField("kern", formatUnits(resolved.value), emphasize = true, key = if (editable) KEY_KERN else null),
            InspectorField("from", resolved.sourceWord()),
            InspectorField("applies to", applies),
        )
    }

    /**
     * The groups [glyph] is in: "first · second" (the group it kerns as on the left of a pair, then on the right), a name
     * alone when both sides are the same group, a dot with nothing beside it for a side with none, and "—" for a glyph in no group.
     */
    fun groupText(
        glyph: String,
        kerning: KerningTable,
    ): String {
        val first = kerning.groupOf(glyph, KernSide.FIRST)?.let(KerningGroups::displayName)
        val second = kerning.groupOf(glyph, KernSide.SECOND)?.let(KerningGroups::displayName)
        return when {
            first == null && second == null -> "—"
            first == second -> first.orEmpty()
            else -> "${first.orEmpty()} · ${second.orEmpty()}".trim()
        }
    }
}
