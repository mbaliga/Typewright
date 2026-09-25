// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import java.io.File
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cross-checks [UnifiedDiff] against GNU diffutils, which is run as a separate program only.
 *
 * - Where the minimal diff is unique (exactly one longest common subsequence of lines, counted
 *   here), every minimal diff program must mark the same lines, so the hunks, everything after
 *   the two header lines, must be byte-identical to `diff -u --minimal`'s: the hunk headers,
 *   the context and the order of lines.
 * - Where several minimal diffs exist, each tool may place a change differently, so only the
 *   number of deleted and inserted lines is compared.
 * - Every diff must let GNU `patch -R` turn the new text back into the old.
 *
 * Where `diff` or `patch` isn't installed, the samples with hand-verified expected hunks are
 * still checked against those.
 */
class UnifiedDiffGnuCrossCheckTest {
    private val temp: Path = Files.createTempDirectory("unified-diff-gnu")

    @AfterTest
    fun cleanUp() {
        temp.toFile().deleteRecursively()
    }

    private class Sample(
        val name: String,
        val old: String,
        val new: String,
        val expectedHunks: String? = null,
    )

    private fun glif(
        name: String,
        vararg contours: List<Pair<Int, Int>>,
    ): String =
        buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<glyph name=\"$name\" format=\"2\">\n")
            append("  <advance width=\"500\"/>\n")
            append("  <outline>\n")
            for (contour in contours) {
                append("    <contour>\n")
                for ((x, y) in contour) append("      <point x=\"$x\" y=\"$y\" type=\"line\"/>\n")
                append("    </contour>\n")
            }
            append("  </outline>\n")
            append("</glyph>\n")
        }

    private val stem = listOf(42 to 1, 458 to 1, 458 to 700, 42 to 700)
    private val bar = listOf(0 to 650, 500 to 650, 500 to 700, 0 to 700)
    private val counter = listOf(100 to 100, 100 to 200, 200 to 200, 200 to 100)

    private fun samples(): List<Sample> {
        val fixed =
            listOf(
                Sample(
                    "point move",
                    glif("T", stem, bar),
                    glif("T", stem.map { if (it == (42 to 1)) 42 to 0 else it }, bar),
                    "@@ -3,7 +3,7 @@\n" +
                        "   <advance width=\"500\"/>\n" +
                        "   <outline>\n" +
                        "     <contour>\n" +
                        "-      <point x=\"42\" y=\"1\" type=\"line\"/>\n" +
                        "+      <point x=\"42\" y=\"0\" type=\"line\"/>\n" +
                        "       <point x=\"458\" y=\"1\" type=\"line\"/>\n" +
                        "       <point x=\"458\" y=\"700\" type=\"line\"/>\n" +
                        "       <point x=\"42\" y=\"700\" type=\"line\"/>\n",
                ),
                Sample("first contour deleted", glif("o", stem, bar, counter), glif("o", bar, counter)),
                Sample("middle contour deleted", glif("o", stem, bar, counter), glif("o", stem, counter)),
                Sample("last contour deleted", glif("o", stem, bar, counter), glif("o", stem, bar)),
                Sample("contour added at the end", glif("o", stem), glif("o", stem, counter)),
                Sample("contour added at the start", glif("o", stem), glif("o", counter, stem)),
                Sample("point inserted", glif("T", stem, bar), glif("T", stem.take(2) + (250 to 1) + stem.drop(2), bar)),
                Sample("point removed", glif("T", stem, bar), glif("T", stem, bar.drop(1))),
                Sample(
                    "every point moved",
                    glif("T", stem, bar),
                    glif(
                        "T",
                        stem.map { (x, y) -> x + 1 to y },
                        bar.map { (x, y) ->
                            x to
                                y - 1
                        },
                    ),
                ),
                Sample(
                    "whole outline replaced",
                    glif("T", (0 until 60).map { it * 10 to it * 7 % 13 }),
                    glif("T", stem, bar),
                ),
                Sample(
                    "no final newline",
                    "a\nb\nc",
                    "a\nb\nc\nd",
                    "@@ -1,3 +1,4 @@\n a\n b\n-c\n\\ No newline at end of file\n+c\n+d\n\\ No newline at end of file\n",
                ),
                Sample("final newline removed", "a\nb\n", "a\nb"),
                Sample("insertion into a run", "a\nb\nb\nc\n", "a\nb\nb\nb\nc\n", "@@ -1,4 +1,5 @@\n a\n b\n b\n+b\n c\n"),
                Sample("replacement next to a repeat", "p\nq\nq\n", "p\nr\nq\n"),
                Sample("from empty", "", "x\ny\n", "@@ -0,0 +1,2 @@\n+x\n+y\n"),
                Sample("to empty", "x\ny\n", "", "@@ -1,2 +0,0 @@\n-x\n-y\n"),
                Sample(
                    "two distant edits",
                    (1..40).joinToString("\n", postfix = "\n") { "line $it" },
                    (1..40).joinToString("\n", postfix = "\n") { if (it == 5 || it == 30) "edited $it" else "line $it" },
                ),
            )
        return fixed + glifLikeSamples(GLIF_LIKE_SAMPLES)
    }

    // Random edits of random glifs: point lines are mostly unique, as in real outlines, while
    // <contour> and </contour> repeat.
    private fun glifLikeSamples(count: Int): List<Sample> {
        val random = Random(1234)

        fun randomContour() = List(random.nextInt(2, 12)) { random.nextInt(0, 60) * 10 to random.nextInt(-2, 70) * 10 }
        return List(count) { index ->
            val contours = List(random.nextInt(1, 5)) { randomContour() }.toMutableList()
            val old = glif("g", *contours.toTypedArray())
            repeat(random.nextInt(1, 4)) {
                val c = random.nextInt(contours.size)
                val points = contours[c].toMutableList()
                when (random.nextInt(6)) {
                    0 -> {
                        points[random.nextInt(points.size)] = random.nextInt(0, 60) * 10 to random.nextInt(-2, 70) * 10
                    }

                    1 -> {
                        points.add(random.nextInt(points.size + 1), random.nextInt(0, 60) * 10 to 0)
                    }

                    2 -> {
                        if (points.size > 1) points.removeAt(random.nextInt(points.size))
                    }

                    3 -> {
                        points.replaceAll { (x, y) -> x + 5 to y }
                    }

                    4 -> {
                        contours.add(random.nextInt(contours.size + 1), randomContour())
                        return@repeat
                    }

                    else -> {
                        if (contours.size > 1) contours.removeAt(c)
                        return@repeat
                    }
                }
                contours[c] = points
            }
            Sample("glif-like $index", old, glif("g", *contours.toTypedArray()))
        }
    }

    // Short texts over five lines, so almost every placement is ambiguous.
    private fun repetitiveSamples(count: Int): List<Sample> {
        val random = Random(5678)
        val alphabet = listOf("<contour>", "</contour>", "<point a/>", "<point b/>", "<point c/>")
        return List(count) { index ->
            val lines = List(random.nextInt(5, 40)) { alphabet[random.nextInt(alphabet.size)] }
            val edited = lines.toMutableList()
            repeat(random.nextInt(1, 5)) {
                when (random.nextInt(3)) {
                    0 -> edited.add(random.nextInt(edited.size + 1), listOf("<contour>", "</contour>", "<point d/>")[random.nextInt(3)])
                    1 -> if (edited.isNotEmpty()) edited.removeAt(random.nextInt(edited.size))
                    else -> if (edited.isNotEmpty()) edited[random.nextInt(edited.size)] = "<point e/>"
                }
            }
            Sample("repetitive $index", lines.joinToString("\n", postfix = "\n"), edited.joinToString("\n", postfix = "\n"))
        }
    }

    @Test
    fun glifEditsGiveGnuDiffsHunksWhereTheDiffIsUniqueAndItsCountsEverywhere() {
        val diffAvailable = commandExists("diff")
        if (!diffAvailable) println("GNU diff not installed: checking the hand-verified samples only")
        val mismatches = mutableListOf<String>()
        val samples = samples()
        var unique = 0
        for (sample in samples) {
            val ours = UnifiedDiff.diff(sample.old, sample.new, "old", "new")
            val ourHunks = hunks(ours)
            if (sample.expectedHunks != null) assertEquals(sample.expectedHunks, ourHunks, sample.name)
            if (diffAvailable) {
                val gnu = gnuDiff(sample, "-u", "--minimal")
                val isUnique = hasUniqueMinimalDiff(sample)
                if (isUnique) unique++
                if (isUnique && hunks(gnu) != ourHunks) mismatches += "${sample.name}:\n--- GNU\n${hunks(gnu)}--- ours\n$ourHunks"
                assertEquals(changeCounts(gnu), changeCounts(ours), "${sample.name}: as many changed lines as GNU diff --minimal")
            }
            assertPatchReverses(sample, ours)
        }
        if (diffAvailable) println("Compared ${samples.size} glif samples with GNU diff: $unique with a unique minimal diff hunk for hunk")
        assertTrue(
            !diffAvailable || unique >= samples.size / 2,
            "most glif samples have a unique minimal diff ($unique of ${samples.size})",
        )
        assertEquals(emptyList(), mismatches, "hunks that differ from GNU diff -u where the minimal diff is unique")
    }

    @Test
    fun repetitiveTextsChangeAsFewLinesAsGnuDiffMinimal() {
        val diffAvailable = commandExists("diff")
        for (sample in repetitiveSamples(REPETITIVE_SAMPLES)) {
            val ours = UnifiedDiff.diff(sample.old, sample.new, "old", "new")
            if (diffAvailable) assertEquals(changeCounts(gnuDiff(sample, "-u", "--minimal")), changeCounts(ours), sample.name)
            assertPatchReverses(sample, ours)
        }
    }

    // Whether exactly one set of matched line pairs is a longest common subsequence: then the
    // lines a minimal diff deletes and inserts are fixed. Counts sets of pairs, not paths, by
    // inclusion and exclusion over the usual LCS table.
    private fun hasUniqueMinimalDiff(sample: Sample): Boolean {
        val a = UnifiedDiff.splitLines(sample.old)
        val b = UnifiedDiff.splitLines(sample.new)
        val length = Array(a.size + 1) { IntArray(b.size + 1) }
        val count = Array(a.size + 1) { Array<BigInteger>(b.size + 1) { BigInteger.ONE } }
        for (i in a.indices.reversed()) {
            for (j in b.indices.reversed()) {
                val matched = if (a[i] == b[j]) length[i + 1][j + 1] + 1 else -1
                val best = maxOf(matched, length[i + 1][j], length[i][j + 1])
                length[i][j] = best
                var ways = BigInteger.ZERO
                if (matched == best) ways += count[i + 1][j + 1]
                if (length[i + 1][j] == best) ways += count[i + 1][j]
                if (length[i][j + 1] == best) ways += count[i][j + 1]
                if (length[i + 1][j + 1] == best) ways -= count[i + 1][j + 1]
                count[i][j] = ways
            }
        }
        return count[0][0] == BigInteger.ONE
    }

    private fun hunks(diff: String): String = if (diff.isEmpty()) "" else diff.substringAfter('\n').substringAfter('\n')

    private fun changeCounts(diff: String): Pair<Int, Int> {
        val body = hunks(diff).lines()
        return body.count { it.startsWith("-") } to body.count { it.startsWith("+") }
    }

    private fun gnuDiff(
        sample: Sample,
        vararg options: String,
    ): String {
        val oldFile = temp.resolve("old").also { it.writeText(sample.old) }
        val newFile = temp.resolve("new").also { it.writeText(sample.new) }
        return run(listOf("diff", *options, oldFile.toString(), newFile.toString()))
    }

    private fun assertPatchReverses(
        sample: Sample,
        ours: String,
    ) {
        if (ours.isEmpty() || !commandExists("patch")) return
        val newFile = temp.resolve("patched").also { it.writeText(sample.new) }
        val patchFile = temp.resolve("ours.diff").also { it.writeText(ours) }
        val output = temp.resolve("restored")
        run(listOf("patch", "--quiet", "--reverse", "--force", "--output=$output", newFile.toString(), patchFile.toString()))
        assertEquals(sample.old, output.readText(), "${sample.name}: GNU patch -R restores the old text")
    }

    private fun run(command: List<String>): String {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().decodeToString()
        check(process.waitFor(30, TimeUnit.SECONDS)) { "$command timed out" }
        // diff exits 1 when the files differ; anything above that is trouble.
        check(process.exitValue() <= 1) { "$command failed (${process.exitValue()}): $output" }
        return output
    }

    private fun commandExists(command: String): Boolean =
        System
            .getenv("PATH")
            .orEmpty()
            .split(File.pathSeparator)
            .any { File(it, command).canExecute() }

    private companion object {
        const val GLIF_LIKE_SAMPLES = 500
        const val REPETITIVE_SAMPLES = 300
    }
}
