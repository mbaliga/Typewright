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
 * Three readings of a font's kerning - the table (`KerningTable`), the Home specimen's line (`ProjectFont.setLine`) and the Space
 * page's string (`SpaceString`) - are each compared with a reference that is written here and shares no code with any of them,
 * over many generated fonts. The specimen and the page both read the table, so comparing them with each other would agree by
 * construction; each is compared with the reference instead, so a change to either that bypasses or bends the table fails here.
 *
 * The reference does not look values up. It writes out the UFO 3 specification's description of what kerning does ("a pair of
 * glyphs is kerned by the most specific entry that names it": the two glyphs, then the first glyph with the second's group, then the
 * first's group with the second glyph, then the two groups) as an expansion: every entry is expanded to the glyph pairs it stands
 * for, least specific first, and a more specific entry overwrites what a less specific one set. That is another way to the same
 * answer, so it can disagree with the table's lookup order if either is wrong.
 */
class KerningAgreementTest {
    private val letters = "ABCDEFGH".toList()
    private val names = letters.map { it.toString() }

    /**
     * What each glyph pair is kerned by, from [info] alone. A group that shares a glyph with an earlier group of its side, which the
     * specification forbids, is left out whole, with all its members, as fontmake (ufo2ft) builds it: "already in ..., discarding".
     */
    private fun reference(info: UfoKerning): Map<Pair<String, String>, Double> {
        fun accepted(side: KernSide): Map<String, List<String>> {
            val kept = linkedMapOf<String, List<String>>()
            val claimed = HashSet<String>()
            for ((group, members) in info.groups) {
                if (!group.startsWith(side.prefix) || members.any { it in claimed }) continue
                kept[group] = members.distinct()
                claimed += members
            }
            return kept
        }

        val acceptedFirst = accepted(KernSide.FIRST)
        val acceptedSecond = accepted(KernSide.SECOND)

        fun expand(
            key: String,
            side: KernSide,
        ): List<String> =
            if (key.startsWith(side.prefix)) {
                (if (side == KernSide.FIRST) acceptedFirst else acceptedSecond)[key].orEmpty()
            } else {
                listOf(key)
            }

        fun isGroup(
            key: String,
            side: KernSide,
        ) = key.startsWith(side.prefix)

        val kerned = HashMap<Pair<String, String>, Double>()
        // Least specific first, so each pass overwrites the one before it: group with group, group with glyph, glyph with group, glyph with glyph.
        for (pass in 0..3) {
            for ((first, row) in info.kerning) {
                for ((second, value) in row) {
                    val rank =
                        when {
                            isGroup(first, KernSide.FIRST) && isGroup(second, KernSide.SECOND) -> 0
                            isGroup(first, KernSide.FIRST) -> 1
                            isGroup(second, KernSide.SECOND) -> 2
                            else -> 3
                        }
                    if (rank != pass) continue
                    for (a in expand(first, KernSide.FIRST)) for (b in expand(second, KernSide.SECOND)) kerned[a to b] = value
                }
            }
        }
        return kerned
    }

    private fun randomKerning(
        random: Random,
        mayDuplicate: Boolean,
    ): UfoKerning {
        val groups = linkedMapOf<String, List<String>>()
        for (side in KernSide.entries) {
            val buckets = Array(3) { mutableListOf<String>() }
            for (letter in letters) {
                // Each glyph is in at most one group on a side, or none ...
                val pick = random.nextInt(0, 4)
                if (pick < 3) buckets[pick] += letter.toString()
                // ... except where the specification's rule is deliberately broken, to see that clashing groups are read as fontmake reads them.
                if (mayDuplicate && random.nextInt(0, 5) == 0) {
                    buckets[random.nextInt(0, 3)].takeIf { letter.toString() !in it }?.add(letter.toString())
                }
            }
            buckets.forEachIndexed { i, members -> if (members.isNotEmpty()) groups["${side.prefix}g$i"] = members }
        }
        val firstKeys = names + groups.keys.filter { it.startsWith(KernSide.FIRST.prefix) }
        val secondKeys = names + groups.keys.filter { it.startsWith(KernSide.SECOND.prefix) }
        val kerning = linkedMapOf<String, MutableMap<String, Double>>()
        for (first in firstKeys) {
            for (second in secondKeys) {
                if (random.nextInt(0, 5) != 0) continue
                // Zero is a value like any other: an entry of 0 overrides a less specific one.
                kerning.getOrPut(first) { linkedMapOf() }[second] = random.nextInt(-5, 3) * 10.0
            }
        }
        return UfoKerning(groups = groups, kerning = kerning)
    }

