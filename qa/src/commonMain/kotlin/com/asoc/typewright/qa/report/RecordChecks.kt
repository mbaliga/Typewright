// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.report

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.outlineBounds
import com.asoc.typewright.project.imports.reservedFontNames
import kotlin.math.roundToInt

private const val REVIEWER_QUOTE_SOURCE = "One source, a Regular UFO with TT curves."

// OpenType's own range for units per em (the head table).
private const val MIN_UNITS_PER_EM = 16
private const val MAX_UNITS_PER_EM = 16_384

// Google Fonts' vertical-metrics rule: ascender + |descender| + line gap between 120% and 200% of the em, a warning above 150%.
private const val SPAN_FAIL_BELOW = 120
private const val SPAN_FAIL_ABOVE = 200
private const val SPAN_WARN_ABOVE = 150

/** Google Fonts' limit on a family name and its style together. */
private const val MAX_FAMILY_AND_STYLE = 32

/** fsSelection bit 7, USE_TYPO_METRICS, as a UFO lists it in `openTypeOS2Selection`. */
private const val USE_TYPO_METRICS_BIT = 7L

// ---- vertical metrics ------------------------------------------------------------------------------

internal fun metricsItem(inputs: CheckInputs): CheckItem {
    val info = inputs.project.fontInfo
    val upm = info.unitsPerEm
    val basisNote =
        "The 120% to 200% span, typo equal to hhea, no line gap and fsSelection bit 7 are Google Fonts' published " +
            "vertical-metrics rules. Which of them stops a release is our heuristic: layer one, on the compiled font, " +
            "gives Fontbakery's own verdicts."
    val problems = mutableListOf<Pair<Severity, String>>()
    val raw = mutableListOf<String>()

    if (upm == null) {
        problems += Severity.FAIL to "The font does not say how many units make up an em, so no metric can be read."
    } else if (upm < MIN_UNITS_PER_EM || upm > MAX_UNITS_PER_EM) {
        problems += Severity.FAIL to "$upm units per em is outside the $MIN_UNITS_PER_EM to $MAX_UNITS_PER_EM that OpenType allows."
    }
    raw += "units per em: ${upm ?: "not set"}"

    val hheaAscender = info.openTypeHheaAscender ?: info.ascender
    val hheaDescender = info.openTypeHheaDescender ?: info.descender
    val hheaGap = info.openTypeHheaLineGap ?: 0
    raw += "hhea: ascender ${hheaAscender ?: "not set"}, descender ${hheaDescender ?: "not set"}, line gap $hheaGap"
    val typoWords = listOf(info.openTypeOS2TypoAscender, info.openTypeOS2TypoDescender, info.openTypeOS2TypoLineGap).map { it ?: "not set" }
    raw += "typo: ascender ${typoWords[0]}, descender ${typoWords[1]}, line gap ${typoWords[2]}"
    raw += "win: ascent ${info.openTypeOS2WinAscent ?: "not set"}, descent ${info.openTypeOS2WinDescent ?: "not set"}"

    var spanText: String? = null
    var spanPercent: Int? = null
    if (upm != null && upm > 0) {
        if (hheaAscender == null || hheaDescender == null) {
            problems += Severity.WARN to "Ascender and descender are not set, so the compiler will choose the line spacing."
        } else {
            val span = hheaAscender - hheaDescender + hheaGap
            val percent = (span * 100.0 / upm).roundToInt()
            spanPercent = percent
            spanText =
                "Ascender $hheaAscender, descender $hheaDescender and line gap $hheaGap make ${span.withThousands()} units, " +
                "$percent% of the ${upm.withThousands()}-unit em"
            raw += "line height: $span units, $percent% of the em"
            when {
                percent < SPAN_FAIL_BELOW || percent > SPAN_FAIL_ABOVE -> {
                    problems += Severity.FAIL to "$spanText; Google Fonts asks for between $SPAN_FAIL_BELOW% and $SPAN_FAIL_ABOVE%."
                }

                percent > SPAN_WARN_ABOVE -> {
                    problems += Severity.WARN to "$spanText; above $SPAN_WARN_ABOVE% lines start to look loose to Google Fonts."
                }
            }
        }
    }

    val typoAscender = info.openTypeOS2TypoAscender
    val typoDescender = info.openTypeOS2TypoDescender
    val typoGap = info.openTypeOS2TypoLineGap
    val typoDiffers =
        (typoAscender != null && hheaAscender != null && typoAscender != hheaAscender) ||
            (typoDescender != null && hheaDescender != null && typoDescender != hheaDescender) ||
            (typoGap != null && typoGap != hheaGap)
    if (typoDiffers) {
        problems += Severity.FAIL to
            "The typographic metrics (typo) and the hhea metrics differ; Google Fonts wants them equal so every program " +
            "lays the lines out alike."
    }
    if (hheaGap != 0 || (typoGap != null && typoGap != 0)) {
        problems += Severity.WARN to "The line gap is not 0 (hhea $hheaGap, typo ${typoGap ?: "not set"}); Google Fonts wants both at 0."
    }

    val selection = info.other.firstOrNull { it.first == "openTypeOS2Selection" }?.second as? PlistValue.PArray
    val bit7 = selection?.items?.any { (it as? PlistValue.PInteger)?.value == USE_TYPO_METRICS_BIT } == true
    raw += "fsSelection bit 7 (use typo metrics): ${if (bit7) "set" else "not set"}"
    if (!bit7) {
        problems += Severity.WARN to
            "fsSelection bit 7 (use typo metrics) is not set, so Windows may read different line spacing than other systems."
    }

    val extremes = inputs.project.glyphs.mapNotNull { it.outlineBounds() }
    val yMax = extremes.maxOfOrNull { it.yMax }
    val yMin = extremes.minOfOrNull { it.yMin }
    val winAscent = info.openTypeOS2WinAscent
    val winDescent = info.openTypeOS2WinDescent
    if (yMax != null && yMin != null) {
        raw += "tallest ink: $yMax, lowest ink: $yMin"
        if ((winAscent != null && winAscent < yMax) || (winDescent != null && winDescent < -yMin)) {
            problems += Severity.FAIL to
                "The Windows ascent and descent (${winAscent ?: "not set"} and ${winDescent ?: "not set"}) " +
                "cut off ink that reaches $yMax and $yMin."
        }
    }

    val worst = problems.minByOrNull { it.first.ordinal }
    val severity = worst?.first ?: Severity.PASS
    val summary =
        when {
            worst != null -> problems.filter { it.first == severity }.joinToString(" ") { it.second }
            spanText != null -> "$spanText, inside Google Fonts' range; typo and hhea agree and the line gap is 0."
            else -> "The vertical metrics follow Google Fonts' rules."
        }
    return CheckItem(
        id = ReviewOrder.VERTICAL_METRICS,
        group = CheckGroup.METRICS,
        title = "Vertical metrics",
        detail = spanPercent?.let { "$it% of the em" },
        severity = severity,
        summary = summary,
        reviewerQuote = null,
        action = CheckAction("Set the metrics in Font info", CheckRoute.OpenFontInfo(FontInfoTarget.METRICS)),
        basis = Basis.PUBLISHED,
        basisNote = basisNote,
        raw = raw.joinToString("\n"),
    )
}

