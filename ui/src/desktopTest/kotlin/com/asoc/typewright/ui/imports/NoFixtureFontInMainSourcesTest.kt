// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.imports

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rule that "your font" means the open project: no code in any main source set of any module
 * names the repository's sample font (Hyle Deco) except the one loader that ships it as a
 * labelled, user-chosen sample. A test font belongs in tests; a view of "your font" that quietly
 * reads a fixture would show someone else's letters as theirs.
 *
 * What it scans: every `.kt` file under every `src/<set>Main` (and `src/main`) directory of every
 * module, with the comments removed first, because a comment may cite where a measured number came
 * from (the sample is where the node-economy fixtures are measured) but cannot load anything. What
 * is left is code and string literals, which is where a font is named, loaded or labelled. What it
 * does not scan: test source sets (they may use the fixture freely), non-Kotlin resources (the Craft
 * lesson `a1-tracing-destroys-the-drawing.yaml` names the sample as the thing the lesson is about;
 * nothing in code reads that path), and build scripts.
 *
 * The match is the name in any spelling the code uses (`HyleDeco`, `Hyle Deco`, `hyle-deco`).
 */
class NoFixtureFontInMainSourcesTest {
    @Test
    fun noMainSourceSetNamesTheSampleFontExceptTheSampleLoader() {
        val hits = fixtureReferences(repositoryRoot()).filterNot { it.path in ALLOWED }
        assertTrue(
            hits.isEmpty(),
            "main source files may not name the sample font (see this test's KDoc); found:\n" +
                hits.joinToString("\n") { "  ${it.path}:${it.line}: ${it.text.trim()}" },
        )
    }

    @Test
    fun everyAllowListEntryIsStillNeededAndHasAReason() {
        val hitPaths = fixtureReferences(repositoryRoot()).map { it.path }.toSet()
        for ((path, reason) in ALLOWED) {
            assertTrue(reason.isNotBlank(), "$path is allow-listed without a reason")
            assertTrue(File(repositoryRoot(), path).isFile, "$path is allow-listed but no longer exists; remove it")
            assertTrue(path in hitPaths, "$path is allow-listed but no longer names the sample; remove it")
        }
    }

    @Test
    fun theScanReadsTheMainSourceSetsOfEveryModule() {
        val root = repositoryRoot()
        val files = mainSourceFiles(root).map { it.relativeTo(root).invariantSeparatorsPath }
        for (expected in listOf(
            "core-font/src/commonMain",
            "project/src/commonMain",
            "campaign/src/commonMain",
            "ui/src/commonMain",
            "ui/src/desktopMain",
        )) {
            assertTrue(files.any { it.startsWith("$expected/") }, "the scan did not reach $expected")
        }
        assertTrue(
            files.none { "/src/commonTest/" in it || "/src/desktopTest/" in it || "/src/jvmTest/" in it },
            "a test source set was scanned",
        )
        assertTrue(files.size > MIN_MAIN_FILES, "only ${files.size} main files were scanned; the walk is missing modules")
    }

    @Test
    fun theScannerFlagsCodeAndStringsButNotComments() {
        val source =
            """
            // HyleDeco in a line comment
            /* HyleDeco in a block comment /* nested HyleDeco */ still comment HyleDeco */
            /** KDoc: HyleDeco */
            val a = "fonts/HyleDeco-Regular.ttf" // trailing HyleDeco comment
            val b = HyleDecoBytes
            val c = ${"\"\"\""}raw HyleDeco ${"\"\"\""}
            val d = "url://not-a-comment ${'$'}{ "Hyle Deco" }"
            val e = '"'
            val f = "after the char literal"
            """.trimIndent()
        val hits = findReferences(source)
        assertEquals(listOf(4, 5, 6, 7), hits.map { it.first }, "lines flagged: $hits")
    }

    @Test
    fun theScannerKeepsLineNumbersAndDoesNotLoseCodeAfterAComment() {
        val stripped = stripKotlinComments("val a = 1 /* two\nlines */ val b = HyleDeco\n// done")
        assertEquals(3, stripped.lines().size)
        assertTrue("val b = HyleDeco" in stripped)
        assertTrue("two" !in stripped && "done" !in stripped)
    }

    private fun repositoryRoot(): File =
        generateSequence(File("").absoluteFile) { it.parentFile }.firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("no settings.gradle.kts above ${File("").absolutePath}; this test runs from inside the repository")

    private companion object {
        const val MIN_MAIN_FILES = 300

        /** Main-source files that may name the sample, each with the reason. */
        val ALLOWED: Map<String, String> =
            mapOf(
                "ui/src/commonMain/kotlin/com/asoc/typewright/ui/imports/BundledSampleFont.kt" to
                    "the one loader: embeds the OFL sample and offers it only through the labelled \"Try the sample\" choice",
                "ui/src/commonMain/kotlin/com/asoc/typewright/ui/settings/NoticeData.kt" to
                    "generated from THIRD_PARTY.md by tools/gen_notices.py; the licence notices must name every font that ships",
            )
    }
}

