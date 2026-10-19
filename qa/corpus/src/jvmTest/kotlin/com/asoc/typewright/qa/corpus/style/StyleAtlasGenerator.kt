// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus.style

import com.asoc.typewright.core.font.sfnt.readSfntFont
import java.io.File
import java.util.Locale

/**
 * Builds data/style-atlas-latin.json (step 2 of 2; step 1 is
 * data/scripts/fetch_style_atlas_fonts.py). It measures every fetched face with [measureStyle],
 * the same code the Brief runs on a project's own letters, and writes per-class and per-feeling
 * distributions plus each face's own measurement. Plain JSON text is written by hand so this
 * file has no dependency beyond the measuring code itself.
 */
internal object StyleAtlasGenerator {
    /** The numeric features, in the order the pack lists them. Keys are the pack's own. */
    val NUMERIC: List<Pair<String, (StyleMeasurement) -> Double?>> =
        listOf(
            "contrast" to { m -> m.contrastRatio },
            "stress" to { m -> m.stressAngleAbs },
            "bracket" to { m -> m.bracketScore },
            "aperture" to { m -> m.apertureOpenness },
            "roundness" to { m -> m.oRoundnessExponent },
            "xHeight" to { m -> m.xHeightToCapHeightRatio },
            "width" to { m -> m.widthClass },
            "stem" to { m -> m.stemToCapHeight },
            "crossbar" to { m -> m.crossbarHeight },
        )

    /** The categorical features: key to the value a face contributes, or null when unmeasured. */
    val CATEGORICAL: List<Pair<String, (StyleMeasurement) -> String?>> =
        listOf(
            "serif" to { m -> m.hasSerif?.let { if (it) "yes" else "no" } },
            "aStoreys" to { m ->
                m.aStoreys
                    .takeIf { it != Storeys.UNKNOWN }
                    ?.name
                    ?.lowercase()
            },
            "gStoreys" to { m ->
                m.gStoreys
                    .takeIf { it != Storeys.UNKNOWN }
                    ?.name
                    ?.lowercase()
            },
            "terminal" to { m ->
                m.terminalStyle
                    .takeIf { it != TerminalStyle.UNKNOWN }
                    ?.name
                    ?.lowercase()
            },
        )

    data class Row(
        val kind: String,
        val key: String,
        val family: String,
        val path: String,
        val score: Double?,
        val drawing: Double?,
    )

    /** Reads manifest.tsv: a `#source` line, then kind, key, family, path, score, drawing. */
    fun readManifest(file: File): Pair<String, List<Row>> {
        var source = ""
        val rows = mutableListOf<Row>()
        file.readLines().forEach { line ->
            if (line.isBlank()) return@forEach
            if (line.startsWith("#source\t")) {
                source = line.removePrefix("#source\t")
                return@forEach
            }
            if (line.startsWith("#")) return@forEach
            val c = line.split('\t')
            rows += Row(c[0], c[1], c[2], c[3], c.getOrNull(4)?.toDoubleOrNull(), c.getOrNull(5)?.toDoubleOrNull())
        }
        return source to rows
    }

