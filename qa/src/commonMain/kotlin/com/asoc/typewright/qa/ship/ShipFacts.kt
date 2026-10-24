// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.ship

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.project.imports.AuditSource
import com.asoc.typewright.project.imports.LicenceKind
import com.asoc.typewright.project.imports.auditLicence
import com.asoc.typewright.project.imports.reservedFontNames

/**
 * What a project's font info says that a repository folder is made of: names, version, people, the copyright line and
 * what can be read out of it, the licence and the vendor ID. Every value is the project's own and is null where the
 * project says nothing; nothing here is ever filled in with a guess, because a guess in a licence or a credit is a false
 * statement under the person's name. What is missing is listed by [ShipChecklist] as a line the person can act on.
 */
data class ShipFacts(
    val family: String?,
    val style: String?,
    val postScriptName: String?,
    /** `1.024`: the major version, a dot and the minor version as three digits. Null when the project has no version. */
    val version: String?,
    val designer: String?,
    val designerUrl: String?,
    val copyright: String?,
    /** The year the copyright line states, when it states one. */
    val copyrightYear: Int?,
    /** The first web address in the copyright line: where the family's repository is. */
    val repositoryUrl: String?,
    val licence: LicenceKind,
    /** The licence in the person's own words (name ID 13), when they wrote any. */
    val licenceText: String?,
    val licenceUrl: String?,
    /** The Reserved Font Names the copyright line or the licence text declares. */
    val reservedFontNames: List<String>,
    val vendorId: String?,
    /** The OS/2 weight class, when the project states one. */
    val weightClass: Int?,
    val unitsPerEm: Int?,
) {
    /** Whether the style is an italic or an oblique, by its name. */
    val italic: Boolean get() = style?.let { ITALIC.containsMatchIn(it) } == true

    /** The weight METADATA states: the project's own OS/2 weight class, else the weight the style name stands for, else null. */
    val weight: Int? get() = weightClass ?: style?.let(::weightOfStyleName)

    /** `HyleDeco-Regular`: the family and style without spaces, which names the font file, the UFO and the PostScript name. */
    val fileStem: String?
        get() {
            val f = family?.let(::pathSafe)?.takeIf { it.isNotEmpty() } ?: return null
            val s = style?.let(::pathSafe)?.takeIf { it.isNotEmpty() } ?: return null
            return "$f-$s"
        }

    /** `Hyle Deco Regular`: the family and the style as the name table writes the full name. */
    val fullName: String?
        get() {
            val f = family?.takeIf { it.isNotBlank() } ?: return null
            val s = style?.takeIf { it.isNotBlank() } ?: return null
            return "$f $s"
        }

    /** `hyle-deco-gf`: the folder name the screen proposes, from the family name alone. */
    val folderName: String?
        get() {
            val words =
                family?.let { WORDS.findAll(it).map { m -> m.value.lowercase() }.toList() }?.takeIf { it.isNotEmpty() } ?: return null
            return words.joinToString("-") + "-gf"
        }

    /** The copyright holder as the Google Fonts pattern names them (`The Hyle Deco Project Authors`), when the line follows the pattern. */
    val copyrightHolder: String?
        get() =
            copyright?.let {
                HOLDER
                    .find(it)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
            }

    companion object {
        /** The facts [info] states. */
        fun of(info: UfoFontInfo): ShipFacts {
            val copyright = info.copyright?.trim()?.takeIf { it.isNotEmpty() }
            val audit = auditLicence("", info, AuditSource.UFO)
            val postScript = info.postscriptFontName?.trim()?.takeIf { it.isNotEmpty() }
            val major = info.versionMajor
            val minor = info.versionMinor
            return ShipFacts(
                family = info.familyName?.trim()?.takeIf { it.isNotEmpty() },
                style = info.styleName?.trim()?.takeIf { it.isNotEmpty() },
                postScriptName = postScript,
                version = if (major != null && minor != null) "$major.${minor.toString().padStart(3, '0')}" else null,
                designer = info.openTypeNameDesigner?.trim()?.takeIf { it.isNotEmpty() },
                designerUrl = info.openTypeNameDesignerURL?.trim()?.takeIf { it.isNotEmpty() },
                copyright = copyright,
                copyrightYear = copyright?.let { YEAR.find(it)?.value?.toInt() },
                repositoryUrl = copyright?.let { URL.find(it)?.value?.trimEnd('.', ',', ')', ';') },
                licence = audit.licence,
                licenceText = audit.licenceText,
                licenceUrl = audit.licenceUrl,
                reservedFontNames = reservedFontNames(copyright, audit.licenceText),
                vendorId = info.openTypeOS2VendorID?.takeIf { it.isNotBlank() },
                weightClass =
                    (info.other.firstOrNull { it.first == "openTypeOS2WeightClass" }?.second as? PlistValue.PInteger)
                        ?.value
                        ?.toInt(),
                unitsPerEm = info.unitsPerEm,
            )
        }

        private val YEAR = Regex("""\b(?:19|20)\d{2}\b""")
        private val URL = Regex("""https?://[^\s)]+""")
        private val HOLDER = Regex("""Copyright\s+(?:\(c\)\s*)?(?:19|20)\d{2}\s+(.+?)\s*(?:\(https?://|$)""", RegexOption.IGNORE_CASE)
        private val ITALIC = Regex("""italic|oblique""", RegexOption.IGNORE_CASE)
        private val WORDS = Regex("""[A-Za-z0-9]+""")

        /** [name] with the characters a file name cannot hold removed, and spaces too: `Hyle Deco` is `HyleDeco`. */
        internal fun pathSafe(name: String): String = name.filterNot { it.isWhitespace() || it in UNSAFE || it.code < SPACE }

        private const val UNSAFE = "/\\:*?\"<>|"
        private const val SPACE = 0x20
    }
}

/**
 * The weight a style name stands for, by the names the OpenType specification and Google Fonts use, or null for a name that
 * is not one of them (the project then has to state a weight class). `Bold Italic` is 700: the italic part is not a weight.
 */
internal fun weightOfStyleName(style: String): Int? {
    val words = style.replace("Italic", "", ignoreCase = true).replace("Oblique", "", ignoreCase = true).trim()
    val key = words.lowercase().replace(" ", "")
    if (key.isEmpty() || key == "regular" || key == "normal" || key == "roman") return 400
    return STYLE_WEIGHTS[key]
}

private val STYLE_WEIGHTS: Map<String, Int> =
    mapOf(
        "thin" to 100,
        "hairline" to 100,
        "extralight" to 200,
        "ultralight" to 200,
        "light" to 300,
        "regular" to 400,
        "medium" to 500,
        "semibold" to 600,
        "demibold" to 600,
        "bold" to 700,
        "extrabold" to 800,
        "ultrabold" to 800,
        "black" to 900,
        "heavy" to 900,
    )
