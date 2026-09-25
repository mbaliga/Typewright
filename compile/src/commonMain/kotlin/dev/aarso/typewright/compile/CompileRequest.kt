// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.compile

/**
 * A read-only view of a project directory (brief §11): one UFO 3 per master, an optional
 * designspace, `typewright.json`. Paths are relative and `/`-separated so the same project can
 * live on a filesystem, in Android storage or in browser memory.
 */
interface ProjectDirectory {
    /** The project's name as the UI shows it. */
    val displayName: String

    /** Every file in the project as a relative, `/`-separated path. */
    fun listFiles(): List<String>

    /** The bytes of one file; [path] is one of [listFiles]. */
    fun readBytes(path: String): ByteArray
}

/** What to compile inside a [ProjectDirectory]. */
sealed interface CompileSource {
    /** Project-relative path of the source. */
    val path: String

    /** One master: a `.ufo` directory. */
    data class Ufo(
        override val path: String,
    ) : CompileSource

    /** Several masters and their axes: a `.designspace` file. */
    data class Designspace(
        override val path: String,
    ) : CompileSource
}

/** Binary formats a build can produce. */
enum class OutputFormat {
    TTF,
    OTF,
    VARIABLE_TTF,
    WOFF2,
}

/** Build options that mirror the Google Fonts pipeline's choices. */
data class CompileOptions(
    val formats: Set<OutputFormat> = setOf(OutputFormat.TTF),
    /** Remove overlaps in static builds, as fontmake does by default. */
    val removeOverlaps: Boolean = true,
    /** Autohint with ttfautohint; Typewright never hints by hand (brief §10). */
    val autohint: Boolean = true,
)

/** One build: which project, which source inside it, and how. */
data class CompileRequest(
    val project: ProjectDirectory,
    val source: CompileSource,
    val options: CompileOptions = CompileOptions(),
)
