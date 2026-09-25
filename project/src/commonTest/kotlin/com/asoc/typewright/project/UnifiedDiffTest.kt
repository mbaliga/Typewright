// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The expected texts here are hand-checked unified diffs. The jvmTest also compares hunks with
 * GNU `diff -u` wherever the minimal diff is unique, and has GNU `patch -R` reverse every diff.
 */
class UnifiedDiffTest {
    private val approvedT =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <glyph name="T" format="2">
          <advance width="500"/>
          <unicode hex="0054"/>
          <outline>
            <contour>
              <point x="42" y="1" type="line"/>
              <point x="458" y="1" type="line"/>
              <point x="458" y="700" type="line"/>
              <point x="42" y="700" type="line"/>
            </contour>
          </outline>
        </glyph>
        """.trimIndent() + "\n"

    private val editedT = approvedT.replace("""<point x="42" y="1" type="line"/>""", """<point x="42" y="0" type="line"/>""")

    @Test
    fun aPointMoveGivesOneHunkWithTheProjectHeader() {
        val diff =
            UnifiedDiff.diff(
                approvedT,
                editedT,
                oldPath = "locks/regular/T_.glif",
                newPath = "HyleDeco-Regular.ufo/glyphs/T_.glif",
                oldTime = "2026-09-25T10:20:00Z",
                newTime = "2026-09-25T11:02:41Z",
            )
        val expected =
            "--- locks/regular/T_.glif\t2026-09-25T10:20:00Z\n" +
                "+++ HyleDeco-Regular.ufo/glyphs/T_.glif\t2026-09-25T11:02:41Z\n" +
                "@@ -4,7 +4,7 @@\n" +
                "   <unicode hex=\"0054\"/>\n" +
                "   <outline>\n" +
                "     <contour>\n" +
                "-      <point x=\"42\" y=\"1\" type=\"line\"/>\n" +
                "+      <point x=\"42\" y=\"0\" type=\"line\"/>\n" +
                "       <point x=\"458\" y=\"1\" type=\"line\"/>\n" +
                "       <point x=\"458\" y=\"700\" type=\"line\"/>\n" +
                "       <point x=\"42\" y=\"700\" type=\"line\"/>\n"
        assertEquals(expected, diff)
        assertEquals(approvedT, UnifiedDiff.reverseApply(diff, editedT))
        assertEquals(editedT, UnifiedDiff.apply(diff, approvedT))
    }

    @Test
    fun equalTextsGiveNoDiff() {
        assertEquals("", UnifiedDiff.diff(approvedT, approvedT, "a", "b"))
        assertEquals(approvedT, UnifiedDiff.reverseApply("", approvedT))
    }

    @Test
    fun theHeaderOmitsTimesWhenNoneAreGiven() {
        val diff = UnifiedDiff.diff("a\n", "b\n", "old.txt", "new.txt")
        assertEquals("--- old.txt\n+++ new.txt\n@@ -1 +1 @@\n-a\n+b\n", diff)
    }

    @Test
    fun anAmbiguousDeletionSlidesDown() {
        val old =
            """
            <glyph name="o" format="2">
              <outline>
                <contour>
                  <point x="1" y="1" type="line"/>
                  <point x="2" y="2" type="line"/>
                </contour>
                <contour>
                  <point x="3" y="3" type="line"/>
                  <point x="4" y="4" type="line"/>
                </contour>
              </outline>
            </glyph>
            """.trimIndent() + "\n"
        val new =
            old.replace(
                "    <contour>\n      <point x=\"1\" y=\"1\" type=\"line\"/>\n      <point x=\"2\" y=\"2\" type=\"line\"/>\n    </contour>\n",
                "",
            )
        val expectedHunks =
            "@@ -1,10 +1,6 @@\n" +
                " <glyph name=\"o\" format=\"2\">\n" +
                "   <outline>\n" +
                "     <contour>\n" +
                "-      <point x=\"1\" y=\"1\" type=\"line\"/>\n" +
                "-      <point x=\"2\" y=\"2\" type=\"line\"/>\n" +
                "-    </contour>\n" +
                "-    <contour>\n" +
                "       <point x=\"3\" y=\"3\" type=\"line\"/>\n" +
                "       <point x=\"4\" y=\"4\" type=\"line\"/>\n" +
                "     </contour>\n"
        assertEquals(expectedHunks, hunks(UnifiedDiff.diff(old, new, "a", "b")))
    }

    @Test
    fun aMissingFinalNewlineIsMarked() {
        val diff = UnifiedDiff.diff("a\nb\nc", "a\nb\nc\nd", "a", "b")
        val expectedHunks = "@@ -1,3 +1,4 @@\n a\n b\n-c\n\\ No newline at end of file\n+c\n+d\n\\ No newline at end of file\n"
        assertEquals(expectedHunks, hunks(diff))
        assertEquals("a\nb\nc", UnifiedDiff.reverseApply(diff, "a\nb\nc\nd"))
        assertEquals("a\nb\nc\nd", UnifiedDiff.apply(diff, "a\nb\nc"))
    }

    @Test
    fun emptyFilesUseTheZeroLengthRangeNotation() {
        assertEquals("@@ -0,0 +1,2 @@\n+x\n+y\n", hunks(UnifiedDiff.diff("", "x\ny\n", "a", "b")))
        assertEquals("@@ -1,2 +0,0 @@\n-x\n-y\n", hunks(UnifiedDiff.diff("x\ny\n", "", "a", "b")))
        assertEquals("", UnifiedDiff.reverseApply(UnifiedDiff.diff("", "x\n", "a", "b"), "x\n"))
    }

    @Test
    fun changesSixLinesApartShareAHunkAndSevenApartDoNot() {
        val base = (1..30).map { "line $it" }

        fun text(lines: List<String>) = lines.joinToString("\n", postfix = "\n")

        val sixApart =
            base.toMutableList().apply {
                set(9, "changed 10")
                set(16, "changed 17")
            }
        assertEquals(1, hunkCount(UnifiedDiff.diff(text(base), text(sixApart), "a", "b")))
        assertEquals("@@ -7,14 +7,14 @@", hunks(UnifiedDiff.diff(text(base), text(sixApart), "a", "b")).lines().first())

        val sevenApart =
            base.toMutableList().apply {
                set(9, "changed 10")
                set(17, "changed 18")
            }
        val diff = UnifiedDiff.diff(text(base), text(sevenApart), "a", "b")
        assertEquals(listOf("@@ -7,7 +7,7 @@", "@@ -15,7 +15,7 @@"), diff.lines().filter { it.startsWith("@@") })
        assertEquals(text(base), UnifiedDiff.reverseApply(diff, text(sevenApart)))
    }

    @Test
    fun insertingIntoARunOfEqualLinesPlacesTheInsertionLast() {
        val diff = UnifiedDiff.diff("a\nb\nb\nc\n", "a\nb\nb\nb\nc\n", "x", "y")
        assertEquals("@@ -1,4 +1,5 @@\n a\n b\n b\n+b\n c\n", hunks(diff))
    }

    @Test
    fun aContourAddedAtTheEndReadsAsANewContour() {
        val old = "<outline>\n  <contour>\n    <point a/>\n  </contour>\n</outline>\n"
        val new = "<outline>\n  <contour>\n    <point a/>\n  </contour>\n  <contour>\n    <point b/>\n  </contour>\n</outline>\n"
        val expected =
            "@@ -2,4 +2,7 @@\n" +
                "   <contour>\n" +
                "     <point a/>\n" +
                "   </contour>\n" +
                "+  <contour>\n" +
                "+    <point b/>\n" +
                "+  </contour>\n" +
                " </outline>\n"
        assertEquals(expected, hunks(UnifiedDiff.diff(old, new, "a", "b")))
    }

    @Test
    fun runsOfOneKindEndWhereIdenticalLinesStop() {
        // Wherever a block of only deletions or only insertions sits, the line after it differs
        // from its first line: otherwise it could have slid one further.
        val random = Random(7)
        repeat(2000) { iteration ->
            val old = randomText(random)
            val new = mutate(old, random)
            val body = UnifiedDiff.diff(old, new, "a", "b", context = 1).lines().drop(2)
            var i = 0
            while (i < body.size) {
                val kind = body[i].firstOrNull()
                if (kind != '-' && kind != '+') {
                    i++
                    continue
                }
                var end = i
                while (end < body.size && body[end].firstOrNull().let { it == '-' || it == '+' || it == '\\' }) end++
                val block = body.subList(i, end).filterNot { it.startsWith("\\") }
                // A context line followed by the no-newline marker differs from any line that has one.
                val nextEndsTheFile = end + 1 < body.size && body[end + 1].startsWith("\\")
                if (block.all { it[0] == kind } && end < body.size && body[end].startsWith(" ") && !nextEndsTheFile) {
                    assertTrue(
                        block.first().substring(1) != body[end].substring(1),
                        "iteration $iteration: a run could slide further in\n${body.joinToString("\n")}",
                    )
                }
                i = end
            }
        }
        // A replacement next to a repeat stays where the search found it, and stays minimal.
        val diff = UnifiedDiff.diff("p\nq\nq\n", "p\nr\nq\n", "a", "b")
        assertEquals("p\nq\nq\n", UnifiedDiff.reverseApply(diff, "p\nr\nq\n"))
        assertEquals(1, diff.lines().drop(2).count { it.startsWith("-") })
    }

    @Test
    fun aMismatchedHunkIsRefusedRatherThanFuzzed() {
        val diff = UnifiedDiff.diff(approvedT, editedT, "a", "b")
        assertFailsWith<IllegalArgumentException> { UnifiedDiff.reverseApply(diff, approvedT) }
        assertFailsWith<IllegalArgumentException> { UnifiedDiff.apply(diff, editedT) }
        assertFailsWith<IllegalArgumentException> { UnifiedDiff.apply("--- a\n+++ b\n@@ -1,2 +1,2 @@\n-x\n", "x\n") }
    }

    @Test
    fun randomEditsRoundTripAndAreMinimal() {
        val random = Random(20260925)
        repeat(3000) { iteration ->
            val old = randomText(random)
            val new = if (random.nextInt(4) == 0) mutate(old, random) else randomText(random)
            val diff = UnifiedDiff.diff(old, new, "old", "new", context = random.nextInt(0, 4))
            assertEquals(new, UnifiedDiff.apply(diff, old), "iteration $iteration forward")
            assertEquals(old, UnifiedDiff.reverseApply(diff, new), "iteration $iteration reverse")

            val oldLines = UnifiedDiff.splitLines(old)
            val newLines = UnifiedDiff.splitLines(new)
            val body = diff.lines().drop(2)
            val deletions = body.count { it.startsWith("-") }
            val insertions = body.count { it.startsWith("+") }
            val lcs = lcsLength(oldLines, newLines)
            assertEquals(oldLines.size - lcs, deletions, "iteration $iteration: deletions are minimal for\n$old\n---\n$new")
            assertEquals(newLines.size - lcs, insertions, "iteration $iteration: insertions are minimal")
        }
    }

    @Test
    fun aLargeRewriteIsHandled() {
        val old = (1..2000).joinToString("\n", postfix = "\n") { "<point x=\"$it\" y=\"${it * 7 % 13}\"/>" }
        val new = (1..40).joinToString("\n", postfix = "\n") { "<point x=\"${it * 50}\" y=\"0\"/>" }
        val diff = UnifiedDiff.diff(old, new, "a", "b")
        assertEquals(old, UnifiedDiff.reverseApply(diff, new))
        assertTrue(diff.startsWith("--- a\n+++ b\n@@ -1,2000 +1,40 @@\n"))
    }

    private fun hunks(diff: String): String = diff.lines().drop(2).joinToString("\n")

    private fun hunkCount(diff: String): Int = diff.lines().count { it.startsWith("@@") }

    private fun randomText(random: Random): String {
        val count = random.nextInt(0, 12)
        val lines = List(count) { listOf("a", "b", "c", "<contour>", "</contour>")[random.nextInt(5)] }
        if (lines.isEmpty()) return ""
        val joined = lines.joinToString("\n")
        return if (random.nextInt(5) == 0) joined else joined + "\n"
    }

    private fun mutate(
        text: String,
        random: Random,
    ): String {
        val lines = UnifiedDiff.splitLines(text).map { it.removeSuffix("\n") }.toMutableList()
        repeat(random.nextInt(1, 4)) {
            when (random.nextInt(3)) {
                0 -> lines.add(random.nextInt(lines.size + 1), "x${random.nextInt(3)}")
                1 -> if (lines.isNotEmpty()) lines.removeAt(random.nextInt(lines.size))
                else -> if (lines.isNotEmpty()) lines[random.nextInt(lines.size)] = "y"
            }
        }
        return if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = "\n")
    }

    private fun lcsLength(
        a: List<String>,
        b: List<String>,
    ): Int {
        val table = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) {
            for (j in b.indices.reversed()) {
                table[i][j] = if (a[i] == b[j]) table[i + 1][j + 1] + 1 else maxOf(table[i + 1][j], table[i][j + 1])
            }
        }
        return table[0][0]
    }
}
