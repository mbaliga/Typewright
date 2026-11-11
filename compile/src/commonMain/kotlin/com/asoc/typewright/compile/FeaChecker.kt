// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

/**
 * One thing the feature-file parser found wrong, in the tool's own words: [line] and [column] count from 1 and are
 * null where the tool named no place (a glyph the font does not have is reported once, wherever it was first used).
 */
data class FeaProblem(
    val line: Int?,
    val column: Int?,
    val message: String,
)

/** What a check of a `features.fea` text came to. */
sealed interface FeaCheckResult {
    /**
     * [tool] read the text: its name and version, and [where] it ran, a phrase that completes "Read ..." ("in this computer's
     * Python 3.11"). [problems] is what it found (the first it stops at, as the parser does), and [built] is true when it also
     * went on to build the lookups, which finds more than reading does; false means it only read the text.
     */
    data class Checked(
        val tool: String,
        val where: String,
        val built: Boolean,
        val problems: List<FeaProblem>,
    ) : FeaCheckResult

    /** The check could not be run here, for [reason] in plain words. Nothing was checked. */
    data class Unavailable(
        val reason: String,
    ) : FeaCheckResult
}

/**
 * Reads a `features.fea` text with the language's own parser, so the editor can show what the build would refuse
 * before the build is run. [glyphNames] is the font's glyph set: a feature that names a glyph the font does not have
 * is an error, as it is to the build. Where there is no parser to run (the phone and the browser have none yet), the
 * result is [FeaCheckResult.Unavailable] with the reason, and the caller says nothing was checked; it never guesses.
 */
interface FeaChecker {
    /**
     * Why this checker will never read a text here, or null when it may. The editor does not wait for one that never will,
     * and does not say a parser is reading while none is.
     */
    val neverReads: String? get() = null

    suspend fun check(
        text: String,
        glyphNames: List<String>,
    ): FeaCheckResult
}

/** A checker for a platform that has no parser to run: it answers [reason] to every text. */
class UnavailableFeaChecker(
    private val reason: String,
) : FeaChecker {
    override val neverReads: String = reason

    override suspend fun check(
        text: String,
        glyphNames: List<String>,
    ): FeaCheckResult = FeaCheckResult.Unavailable(reason)
}

/** The `.fea` checker for the platform this code runs on. */
expect fun platformFeaChecker(): FeaChecker
