// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * One save in the §8 phase order (leaf files, then UFO index files, then `typewright.json`,
 * then deletions of what is no longer produced), over a project that also holds a foreign
 * README.md. The crash-injection tests stop it at every point and check what recovery leaves.
 */
object SaveScenario {
    const val FOREIGN_README: String = "README.md"

    val initial: Map<String, String> =
        mapOf(
            FOREIGN_README to "The user's own notes. Typewright never touches this.\n",
            "typewright.json" to "{ \"format_version\": 1, \"name\": \"before\" }\n",
            "HyleDeco-Regular.ufo/glyphs/T_.glif" to "<glyph name=\"T\"> approved </glyph>\n",
            "HyleDeco-Regular.ufo/glyphs/contents.plist" to "<plist> T </plist>\n",
            "locks/regular/H_.glif" to "<glyph name=\"H\"> snapshot </glyph>\n",
        )

    val initialDirectories: Set<String> = setOf("comparisons", "lessons", "scrapbook")

    sealed interface Step {
        val path: String
    }

    data class Write(
        override val path: String,
        val text: String,
    ) : Step

    data class Delete(
        override val path: String,
    ) : Step

    val steps: List<Step> =
        listOf(
            Write("locks/regular/T_.glif", "<glyph name=\"T\"> approved snapshot </glyph>\n"),
            Write(
                "HyleDeco-Regular.ufo/glyphs/T_.glif",
                "<glyph name=\"T\"> edited, with a longer body so that half of it is visibly partial </glyph>\n",
            ),
            Write("HyleDeco-Regular.ufo/glyphs/n.glif", "<glyph name=\"n\"> new </glyph>\n"),
            Write("HyleDeco-Regular.ufo/glyphs/contents.plist", "<plist> T n </plist>\n"),
            Delete("locks/regular/H_.glif"),
            Write("typewright.json", "{ \"format_version\": 1, \"name\": \"after\" }\n"),
        )

    val initialBytes: Map<String, ByteArray> get() = initial.mapValues { it.value.encodeToByteArray() }

    /** Runs every step against [store], in order. */
    suspend fun run(store: ProjectStore) {
        for (step in steps) {
            when (step) {
                is Write -> store.writeAtomic(step.path, step.text.encodeToByteArray())
                is Delete -> store.delete(step.path)
            }
        }
    }

    /**
     * Asserts that [files] (a store's content after recovery) is the initial state with a
     * prefix of the steps applied, each step wholly or not at all, no temp file left and the
     * foreign README untouched. Returns how many steps were applied.
     */
    fun assertConsistent(
        files: Map<String, ByteArray>,
        description: String,
    ): Int {
        val text = files.mapValues { it.value.decodeToString() }
        for (path in text.keys) {
            assertTrue(
                !path.endsWith(ProjectPath.TMP_SUFFIX) && !path.endsWith(ProjectPath.NEW_SUFFIX),
                "$description: $path was left behind",
            )
        }
        assertEquals(initial[FOREIGN_README], text[FOREIGN_README], "$description: the foreign README changed")
        val known = initial.keys + steps.map { it.path }
        for (path in text.keys) assertTrue(path in known, "$description: unexpected file $path")

        val applied =
            steps.mapIndexed { index, step ->
                val now = text[step.path]
                val before = contentBefore(index)
                val after =
                    when (step) {
                        is Write -> step.text
                        is Delete -> null
                    }
                when (now) {
                    after -> true
                    before -> false
                    else -> fail("$description: ${step.path} is neither wholly old nor wholly new: $now")
                }
            }
        val count = applied.indexOfFirst { !it }.let { if (it < 0) applied.size else it }
        assertTrue(applied.drop(count).none { it }, "$description: steps applied out of order: $applied")
        for (path in initial.keys - steps.map { it.path }.toSet()) {
            assertEquals(initial[path], text[path], "$description: $path, which the save doesn't touch, changed")
        }
        return count
    }

    // What step [index]'s path held before that step ran.
    private fun contentBefore(index: Int): String? {
        val path = steps[index].path
        var content = initial[path]
        for (earlier in steps.take(index)) {
            if (earlier.path != path) continue
            content =
                when (earlier) {
                    is Write -> earlier.text
                    is Delete -> null
                }
        }
        return content
    }
}
