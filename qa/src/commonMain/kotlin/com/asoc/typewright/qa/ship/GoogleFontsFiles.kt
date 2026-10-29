// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.ship

import com.asoc.typewright.project.imports.LicenceKind

// The text of the files the repository folder is made of, each a pure function of what the project states. A fact the project does not state
// is never written as a placeholder: the line, or the whole file, is left out and the plan lists it (ShipPlan.omissions).

/** `OFL.txt` for a font whose copyright line is [copyright]: that line first, exactly as the project states it, then the licence. */
internal fun generateOflFromCopyright(copyright: String): String = oflFile(copyright.trim())

/** `AUTHORS.txt`: the designers [designer] names, one a line (names separated by commas), or null when it names none. */
internal fun authorsText(designer: String): String? {
    val names = designer.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    return if (names.isEmpty()) null else names.joinToString("\n", postfix = "\n")
}

/** The shortest `DESCRIPTION.en_us.html` Google Fonts' checker accepts, in bytes (its `description/min_length` check wants more than this). */
const val DESCRIPTION_MIN_BYTES: Int = 200

/**
 * The draft `DESCRIPTION.en_us.html`, from what the project and its brief state and nothing else: the family and what the brief calls its
 * genre and uses, the one style with its glyph count, scripts and measured heights, and the repository when the copyright line names it.
 * It is a draft to rewrite, and says only what is true of the project; it carries no placeholder text.
 */
internal fun descriptionHtml(inputs: ShipInputs): String {
    val facts = inputs.facts
    val family = facts.family ?: inputs.ufoFolderName.removeSuffix(".ufo")
    val brief = inputs.brief
    val paragraphs = mutableListOf<String>()

    val kind = brief.genre?.lowercase()?.let { "${articleFor(it)} $it typeface" } ?: "a typeface"
    val uses = brief.uses.map { it.lowercase() }
    val purpose =
        when (uses.size) {
            0 -> ""
            1 -> " for ${uses.single()}"
            else -> " for ${uses.dropLast(1).joinToString(", ")} and ${uses.last()}"
        }
    paragraphs += "$family is $kind$purpose."

    val coverage = scriptWords(inputs.font.scripts)
    val heights =
        listOfNotNull(
            inputs.font.capHeight?.let { "a cap height of $it" },
            inputs.font.xHeight?.let { "an x-height of $it" },
        )
    val sentences =
        listOfNotNull(
            facts.style?.let { "The style in this folder is $it." },
            "It has ${inputs.font.glyphCount} glyphs${if (coverage != null) " covering $coverage" else ""}.",
            facts.unitsPerEm?.let { upm ->
                "It is drawn on an em of $upm units${if (heights.isEmpty()) "" else ", with ${heights.joinToString(" and ")}"}."
            },
        )
    paragraphs += sentences.joinToString(" ")

    val html = paragraphs.map { "<p>\n${escapeHtml(it)}\n</p>" }.toMutableList()
    facts.repositoryUrl?.let { url ->
        html += "<p>\nTo contribute, see <a href=\"${escapeAttribute(url)}\">${escapeHtml(displayAddress(url))}</a>\n</p>"
    }
    return html.joinToString("\n") + "\n"
}

/** `a` or `an` for [phrase], by its first letter. */
private fun articleFor(phrase: String): String = if (phrase.firstOrNull()?.let { it in VOWELS } == true) "an" else "a"

private const val VOWELS = "aeiou"

private fun scriptWords(tags: List<String>): String? {
    val names = tags.mapNotNull { SCRIPT_NAMES[it] ?: it.takeIf { tag -> tag.isNotBlank() } }
    return when (names.size) {
        0 -> null
        1 -> names.single()
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }
}

private val SCRIPT_NAMES: Map<String, String> =
    mapOf(
        "Latn" to "Latin",
        "Cyrl" to "Cyrillic",
        "Grek" to "Greek",
        "Deva" to "Devanagari",
        "Arab" to "Arabic",
        "Hira" to "Hiragana",
        "Kana" to "Katakana",
    )

/** Why there is no `date_added` line: the reason the plan lists and the page shows. */
const val DATE_ADDED_UNKNOWN: String = "Google Fonts sets it on the day it accepts the family, and the project does not know that day"

/**
 * `METADATA.pb`, Google Fonts' record of the family in protobuf text format: name, designer, licence, category, the day Google Fonts added the
 * family when the person states it, the font file with its names and copyright, the subsets the font's characters belong in, and the
 * repository. A line whose fact the project does not state is left out and recorded in [omissions]: no designer line without a designer, no
 * category line until one is chosen, no `date_added` line until the person gives the day (the day the folder is made is not it).
 */
