// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

/**
 * Unified diffs, as `diff -u` writes them, in pure Kotlin: the record law 1 keeps when an
 * approved glyph is edited (docs/PROJECT_MODEL.md §7.3). A glif has one point per line, so a
 * line diff of two glifs reads well, and `patch -R` against the current glif gives back the
 * approved one.
 *
 * The comparison is Myers' O(ND) algorithm with its linear-space refinement (E. W. Myers, "An
 * O(ND) Difference Algorithm and Its Variations", Algorithmica 1(2), 1986, §4b), written from
 * the paper, so the diff is always minimal. Where several minimal diffs exist, one rule settles
 * the placement: a run made only of deletions, or only of insertions, moves as far down as
 * identical lines allow, joining any run of the same kind it reaches. So a contour added after
 * another reads `+<contour>` … `+</contour>` rather than starting at the previous contour's
 * `</contour>`. Replacements stay where the search put them. The output is a pure function of
 * the two texts, so the same edit always gives the same bytes.
 */
object UnifiedDiff {
    /** Lines of unchanged context around each change, as `diff -u` uses by default. */
    const val CONTEXT_LINES: Int = 3

    /** The marker `diff` writes after a line that has no final newline. */
    const val NO_NEWLINE_MARKER: String = "\\ No newline at end of file"

    /**
     * The unified diff from [oldText] to [newText], or `""` when they are equal. The header
     * names [oldPath] and [newPath], each followed by a tab and its time when [oldTime] or
     * [newTime] is given:
     *
     * ```
     * --- locks/regular/T_.glif	2026-09-25T10:20:00Z
     * +++ HyleDeco-Regular.ufo/glyphs/T_.glif	2026-09-25T11:02:41Z
     * @@ -6,7 +6,7 @@
     * ```
     */
    fun diff(
        oldText: String,
        newText: String,
        oldPath: String,
        newPath: String,
        oldTime: String? = null,
        newTime: String? = null,
        context: Int = CONTEXT_LINES,
    ): String {
        require(context >= 0) { "Context must not be negative; got $context" }
        if (oldText == newText) return ""
        val oldLines = splitLines(oldText)
        val newLines = splitLines(newText)
        val ids = HashMap<String, Int>()
        val a = IntArray(oldLines.size) { ids.getOrPut(oldLines[it]) { ids.size } }
        val b = IntArray(newLines.size) { ids.getOrPut(newLines[it]) { ids.size } }
        val deleted = BooleanArray(a.size)
        val inserted = BooleanArray(b.size)
        MyersDiff(a, b, deleted, inserted).solve(0, a.size, 0, b.size)
        val groups = slidePureRunsDown(changeGroups(deleted, inserted), a, b)

        val out = StringBuilder()
        out.append("--- ").append(oldPath)
        if (oldTime != null) out.append('\t').append(oldTime)
        out.append('\n')
        out.append("+++ ").append(newPath)
        if (newTime != null) out.append('\t').append(newTime)
        out.append('\n')
        writeHunks(groups, oldLines, newLines, context, out)
        return out.toString()
    }

    /** [oldText] with [diff] applied: the new text. Throws [IllegalArgumentException] if a hunk doesn't match. */
    fun apply(
        diff: String,
        oldText: String,
    ): String = applyHunks(parseHunks(diff), oldText, reverse = false)

    /** [newText] with [diff] applied in reverse (`patch -R`): the old text. Throws [IllegalArgumentException] if a hunk doesn't match. */
    fun reverseApply(
        diff: String,
        newText: String,
    ): String = applyHunks(parseHunks(diff), newText, reverse = true)

    // ---- Lines ------------------------------------------------------------------------------

