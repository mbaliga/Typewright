// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.withoutXmlIllegalCharacters
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.sidebearings
import com.asoc.typewright.project.imports.AuditSource
import com.asoc.typewright.project.imports.LicenceKind
import com.asoc.typewright.project.imports.auditLicence
import com.asoc.typewright.qa.report.FontInfoTarget
import com.asoc.typewright.ui.draw.ExprResult
import com.asoc.typewright.ui.draw.ExprScope
import com.asoc.typewright.ui.draw.NumericExpr
import kotlin.math.abs

/** The parts of Font info, in the order the page lists them. */
enum class FontInfoSection { IDENTITY, METRICS, PEOPLE_AND_LICENCE }

/** One value of `fontinfo.plist` that Font info lets the person change, with how the page labels it. */
enum class FontInfoField(
    val label: String,
    val section: FontInfoSection,
    /** True for a number of font units, typed as a number or as `=` and an expression. */
    val numeric: Boolean = false,
) {
    FAMILY("Family", FontInfoSection.IDENTITY),
    STYLE("Style", FontInfoSection.IDENTITY),
    VERSION("Version", FontInfoSection.IDENTITY),
    ASCENDER("Ascender", FontInfoSection.METRICS, numeric = true),
    CAP_HEIGHT("Cap", FontInfoSection.METRICS, numeric = true),
    X_HEIGHT("x-height", FontInfoSection.METRICS, numeric = true),
    DESCENDER("Descender", FontInfoSection.METRICS, numeric = true),
    LINE_GAP("Line gap", FontInfoSection.METRICS, numeric = true),
    DESIGNER("Designer · ID 9", FontInfoSection.PEOPLE_AND_LICENCE),
    DESIGNER_URL("Designer URL · ID 12", FontInfoSection.PEOPLE_AND_LICENCE),
    COPYRIGHT("Copyright · ID 0", FontInfoSection.PEOPLE_AND_LICENCE),
    LICENCE_TEXT("Licence · ID 13", FontInfoSection.PEOPLE_AND_LICENCE),
    LICENCE_URL("Licence URL · ID 14", FontInfoSection.PEOPLE_AND_LICENCE),
    VENDOR_ID("Vendor ID", FontInfoSection.PEOPLE_AND_LICENCE),
}

/** The licence a person says their font is under. The Open Font License and Apache write their standard name-table lines; the others are the person's own words. */
enum class LicenceChoice(
    val label: String,
) {
    OFL("OFL"),
    APACHE("Apache"),
    PROPRIETARY("Proprietary"),
    OTHER("Other"),
}

/** What a change to one field comes to: the [Set] font info it makes, [Unchanged] when it would change nothing, or [Refused] with the reason in plain words. */
sealed interface FontInfoChange {
    data class Set(
        val info: UfoFontInfo,
        /** Anything the person should know the change also did. */
        val note: String? = null,
    ) : FontInfoChange

    data class Unchanged(
        val reason: String,
    ) : FontInfoChange

    data class Refused(
        val message: String,
    ) : FontInfoChange
}

/**
 * The strings Google Fonts' requirements give for the licence name-table entries (IDs 13 and 14) of an Open Font License font
 * and of an Apache one. They are the published text, written as it is, never composed from the person's name.
 */
object LicenceStrings {
    const val OFL_DESCRIPTION =
        "This Font Software is licensed under the SIL Open Font License, Version 1.1. This license is available with a FAQ at: " +
            "https://openfontlicense.org"
    const val OFL_URL = "https://openfontlicense.org"
    const val APACHE_DESCRIPTION = "Licensed under the Apache License, Version 2.0"
    const val APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
}

/**
 * The rules for changing `fontinfo.plist` from the Font info page, kept apart from the page so they can be tested without
 * drawing anything. Each rule reads what was typed and answers the font info that results, or says in plain words why it
 * will not: a value that would write a character a plist cannot carry (a control character, a lone surrogate) is refused
 * (law 7: every file we write opens in FontForge, Glyphs and RoboFont), and so are numbers that make no sense (a descender
 * above the baseline). A value the font cannot know (the designer, the copyright holder) is never invented: it is empty until
 * the person types it.
 */
object FontInfoRules {
    private const val MAX_VENDOR_ID = 4
    private const val MAX_VERSION_DIGITS = 4
    private const val VERSION_MINOR_DIGITS = 3
    private const val UNITS_PER_EM_FALLBACK = 1000
    private const val SANE_METRIC_EM_MULTIPLE = 10
    private val PRINTABLE_ASCII = 0x20..0x7E

