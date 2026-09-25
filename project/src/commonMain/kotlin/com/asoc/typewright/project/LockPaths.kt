// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.font.ufo.userNameToFileName
import kotlin.time.Instant

/**
 * Where law-1 snapshots and diffs live under `locks/<master>/` (docs/PROJECT_MODEL.md §3, §7.2,
 * §7.3), and the file-name stems they use. A lock's stem is assigned once, from the approved
 * glyph's name at the time it is (re)computed, the same way a glyph's own `.glif` file name is
 * (`userNameToFileName`), so `locks/regular/T_.glif` matches `HyleDeco-Regular.ufo/glyphs/T_.glif`.
 * It is kept stable across saves via [stemHints], exactly as [com.asoc.typewright.core.font.ufo.writeUfoProject]'s
 * `fileNameHints` keeps a glyph's own file name stable.
 */
internal object LockPaths {
    /** The `locks/<master>/` directory. */
    fun directory(master: String): String = "locks/$master"

    /** The approved-snapshot path for [ref], given its [stem] (no `.glif` suffix). */
    fun approvedPath(
        master: String,
        stem: String,
    ): String = "${directory(master)}/$stem.glif"

    /**
     * The stem every lock in [locks] of [master] resolves to, reusing [stemHints] (glyph name ->
     * previously assigned stem) where the glyph name is unchanged, and assigning a fresh one
     * (unique within this master's locks, collision-checked case-insensitively) otherwise.
     * Ordered by [FontState.master]'s own glyph order so the result is deterministic.
     */
    fun assignStems(
        master: String,
        locks: Map<GlyphRef, GlyphLock>,
        glyphOrder: List<String>,
        stemHints: Map<String, String>,
    ): Map<GlyphRef, String> {
        val refsInOrder =
            locks.keys
                .filter { it.master == master }
                .sortedBy { ref -> glyphOrder.indexOf(ref.glyph).let { if (it < 0) Int.MAX_VALUE else it } }
        val used = mutableSetOf<String>()
        val result = LinkedHashMap<GlyphRef, String>()
        for (ref in refsInOrder) {
            val hint = stemHints[ref.glyph]
            val stem =
                if (hint != null && used.add(hint.lowercase())) {
                    hint
                } else {
                    userNameToFileName(ref.glyph, used).also { used += it.lowercase() }
                }
            result[ref] = stem
        }
        return result
    }

    /**
     * Every episode's diff path for the glyph whose lock's approved snapshot has [stem], oldest
     * episode first. Two episodes recorded within the same UTC second get `-2`, `-3`, … appended,
     * so their file names stay distinct (§7.2).
     */
    fun diffPaths(
        master: String,
        stem: String,
        episodes: List<UnlockEpisode>,
    ): List<String> {
        val perSecond = mutableMapOf<String, Int>()
        return episodes.map { episode ->
            val compact = ProjectTimestamps.formatCompact(episode.at)
            val count = (perSecond[compact] ?: 0) + 1
            perSecond[compact] = count
            val suffix = if (count == 1) "" else "-$count"
            "${directory(master)}/$stem.$compact$suffix.diff"
        }
    }

    /** Whether [episode] is still open (not yet relocked). */
    fun UnlockEpisode.isOpen(): Boolean = relockedAt == null
}

/** [Instant] truncated to whole seconds, as every project timestamp is (docs/PROJECT_MODEL.md §3). */
internal fun Instant.truncatedToProjectSeconds(): Instant = Instant.fromEpochSeconds(epochSeconds)
