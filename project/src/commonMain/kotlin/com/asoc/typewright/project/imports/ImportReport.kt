// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.imports

import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.CurveFormat

/** Where an import came from. */
enum class ImportFormat {
    /** A single `.ttf` or `.otf` font file. */
    FONT_FILE,

    /** A UFO 3 folder. */
    UFO,
}

/** How a font's curves are held in the new project. */
enum class OutlineKind(
    val words: String,
) {
    /** TrueType curves, kept exactly as drawn: quadratic segments are not turned into cubic ones. */
    QUADRATIC("Quadratic (TrueType) curves, kept exactly as drawn"),

    /** Cubic curves, as a UFO holds them. */
    CUBIC("Cubic curves, kept exactly as drawn"),

    /** A UFO that holds both. */
    MIXED("Both quadratic and cubic curves, kept exactly as drawn"),
}

/** The vertical metrics an import read, from `head`, `hhea` and `OS/2` (or `fontinfo.plist`); a value the source does not hold is null. */
data class ImportMetrics(
    val unitsPerEm: Int?,
    val ascender: Int?,
    val descender: Int?,
    val lineGap: Int?,
    val typoAscender: Int?,
    val typoDescender: Int?,
    val typoLineGap: Int?,
    val winAscent: Int?,
    val winDescent: Int?,
    val xHeight: Int?,
    val capHeight: Int?,
)

/** How many characters of one script the font maps; [code] is the ISO 15924 code the project records. */
data class ScriptCoverage(
    val code: String,
    val name: String,
    val characters: Int,
)

/**
 * What an import found and what it did to it (not a verdict: that is [ImportAudit]). [glyphCount]
 * counts glyphs, [mappedCharacters] counts distinct code points the font maps to a glyph (its cmap
 * coverage), [scripts] are the scripts with at least one mapped character, and [notes] are plain
 * sentences about what the project does not carry over or had to change.
 */
data class ImportReport(
    val format: ImportFormat,
    val glyphCount: Int,
    val metrics: ImportMetrics,
    val mappedCharacters: Int,
    val scripts: List<ScriptCoverage>,
    val outlines: OutlineKind,
    val notes: List<String>,
) {
    /** The ISO 15924 codes `typewright.json` records, in this order. */
    val scriptCodes: List<String> get() = scripts.map { it.code }
}

/** Builds the report for [project], which came from [format]; [notes] are appended as given. */
internal fun reportFor(
    format: ImportFormat,
    project: UfoProject,
    notes: List<String>,
): ImportReport {
    val info = project.fontInfo
    val metrics =
        ImportMetrics(
            unitsPerEm = info.unitsPerEm,
            ascender = info.openTypeHheaAscender ?: info.ascender,
            descender = info.openTypeHheaDescender ?: info.descender,
            lineGap = info.openTypeHheaLineGap,
            typoAscender = info.openTypeOS2TypoAscender,
            typoDescender = info.openTypeOS2TypoDescender,
            typoLineGap = info.openTypeOS2TypoLineGap,
            winAscent = info.openTypeOS2WinAscent,
            winDescent = info.openTypeOS2WinDescent,
            xHeight = info.xHeight,
            capHeight = info.capHeight,
        )
    val codePoints = project.glyphs.flatMap { it.unicodes }.toSet()
    return ImportReport(
        format = format,
        glyphCount = project.glyphs.size,
        metrics = metrics,
        mappedCharacters = codePoints.size,
        scripts = scriptCoverage(codePoints),
        outlines = outlineKind(project),
        notes = notes,
    )
}

/** The scripts [codePoints] reach into, Latin first, then kana, Devanagari and Arabic: the order `typewright.json` lists scripts in. */
fun scriptCoverage(codePoints: Set<Int>): List<ScriptCoverage> =
    SCRIPT_RANGES.mapNotNull { (code, name, ranges) ->
        val count = codePoints.count { cp -> ranges.any { cp in it } }
        if (count > 0) ScriptCoverage(code, name, count) else null
    }

private data class ScriptRanges(
    val code: String,
    val name: String,
    val ranges: List<IntRange>,
)

private val SCRIPT_RANGES =
    listOf(
        ScriptRanges(
            "Latn",
            "Latin",
            listOf(
                0x41..0x5A,
                0x61..0x7A,
                0xAA..0xAA,
                0xBA..0xBA,
                0xC0..0xD6,
                0xD8..0xF6,
                0xF8..0x2AF,
                0x1E00..0x1EFF,
                0x2C60..0x2C7F,
                0xA720..0xA7FF,
            ),
        ),
        ScriptRanges(
            "Hrkt",
            "Kana",
            listOf(0x3041..0x3096, 0x309D..0x309F, 0x30A1..0x30FA, 0x30FC..0x30FF, 0x31F0..0x31FF, 0xFF66..0xFF9F),
        ),
        ScriptRanges("Deva", "Devanagari", listOf(0x0900..0x097F, 0xA8E0..0xA8FF)),
        ScriptRanges("Arab", "Arabic", listOf(0x0600..0x06FF, 0x0750..0x077F, 0x08A0..0x08FF, 0xFB50..0xFDFF, 0xFE70..0xFEFF)),
    )

private fun outlineKind(project: UfoProject): OutlineKind {
    val formats =
        project.glyphs
            .flatMap { it.contours }
            .filter { contour -> contour.points.any { !it.onCurve } }
            .map { it.format }
            .toSet()
    return when {
        formats.size > 1 -> OutlineKind.MIXED
        formats.singleOrNull() == CurveFormat.CUBIC -> OutlineKind.CUBIC
        formats.singleOrNull() == CurveFormat.QUADRATIC -> OutlineKind.QUADRATIC
        project.lib.lineContourFormat == CurveFormat.QUADRATIC -> OutlineKind.QUADRATIC
        else -> OutlineKind.CUBIC
    }
}