    /** The value of [field] in [info] as the editor opens it, or null when `fontinfo.plist` does not state it. */
    fun textIn(
        info: UfoFontInfo,
        field: FontInfoField,
    ): String? =
        when (field) {
            FontInfoField.FAMILY -> info.familyName
            FontInfoField.STYLE -> info.styleName
            FontInfoField.VERSION -> versionText(info)
            FontInfoField.ASCENDER -> ascenderOf(info)?.toString()
            FontInfoField.CAP_HEIGHT -> info.capHeight?.toString()
            FontInfoField.X_HEIGHT -> info.xHeight?.toString()
            FontInfoField.DESCENDER -> descenderOf(info)?.toString()
            FontInfoField.LINE_GAP -> (info.openTypeHheaLineGap ?: 0).toString()
            FontInfoField.DESIGNER -> info.openTypeNameDesigner
            FontInfoField.DESIGNER_URL -> info.openTypeNameDesignerURL
            FontInfoField.COPYRIGHT -> info.copyright
            FontInfoField.LICENCE_TEXT -> info.openTypeNameLicense
            FontInfoField.LICENCE_URL -> info.openTypeNameLicenseURL
            FontInfoField.VENDOR_ID -> info.openTypeOS2VendorID
        }

    /** The ascender the page shows and judges: the hhea one when the font states it, else the generic one. */
    fun ascenderOf(info: UfoFontInfo): Int? = info.openTypeHheaAscender ?: info.ascender

    /** The descender the page shows and judges: the hhea one when the font states it, else the generic one. */
    fun descenderOf(info: UfoFontInfo): Int? = info.openTypeHheaDescender ?: info.descender

    /** `1.024`: the major version, a dot and the minor to three digits, or null when the font states neither. */
    fun versionText(info: UfoFontInfo): String? {
        val major = info.versionMajor
        val minor = info.versionMinor
        if (major == null && minor == null) return null
        return "${major ?: 0}.${(minor ?: 0).toString().padStart(VERSION_MINOR_DIGITS, '0')}"
    }

    /** What the person typed into [field], applied to [info]; [scope] says what `=` can name in a number. */
    fun change(
        info: UfoFontInfo,
        field: FontInfoField,
        typed: String,
        scope: ExprScope = ExprScopeOf(info, emptyList()),
    ): FontInfoChange {
        val text = typed.trim()
        if (!field.numeric) unwritable(text)?.let { return FontInfoChange.Refused(it) }
        val changed: FontInfoChange = if (field.numeric) numeric(info, field, text, scope) else textual(info, field, text)
        val alreadyThere = changed is FontInfoChange.Set && changed.info == info
        return if (alreadyThere) FontInfoChange.Unchanged("That is the value already.") else changed
    }

    /** [info] with the standard name-table lines of [choice] written, or [FontInfoChange.Unchanged] when the person writes their own. */
    fun chooseLicence(
        info: UfoFontInfo,
        choice: LicenceChoice,
    ): FontInfoChange {
        val set =
            when (choice) {
                LicenceChoice.OFL -> {
                    info.copy(
                        openTypeNameLicense = LicenceStrings.OFL_DESCRIPTION,
                        openTypeNameLicenseURL = LicenceStrings.OFL_URL,
                    )
                }

                LicenceChoice.APACHE -> {
                    info.copy(
                        openTypeNameLicense = LicenceStrings.APACHE_DESCRIPTION,
                        openTypeNameLicenseURL = LicenceStrings.APACHE_URL,
                    )
                }

                LicenceChoice.PROPRIETARY, LicenceChoice.OTHER -> {
                    return FontInfoChange.Unchanged(
                        "Write the licence text and its web address below; Typewright does not write a licence for you.",
                    )
                }
            }
        return if (set == info) FontInfoChange.Unchanged("That is the licence already.") else FontInfoChange.Set(set)
    }

    /** The licence [info] states, as the importer's audit reads it, or null when it states none. */
    fun licenceOf(info: UfoFontInfo): LicenceChoice? =
        when (auditLicence("fontinfo.plist", info, AuditSource.UFO).licence) {
            LicenceKind.OFL -> LicenceChoice.OFL
            LicenceKind.APACHE -> LicenceChoice.APACHE
            LicenceKind.PROPRIETARY -> LicenceChoice.PROPRIETARY
            LicenceKind.USE_LIMITED, LicenceKind.OTHER -> LicenceChoice.OTHER
            LicenceKind.NONE -> null
        }

