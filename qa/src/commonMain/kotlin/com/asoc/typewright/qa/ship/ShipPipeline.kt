// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.ship

import com.asoc.typewright.core.font.ufo.UfoFontInfo

// This file (com.asoc.typewright.qa.ship) is the Ship room's repository-scaffold generators
// (brief §12: "The Ship room is a checklist that produces a repository"). Every function below is
// pure string generation over a UfoFontInfo plus a handful of parameters (family, designer, year,
// gitUrl) -- no file I/O (a platform caller writes the returned text to a file), no compile step
// and no Fontspector requirement, so this stays testable in commonTest on both targets.
// data/scripts never hand-writes these files; a real GF submission has its own packager (gftools)
// that does the same job from a config file -- these generators exist so the app can show the user
// a preview of what Ship will write, and so a hosted/local packaging step (out of this module's
// scope) has something to start from.

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
 * 1.1 body, reproduced verbatim (the licence text itself is not Typewright's to paraphrase or
 * alter -- the OFL is, by its own terms, meant to be copied whole). The FAQ line points at
 * `openfontlicense.org`, the licence's current home per docs/RESEARCH_font_quality.md's
 * provenance section; many already-onboarded Google Fonts still carry the older
 * `scripts.sil.org/OFL` URL from when they were submitted, which still resolves, but a file this
 * function generates today should point new readers at the current one.
 */
fun generateOflText(
    family: String,
    year: Int,
    gitUrl: String,
): String =
    buildString {
        append(gfCopyrightLine(year, family, gitUrl)).append('\n')
        append('\n')
        append(OFL_BODY)
    }

private const val OFL_BODY =
    """This Font Software is licensed under the SIL Open Font License, Version 1.1.
This license is copied below, and is also available with a FAQ at:
https://openfontlicense.org

-----------------------------------------------------------
SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
-----------------------------------------------------------

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of collaborative font projects, to support the font
creation efforts of academic and linguistic communities, and to
provide a free and open framework in which fonts may be shared and
improved in partnership with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded,
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply to
any document created using the fonts or their derivatives.

DEFINITIONS
"Font Software" refers to the set of files released by the Copyright
Holder(s) under this license and clearly marked as such. This may
include source files, build scripts and documentation.

"Reserved Font Name" refers to any names specified as such after the
copyright statement(s).

"Original Version" refers to the collection of Font Software
components as distributed by the Copyright Holder(s).

"Modified Version" refers to any derivative made by adding to,
deleting, or substituting -- in part or in whole -- any of the
components of the Original Version, by changing formats or by porting
the Font Software to a new environment.

"Author" refers to any designer, engineer, programmer, technical
writer or other person who contributed to the Font Software.

PERMISSION & CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed,
modify, redistribute, and sell modified and unmodified copies of the
Font Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components, in
Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human-readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless explicit written permission is granted by the
corresponding Copyright Holder. This restriction only applies to the
primary font name as presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font
Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Author(s) or with their explicit written
permission.

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created using
the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.
"""

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
        append("To contribute, see <a href=\"").append(escapeHtmlAttribute(gitUrl)).append("\">")
        append(escapeHtml(displayUrl(gitUrl))).append("</a>\n")
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

private fun escapeHtml(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private fun escapeHtmlAttribute(text: String): String = escapeHtml(text).replace("\"", "&quot;")

/** [gitUrl] with a leading `https://` stripped for display text, the way google/fonts' own DESCRIPTION files show a link's text. */
private fun displayUrl(gitUrl: String): String = gitUrl.removePrefix("https://").removePrefix("http://")

private fun escapeProtoText(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"")
