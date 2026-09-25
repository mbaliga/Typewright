// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

/**
 * The one rule every store applies to a project-relative path, and the temp-file names the
 * atomic-write protocols (docs/PROJECT_MODEL.md §5) leave behind after a crash.
 *
 * A valid path is relative, `/`-separated and stays inside the project: not empty, no leading
 * or trailing `/`, no empty, `.` or `..` segment, no backslash and no NUL. Backslash is refused
 * rather than translated so that a path means the same thing on every platform, and so a
 * crafted name can't climb out of the project on a host that treats `\` as a separator.
 */
object ProjectPath {
    /** Suffix of a partial write. It may be incomplete, so recovery always deletes it. */
    const val TMP_SUFFIX: String = ".tmp"

    /** Suffix of a completed, synced write the swap protocol hasn't renamed yet; recovery rolls it forward. */
    const val NEW_SUFFIX: String = ".new"

    /** Suffix of Chromium's File System Access swap file; a stale one is partial, so recovery deletes it. */
    const val CRSWAP_SUFFIX: String = ".crswap"

    /** A root `.git` directory holds the user's version control, never project files; stores neither list nor sweep it. */
    const val GIT_DIRECTORY: String = ".git"

    /** Whether [path] satisfies the rule in this object's KDoc. */
    fun isValid(path: String): Boolean = problem(path) == null

    /** Returns [path] unchanged if it is valid, else throws [IllegalArgumentException] saying why. */
    fun validate(path: String): String {
        val problem = problem(path)
        require(problem == null) { "Invalid project path \"${printable(path)}\": $problem" }
        return path
    }

    /**
     * Returns [path] if a store may write it: valid, and not itself named like a temp file
     * (`*.tmp`, `*.new`, `*.crswap`), which recovery would delete or roll forward.
     */
    fun validateWritable(path: String): String {
        validate(path)
        val suffix = listOf(TMP_SUFFIX, NEW_SUFFIX, CRSWAP_SUFFIX).firstOrNull { path.endsWith(it) }
        require(suffix == null) { "Invalid project path \"$path\": a file ending $suffix is reserved for atomic writes" }
        return path
    }

    /** The segments of a valid [path]. */
    fun segments(path: String): List<String> = validate(path).split('/')

    /** The directory part of a valid [path], or `""` when it sits at the project root. */
    fun parent(path: String): String {
        val slash = validate(path).lastIndexOf('/')
        return if (slash < 0) "" else path.substring(0, slash)
    }

    /** The last segment of a valid [path]. */
    fun name(path: String): String = validate(path).substringAfterLast('/')

    /** [parent] and [name] joined back together; [parent] may be `""`. */
    fun join(
        parent: String,
        name: String,
    ): String = validate(if (parent.isEmpty()) name else "$parent/$name")

    /** Whether [path] is inside the root `.git` directory, which stores leave alone. */
    fun isInGitDirectory(path: String): Boolean = path == GIT_DIRECTORY || path.startsWith("$GIT_DIRECTORY/")

    private fun problem(path: String): String? {
        if (path.isEmpty()) return "it is empty"
        if ('\u0000' in path) return "it contains NUL"
        if ('\\' in path) return "it contains a backslash"
        if (path.startsWith("/")) return "it is absolute"
        if (path.endsWith("/")) return "it ends with /"
        for (segment in path.split('/')) {
            when (segment) {
                "" -> return "it has an empty segment"
                "." -> return "it has a . segment"
                ".." -> return "it has a .. segment"
            }
        }
        return null
    }

    private fun printable(path: String): String = path.replace("\u0000", "\\0")
}

/**
 * The files a project's format writes (docs/PROJECT_MODEL.md §3), which are the only files whose
 * leftover temp files recovery may delete or roll forward:
 *
 * - `typewright.json`;
 * - in each top-level `.ufo` directory: `metainfo.plist`, `fontinfo.plist`,
 *   `layercontents.plist`, `lib.plist`, `groups.plist`, `kerning.plist`, `features.fea`,
 *   `glyphs/contents.plist` and the `.glif` files in `glyphs/`;
 * - the `.glif` and `.diff` files in `locks/<master>/`;
 * - `scrapbook/manifest.json` and the `.jpg`, `.png` and `.webp` images in `scrapbook/`;
 * - `lessons/workbook-<script>.json`;
 * - `comparisons/overlays.json` and the `.ttf` files in `comparisons/`;
 * - anything in `build/`, which holds only regenerable output.
 *
 * Everything else in the directory belongs to someone else: a README, `.git/`, a UFO's `data/`
 * and `images/`, its other layers, and any other tool's files.
 */