    /** The field a Check finding's action lands on: the first one the finding is about that is not yet set, else the first it is about. */
    fun landing(
        target: FontInfoTarget,
        info: UfoFontInfo,
    ): FontInfoField =
        when (target) {
            FontInfoTarget.METRICS -> {
                when {
                    ascenderOf(info) == null -> FontInfoField.ASCENDER
                    descenderOf(info) == null -> FontInfoField.DESCENDER
                    (info.openTypeHheaLineGap ?: 0) != 0 -> FontInfoField.LINE_GAP
                    else -> FontInfoField.ASCENDER
                }
            }

            FontInfoTarget.NAMES -> {
                when {
                    info.familyName.isNullOrBlank() -> FontInfoField.FAMILY
                    info.styleName.isNullOrBlank() -> FontInfoField.STYLE
                    else -> FontInfoField.FAMILY
                }
            }

            FontInfoTarget.LICENCE -> {
                if (info.copyright.isNullOrBlank()) FontInfoField.COPYRIGHT else FontInfoField.LICENCE_TEXT
            }
        }

    // ---- text ---------------------------------------------------------------------------------------

    /** Why [text] cannot be written into a plist, or null when it can. */
    fun unwritable(text: String): String? {
        val control = text.any { it < ' ' || it in '\u007F'..'\u009F' }
        if (control || withoutXmlIllegalCharacters(text) != text) {
            return "That holds a character a font file cannot carry " +
                "(a control character, such as a tab or a line break inside the text). Type it on one line."
        }
        return null
    }

    private fun textual(
        info: UfoFontInfo,
        field: FontInfoField,
        text: String,
    ): FontInfoChange {
        val orNull = text.takeIf { it.isNotEmpty() }
        return when (field) {
            FontInfoField.FAMILY -> required(text, "family name") { FontInfoChange.Set(info.copy(familyName = it)) }
            FontInfoField.STYLE -> required(text, "style name") { FontInfoChange.Set(info.copy(styleName = it)) }
            FontInfoField.VERSION -> version(info, text)
            FontInfoField.DESIGNER -> FontInfoChange.Set(info.copy(openTypeNameDesigner = orNull))
            FontInfoField.DESIGNER_URL -> url(text, "designer's") { FontInfoChange.Set(info.copy(openTypeNameDesignerURL = it)) }
            FontInfoField.COPYRIGHT -> FontInfoChange.Set(info.copy(copyright = orNull))
            FontInfoField.LICENCE_TEXT -> FontInfoChange.Set(info.copy(openTypeNameLicense = orNull))
            FontInfoField.LICENCE_URL -> url(text, "licence's") { FontInfoChange.Set(info.copy(openTypeNameLicenseURL = it)) }
            FontInfoField.VENDOR_ID -> vendorId(info, text)
            else -> FontInfoChange.Refused("${field.label} is a number.")
        }
    }

    private fun required(
        text: String,
        what: String,
        set: (String) -> FontInfoChange,
    ): FontInfoChange = if (text.isEmpty()) FontInfoChange.Refused("A font needs a $what; type one.") else set(text)

    private fun url(
        text: String,
        whose: String,
        set: (String?) -> FontInfoChange,
    ): FontInfoChange =
        when {
            text.isEmpty() -> set(null)
            text.startsWith("https://") || text.startsWith("http://") -> set(text)
            else -> FontInfoChange.Refused("The $whose web address starts with https:// (or http://).")
        }

    private fun version(
        info: UfoFontInfo,
        text: String,
    ): FontInfoChange {
        if (text.isEmpty()) return FontInfoChange.Set(info.copy(versionMajor = null, versionMinor = null))
        val parts = text.split('.')
        val major = parts.getOrNull(0)?.takeIf { it.length in 1..MAX_VERSION_DIGITS && it.all(Char::isDigit) }?.toIntOrNull()
        val minor = parts.getOrNull(1)?.takeIf { it.length == VERSION_MINOR_DIGITS && it.all(Char::isDigit) }?.toIntOrNull()
        if (parts.size != 2 || major == null || minor == null) {
            return FontInfoChange.Refused("Write the version as a number, a dot and three digits, like 1.024.")
        }
        return FontInfoChange.Set(info.copy(versionMajor = major, versionMinor = minor))
    }

    private fun vendorId(
        info: UfoFontInfo,
        text: String,
    ): FontInfoChange {
        if (text.isEmpty()) return FontInfoChange.Set(info.copy(openTypeOS2VendorID = null))
        if (text.length > MAX_VENDOR_ID || text.any { it.code !in PRINTABLE_ASCII }) {
            return FontInfoChange.Refused("A vendor ID is up to four letters or digits, such as GOOG.")
        }
        return FontInfoChange.Set(info.copy(openTypeOS2VendorID = text))
    }

    // ---- numbers ------------------------------------------------------------------------------------