    /** Measures every face once and returns the pack's JSON text, plus a log of faces that failed. */
    fun generate(fontsDir: File): Pair<String, List<String>> {
        val (source, rows) = readManifest(File(fontsDir, "manifest.tsv"))
        val paths = rows.filter { it.kind != "corpus" }.map { it.path }.distinct()
        // Measuring is pure and the files are independent, so they are measured in parallel;
        // everything below reads the results back in manifest order, so the pack is the same run
        // to run. Results are keyed by file: a bundled face and a class member can share a family
        // name but not a file (the class member is its Regular instance, the bundled one is the
        // file exactly as the app ships it).
        val results =
            paths
                .parallelStream()
                .map { path ->
                    path to runCatching { measureStyle(readSfntFont(File(fontsDir, path).readBytes())) }
                }.toList()
                .toMap()
        val failures = mutableListOf<String>()
        val byPath = linkedMapOf<String, StyleMeasurement>()
        paths.forEach { path ->
            results
                .getValue(path)
                .onSuccess { byPath[path] = it }
                .onFailure { failures += "$path: ${it::class.simpleName}: ${it.message}" }
        }
        val measured = linkedMapOf<String, StyleMeasurement>()
        rows.filter { it.kind == "class" || it.kind == "feeling" }.forEach { r ->
            byPath[r.path]?.let { measured.putIfAbsent(r.family, it) }
        }

        val classRows = rows.filter { it.kind == "class" }
        val feelingRows = rows.filter { it.kind == "feeling" }
        val bundledRows = rows.filter { it.kind == "bundled" }
        val corpusRows = rows.filter { it.kind == "corpus" }.map { it.key }.toSet()
        val json = StringBuilder()
        json.append("{\n")
        json.append(" \"source\": ").append(str(source)).append(",\n")
        json
            .append(
                " \"generator\": ",
            ).append(
                str("data/scripts/fetch_style_atlas_fonts.py + StyleAtlasGeneratorTest (qa/corpus jvmTest), measuring with measureStyle"),
            ).append(",\n")
        json.append(" \"version\": 1,\n")
        json.append(" \"numeric\": [").append(NUMERIC.joinToString(", ") { str(it.first) }).append("],\n")
        json.append(" \"categorical\": [").append(CATEGORICAL.joinToString(", ") { str(it.first) }).append("],\n")

        json.append(" \"classes\": {\n")
        val classKeys = classRows.map { it.key }.distinct()
        classKeys.forEachIndexed { i, key ->
            val members = classRows.filter { it.key == key }.mapNotNull { r -> measured[r.family]?.let { r.family to it } }
            json.append("  ").append(str(key)).append(": {")
            json.append("\"corpus\": ").append(key in corpusRows).append(", ")
            json.append("\"faces\": [").append(members.joinToString(", ") { str(it.first) }).append("], ")
            appendStats(json, members.map { it.second })
            json.append("}").append(if (i < classKeys.size - 1) ",\n" else "\n")
        }
        json.append(" },\n")

        json.append(" \"feelings\": {\n")
        val feelingKeys = feelingRows.map { it.key }.distinct()
        feelingKeys.forEachIndexed { i, key ->
            val members = feelingRows.filter { it.key == key }.mapNotNull { r -> measured[r.family]?.let { Triple(r.family, r.score, it) } }
            json.append("  ").append(str(key)).append(": {")
            json
                .append("\"faces\": [")
                .append(
                    members.joinToString(", ") {
                        "{\"family\": " + str(it.first) + ", \"score\": " +
                            num(it.second) +
                            "}"
                    },
                ).append("], ")
            appendStats(json, members.map { it.third })
            json.append("}").append(if (i < feelingKeys.size - 1) ",\n" else "\n")
        }
        json.append(" },\n")

        json.append(" \"faces\": {\n")
        val faceRows = rows.filter { it.kind == "class" || it.kind == "feeling" }.distinctBy { it.family }.filter { it.family in measured }
        faceRows.forEachIndexed { i, r ->
            val m = measured.getValue(r.family)
            val memberOf = classRows.filter { it.family == r.family }.map { it.key }.distinct()
            json.append("  ").append(str(r.family)).append(": {")
            json.append("\"classes\": [").append(memberOf.joinToString(", ") { str(it) }).append("], ")
            json.append("\"drawing\": ").append(num(r.drawing)).append(", ")
            appendFeatures(json, m)
            json.append("}").append(if (i < faceRows.size - 1) ",\n" else "\n")
        }
        json.append(" },\n")

        json.append(" \"bundled\": {\n")
        val bundled = bundledRows.filter { it.path in byPath }
        bundled.forEachIndexed { i, r ->
            json.append("  ").append(str(r.key)).append(": {")
            json.append("\"family\": ").append(str(r.family)).append(", ")
            appendFeatures(json, byPath.getValue(r.path))
            json.append("}").append(if (i < bundled.size - 1) ",\n" else "\n")
        }
        json.append(" }\n")
        json.append("}\n")
        return json.toString() to failures
    }

    private fun appendFeatures(
        json: StringBuilder,
        m: StyleMeasurement,
    ) {
        json.append(NUMERIC.joinToString(", ") { (k, get) -> str(k) + ": " + num(get(m)) })
        json.append(", ")
        json.append(CATEGORICAL.joinToString(", ") { (k, get) -> str(k) + ": " + (get(m)?.let { str(it) } ?: "null") })
    }

    private fun appendStats(
        json: StringBuilder,
        members: List<StyleMeasurement>,
    ) {
        json.append("\"n\": ").append(members.size).append(", ")
        json.append("\"dist\": {")
        json.append(
            NUMERIC
                .mapNotNull { (k, get) ->
                    val values = members.mapNotNull(get).filter { it.isFinite() }
                    quartiles(values)?.let { q -> str(k) + ": " + q }
                }.joinToString(", "),
        )
        json.append("}, \"share\": {")
        json.append(
            CATEGORICAL
                .mapNotNull { (k, get) ->
                    val values = members.mapNotNull(get)
                    if (values.isEmpty()) {
                        null
                    } else {
                        str(k) + ": {" +
                            values
                                .groupingBy { it }
                                .eachCount()
                                .toSortedMap()
                                .entries
                                .joinToString(", ") { str(it.key) + ": " + it.value } +
                            "}"
                    }
                }.joinToString(", "),
        )
        json.append("}")
    }

    /** min, q1, median, q3, max and n, interpolated as data/scripts/build_node_economy_corpus.py does. */
    fun quartiles(values: List<Double>): String? {
        if (values.isEmpty()) return null
        val v = values.sorted()
        val n = v.size

        fun q(p: Double): Double {
            val k = (n - 1) * p
            val f = k.toInt()
            val c = minOf(f + 1, n - 1)
            return v[f] + (v[c] - v[f]) * (k - f)
        }
        return "{\"min\": ${num(
            v.first(),
        )}, \"q1\": ${num(q(0.25))}, \"med\": ${num(q(0.5))}, \"q3\": ${num(q(0.75))}, \"max\": ${num(v.last())}, \"n\": $n}"
    }

    private fun num(value: Double?): String =
        if (value == null ||
            !value.isFinite()
        ) {
            "null"
        } else {
            String
                .format(Locale.ROOT, "%.4f", value)
                .trimEnd('0')
                .trimEnd('.')
                .ifEmpty { "0" }
        }

    private fun str(value: String): String =
        buildString {
            append('"')
            value.forEach { ch ->
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\t' -> append("\\t")
                    else -> if (ch < ' ') append(String.format(Locale.ROOT, "\\u%04x", ch.code)) else append(ch)
                }
            }
            append('"')
        }
}
