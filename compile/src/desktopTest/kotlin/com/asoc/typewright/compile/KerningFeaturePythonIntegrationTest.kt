// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.project.CreateResult
import com.asoc.typewright.project.InMemoryProjectStore
import com.asoc.typewright.project.KernSide
import com.asoc.typewright.project.KerningTable
import com.asoc.typewright.project.ProjectFiles
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.SessionEnvironment
import com.asoc.typewright.project.imports.ImportReview
import com.asoc.typewright.project.imports.reviewFontFile
import com.asoc.typewright.project.imports.toNewProjectSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * What the Space page tells a person about a hand-written kern feature, checked against the real build: when `features.fea`
 * has a `feature kern` of its own and no `# Automatic Code` marker, fontmake (ufo2ft) leaves out the kerning set on the Kerning
 * page; with the marker inside that feature it keeps both. The compiled font's GPOS table is read back with fontTools, so
 * the claim is about the font and not about a log line. Never skips: without a Python that has fontmake these fail
 * (`:compile:pythonIntegrationTest`).
 */
class KerningFeaturePythonIntegrationTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun repoFile(path: String): File {
        val root = System.getProperty("typewright.repoRoot") ?: fail("system property typewright.repoRoot is not set by the build")
        return File(root, path).also { assertTrue(it.isFile, "$path is missing from the repository") }
    }

    private fun python(): String =
        System.getenv(PYTHON_ENV_VARIABLE)?.takeIf { it.isNotBlank() }
            ?: fail("$PYTHON_ENV_VARIABLE is not set. Point it at a Python that has fontmake and skia-pathops.")

    /** Hyle Deco as a project with the given kerning, groups and features on its one master. */
    private fun project(kerning: UfoKerning): Pair<ProjectFiles, String> {
        val ready =
            reviewFontFile("HyleDeco-Regular.ttf", repoFile("fonts/HyleDeco-Regular.ttf").readBytes()) as? ImportReview.Ready
                ?: fail("Hyle Deco was not ready to import")
        val spec = ready.candidate.toNewProjectSpec(projectName = "HyleDeco", newFamily = "Hyle Deco Redraw")
        val withKerning = spec.copy(masters = spec.masters.map { it.copy(ufo = it.ufo.copy(kerningInfo = kerning)) })
        val env = SessionEnvironment(scope = scope, io = Dispatchers.IO)
        return runBlocking {
            val session = (ProjectSession.create(InMemoryProjectStore("kerning"), withKerning, env) as CreateResult.Created).session
            val master =
                session.state.value.font.masters
                    .single()
            session.buildSnapshot(includeBuild = false).also { session.close() } to master.path
        }
    }

    private class Built(
        val log: List<String>,
        val ttf: File,
    ) {
        /**
         * The kerning an OpenType engine applies to each of [pairs] from the compiled font's GPOS: of the pair-positioning subtables that cover the
         * first glyph, in order, the first that has a value for the pair (a class subtable always has one, which may be 0). One fontTools run for all pairs.
         */
        fun effective(
            python: String,
            pairs: List<Pair<String, String>>,
        ): Map<Pair<String, String>, Int> {
            val arguments = pairs.map { (a, b) -> "$a,$b" }
            val outcome = RealProcessRunner.run(listOf(python, "-c", EFFECTIVE_SCRIPT, ttf.absolutePath) + arguments, 120_000L)
            if (!outcome.succeeded) fail("reading GPOS failed: ${outcome.problem} ${outcome.output}")
            val values =
                outcome.output
                    .lines()
                    .last { it.startsWith("[") }
                    .trim('[', ']')
                    .split(',')
                    .map { it.trim().toInt() }
            return pairs.zip(values).toMap()
        }

        /** Every x-advance fontTools reads for ([first], [second]) from the compiled GPOS table: pair records and class records alike. */
        fun adjustments(
            python: String,
            first: String,
            second: String,
        ): List<Int> {
            val outcome = RealProcessRunner.run(listOf(python, "-c", GPOS_SCRIPT, ttf.absolutePath, first, second), 60_000L)
            if (!outcome.succeeded) fail("reading GPOS failed: ${outcome.problem} ${outcome.output}")
            return outcome.output
                .lines()
                .last {
                    it.startsWith("[")
                }.trim('[', ']')
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { it.toInt() }
        }
    }

    private fun build(kerning: UfoKerning): Built {
        python()
        val (files, ufoPath) = project(kerning)
        val result = runBlocking { SystemPythonFontmakeBackend().compile(CompileRequest(files, CompileSource.Ufo(ufoPath))) }
        if (result !is CompileResult.Success) fail("the build did not succeed: $result\n" + result.log.joinToString("\n") { it.message })
        val ttf = Files.createTempFile("typewright-kern-", ".ttf").toFile().apply { deleteOnExit() }
        ttf.writeBytes(result.binaries.single().bytes)
        return Built(result.log.map { it.message }, ttf)
    }

    private val groups =
        mapOf(
            "public.kern1.straight" to listOf("H", "n"),
            "public.kern2.round" to listOf("o", "O"),
        )

    /** The UFO's own kerning: n o as a pair exception (-37) and the straight letters before the round ones (-23). */
    private fun ufoKerning(features: String?) =
        UfoKerning(
            groups = groups,
            kerning = mapOf("n" to mapOf("o" to -37.0), "public.kern1.straight" to mapOf("public.kern2.round" to -23.0)),
            features = features,
        )

    @Test
    fun withNoFeatureFileTheKerningSetOnThePageReachesTheFont() {
        val built = build(ufoKerning(features = null))
        val p = python()
        assertTrue(-37 in built.adjustments(p, "n", "o"), "the pair exception n o is in GPOS")
        assertTrue(-23 in built.adjustments(p, "H", "O"), "and the group pair, as a class pair, reaches H O")
    }

    @Test
    fun aKernFeatureOfItsOwnWithNoMarkerReplacesTheKerningSetOnThePage() {
        val built = build(ufoKerning(features = "feature kern {\n    pos n o -12;\n} kern;\n"))
        val p = python()
        assertEquals(listOf(-12), built.adjustments(p, "n", "o").filter { it != 0 }, "only the hand-written pair is in the font")
        assertTrue(built.adjustments(p, "H", "O").none { it == -23 }, "the group kerning from the page is not")
        assertTrue(
            built.log.any { "manually written kerning features" in it && "Dropping the former" in it },
            "fontmake says so in its log, in its words, which this page therefore does not quote",
        )
    }

    @Test
    fun theMarkerInsideTheFeatureKeepsBothTheHandWrittenAndTheKerningFromThePage() {
        val built = build(ufoKerning(features = "feature kern {\n    pos n o -12;\n    # Automatic Code\n} kern;\n"))
        val p = python()
        assertTrue(-12 in built.adjustments(p, "n", "o"), "the hand-written pair is in the font")
        assertTrue(-23 in built.adjustments(p, "H", "O"), "and so is the group kerning from the page")
        assertTrue(built.log.none { "Dropping the former" in it })
    }

    // ---- the lookup order the Space page and the specimen read, against what fontmake builds ---------------------------

    private val letters = listOf("n", "o", "H", "O", "a", "e")

    /** Groups and pairs among [letters], whole numbers including zero (an exception that cancels a group value), from [seed]. */
    private fun randomKerning(
        seed: Int,
        duplicates: Boolean,
    ): UfoKerning {
        val random = Random(seed)
        val groups = linkedMapOf<String, List<String>>()
        for (side in KernSide.entries) {
            val buckets = Array(2) { mutableListOf<String>() }
            for (letter in letters) {
                if (random.nextInt(0, 3) < 2) buckets[random.nextInt(0, 2)] += letter
                // Once in a while a glyph is put in a second group of the same side, which the specification forbids.
                if (duplicates && random.nextInt(0, 3) == 0) buckets[random.nextInt(0, 2)].takeIf { letter !in it }?.add(letter)
            }
            buckets.forEachIndexed { i, members -> if (members.isNotEmpty()) groups["${side.prefix}g$i"] = members }
        }
        val firstKeys = letters + groups.keys.filter { it.startsWith(KernSide.FIRST.prefix) }
        val secondKeys = letters + groups.keys.filter { it.startsWith(KernSide.SECOND.prefix) }
        val kerning = linkedMapOf<String, MutableMap<String, Double>>()
        for (first in firstKeys) {
            for (second in secondKeys) {
                if (random.nextInt(0, 4) == 0) kerning.getOrPut(first) { linkedMapOf() }[second] = random.nextInt(-5, 3) * 10.0
            }
        }
        return UfoKerning(groups = groups, kerning = kerning)
    }

    private fun assertBuiltKerningMatchesTheTable(
        info: UfoKerning,
        label: String,
    ) {
        val built = build(info)
        val table = KerningTable(info)
        val pairs = letters.flatMap { a -> letters.map { b -> a to b } }
        val effective = built.effective(python(), pairs)
        for ((a, b) in pairs) {
            assertEquals(table.value(a, b).toInt(), effective.getValue(a to b), "$label: $a $b (table says ${table.resolve(a, b)})")
        }
    }

    @Test
    fun theValueTheTableGivesEveryPairIsTheValueFontmakeBuildsIntoTheFont() {
        for (seed in 0 until RANDOM_FONTS) assertBuiltKerningMatchesTheTable(randomKerning(seed, duplicates = false), "seed $seed")
    }

    @Test
    fun groupsThatShareAGlyphAreReadByTheTableAsFontmakeBuildsThem() {
        val info =
            UfoKerning(
                groups = mapOf("public.kern1.a" to listOf("n"), "public.kern1.b" to listOf("n", "H"), "public.kern2.r" to listOf("o")),
                kerning = mapOf("public.kern1.a" to mapOf("public.kern2.r" to -10.0), "public.kern1.b" to mapOf("public.kern2.r" to -20.0)),
            )
        // n is in a and b: fontmake keeps a for n and skips b whole, so H, which only b holds, is not kerned by b either (the built font says 0).
        assertEquals(-10.0, KerningTable(info).value("n", "o"), "the table keeps the first group")
        assertEquals(0.0, KerningTable(info).value("H", "o"), "and ignores the later group whole")
        assertBuiltKerningMatchesTheTable(info, "n in two groups")
        for (seed in 100 until 100 + RANDOM_FONTS) {
            assertBuiltKerningMatchesTheTable(
                randomKerning(seed, duplicates = true),
                "duplicates, seed $seed",
            )
        }
    }

    private companion object {
        const val RANDOM_FONTS = 3

        /** Prints, as `[a, b, ...]`, the kerning the compiled font gives each `first,second` pair named on the command line: the first pair-positioning subtable covering the first glyph that has a value for it. */
        val EFFECTIVE_SCRIPT =
            """
            import sys
            from fontTools.ttLib import TTFont
            font = TTFont(sys.argv[1])
            subtables = []
            if "GPOS" in font:
                for lookup in font["GPOS"].table.LookupList.Lookup:
                    for sub in lookup.SubTable:
                        if sub.LookupType == 9:
                            sub = sub.ExtSubTable
                        if sub.LookupType == 2:
                            subtables.append(sub)
            def xadvance(value):
                return getattr(value, "XAdvance", 0) if value else 0
            def effective(first, second):
                for sub in subtables:
                    glyphs = sub.Coverage.glyphs
                    if first not in glyphs:
                        continue
                    if sub.Format == 1:
                        for record in sub.PairSet[glyphs.index(first)].PairValueRecord:
                            if record.SecondGlyph == second:
                                return xadvance(record.Value1)
                    else:
                        one = sub.ClassDef1.classDefs.get(first, 0)
                        two = sub.ClassDef2.classDefs.get(second, 0)
                        return xadvance(sub.Class1Record[one].Class2Record[two].Value1)
                return 0
            print(str([effective(*pair.split(",")) for pair in sys.argv[2:]]))
            """.trimIndent()

        /** Prints the x-advance of every pair-positioning record fontTools finds for the pair, as `[a, b, ...]`; class records give 0 for classes with no value. */
        val GPOS_SCRIPT =
            """
            import sys
            from fontTools.ttLib import TTFont
            font = TTFont(sys.argv[1])
            first, second = sys.argv[2], sys.argv[3]
            found = []
            if "GPOS" in font:
                for lookup in font["GPOS"].table.LookupList.Lookup:
                    for sub in lookup.SubTable:
                        if sub.LookupType == 9:
                            sub = sub.ExtSubTable
                        if sub.LookupType != 2:
                            continue
                        glyphs = sub.Coverage.glyphs
                        if first not in glyphs:
                            continue
                        if sub.Format == 1:
                            for record in sub.PairSet[glyphs.index(first)].PairValueRecord:
                                if record.SecondGlyph == second:
                                    found.append(getattr(record.Value1, "XAdvance", 0) if record.Value1 else 0)
                        else:
                            one = sub.ClassDef1.classDefs.get(first, 0)
                            two = sub.ClassDef2.classDefs.get(second, 0)
                            value = sub.Class1Record[one].Class2Record[two].Value1
                            found.append(getattr(value, "XAdvance", 0) if value else 0)
            print(str(found))
            """.trimIndent()
    }
}