    /** [text] as lines, each keeping its `\n`; only the last may lack one. */
    internal fun splitLines(text: String): List<String> {
        val lines = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            val newline = text.indexOf('\n', start)
            val end = if (newline < 0) text.length else newline + 1
            lines += text.substring(start, end)
            start = end
        }
        return lines
    }

    private fun appendLine(
        prefix: Char,
        line: String,
        out: StringBuilder,
    ) {
        out.append(prefix).append(line)
        if (!line.endsWith('\n')) out.append('\n').append(NO_NEWLINE_MARKER).append('\n')
    }

    // ---- Change groups ---------------------------------------------------------------------

    /**
     * A maximal run of changes between two matched lines: old lines [a0, a1) deleted and new
     * lines [b0, b1) inserted. Between consecutive groups the old and new files have the same
     * number of matched lines.
     */
    private class Group(
        val a0: Int,
        val a1: Int,
        val b0: Int,
        val b1: Int,
    ) {
        val isDeletion: Boolean get() = b0 == b1
        val isInsertion: Boolean get() = a0 == a1
    }

    private fun changeGroups(
        deleted: BooleanArray,
        inserted: BooleanArray,
    ): List<Group> {
        val groups = ArrayList<Group>()
        var i = 0
        var j = 0
        while (i < deleted.size || j < inserted.size) {
            if (i < deleted.size && j < inserted.size && !deleted[i] && !inserted[j]) {
                i++
                j++
                continue
            }
            val a0 = i
            val b0 = j
            while (i < deleted.size && deleted[i]) i++
            while (j < inserted.size && inserted[j]) j++
            groups += Group(a0, i, b0, j)
        }
        return groups
    }

    /**
     * Moves each run of only deletions, or only insertions, down while the line after it equals
     * its first line: the run then covers the same lines shifted by one, and the matched line
     * it passes is matched one line earlier instead, so the diff stays minimal and valid. A run
     * that reaches the next change joins it, and keeps moving if the joined run is still of one
     * kind. Runs that both delete and insert stay where they are.
     */
    private fun slidePureRunsDown(
        groups: List<Group>,
        a: IntArray,
        b: IntArray,
    ): List<Group> {
        val out = ArrayList<Group>(groups.size)
        var next = 0
        while (next < groups.size) {
            var group = groups[next++]
            while (true) {
                val lines =
                    if (group.isInsertion) {
                        b
                    } else if (group.isDeletion) {
                        a
                    } else {
                        break
                    }
                val start = if (group.isInsertion) group.b0 else group.a0
                val end = if (group.isInsertion) group.b1 else group.a1
                // The pair after a group is always a match (groups are maximal), unless the file ends.
                if (end >= lines.size || lines[start] != lines[end]) break
                group = Group(group.a0 + 1, group.a1 + 1, group.b0 + 1, group.b1 + 1)
                if (next < groups.size && groups[next].a0 == group.a1 && groups[next].b0 == group.b1) {
                    val joined = groups[next++]
                    group = Group(group.a0, joined.a1, group.b0, joined.b1)
                }
            }
            out += group
        }
        return out
    }

    // ---- Rendering ---------------------------------------------------------------------------

    private fun writeHunks(
        groups: List<Group>,
        oldLines: List<String>,
        newLines: List<String>,
        context: Int,
        out: StringBuilder,
    ) {
        var first = 0
        while (first < groups.size) {
            // Changes at most 2 × context unchanged lines apart share a hunk.
            var last = first
            while (last + 1 < groups.size && groups[last + 1].a0 - groups[last].a1 <= 2 * context) last++
            val previousEnd = if (first > 0) groups[first - 1].a1 else 0
            val leading = minOf(context, groups[first].a0 - previousEnd)
            val nextStart = if (last + 1 < groups.size) groups[last + 1].a0 else oldLines.size
            val trailing = minOf(context, nextStart - groups[last].a1)
            val oldStart = groups[first].a0 - leading
            val newStart = groups[first].b0 - leading
            val oldEnd = groups[last].a1 + trailing
            val newEnd = groups[last].b1 + trailing
            out
                .append("@@ -")
                .append(range(oldStart, oldEnd))
                .append(" +")
                .append(range(newStart, newEnd))
                .append(" @@\n")
            var i = oldStart
            for (index in first..last) {
                val group = groups[index]
                while (i < group.a0) appendLine(' ', oldLines[i++], out)
                for (k in group.a0 until group.a1) appendLine('-', oldLines[k], out)
                for (k in group.b0 until group.b1) appendLine('+', newLines[k], out)
                i = group.a1
            }
            while (i < oldEnd) appendLine(' ', oldLines[i++], out)
            first = last + 1
        }
    }

    // `diff -u` range notation: "start,count", just "start" when the count is 1, and for an
    // empty range the line before it with ",0".
    private fun range(
        start: Int,
        end: Int,
    ): String =
        when (end - start) {
            0 -> "$start,0"
            1 -> "${start + 1}"
            else -> "${start + 1},${end - start}"
        }

    // ---- Parsing and applying ------------------------------------------------------------------

    private class Hunk(
        val oldStart: Int,
        val oldCount: Int,
        val newStart: Int,
        val newCount: Int,
        val oldLines: List<String>,
        val newLines: List<String>,
    )

    private val HUNK_HEADER = Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""")

    private fun parseHunks(diff: String): List<Hunk> {
        val lines = splitLines(diff)
        val hunks = ArrayList<Hunk>()
        var i = 0
        while (i < lines.size && !lines[i].startsWith("@@")) i++
        while (i < lines.size) {
            val header =
                HUNK_HEADER.find(lines[i])
                    ?: throw IllegalArgumentException("Diff line ${i + 1} is not a hunk header: ${lines[i].trimEnd()}")
            val oldStart = header.groupValues[1].toInt()
            val oldCount = header.groupValues[2].ifEmpty { "1" }.toInt()
            val newStart = header.groupValues[3].toInt()
            val newCount = header.groupValues[4].ifEmpty { "1" }.toInt()
            i++
            val oldSide = ArrayList<String>()
            val newSide = ArrayList<String>()
            // Which side(s) the previous body line went to, so a no-newline marker can trim it.
            var lastOld = false
            var lastNew = false
            while (i < lines.size && (oldSide.size < oldCount || newSide.size < newCount || lines[i].startsWith("\\"))) {
                val line = lines[i]
                val body = line.substring(1)
                when (line[0]) {
                    ' ' -> {
                        oldSide += body
                        newSide += body
                        lastOld = true
                        lastNew = true
                    }

                    '-' -> {
                        oldSide += body
                        lastOld = true
                        lastNew = false
                    }

                    '+' -> {
                        newSide += body
                        lastOld = false
                        lastNew = true
                    }

                    '\\' -> {
                        require(lastOld || lastNew) { "Diff line ${i + 1}: a no-newline marker with no line before it" }
                        if (lastOld) oldSide[oldSide.lastIndex] = oldSide.last().removeSuffix("\n")
                        if (lastNew) newSide[newSide.lastIndex] = newSide.last().removeSuffix("\n")
                        lastOld = false
                        lastNew = false
                    }

                    else -> {
                        throw IllegalArgumentException("Diff line ${i + 1} is not part of a hunk: ${line.trimEnd()}")
                    }
                }
                i++
            }
            require(oldSide.size == oldCount && newSide.size == newCount) {
                "Hunk ${hunks.size + 1} promises $oldCount old and $newCount new lines but has ${oldSide.size} and ${newSide.size}"
            }
            hunks += Hunk(oldStart, oldCount, newStart, newCount, oldSide, newSide)
        }
        return hunks
    }

    private fun applyHunks(
        hunks: List<Hunk>,
        text: String,
        reverse: Boolean,
    ): String {
        val source = splitLines(text)
        val out = StringBuilder()
        var position = 0
        hunks.forEachIndexed { index, hunk ->
            val start = if (reverse) hunk.newStart else hunk.oldStart
            val count = if (reverse) hunk.newCount else hunk.oldCount
            val expected = if (reverse) hunk.newLines else hunk.oldLines
            val replacement = if (reverse) hunk.oldLines else hunk.newLines
            val at = if (count == 0) start else start - 1
            require(at >= position && at + expected.size <= source.size) { "Hunk ${index + 1} is out of place" }
            for (k in position until at) out.append(source[k])
            for (k in expected.indices) {
                require(source[at + k] == expected[k]) { "Hunk ${index + 1} doesn't match line ${at + k + 1}" }
            }
            replacement.forEach(out::append)
            position = at + expected.size
        }
        for (k in position until source.size) out.append(source[k])
        return out.toString()
    }
}

/**
 * Myers' linear-space diff over interned lines [a] and [b], marking the [deleted] old lines and
 * the [inserted] new ones. [solve] strips a box's common head and tail, finds a point that lies
 * on an optimal edit path through what remains (the middle snake of the paper's §4b), and
 * solves the two smaller boxes on either side of it.
 *
 * The middle snake comes from two greedy searches run in turn, one from the box's top-left
 * corner and one from its bottom-right. After d edits, `forward[k]` holds the furthest x that a
 * path from the top-left reaches on diagonal k = x − y, and `reverse[k]` the furthest distance
 * back from the bottom-right, on diagonal k of the reversed sequences. Where a forward and a
 * reverse path cover the same diagonal and meet, their edit counts add up to an optimal path.
 */
private class MyersDiff(
    private val a: IntArray,
    private val b: IntArray,
    private val deleted: BooleanArray,
    private val inserted: BooleanArray,
) {
    // Diagonals run from −(most + 1) to most + 1, where most is the largest d either search needs.
    private val most = (a.size + b.size + 1) / 2
    private val offset = most + 1
    private val forward = IntArray(2 * most + 3)
    private val reverse = IntArray(2 * most + 3)

    fun solve(
        aStart: Int,
        aEnd: Int,
        bStart: Int,
        bEnd: Int,
    ) {
        var a0 = aStart
        var a1 = aEnd
        var b0 = bStart
        var b1 = bEnd
        while (a0 < a1 && b0 < b1 && a[a0] == b[b0]) {
            a0++
            b0++
        }
        while (a0 < a1 && b0 < b1 && a[a1 - 1] == b[b1 - 1]) {
            a1--
            b1--
        }
        when {
            a0 == a1 -> {
                for (j in b0 until b1) inserted[j] = true
            }

            b0 == b1 -> {
                for (i in a0 until a1) deleted[i] = true
            }

            else -> {
                val (x, y) = middlePoint(a0, a1, b0, b1)
                solve(a0, x, b0, y)
                solve(x, a1, y, b1)
            }
        }
    }

    /**
     * A point strictly inside the box [a0, a1) × [b0, b1) that lies on an optimal edit path. The
     * box has no common head or tail and neither side is empty, so its edit distance is at
     * least 2 and each half has fewer edits.
     */
    private fun middlePoint(
        a0: Int,
        a1: Int,
        b0: Int,
        b1: Int,
    ): Pair<Int, Int> {
        val n = a1 - a0
        val m = b1 - b0
        val delta = n - m
        val deltaIsOdd = (delta and 1) != 0
        for (d in 0..(n + m + 1) / 2) {
            // Forward: extend every d-path by one edit, then along its snake.
            for (k in -d..d step 2) {
                val x = furthest(forward, d, k, n, m) ?: continue
                var fx = x
                while (fx < n && fx - k < m && a[a0 + fx] == b[b0 + fx - k]) fx++
                forward[k + offset] = fx
                // With an odd delta, a forward d-path meets a reverse (d − 1)-path.
                val kr = delta - k
                if (deltaIsOdd && d > 0 && kr in -(d - 1)..(d - 1)) {
                    val rx = reverse[kr + offset]
                    if (rx != UNREACHED && fx + rx >= n) return (a0 + fx) to (b0 + fx - k)
                }
            }
            // Reverse: the same over the reversed sequences.
            for (k in -d..d step 2) {
                val x = furthest(reverse, d, k, n, m) ?: continue
                var rx = x
                while (rx < n && rx - k < m && a[a1 - 1 - rx] == b[b1 - 1 - (rx - k)]) rx++
                reverse[k + offset] = rx
                // With an even delta, a reverse d-path meets a forward d-path.
                val kf = delta - k
                if (!deltaIsOdd && kf in -d..d) {
                    val fx = forward[kf + offset]
                    if (fx != UNREACHED && fx + rx >= n) return (a1 - rx) to (b1 - (rx - k))
                }
            }
        }
        error("The searches always meet within (n + m + 1) / 2 edits")
    }

    /**
     * Where a d-path on diagonal k starts its snake: one edit on from the furthest (d − 1)-path
     * on diagonal k + 1 (a step in y) or k − 1 (a step in x), whichever gets further while
     * staying inside the n × m box. Records and returns null when no d-path reaches diagonal k.
     */
    private fun furthest(
        paths: IntArray,
        d: Int,
        k: Int,
        n: Int,
        m: Int,
    ): Int? {
        val x =
            if (d == 0) {
                0
            } else {
                val fromAbove = if (k + 1 <= d - 1) paths[k + 1 + offset] else UNREACHED
                val fromLeft = if (k - 1 >= -(d - 1)) paths[k - 1 + offset] else UNREACHED
                val down = if (fromAbove != UNREACHED && fromAbove - k <= m) fromAbove else UNREACHED
                val right = if (fromLeft != UNREACHED && fromLeft + 1 <= n) fromLeft + 1 else UNREACHED
                maxOf(down, right)
            }
        if (x == UNREACHED || x - k < 0 || x - k > m || x > n) {
            paths[k + offset] = UNREACHED
            return null
        }
        return x
    }

    private companion object {
        const val UNREACHED = -1
    }
}