    private fun numeric(
        info: UfoFontInfo,
        field: FontInfoField,
        text: String,
        scope: ExprScope,
    ): FontInfoChange {
        val units =
            when (val result = NumericExpr.evaluate(text, scope)) {
                is ExprResult.Error -> return FontInfoChange.Refused(result.message)
                is ExprResult.Value -> result.units
            }
        val limit = SANE_METRIC_EM_MULTIPLE * (info.unitsPerEm ?: UNITS_PER_EM_FALLBACK)
        if (abs(units) > limit) {
            val em = info.unitsPerEm ?: UNITS_PER_EM_FALLBACK
            return FontInfoChange.Refused("$units is far outside the em ($em units); check the number.")
        }
        return when (field) {
            FontInfoField.ASCENDER -> {
                ascender(info, units)
            }

            FontInfoField.DESCENDER -> {
                descender(info, units)
            }

            FontInfoField.CAP_HEIGHT -> {
                positive(units, "cap height") { FontInfoChange.Set(info.copy(capHeight = units)) }
            }

            FontInfoField.X_HEIGHT -> {
                positive(units, "x-height") { FontInfoChange.Set(info.copy(xHeight = units)) }
            }

            FontInfoField.LINE_GAP -> {
                lineGap(info, units)
            }

            else -> {
                FontInfoChange.Refused("${field.label} is text.")
            }
        }
    }

    private fun positive(
        units: Int,
        what: String,
        set: () -> FontInfoChange,
    ): FontInfoChange = if (units <= 0) FontInfoChange.Refused("The $what is the height above the baseline, so it is above 0.") else set()

    private fun ascender(
        info: UfoFontInfo,
        units: Int,
    ): FontInfoChange {
        if (units <= 0) return FontInfoChange.Refused("The ascender is the height above the baseline, so it is above 0.")
        descenderOf(info)?.let { below ->
            if (units <= below) return FontInfoChange.Refused("The ascender has to be above the descender ($below).")
        }
        val hhea = info.openTypeHheaAscender != null
        val typo = info.openTypeOS2TypoAscender != null
        val set =
            info.copy(
                ascender = units,
                openTypeHheaAscender = if (hhea) units else null,
                openTypeOS2TypoAscender = if (typo) units else null,
            )
        return FontInfoChange.Set(set, keptInStep("ascender", hhea, typo))
    }

    private fun descender(
        info: UfoFontInfo,
        units: Int,
    ): FontInfoChange {
        if (units > 0) {
            return FontInfoChange.Refused("The descender is below the baseline, so it is a negative number (${-units}, not $units).")
        }
        val hhea = info.openTypeHheaDescender != null
        val typo = info.openTypeOS2TypoDescender != null
        val set =
            info.copy(
                descender = units,
                openTypeHheaDescender = if (hhea) units else null,
                openTypeOS2TypoDescender = if (typo) units else null,
            )
        return FontInfoChange.Set(set, keptInStep("descender", hhea, typo))
    }

    private fun lineGap(
        info: UfoFontInfo,
        units: Int,
    ): FontInfoChange {
        if (units < 0) return FontInfoChange.Refused("The line gap is extra space between lines, so it is 0 or more.")
        val typo = info.openTypeOS2TypoLineGap != null
        val set = info.copy(openTypeHheaLineGap = units, openTypeOS2TypoLineGap = if (typo) units else null)
        return FontInfoChange.Set(set, if (typo) "The typo line gap is set to match." else null)
    }

    /** Google Fonts wants hhea and typo to agree, so a value the font states in either is moved with the generic one. */
    private fun keptInStep(
        what: String,
        hhea: Boolean,
        typo: Boolean,
    ): String? =
        when {
            hhea && typo -> "The hhea and typo $what are set to match."
            hhea -> "The hhea $what is set to match."
            typo -> "The typo $what is set to match."
            else -> null
        }
}

/** What `=` can name in a Font info number: the font's own metrics (`=xheight`, `=capheight`, `=ascender`, `=descender`, `=upm`) and a glyph's advance and sidebearings (`=n.rsb`). */
internal class ExprScopeOf(
    private val info: UfoFontInfo,
    private val glyphs: List<Glyph>,
) : ExprScope {
    override fun metric(name: String): Double? =
        when (name) {
            "xheight" -> info.xHeight?.toDouble()
            "capheight" -> info.capHeight?.toDouble()
            "ascender" -> FontInfoRules.ascenderOf(info)?.toDouble()
            "descender" -> FontInfoRules.descenderOf(info)?.toDouble()
            "baseline" -> 0.0
            "upm" -> (info.unitsPerEm ?: DEFAULT_UPM).toDouble()
            else -> null
        }

    override fun glyphValue(
        glyph: String?,
        property: String,
    ): Double? {
        val found = glyph?.let { name -> glyphs.firstOrNull { it.name == name } } ?: return null
        return when (property) {
            "adv" -> found.advanceWidth.toDouble()
            "lsb" -> found.sidebearings()?.left?.toDouble()
            "rsb" -> found.sidebearings()?.right?.toDouble()
            else -> null
        }
    }

    private companion object {
        const val DEFAULT_UPM = 1000
    }
}