/** One line of a main source file that names the sample. */
internal data class FixtureReference(
    val path: String,
    val line: Int,
    val text: String,
)

private val FIXTURE_NAME = Regex("hyle[ _-]?deco", RegexOption.IGNORE_CASE)

private val SKIPPED_DIRECTORIES = setOf("build", ".git", ".gradle", ".kotlin", ".idea", ".claude", "node_modules")

/** Every Kotlin file in a main source set (`src/commonMain`, `src/desktopMain`, `src/main` …) under [root]. */
internal fun mainSourceFiles(root: File): List<File> =
    root
        .walkTopDown()
        .onEnter { it == root || it.name !in SKIPPED_DIRECTORIES }
        .filter { it.isFile && it.extension == "kt" && inMainSourceSet(it.relativeTo(root).invariantSeparatorsPath) }
        .toList()

private fun inMainSourceSet(relativePath: String): Boolean {
    val parts = relativePath.split('/')
    val src = parts.indexOf("src")
    if (src < 0 || src + 1 >= parts.size - 1) return false
    val set = parts[src + 1]
    return set == "main" || set.endsWith("Main")
}

/** The lines of every main source file that name the sample outside a comment. */
internal fun fixtureReferences(root: File): List<FixtureReference> =
    mainSourceFiles(root).flatMap { file ->
        val relative = file.relativeTo(root).invariantSeparatorsPath
        val lines = stripKotlinComments(file.readText()).lines()
        findReferences(lines).map { (number, text) -> FixtureReference(relative, number, text) }
    }

private fun findReferences(source: String): List<Pair<Int, String>> = findReferences(stripKotlinComments(source).lines())

private fun findReferences(strippedLines: List<String>): List<Pair<Int, String>> =
    strippedLines.mapIndexedNotNull { index, text -> if (FIXTURE_NAME.containsMatchIn(text)) (index + 1) to text else null }

/**
 * [source] with every comment (line, block, nested block, KDoc) replaced by spaces, line breaks kept
 * so line numbers survive. Strings are respected: `//` inside a string literal is not a comment, a
 * string template's `${ … }` is code, and a raw string runs to its closing quotes.
 */
internal fun stripKotlinComments(source: String): String = KotlinCommentStripper(source).run()

private class KotlinCommentStripper(
    private val s: String,
) {
    private val out = StringBuilder(s.length)
    private var i = 0

    fun run(): String {
        code(untilClosingBrace = false)
        return out.toString()
    }

    private fun at(text: String) = s.startsWith(text, i)

    private fun emit(c: Char) {
        out.append(c)
        i++
    }

    private fun blank() {
        out.append(if (s[i] == '\n') '\n' else ' ')
        i++
    }

    private fun code(untilClosingBrace: Boolean) {
        var depth = 0
        while (i < s.length) {
            val c = s[i]
            when {
                at("//") -> {
                    while (i < s.length && s[i] != '\n') blank()
                }

                at("/*") -> {
                    blockComment()
                }

                at("\"\"\"") -> {
                    rawString()
                }

                c == '"' -> {
                    string()
                }

                c == '\'' -> {
                    charLiteral()
                }

                c == '{' -> {
                    depth++
                    emit(c)
                }

                c == '}' -> {
                    if (untilClosingBrace && depth == 0) {
                        emit(c)
                        return
                    }
                    depth--
                    emit(c)
                }

                else -> {
                    emit(c)
                }
            }
        }
    }

    private fun blockComment() {
        var depth = 0
        do {
            when {
                at("/*") -> {
                    depth++
                    blank()
                    blank()
                }

                at("*/") -> {
                    depth--
                    blank()
                    blank()
                }

                else -> {
                    blank()
                }
            }
        } while (i < s.length && depth > 0)
    }

    private fun string() {
        emit('"')
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' -> {
                    emit(c)
                    if (i < s.length) emit(s[i])
                }

                c == '"' -> {
                    emit(c)
                    return
                }

                c == '\n' -> {
                    return
                }

                at("\${") -> {
                    emit('$')
                    emit('{')
                    code(untilClosingBrace = true)
                }

                else -> {
                    emit(c)
                }
            }
        }
    }

    private fun rawString() {
        repeat(3) { emit('"') }
        while (i < s.length) {
            when {
                at("\"\"\"") -> {
                    // A raw string ends at the last quote of a run of three or more.
                    while (at("\"")) emit('"')
                    return
                }

                at("\${") -> {
                    emit('$')
                    emit('{')
                    code(untilClosingBrace = true)
                }

                else -> {
                    emit(s[i])
                }
            }
        }
    }

    private fun charLiteral() {
        emit('\'')
        if (i < s.length && s[i] == '\\') {
            emit('\\')
            if (i < s.length) emit(s[i])
        } else if (i < s.length) {
            emit(s[i])
        }
        while (i < s.length && s[i] != '\'' && s[i] != '\n') emit(s[i])
        if (i < s.length && s[i] == '\'') emit('\'')
    }
}