object ProjectLayout {
    /** Whether the project format writes [path]. */
    fun isFormatPath(path: String): Boolean {
        if (!ProjectPath.isValid(path)) return false
        val segments = path.split('/')
        val name = segments.last()
        return when (segments[0]) {
            PROJECT_RECORD -> segments.size == 1
            "locks" -> segments.size == 3 && (hasStemAndSuffix(name, ".glif") || hasStemAndSuffix(name, ".diff"))
            "scrapbook" -> segments.size == 2 && (name == "manifest.json" || PIN_IMAGE_SUFFIXES.any { hasStemAndSuffix(name, it) })
            "lessons" -> segments.size == 2 && LESSONS_FILE.matches(name)
            "comparisons" -> segments.size == 2 && (name == "overlays.json" || hasStemAndSuffix(name, ".ttf"))
            "build" -> segments.size >= 2
            else -> hasStemAndSuffix(segments[0], ".ufo") && isUfoFile(segments.drop(1))
        }
    }

    private fun isUfoFile(inside: List<String>): Boolean =
        when (inside.size) {
            1 -> inside[0] in UFO_TOP_FILES
            2 -> inside[0] == "glyphs" && (inside[1] == "contents.plist" || hasStemAndSuffix(inside[1], ".glif"))
            else -> false
        }

    private fun hasStemAndSuffix(
        name: String,
        suffix: String,
    ): Boolean = name.length > suffix.length && name.endsWith(suffix)

    private const val PROJECT_RECORD = "typewright.json"
    private val UFO_TOP_FILES =
        setOf("metainfo.plist", "fontinfo.plist", "layercontents.plist", "lib.plist", "groups.plist", "kerning.plist", "features.fea")
    private val PIN_IMAGE_SUFFIXES = listOf(".jpg", ".png", ".webp")
    private val LESSONS_FILE = Regex("workbook-[a-z0-9]+\\.json")
}

/**
 * What [ProjectStore.recover] does with a listing: which temp files to delete and which `.new`
 * files to roll forward. Shared so every store, including the platform ones, applies the §5
 * rules identically. Only temp files of paths the format writes ([ProjectLayout.isFormatPath])
 * count; another tool's `cache.tmp` or `notes.txt.new` is not ours to delete or finish.
 */
data class RecoveryPlan(
    /** Partial writes (`X.tmp`, `X.crswap`) to delete, sorted. */
    val discard: List<String>,
    /** Completed writes (`X.new`) to rename over `X`, sorted. */
    val rollForward: List<String>,
) {
    /** Whether there is nothing to recover. */
    val isEmpty: Boolean get() = discard.isEmpty() && rollForward.isEmpty()

    companion object {
        /** The plan for a store whose files are [paths]. */
        fun of(paths: Collection<String>): RecoveryPlan {
            val candidates = paths.filterNot(ProjectPath::isInGitDirectory).sorted()
            val discard =
                candidates.filter {
                    isTempOfFormatPath(it, ProjectPath.TMP_SUFFIX) || isTempOfFormatPath(it, ProjectPath.CRSWAP_SUFFIX)
                }
            val rollForward = candidates.filter { isTempOfFormatPath(it, ProjectPath.NEW_SUFFIX) }
            return RecoveryPlan(discard, rollForward)
        }

        /** The file a `.new` completes: [newPath] without its suffix. */
        fun targetOf(newPath: String): String = newPath.removeSuffix(ProjectPath.NEW_SUFFIX)

        private fun isTempOfFormatPath(
            path: String,
            suffix: String,
        ): Boolean = path.endsWith(suffix) && ProjectLayout.isFormatPath(path.removeSuffix(suffix))
    }
}
