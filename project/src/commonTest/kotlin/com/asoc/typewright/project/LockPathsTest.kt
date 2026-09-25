// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlin.test.Test
import kotlin.test.assertEquals

/** [LockPaths]: stem assignment (stable, collision-safe) and diff-path derivation (same-second collisions). Runs on the JVM and Wasm. */
class LockPathsTest {
    private fun lockOf(at: String) =
        GlyphLock(LockState.LOCKED, ApprovalOrigin.DRAW, ProjectTimestamps.parse(at), Fixtures.glyph("A"), emptyList())

    @Test
    fun approvedPathAndDirectoryFollowTheSchema() {
        assertEquals("locks/regular", LockPaths.directory("regular"))
        assertEquals("locks/regular/T_.glif", LockPaths.approvedPath("regular", "T_"))
    }

    @Test
    fun assignStemsGivesEachLockedGlyphAFileNameLikeStem() {
        val locks =
            mapOf(
                GlyphRef("regular", "A") to lockOf("2026-09-25T10:00:00Z"),
                GlyphRef("regular", "T") to lockOf("2026-09-25T10:00:00Z"),
            )
        val stems = LockPaths.assignStems("regular", locks, glyphOrder = listOf("T", "A"), stemHints = emptyMap())
        assertEquals("T_", stems.getValue(GlyphRef("regular", "T")))
        assertEquals("A_", stems.getValue(GlyphRef("regular", "A")))
    }

    @Test
    fun assignStemsReusesAHintWhenPresentAndValid() {
        val locks = mapOf(GlyphRef("regular", "A") to lockOf("2026-09-25T10:00:00Z"))
        val stems = LockPaths.assignStems("regular", locks, listOf("A"), stemHints = mapOf("A" to "already-assigned"))
        assertEquals("already-assigned", stems.getValue(GlyphRef("regular", "A")))
    }

    @Test
    fun assignStemsIgnoresLocksOfOtherMasters() {
        val locks = mapOf(GlyphRef("bold", "A") to lockOf("2026-09-25T10:00:00Z"))
        val stems = LockPaths.assignStems("regular", locks, listOf("A"), emptyMap())
        assertEquals(emptyMap(), stems)
    }

    @Test
    fun diffPathsAreDistinctWhenTwoEpisodesLandInTheSameSecond() {
        val at = ProjectTimestamps.parse("2026-09-25T11:02:41Z")
        val episodes =
            listOf(
                UnlockEpisode(at, UnlockCause.USER, null, at, "diff1"),
                UnlockEpisode(at, UnlockCause.USER, null, at, "diff2"),
            )
        val paths = LockPaths.diffPaths("regular", "T_", episodes)
        assertEquals(listOf("locks/regular/T_.20260925T110241Z.diff", "locks/regular/T_.20260925T110241Z-2.diff"), paths)
    }

    @Test
    fun diffPathsAtDifferentSecondsNeedNoSuffix() {
        val a = ProjectTimestamps.parse("2026-09-25T11:02:41Z")
        val b = ProjectTimestamps.parse("2026-09-25T11:05:00Z")
        val episodes = listOf(UnlockEpisode(a, UnlockCause.USER, null, a, "x"), UnlockEpisode(b, UnlockCause.USER, null, b, "y"))
        val paths = LockPaths.diffPaths("regular", "T_", episodes)
        assertEquals(listOf("locks/regular/T_.20260925T110241Z.diff", "locks/regular/T_.20260925T110500Z.diff"), paths)
    }
}
