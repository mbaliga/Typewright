// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.goldenpath

import com.asoc.typewright.project.SessionEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Shared fixtures for steps 3 and 7 (docs/PROJECT_MODEL.md §12), the two golden-path steps that
 * drive `:project`'s real [ProjectSession][com.asoc.typewright.project.ProjectSession] against
 * real files on disk rather than in-memory state. No other step needs these: 1a/1b/2/4/6 are not
 * built yet, and step 5 has its own arrangement in `GoldenPathSteps.kt`.
 */
object ProjectFixtures {
    /** The golden path's own fixed epoch, `2026-09-25T10:00:00Z` (PROMPTS_V1 P10 step 6). */
    val EPOCH: Instant = Instant.parse("2026-09-25T10:00:00Z")

    /** A [SessionEnvironment] for a real run: [SteppingClock] from [EPOCH], real disk I/O, [scope] for autosave. */
    fun env(scope: CoroutineScope): SessionEnvironment =
        SessionEnvironment(scope = scope, io = Dispatchers.IO, clock = SteppingClock(EPOCH))

    /** A fresh, empty temp directory for a project store, named after [step]. */
    fun tempDir(step: String): File = Files.createTempDirectory("typewright-golden-path-$step-").toFile()

    /** Every text file under UFO directory [dir], relative to it and `/`-separated -- exactly what `readUfoProject` reads. */
    fun readUfoDir(dir: File): Map<String, String> =
        dir
            .walkTopDown()
            .filter { it.isFile }
            .associate { it.relativeTo(dir).path.replace(File.separatorChar, '/') to it.readText(Charsets.UTF_8) }

    /**
     * Every regular file under [root], relative and `/`-separated, hashed with SHA-256 -- enough to
     * prove two project trees are byte-identical without holding both fully in memory.
     */
    fun hashTree(root: File): Map<String, String> =
        root
            .walkTopDown()
            .filter { it.isFile }
            .associate { it.relativeTo(root).path.replace(File.separatorChar, '/') to sha256Hex(it.readBytes()) }

    /** Fails if any file under [root] is a leftover atomic-write temp file (docs/PROJECT_MODEL.md §8's `.tmp`/`.new` contract). */
    fun assertNoTempFiles(root: File) {
        for (file in root.walkTopDown().filter { it.isFile }) {
            val relative = file.relativeTo(root).path
            assertTrue(!relative.endsWith(".tmp") && !relative.endsWith(".new"), "$relative was left behind")
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

/**
 * A [Clock] that starts at [start] and advances by one second on every call to [now]: deterministic,
 * strictly ordered timestamps for a real save/close/reopen round trip.
 */
class SteppingClock(
    start: Instant,
) : Clock {
    private var current = start

    override fun now(): Instant {
        val at = current
        current += 1.seconds
        return at
    }
}