internal fun metadataPb(
    inputs: ShipInputs,
    stem: String,
    omissions: MutableList<Omission>,
): String {
    val facts = inputs.facts
    val family = facts.family ?: stem.substringBefore('-')
    val text = StringBuilder()
    text.appendLine("name: ${protoString(family)}")
    if (facts.designer != null) {
        text.appendLine("designer: ${protoString(facts.designer)}")
    } else {
        omissions += Omission("METADATA.pb designer", "the project names no designer")
    }
    text.appendLine("license: ${protoString("OFL")}")
    if (inputs.category != null) {
        text.appendLine("category: ${protoString(inputs.category.protoName)}")
    } else {
        omissions += Omission("METADATA.pb category", "no category is chosen")
    }
    if (inputs.dateAdded != null) {
        text.appendLine("date_added: ${protoString(inputs.dateAdded)}")
    } else {
        omissions += Omission("METADATA.pb date_added", DATE_ADDED_UNKNOWN)
    }
    text.appendLine("fonts {")
    text.appendLine("  name: ${protoString(family)}")
    text.appendLine("  style: ${protoString(if (facts.italic) "italic" else "normal")}")
    if (facts.weight != null) {
        text.appendLine("  weight: ${facts.weight}")
    } else {
        omissions += Omission("METADATA.pb weight", "the style name is not a weight and the project states no weight class")
    }
    text.appendLine("  filename: ${protoString("$stem.ttf")}")
    text.appendLine("  post_script_name: ${protoString(facts.postScriptName ?: stem)}")
    facts.fullName?.let { text.appendLine("  full_name: ${protoString(it)}") }
    facts.copyright?.let { text.appendLine("  copyright: ${protoString(it)}") }
    text.appendLine("}")
    for (subset in subsetsOf(inputs)) text.appendLine("subsets: ${protoString(subset)}")
    facts.repositoryUrl?.let {
        text.appendLine("source {")
        text.appendLine("  repository_url: ${protoString(it)}")
        text.appendLine("}")
    }
    return text.toString()
}

/**
 * The Google Fonts subsets the font's characters belong in, alphabetically as its checker wants them: `menu` always (the family's name in
 * its own letters), `latin` for a font that has basic Latin letters, and `latin-ext` when it has characters in the Latin Extended blocks.
 * Only Latin is read, from the characters the font actually maps; a font in another script gets `menu`, and its other subsets are left
 * to the person.
 */
internal fun subsetsOf(inputs: ShipInputs): List<String> {
    val codepoints = inputs.font.codepoints
    val subsets = mutableListOf("menu")
    if (codepoints.any { it in BASIC_LATIN_CAPITALS || it in BASIC_LATIN_SMALL }) subsets += "latin"
    if (codepoints.any { cp -> LATIN_EXTENDED.any { cp in it } }) subsets += "latin-ext"
    return subsets.sorted()
}

private val BASIC_LATIN_CAPITALS = 0x41..0x5A
private val BASIC_LATIN_SMALL = 0x61..0x7A
private val LATIN_EXTENDED = listOf(0x100..0x24F, 0x1E00..0x1EFF, 0x2C60..0x2C7F, 0xA720..0xA7FF)

/**
 * `upstream.yaml`: where each file of this repository goes in Google Fonts' family directory (`files:`, from where in the repository to where
 * in the family directory), on the [branch] the person says their repository publishes from, and the repository when the copyright line names
 * one. There is no default branch: the plan writes this file only when the person has stated one.
 */
internal fun upstreamYaml(
    facts: ShipFacts,
    stem: String,
    present: Collection<String>,
    branch: String,
    omissions: MutableList<Omission>,
): String {
    val text = StringBuilder()
    facts.family?.let { text.appendLine("name: ${yamlScalar(it)}") }
    if (facts.repositoryUrl != null) {
        text.appendLine("repository_url: ${yamlScalar(facts.repositoryUrl)}")
    } else {
        omissions += Omission("upstream.yaml repository_url", "the copyright line names no repository address")
    }
    text.appendLine("branch: ${yamlScalar(branch)}")
    text.appendLine("files:")
    for ((from, to) in upstreamFileMap(stem, present)) text.appendLine("  ${yamlScalar(from)}: ${yamlScalar(to)}")
    return text.toString()
}

