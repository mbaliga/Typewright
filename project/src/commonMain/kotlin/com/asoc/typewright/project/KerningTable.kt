// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.font.ufo.UfoKerning

/** The two sides of a kerning pair, each with the group-name prefix the UFO 3 `groups.plist` specification reserves for it. */
enum class KernSide(
    val prefix: String,
) {
    FIRST("public.kern1."),
    SECOND("public.kern2."),
}

/** Group names as the UFO specification defines them for kerning: a reserved prefix, then one or more characters. */
object KerningGroups {
    /** The side [groupName] belongs to, or null for an ordinary group (or a bare prefix, which the specification does not allow as a name). */
    fun sideOf(groupName: String): KernSide? =
        KernSide.entries.firstOrNull {
            groupName.startsWith(it.prefix) &&
                groupName.length > it.prefix.length
        }

    /** [groupName] without its side prefix, the part a person names; an ordinary group's name is returned as it is. */
    fun displayName(groupName: String): String = sideOf(groupName)?.let { groupName.removePrefix(it.prefix) } ?: groupName

    /** The full group name for [display] on [side]. */
    fun nameOn(
        side: KernSide,
        display: String,
    ): String = side.prefix + display
}

/** One kerning entry, by the two names `kerning.plist` keys it with: a glyph or a group on each side. */
data class KernKey(
    val first: String,
    val second: String,
)

/** Which of the specification's lookup levels a pair's value came from. */
enum class KernSource {
    /** The two glyphs themselves: a glyph pair, the strongest level and the one that holds exceptions. */
    PAIR,

    /** The first glyph with the second glyph's group. */
    GLYPH_GROUP,

    /** The first glyph's group with the second glyph. */
    GROUP_GLYPH,

    /** The first glyph's group with the second glyph's group. */
    GROUP_GROUP,

    /** Nothing in the kerning applies: the pair is not kerned. */
    NONE,
}

/** What kerning a pair of glyphs gets, from where: [value] is 0 when [source] is [KernSource.NONE]; [key] is the entry it was read from. */
data class KernResolution(
    val value: Double,
    val source: KernSource,
    val key: KernKey?,
)

/**
 * A kerning group that the build leaves out altogether because it shares glyphs with a group before it on the same side: the
 * UFO 3 specification allows a glyph in one group per side, and fontmake (ufo2ft) keeps the first group that claims a glyph and
 * skips any later group that claims one of the same, with all its other members. [sharedWith] is the earlier group, [shared] the glyphs.
 */
data class IgnoredGroup(
    val group: String,
    val sharedWith: String,
    val shared: List<String>,
)

/**
 * A font's kerning read the way the UFO 3 specification's "Kerning Value Lookup Algorithm" reads
 * it: the glyph pair, then the first glyph with the second glyph's `public.kern2.*` group, then the
 * first glyph's `public.kern1.*` group with the second glyph, then the two groups; the first entry
 * found wins, even when its value is zero, and nothing found is zero. Groups that break the
 * specification's one-group-per-side rule are read as fontmake builds them (see [IgnoredGroup]), so
 * what the page shows is what the font gets. This is the one place that order is written down: the
 * specimen, Space and the group editor all read it from here.
 */
class KerningTable(
    val info: UfoKerning,
) {
    private val firstSide = SideReading.of(info, KernSide.FIRST)
    private val secondSide = SideReading.of(info, KernSide.SECOND)
    private val firstGroupOf: Map<String, String> = firstSide.membership
    private val secondGroupOf: Map<String, String> = secondSide.membership

    /** The groups the build leaves out because they share a glyph with an earlier group of their side, first side then second; empty for a font that follows the rule. */
    val ignoredGroups: List<IgnoredGroup> = firstSide.ignored + secondSide.ignored

    /** The kerning group [glyph] belongs to on [side], or null. */
    fun groupOf(
        glyph: String,
        side: KernSide,
    ): String? =
        when (side) {
            KernSide.FIRST -> firstGroupOf[glyph]
            KernSide.SECOND -> secondGroupOf[glyph]
        }

    /** The value of the entry [key], or null where `kerning.plist` has none. */
    fun entry(key: KernKey): Double? =
        info.kerning[key.first]
            ?.get(key.second)

    /**
     * The kerning between [first] and [second] and where it came from. [ignoring] pretends one entry
     * is not there, to ask what a pair would get without it (what an exception overrides).
     */
    fun resolve(
        first: String,
        second: String,
        ignoring: KernKey? = null,
    ): KernResolution {
        if (info.kerning.isEmpty()) return NOT_KERNED
        val firstGroup = firstGroupOf[first]
        val secondGroup = secondGroupOf[second]

        fun read(
            a: String?,
            b: String?,
            source: KernSource,
        ): KernResolution? {
            if (a == null || b == null) return null
            val key = KernKey(a, b)
            if (key == ignoring) return null
            return entry(key)?.let { KernResolution(it, source, key) }
        }
        return read(first, second, KernSource.PAIR)
            ?: read(first, secondGroup, KernSource.GLYPH_GROUP)
            ?: read(firstGroup, second, KernSource.GROUP_GLYPH)
            ?: read(firstGroup, secondGroup, KernSource.GROUP_GROUP)
            ?: NOT_KERNED
    }

    /** The kerning between [first] and [second] in font units; 0 where there is none. */
    fun value(
        first: String,
        second: String,
    ): Double = resolve(first, second).value

    /** How many glyph pairs the entry [key] stands for: each side's group size, or 1 for a glyph. Exceptions that override some of them are not subtracted. */
    fun coverage(key: KernKey): Int = sideSize(key.first) * sideSize(key.second)

    private fun sideSize(name: String): Int {
        val side = KerningGroups.sideOf(name) ?: return 1
        // A group the build ignores stands for nothing: none of its members belongs to it.
        return info.groups[name]
            ?.distinct()
            ?.count { glyph -> groupOf(glyph, side) == name } ?: 0
    }

    /** Which group each glyph of one side is in, and which groups of that side are ignored for sharing a glyph with an earlier one. */
    private class SideReading(
        val membership: Map<String, String>,
        val ignored: List<IgnoredGroup>,
    ) {
        companion object {
            fun of(
                info: UfoKerning,
                side: KernSide,
            ): SideReading {
                val membership = HashMap<String, String>()
                val ignored = ArrayList<IgnoredGroup>()
                for ((group, members) in info.groups) {
                    if (KerningGroups.sideOf(group) != side) continue
                    val shared = members.distinct().filter { it in membership }
                    if (shared.isNotEmpty()) {
                        ignored += IgnoredGroup(group, membership.getValue(shared.first()), shared)
                        continue
                    }
                    for (member in members) membership[member] = group
                }
                return SideReading(membership, ignored)
            }
        }
    }

    companion object {
        private val NOT_KERNED = KernResolution(0.0, KernSource.NONE, null)
    }
}
