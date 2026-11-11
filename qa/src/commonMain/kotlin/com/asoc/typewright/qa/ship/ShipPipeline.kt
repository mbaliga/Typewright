// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.ship

import com.asoc.typewright.core.font.ufo.UfoFontInfo

// The older repository-scaffold generators (brief §12). Every function below is pure string generation over a UfoFontInfo plus a
// handful of parameters (family, designer, year, gitUrl) -- no file I/O, no compile step and no Fontspector requirement.
// The Ship room does not use them: it writes the folder from buildShipPlan (ShipPlan.kt), which takes every fact from the project and
// leaves out what the project does not state. These stay for the campaign workbook's gate, which still passes them a family, year and
// address of its own, and they share the licence body (OflBody.kt) and the copyright line with the plan.

/**
 * Google Fonts' copyright-string pattern (docs/RESEARCH_font_quality.md, "Provenance": `Copyright
 * { year } The { family } Project Authors ({ git_url })`), used verbatim as `OFL.txt`'s first line
 * and as every `fonts { copyright: ... }` entry in [generateMetadataPbText].
 */
fun gfCopyrightLine(
    year: Int,
    family: String,
    gitUrl: String,
): String = "Copyright $year The $family Project Authors ($gitUrl)"

/**
 * The complete text of `OFL.txt`: [gfCopyrightLine] followed by the standard SIL Open Font License
 * 1.1 body, reproduced verbatim as Google Fonts' template wraps it ([OFL_BODY]; the licence text
 * itself is not Typewright's to paraphrase or alter -- the OFL is, by its own terms, meant to be
 * copied whole). The FAQ line points at `openfontlicense.org`, the licence's current home per
 * docs/RESEARCH_font_quality.md's provenance section.
 */
fun generateOflText(
    family: String,
    year: Int,
    gitUrl: String,
): String = oflFile(gfCopyrightLine(year, family, gitUrl))

/**
 * A minimal `DESCRIPTION.en_us.html` scaffold: one placeholder paragraph the user is expected to
 * replace, and a "to contribute" paragraph linking [gitUrl] (the shape real onboarded fonts use,
 * for example google/fonts' `ofl/opensans/DESCRIPTION.en_us.html`). [family] and [gitUrl] are
 * HTML-escaped.
 *
 * **Naming note.** `gftools`' packager now generates `ARTICLE.en_us.html` for new submissions,
 * while fontbakery's description checks still reference `DESCRIPTION.en_us.html`
 * (docs/RESEARCH_font_quality.md's provenance section calls which one is canonical in 2026
 * unconfirmed, `[CONFIRM]`). This function is named and shaped for `DESCRIPTION.en_us.html`
 * because that is what this task named; it is not this function's place to decide the open
 * question.
 */
fun generateDescriptionHtml(
    family: String,
    gitUrl: String,
): String =
    buildString {
        append("<p>\n")
        append(escapeHtml(family)).append(" is a Typewright-made font. Replace this paragraph with a real description.\n")
        append("</p>\n")
        append("<p>\n")
        append("To contribute, see <a href=\"").append(escapeAttribute(gitUrl)).append("\">")
        append(escapeHtml(displayAddress(gitUrl))).append("</a>\n")
        append("</p>\n")
    }

/**
 * A `METADATA.pb`-shaped text scaffold (protobuf text format), in place of the legacy
 * `upstream.yaml` `gftools` now merges into `METADATA.pb` and deletes
 * (docs/ARCHITECTURE_REVIEW.md section 5 item 52). Only fields this function's inputs can fill
 * honestly are emitted with real values; fields no input here supplies (`category`, `date_added`,
 * `subsets`, variable-font `axes`) are left as commented-out placeholders rather than invented
 * ones (law 5) -- a real packager (`gftools`) fills those from data this pure function is not
 * given. Shaped after a real onboarded family's `METADATA.pb` (google/fonts' `ofl/opensans`).
 */
fun generateMetadataPbText(
    fontInfo: UfoFontInfo,
    family: String,
    designer: String,
    year: Int,
    gitUrl: String,
): String {
    val styleName = fontInfo.styleName ?: "Regular"
    val style = if (styleName.contains("Italic", ignoreCase = true)) "italic" else "normal"
    val fileFamily = family.replace(" ", "")
    val fileStyle = styleName.replace(" ", "")
    val filename = "$fileFamily-$fileStyle.ttf"
    val postScriptName = "$fileFamily-$fileStyle"
    val fullName = "$family $styleName".trim()
    val copyright = gfCopyrightLine(year, family, gitUrl)

    return buildString {
        append("name: \"").append(escapeProtoText(family)).append("\"\n")
        append("designer: \"").append(escapeProtoText(designer)).append("\"\n")
        append("license: \"OFL\"\n")
        append("# category: not modelled by UfoFontInfo yet -- fill in before submitting (SANS_SERIF, SERIF, ...)\n")
        append("# date_added: not modelled by UfoFontInfo yet -- fill in the real onboarding date\n")
        append("fonts {\n")
        append("  name: \"").append(escapeProtoText(family)).append("\"\n")
        append("  style: \"").append(style).append("\"\n")
        append("  weight: 400 # placeholder -- UfoFontInfo does not model a weight class yet\n")
        append("  filename: \"").append(escapeProtoText(filename)).append("\"\n")
        append("  post_script_name: \"").append(escapeProtoText(postScriptName)).append("\"\n")
        append("  full_name: \"").append(escapeProtoText(fullName)).append("\"\n")
        append("  copyright: \"").append(escapeProtoText(copyright)).append("\"\n")
        append("}\n")
        append("# subsets: not modelled by UfoFontInfo yet -- at minimum \"menu\" and \"latin\" are required\n")
    }
}

/** `OFL.txt` with [copyrightLine] first and the licence after it, ending in a newline. */
internal fun oflFile(copyrightLine: String): String = copyrightLine + "\n" + OFL_BODY + "\n"

private fun escapeProtoText(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"")