// ---- provenance --------------------------------------------------------------------------------------

internal fun provenanceItem(inputs: CheckInputs): CheckItem {
    val provenance = inputs.provenance
    val quadratic = inputs.project.glyphs.count { glyph -> glyph.contours.any { it.format == CurveFormat.QUADRATIC } }
    val basisNote =
        "Whether a source is the origin of the binary is a property of the workflow, not of the outlines; no tool checks it. " +
            "This reads what the project records about how it began and which glyphs the person has approved since: our heuristic."
    val raw =
        listOf(
            "began as an imported compiled font: ${provenance?.fromCompiledFont ?: "not recorded"}",
            "outline glyphs still as imported: ${provenance?.untouchedGlyphs ?: "not recorded"} " +
                "of ${provenance?.outlineGlyphs ?: "not recorded"}",
            "glyphs with TrueType-style quadratic curves: $quadratic",
        ).joinToString("\n")
    val file = provenance?.sourceFileName?.let { "$it was opened as this project, so " } ?: "This project began as a compiled font, so "
    val importedFossil = provenance != null && provenance.fromCompiledFont && provenance.untouchedGlyphs > 0
    val item = { severity: Severity, summary: String, detail: String? ->
        CheckItem(
            id = ReviewOrder.SOURCE_FOSSIL,
            group = CheckGroup.PROVENANCE,
            title = "Source is a fossil",
            detail = detail,
            severity = severity,
            summary = summary,
            reviewerQuote = REVIEWER_QUOTE_SOURCE,
            action = CheckAction("Rebuild the source from your drawings", CheckRoute.OpenCapture).takeIf { severity != Severity.PASS },
            basis = Basis.HEURISTIC,
            basisNote = basisNote,
            raw = raw,
        )
    }
    return when {
        importedFossil && provenance!!.untouchedGlyphs >= provenance.outlineGlyphs -> {
            item(
                Severity.FAIL,
                "${file}its outlines are the binary's, not drawn in this source. A source is the file you drew in; " +
                    "a reviewer who rebuilds from this " +
                    "UFO gets the compiled font's curves back.",
                "none of ${provenance.outlineGlyphs.withThousands()} glyphs redrawn",
            )
        }

        importedFossil -> {
            item(
                Severity.WARN,
                "${file}${provenance!!.untouchedGlyphs.withThousands()} of its " +
                    "${provenance.outlineGlyphs.withThousands()} glyphs are still the " +
                    "binary's outlines. A source is the file you drew in.",
                "${provenance.untouchedGlyphs.withThousands()} of ${provenance.outlineGlyphs.withThousands()} glyphs as imported",
            )
        }

        provenance == null && quadratic > 0 -> {
            item(
                Severity.WARN,
                "${plural(quadratic, "glyph")} carry TrueType-style quadratic curves, which is what a source extracted " +
                    "from a compiled font " +
                    "has. Typewright draws in cubic curves, so these outlines did not start here.",
                "TrueType curves in $quadratic glyphs",
            )
        }

        else -> {
            item(
                Severity.PASS,
                "Nothing in this project says its outlines came from a compiled font that has not been redrawn: the source is the origin.",
                null,
            )
        }
    }
}

