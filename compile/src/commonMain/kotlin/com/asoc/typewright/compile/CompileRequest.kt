// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

/**
 * A read-only view of a project directory (brief §11): one UFO 3 per master, an optional
 * designspace, `typewright.json`. Paths are relative and `/`-separated so the same project can
 * live on a filesystem, in Android storage or in browser memory.
 *
 * This is `:project`'s own [com.asoc.typewright.project.ProjectFiles] (docs/PROJECT_MODEL.md §2
 * "Reconciling"), moved down so `:compile` reads exactly the snapshot a
 * [com.asoc.typewright.project.ProjectSession.buildSnapshot] hands out, with no call site
 * changes: a session's in-memory encode of its current state, so an autosave can never race a
 * build.
 */
typealias ProjectDirectory = com.asoc.typewright.project.ProjectFiles

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
    /**
     * Make the binary what Google Fonts' own checker asks of every font it takes, in the ways that are the build's and not the drawing's: OS/2
     * `fsType` 0 (no embedding restrictions), `USE_TYPO_METRICS` set, and, for a font that is not hinted, the `gasp` table and the smart-dropout
     * `prep` program that `gftools fix-nonhinting` adds. **A value the project states is left as it is** (`openTypeOS2Type` for the embedding
     * bits, `openTypeOS2Selection` for the style flags): the build log says what it left, and Google Fonts' checker names it on the binary. The
     * project's own files are never changed; the build log lists what was done.
     */
    val googleFontsFixes: Boolean = true,
)

/** One build: which project, which source inside it, and how. */
data class CompileRequest(
    val project: ProjectDirectory,
    val source: CompileSource,
    val options: CompileOptions = CompileOptions(),
)
