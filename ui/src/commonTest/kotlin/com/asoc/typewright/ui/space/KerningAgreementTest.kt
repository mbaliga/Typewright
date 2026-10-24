// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.project.KernSide
import com.asoc.typewright.project.KerningTable
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The kerning the Space page shows, the kerning the specimen sets, and the UFO 3 specification's own
 * lookup algorithm (unifiedfontobject.org/versions/ufo3/kerning.plist, "Kerning Value Lookup Algorithm",
 * written out below from the specification's pseudo-code over a flat table) agree on every pair of many
 * generated fonts. The three read the same table of pairs and groups; none is derived from another.
 */
class KerningAgreementTest {
    private val letters = "ABCDEFGH".toList()

    /** The specification's algorithm, step for step, on `(first, second) -> value` entries and one group per glyph per side. */
    private fun specLookup(
        first: String,
        second: String,
        flat: Map<Pair<String, String>, Double>,
        groups: UfoKerning,
    ): Double {
        fun groupOf(
            glyph: String,
            side: KernSide,
        ): String? =
            groups.groups.entries
                .firstOrNull { (name, members) -> name.startsWith(side.prefix) && glyph in members }
                ?.key
        flat[first to second]?.let { return it }
        val secondGroup = groupOf(second, KernSide.SECOND)
        if (secondGroup != null) flat[first to secondGroup]?.let { return it }
        val firstGroup = groupOf(first, KernSide.FIRST)
        if (firstGroup != null) flat[firstGroup to second]?.let { return it }
        if (firstGroup != null && secondGroup != null) flat[firstGroup to secondGroup]?.let { return it }
        return 0.0
    }

    private fun randomKerning(random: Random): UfoKerning {
        val groups = linkedMapOf<String, List<String>>()
        for (side in KernSide.entries) {
            val buckets = Array(3) { mutableListOf<String>() }
            for (letter in letters) {
                // Each glyph is in at most one group on a side, or none.
                val pick = random.nextInt(0, 4)
                if (pick < 3) buckets[pick] += letter.toString()
            }
            buckets.forEachIndexed { i, members -> if (members.isNotEmpty()) groups["${side.prefix}g$i"] = members }
        }
        val firstKeys = letters.map { it.toString() } + groups.keys.filter { it.startsWith(KernSide.FIRST.prefix) }
        val secondKeys = letters.map { it.toString() } + groups.keys.filter { it.startsWith(KernSide.SECOND.prefix) }
        val kerning = linkedMapOf<String, MutableMap<String, Double>>()
        for (first in firstKeys) {
            for (second in secondKeys) {
                if (random.nextInt(0, 5) != 0) continue
                kerning.getOrPut(first) { linkedMapOf() }[second] = random.nextInt(-5, 3) * 10.0
            }
        }
        return UfoKerning(groups = groups, kerning = kerning)
    }

    @Test
    fun theTableTheSpecimenAndTheSpecificationsAlgorithmAgreeOnEveryPairOfManyFonts() {
        var kerned = 0
        var fromGroups = 0
        repeat(60) { seed ->
            val info = randomKerning(Random(seed))
            val flat = info.kerning.flatMap { (first, row) -> row.map { (second, value) -> (first to second) to value } }.toMap()
            val glyphs = letters.mapIndexed { i, c -> SpaceFixtures.glyph(c.toString(), c, 500 + i * 10) }
            val font = SpaceFixtures.font(glyphs, info)
            val table = KerningTable(info)
            for (a in letters) {
                for (b in letters) {
                    val wanted = specLookup(a.toString(), b.toString(), flat, info)
                    assertEquals(wanted, table.value(a.toString(), b.toString()), "table: $a $b, seed $seed")
                    // Setting the two letters as a line: the second one's origin is the first's advance plus that kerning.
                    val line = font.setLine("$a$b")
                    val advance = glyphs.first { it.name == a.toString() }.advanceWidth
                    assertEquals(advance + wanted, line.glyphs[1].x, "specimen: $a $b, seed $seed")
                    // And the Space page's string agrees with the specimen's line.
                    assertEquals(line.glyphs[1].x, SpaceString.set(font, "$a$b").cells[1].x, "space: $a $b, seed $seed")
                    if (wanted != 0.0) kerned++
                    if (table.resolve(a.toString(), b.toString()).key?.let { it.first != a.toString() || it.second != b.toString() } ==
                        true
                    ) {
                        fromGroups++
                    }
                }
            }
        }
        assertTrue(kerned > 500, "the generated fonts kern many pairs, not a few: $kerned")
        assertTrue(fromGroups > 200, "and many of them through groups, so the group order is exercised: $fromGroups")
    }
}