// ---- names -------------------------------------------------------------------------------------------

internal fun namesItem(inputs: CheckInputs): CheckItem {
    val info = inputs.project.fontInfo
    val family = info.familyName?.trim().orEmpty()
    val style = info.styleName?.trim().orEmpty()
    val problems = mutableListOf<Pair<Severity, String>>()
    val raw = mutableListOf("family name: ${info.familyName ?: "not set"}", "style name: ${info.styleName ?: "not set"}")

    if (family.isEmpty()) problems += Severity.FAIL to "The font has no family name."
    if (style.isEmpty()) problems += Severity.FAIL to "The font has no style name."
    if (family.isNotEmpty() && style.isNotEmpty() && family.length + style.length + 1 > MAX_FAMILY_AND_STYLE) {
        problems += Severity.FAIL to
            "\"$family $style\" is ${family.length + style.length + 1} characters; Google Fonts allows " +
            "$MAX_FAMILY_AND_STYLE for a family and its style together."
    }
    val oddities = familyNameOddities(family)
    if (oddities.isNotEmpty()) {
        problems += Severity.WARN to "The family name \"$family\" has ${nameList(oddities)}, which Google Fonts asks family names to avoid."
    }

    val reserved = reservedFontNames(info.copyright, info.openTypeNameLicense, info.trademark)
    val keeps = reserved.filter { family.contains(it, ignoreCase = true) }
    raw += "reserved font names declared: ${if (reserved.isEmpty()) "none" else reserved.joinToString(", ")}"
    if (keeps.isNotEmpty()) {
        problems += Severity.FAIL to
            "The family name \"$family\" still uses ${nameList(keeps.map { "\"$it\"" })}, which the licence reserves. " +
            "A modified version has to be renamed."
    }

    val taken = knownFamilyNames(inputs)
    val clash = if (family.isNotEmpty()) taken[family.lowercase()] else null
    raw += "family names compared: ${taken.size.withThousands()} (the Google Fonts faces in qa/corpus, and the project's other masters)"
    if (clash != null) {
        problems += Severity.FAIL to
            "\"$family\" is already the name of $clash, so the two fonts would collide in a name table and on a device."
    }

    val worst = problems.minByOrNull { it.first.ordinal }
    val severity = worst?.first ?: Severity.PASS
    val summary =
        if (worst == null) {
            "\"$family $style\" is present, within $MAX_FAMILY_AND_STYLE characters, keeps no reserved name, " +
                "and clashes with none of the ${taken.size.withThousands()} family names compared in our corpus. " +
                "That is our heuristic: not every font in the world is in it."
        } else {
            problems.filter { it.first == severity }.joinToString(" ") { it.second }
        }
    return CheckItem(
        id = ReviewOrder.NAMES,
        group = CheckGroup.NAMES,
        title = "Family and style names",
        detail = family.takeIf { it.isNotEmpty() }?.let { if (style.isNotEmpty()) "$it $style" else it },
        severity = severity,
        summary = summary,
        reviewerQuote = null,
        action =
            CheckAction(
                "Set the names in Font info",
                CheckRoute.OpenFontInfo(FontInfoTarget.NAMES),
            ).takeIf { severity != Severity.PASS },
        basis = Basis.PUBLISHED,
        basisNote =
            "The 32-character limit and the naming habits to avoid are in Google Fonts' requirements. Uniqueness is checked against the " +
                "family names in qa/corpus, not against every font in the world.",
        raw = raw.joinToString("\n"),
    )
}

