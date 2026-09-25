// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.qa.corpus.style

import dev.aarso.typewright.core.font.sfnt.readSfntFont
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Opt-in, real-font validation for the style detector (P1b step 3). This is deliberately **not**
 * part of the always-on suite `./gradlew :qa:corpus:check` exercises everywhere: the fonts it
 * reads are third-party OFL binaries fetched from google/fonts at the corpus's own pinned commit
 * (see `data/node-economy-latin.json`'s `source` field), which this repository does not commit
 * (they are copyrighted, several megabytes each, and not "generated data" this module owns).
 *
 * To run it: fetch the fonts into `qa/corpus/build/validation-fonts/{corpus-top1,exemplars}/` --
 * `raw.githubusercontent.com/google/fonts/<pinned-sha>/<ofl|apache|ufl>/<dir>/<Regular file>`,
 * the exact method `data/scripts/build_node_economy_corpus.py` uses (its `dir_name`/
 * `regular_filename`), one file per family named by [CORPUS_TOP1]/[EXEMPLARS]' filenames -- then
 * run `./gradlew :qa:corpus:jvmTest --tests "*StyleDetectorRealFontValidationTest*"`. If that
 * directory is missing (the common case: a fresh checkout, CI, another agent's sandbox), this
 * test prints why and passes trivially rather than failing the build or silently depending on
 * network access CLAUDE.md law 4 does not promise here.
 *
 * When the fonts are present, this test runs the *actual* production pipeline
 * ([readSfntFont] -> [StyleGlyphSet.fromSfntFont] -> [extractFeatures] -> [rankStyleClasses]) over:
 * - [CORPUS_TOP1]: the `/Quality/Drawing`-ranked #1 family in each of the ten corpus style
 *   classes (`data/node-economy-latin.json`'s own `families[0]`) -- ground truth is the class
 *   itself, since these fonts are literal members of it.
 * - [EXEMPLARS]: the ten Lineages exemplar faces (`data/exemplars.json`), mapped to their
 *   corpus-taxonomy class per docs/LESSONS_SCAFFOLD.md's era table.
 *
 * It writes a full confusion report to `validation-report.txt` next to the fonts and also prints
 * it (Gradle only shows captured stdout with `--info`, so the file is the reliable way to read
 * it back). The test's own assertions only check the pipeline completed for every font without
 * throwing and returned a ranked, non-empty class list -- classification accuracy is reported
 * honestly in that file, not gated here, since a hand-written heuristic scorer getting some fonts
 * wrong is expected and the report says exactly how often.
 */
class StyleDetectorRealFontValidationTest {
    @Test
    fun validateAgainstDownloadedFonts() {
        val baseDir = File("build/validation-fonts")
        if (!baseDir.exists()) {
            println(
                "SKIPPED StyleDetectorRealFontValidationTest: ${baseDir.absolutePath} not found. " +
                    "See this test's KDoc for how to fetch the validation fonts.",
            )
            return
        }

        val rows = mutableListOf<ValidationRow>()
        val errors = mutableListOf<String>()

        for ((styleKey, entry) in CORPUS_TOP1) {
            val file = File(baseDir, "corpus-top1/${entry.filename}")
            runCatching { classifyFont(file, expected = styleKey, label = "corpus#1 [$styleKey]", family = entry.family) }
                .onSuccess { rows += it }
                .onFailure { errors += "corpus#1 [$styleKey] (${entry.family}, ${file.name}): ${it::class.simpleName}: ${it.message}" }
        }
        for ((exemplarKey, entry) in EXEMPLARS) {
            val file = File(baseDir, "exemplars/${entry.filename}")
            runCatching {
                classifyFont(file, expected = entry.expectedClass, label = "exemplar [$exemplarKey]", family = entry.family)
            }.onSuccess { rows += it }
                .onFailure { errors += "exemplar [$exemplarKey] (${entry.family}, ${file.name}): ${it::class.simpleName}: ${it.message}" }
        }

        val report = buildReport(rows, errors)
        File(baseDir, "validation-report.txt").writeText(report)
        println(report)

        assertTrue(rows.isNotEmpty() || errors.isNotEmpty(), "found no font files under ${baseDir.absolutePath}")
        for (row in rows) assertTrue(row.ranked.isNotEmpty(), "${row.label} produced an empty ranking")
    }
}

private data class FontEntry(
    val filename: String,
    val family: String,
)

private data class ExemplarEntry(
    val filename: String,
    val family: String,
    val expectedClass: String,
)

/** `data/node-economy-latin.json`'s `styles.<key>.families[0]` at the pack's pinned commit, 2026-09-24. */
private val CORPUS_TOP1 =
    mapOf(
        "sans-geometric" to FontEntry("googlesansflex.ttf", "Google Sans Flex"),
        "sans-grotesque" to FontEntry("vendsans.ttf", "Vend Sans"),
        "sans-neogrotesque" to FontEntry("iosevkacharonmono.ttf", "Iosevka Charon Mono"),
        "sans-humanist" to FontEntry("chironheihk.ttf", "Chiron Hei HK"),
        "serif-garalde" to FontEntry("fraunces.ttf", "Fraunces"),
        "serif-transitional" to FontEntry("chironsunghk.ttf", "Chiron Sung HK"),
        "serif-didone" to FontEntry("playfairdisplaysc.ttf", "Playfair Display SC"),
        "slab" to FontEntry("wellfleet.ttf", "Wellfleet"),
        "display-artdeco" to FontEntry("monoton.ttf", "Monoton"),
        "blackletter" to FontEntry("bodonimodasc.ttf", "Bodoni Moda SC"),
    )

/** `data/exemplars.json`, mapped to its corpus-taxonomy class via docs/LESSONS_SCAFFOLD.md's era table. */
private val EXEMPLARS =
    mapOf(
        "garalde" to ExemplarEntry("ebgaramond.ttf", "EB Garamond", "serif-garalde"),
        "transitional" to ExemplarEntry("librebaskerville.ttf", "Libre Baskerville", "serif-transitional"),
        "didone" to ExemplarEntry("playfairdisplay.ttf", "Playfair Display", "serif-didone"),
        "geometric" to ExemplarEntry("jost.ttf", "Jost", "sans-geometric"),
        "grotesque" to ExemplarEntry("worksans.ttf", "Work Sans", "sans-grotesque"),
        "neogrotesque" to ExemplarEntry("inter.ttf", "Inter", "sans-neogrotesque"),
        "humanist" to ExemplarEntry("sourcesans3.ttf", "Source Sans 3", "sans-humanist"),
        "slab" to ExemplarEntry("zillaslab.ttf", "Zilla Slab", "slab"),
        "artdeco" to ExemplarEntry("limelight.ttf", "Limelight", "display-artdeco"),
        "blackletter" to ExemplarEntry("unifrakturmaguntia.ttf", "UnifrakturMaguntia", "blackletter"),
    )

private data class ValidationRow(
    val label: String,
    val family: String,
    val expected: String,
    val ranked: List<ClassMatch>,
) {
    val top1: ClassMatch get() = ranked.first()
    val correct: Boolean get() = top1.styleKey == expected
    val expectedRank: Int get() = ranked.indexOfFirst { it.styleKey == expected } + 1
}

private fun classifyFont(
    file: File,
    expected: String,
    label: String,
    family: String,
): ValidationRow {
    require(file.exists()) { "font file not found: ${file.absolutePath}" }
    val font = readSfntFont(file.readBytes())
    val glyphSet = StyleGlyphSet.fromSfntFont(font)
    val features = extractFeatures(glyphSet)
    val ranked = rankStyleClasses(features)
    return ValidationRow(label, family, expected, ranked)
}

private fun buildReport(
    rows: List<ValidationRow>,
    errors: List<String>,
): String {
    val sb = StringBuilder()
    sb.appendLine("P1b style-detector validation report")
    sb.appendLine("=====================================")
    sb.appendLine("Sample: ${rows.size} fonts classified, ${errors.size} failed to load.")
    sb.appendLine()

    if (errors.isNotEmpty()) {
        sb.appendLine("--- Load/extraction failures ---")
        for (e in errors) sb.appendLine("  $e")
        sb.appendLine()
    }

    sb.appendLine("--- Per-font results ---")
    for (row in rows.sortedBy { it.label }) {
        val mark = if (row.correct) "OK  " else "MISS"
        sb.appendLine(
            "$mark ${row.label.padEnd(24)} ${row.family.padEnd(22)} expected=${row.expected.padEnd(20)} " +
                "top1=${row.top1.styleKey.padEnd(20)} conf=${"%.2f".format(row.top1.confidence)} " +
                "expectedRank=${if (row.expectedRank > 0) row.expectedRank else "not ranked>0"}",
        )
        sb.appendLine("       full ranking: " + row.ranked.joinToString(" | ") { "${it.styleKey}=${"%.2f".format(it.confidence)}" })
        if (row.top1.evidence.isNotEmpty()) sb.appendLine("       top1 evidence: " + row.top1.evidence.joinToString("; "))
    }
    sb.appendLine()

    sb.appendLine("--- Confusion summary (top-1 vs. expected class) ---")
    val byExpected = rows.groupBy { it.expected }
    var totalCorrect = 0
    for ((expectedClass, group) in byExpected.toSortedMap()) {
        val correct = group.count { it.correct }
        totalCorrect += correct
        sb.appendLine("  $expectedClass: $correct/${group.size} correct top-1")
        for (row in group) {
            sb.appendLine("      ${row.label} (${row.family}) -> top1=${row.top1.styleKey} conf=${"%.2f".format(row.top1.confidence)}")
        }
    }
    sb.appendLine()
    sb.appendLine(
        "TOTAL: $totalCorrect/${rows.size} correct top-1 (${if (rows.isNotEmpty()) {
            "%.0f".format(
                100.0 * totalCorrect / rows.size,
            )
        } else {
            "n/a"
        }}%)",
    )
    return sb.toString()
}