    private fun check(mayDuplicate: Boolean): Pair<Int, Int> {
        var kerned = 0
        var fromGroups = 0
        repeat(80) { seed ->
            val info = randomKerning(Random(seed), mayDuplicate)
            val wanted = reference(info)
            val glyphs = letters.mapIndexed { i, c -> SpaceFixtures.glyph(c.toString(), c, 500 + i * 10) }
            val font = SpaceFixtures.font(glyphs, info)
            val table = KerningTable(info)
            for (a in names) {
                for (b in names) {
                    val expected = wanted[a to b] ?: 0.0
                    assertEquals(expected, table.value(a, b), "table: $a $b, seed $seed")
                    // Setting the two letters as a line: the second one's origin is the first's advance plus that kerning.
                    val advance = glyphs.first { it.name == a }.advanceWidth
                    assertEquals(advance + expected, font.setLine("$a$b").glyphs[1].x, "specimen: $a $b, seed $seed")
                    assertEquals(advance + expected, SpaceString.set(font, "$a$b").cells[1].x, "space: $a $b, seed $seed")
                    if (expected != 0.0) kerned++
                    if (table.resolve(a, b).key?.let { it.first != a || it.second != b } == true) fromGroups++
                }
            }
        }
        return kerned to fromGroups
    }

    @Test
    fun theTableTheSpecimenAndThePageEachAgreeWithAnIndependentReferenceOnEveryPairOfManyFonts() {
        val (kerned, fromGroups) = check(mayDuplicate = false)
        assertTrue(kerned > 500, "the generated fonts kern many pairs, not a few: $kerned")
        assertTrue(fromGroups > 200, "and many of them through groups, so the group order is exercised: $fromGroups")
    }

    @Test
    fun groupsThatShareAGlyphAreReadAsTheBuildToolReadsThem() {
        val (kerned, fromGroups) = check(mayDuplicate = true)
        assertTrue(kerned > 500, "$kerned")
        assertTrue(fromGroups > 200, "$fromGroups")
    }

    @Test
    fun theReferenceIsNotTheTableInDisguise() {
        // A hand-worked font: the reference must give the specification's answers, or agreeing with it means nothing.
        val info =
            UfoKerning(
                groups =
                    mapOf(
                        "public.kern1.A" to listOf("A", "B"),
                        "public.kern2.A" to listOf("C", "D"),
                    ),
                kerning =
                    mapOf(
                        "public.kern1.A" to mapOf("public.kern2.A" to -50.0, "D" to -30.0),
                        "A" to mapOf("public.kern2.A" to -20.0, "D" to 0.0),
                    ),
            )
        val expected = reference(info)
        assertEquals(-50.0, expected["B" to "C"], "group with group")
        assertEquals(-30.0, expected["B" to "D"], "group with glyph beats group with group")
        assertEquals(-20.0, expected["A" to "C"], "glyph with group beats group with group")
        assertEquals(0.0, expected["A" to "D"], "glyph with glyph, even a zero, beats everything")
        assertEquals(null, expected["C" to "A"], "a pair nothing names is not in the reference at all")
        val table = KerningTable(info)
        for ((pair, value) in expected) assertEquals(value, table.value(pair.first, pair.second), "$pair")
    }
}