/** The request a person pastes into a Google Fonts issue or pull request: only facts the project states, and what the build found. */
internal fun requestText(
    inputs: ShipInputs,
    stem: String,
): String {
    val facts = inputs.facts
    val family = facts.family ?: stem.substringBefore('-')
    val lines = mutableListOf<String>()
    lines += "# Add $family to Google Fonts"
    lines += ""
    lines += "I would like to add **$family** to Google Fonts."
    lines += ""
    val style = facts.style?.let { s -> facts.weight?.let { "$s, $it" } ?: s }
    lines += "- Family: $family" + (style?.let { " ($it)" } ?: "")
    facts.designer?.let { lines += "- Designer: $it" }
    inputs.category?.let { lines += "- Category: ${it.label}" }
    lines += "- Licence: SIL Open Font License 1.1, in `OFL.txt`"
    facts.repositoryUrl?.let { lines += "- Upstream repository: $it" }
    lines += "- Sources: `sources/$stem.ufo` (UFO 3)"
    if (inputs.binary != null) lines += "- Font file: `fonts/ttf/$stem.ttf`, built from those sources with fontmake"
    inputs.layerOne?.let {
        lines +=
            "- Checked with ${it.tool}'s Google Fonts profile on the compiled font: " +
            "${it.fail} fail · ${it.warn} warn · ${it.pass} pass. The full report is not attached."
    }
    lines += ""
    lines += "This folder was made with Typewright."
    return lines.joinToString("\n", postfix = "\n")
}

/** The licence file Own licence mode writes, as its path and text, or null when the project's licence has no words to put in it. */
internal fun ownLicenceFile(inputs: ShipInputs): Pair<String, String>? {
    val facts = inputs.facts
    if (facts.licence == LicenceKind.OFL) {
        val copyright = facts.copyright ?: return null
        return "OFL.txt" to generateOflFromCopyright(copyright)
    }
    val words = listOfNotNull(facts.licenceText, facts.licenceUrl).filter { it.isNotBlank() }
    if (words.isEmpty()) return null
    return "LICENSE.txt" to (listOfNotNull(facts.copyright).plus(words).joinToString("\n\n", postfix = "\n"))
}

/** Why Own licence mode wrote no licence file. */
internal fun ownLicenceOmission(facts: ShipFacts): String =
    if (facts.licence == LicenceKind.OFL) {
        "the project has no copyright line to put first in OFL.txt"
    } else {
        "the project states no licence text of its own"
    }

// ---- escaping -------------------------------------------------------------------------------------

internal fun escapeHtml(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

internal fun escapeAttribute(text: String): String = escapeHtml(text).replace("\"", "&quot;")

/** [address] as a link's visible text: without `https://`, which Google Fonts' checker asks descriptions to leave out. */
internal fun displayAddress(address: String): String = address.removePrefix("https://").removePrefix("http://")

/**
 * [text] as a protobuf text-format string. Only the backslash, the quote and control characters are escaped; letters outside ASCII are
 * written as they are, because Google Fonts' checker rejects the escaped (octal) form of a name such as `Julián`.
 */
internal fun protoString(text: String): String {
    val out = StringBuilder("\"")
    for (c in text) {
        when {
            c == '\\' -> out.append("\\\\")
            c == '"' -> out.append("\\\"")
            c == '\n' -> out.append("\\n")
            c == '\t' -> out.append("\\t")
            c == '\r' -> out.append("\\r")
            c.code < 0x20 -> out.append("\\x").append(c.code.toString(16).padStart(2, '0'))
            else -> out.append(c)
        }
    }
    return out.append('"').toString()
}

private val PLAIN_YAML = Regex("""[A-Za-z0-9_./-][A-Za-z0-9_./ -]*""")

/** [text] as a YAML scalar: plain where it is only letters, digits and a few marks, otherwise double-quoted. */
internal fun yamlScalar(text: String): String {
    val plainEnough = PLAIN_YAML.matches(text) && !text.endsWith(' ') && text !in YAML_WORDS
    if (plainEnough) return text
    val out = StringBuilder("\"")
    for (c in text) {
        when {
            c == '\\' -> out.append("\\\\")
            c == '"' -> out.append("\\\"")
            c == '\n' -> out.append("\\n")
            c.code < 0x20 -> out.append("\\x").append(c.code.toString(16).padStart(2, '0'))
            else -> out.append(c)
        }
    }
    return out.append('"').toString()
}

private val YAML_WORDS = setOf("true", "false", "null", "yes", "no", "on", "off", "~")
