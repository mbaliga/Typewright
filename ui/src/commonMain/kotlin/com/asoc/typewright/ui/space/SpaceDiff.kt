// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.sidebearings

/**
 * How the spacing and kerning of the font differ from the Before it is compared with: which glyphs
 * have a different advance or sidebearing ([spacingChanged], by name), how many kerning entries were
 * added, removed or given another value ([kerningEntries]), how many groups differ ([groups]) and
 * whether `features.fea` does ([featuresChanged]). Drawings are not compared: Before and After differ
 * in space, not in shape.
 */
data class SpaceDiff(
    val spacingChanged: List<String>,
    val kerningEntries: Int,
    val groups: Int,
    val featuresChanged: Boolean,
) {
    val isEmpty: Boolean get() = spacingChanged.isEmpty() && kerningEntries == 0 && groups == 0 && !featuresChanged

    /** One line saying what differs ("2 glyphs spaced, 1 kerning pair"), or null when nothing does. */
    fun summary(): String? {
        if (isEmpty) return null
        val parts = ArrayList<String>()
        if (spacingChanged.isNotEmpty()) {
            parts += "${spacingChanged.size} ${if (spacingChanged.size == 1) "glyph" else "glyphs"} with new sidebearings or advance"
        }
        if (kerningEntries > 0) parts += "$kerningEntries kerning ${if (kerningEntries == 1) "entry" else "entries"}"
        if (groups > 0) parts += "$groups ${if (groups == 1) "group" else "groups"}"
        if (featuresChanged) parts += "features.fea"
        return parts.joinToString(", ")
    }

    companion object {
        /** The difference between [before] and [after]. */
        fun of(
            before: UfoProject,
            after: UfoProject,
        ): SpaceDiff {
            val earlier = before.glyphs.associateBy { it.name }
            val spaced =
                after.glyphs
                    .filter { glyph ->
                        val old = earlier[glyph.name] ?: return@filter false
                        old.advanceWidth != glyph.advanceWidth || old.sidebearings() != glyph.sidebearings()
                    }.map { it.name }
            val oldPairs =
                before.kerningInfo.kerning
                    .flatMap { (a, row) -> row.map { (b, v) -> (a to b) to v } }
                    .toMap()
            val newPairs =
                after.kerningInfo.kerning
                    .flatMap { (a, row) -> row.map { (b, v) -> (a to b) to v } }
                    .toMap()
            val entries = (oldPairs.keys + newPairs.keys).count { oldPairs[it] != newPairs[it] }
            val oldGroups = before.kerningInfo.groups
            val newGroups = after.kerningInfo.groups
            val groups = (oldGroups.keys + newGroups.keys).count { oldGroups[it] != newGroups[it] }
            return SpaceDiff(spaced, entries, groups, before.kerningInfo.features != after.kerningInfo.features)
        }
    }
}