/** What in a family name Google Fonts asks to avoid: a hyphen, an accent, a name in capitals, or camelCase run together. */
internal fun familyNameOddities(family: String): List<String> =
    buildList {
        if (family.contains('-')) add("a hyphen")
        if (family.any { it.code > 127 }) add("an accented or non-Latin letter")
        val letters = family.filter { it.isLetter() }
        if (letters.length > 3 && letters.all { it.isUpperCase() }) add("all capitals")
        val camel = family.split(' ').any { word -> word.zipWithNext().any { (a, b) -> a.isLowerCase() && b.isUpperCase() } }
        if (camel) add("camelCase")
    }

/**
 * How many family names the Names check compared the project's name with: the Google Fonts families in `qa/corpus` and the project's other
 * masters, each name once. A clash is only ever found among these, so a page that says names are unique says this number with it.
 */
fun familyNamesCompared(inputs: CheckInputs): Int = knownFamilyNames(inputs).size

/** Lower-cased family name to a plain description of what holds it, from the corpus faces and the other masters. */
private fun knownFamilyNames(inputs: CheckInputs): Map<String, String> {
    val taken = LinkedHashMap<String, String>()
    val corpus = inputs.corpus
    if (corpus != null) {
        for (key in corpus.styleKeys.sorted()) {
            for (face in corpus.families(key)) taken.getOrPut(face.family.lowercase()) { "a Google Fonts family (${face.family})" }
        }
    }
    for (master in inputs.otherMasters) {
        val name = master.familyName?.trim().orEmpty()
        if (name.isNotEmpty()) taken[name.lowercase()] = "another master in this project"
    }
    return taken
}

// ---- licence ------------------------------------------------------------------------------------------

private val COPYRIGHT_PATTERN = Regex("""^Copyright\s+(\(c\)\s+)?\d{4}\b""", RegexOption.IGNORE_CASE)

internal fun licenceItem(inputs: CheckInputs): CheckItem {
    val info = inputs.project.fontInfo
    val copyright = info.copyright?.trim().orEmpty()
    val licence = info.openTypeNameLicense.orEmpty()
    val licenceUrl = info.openTypeNameLicenseURL.orEmpty()
    val problems = mutableListOf<Pair<Severity, String>>()
    val raw =
        listOf(
            "copyright: ${info.copyright ?: "not set"}",
            "licence description: ${info.openTypeNameLicense ?: "not set"}",
            "licence URL: ${info.openTypeNameLicenseURL ?: "not set"}",
        )

    if (copyright.isEmpty()) {
        problems += Severity.FAIL to
            "The font has no copyright line, and the licence needs one: it is the line that says whose drawing this is."
    } else if (!COPYRIGHT_PATTERN.containsMatchIn(copyright)) {
        problems += Severity.WARN to
            "The copyright line \"$copyright\" does not start with Copyright and a year, the form Google Fonts expects " +
            "(Copyright 2026 The Family Project Authors (https://…))."
    }
    val ofl = licence.contains("SIL Open Font License", ignoreCase = true)
    if (!ofl) {
        problems += Severity.WARN to
            "The font's licence description does not name the SIL Open Font License, Version 1.1, " +
            "which a Google Fonts release carries in its name table."
    }
    if (!licenceUrl.contains("openfontlicense.org", ignoreCase = true) && !licenceUrl.contains("scripts.sil.org/OFL", ignoreCase = true)) {
        problems += Severity.WARN to "The licence URL is not the Open Font License's (https://openfontlicense.org)."
    }

    val worst = problems.minByOrNull { it.first.ordinal }
    val severity = worst?.first ?: Severity.PASS
    val summary =
        if (worst == null) {
            "The copyright line is there and the name table names the SIL Open Font License, Version 1.1."
        } else {
            problems.filter { it.first == severity }.joinToString(" ") { it.second }
        }
    return CheckItem(
        id = ReviewOrder.LICENCE,
        group = CheckGroup.LICENCE,
        title = "Copyright and licence",
        detail = null,
        severity = severity,
        summary = summary,
        reviewerQuote = null,
        action =
            CheckAction("Set the licence lines in Font info", CheckRoute.OpenFontInfo(FontInfoTarget.LICENCE)).takeIf {
                severity !=
                    Severity.PASS
            },
        basis = Basis.PUBLISHED,
        basisNote =
            "The copyright pattern and the licence strings are Google Fonts' requirements for the Open Font License route. " +
                "A font under its own licence chooses that on Ship and is judged there, not here.",
        raw = raw.joinToString("\n"),
    )
}
