// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.imports

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoFontInfo

/** What kind of licence a font states. */
enum class LicenceKind {
    /** The SIL Open Font License. */
    OFL,

    /** The Apache License. */
    APACHE,

    /** A licence that withholds the right to modify: a proprietary or end-user licence, or a restricted-embedding flag with no open licence. */
    PROPRIETARY,

    /** A licence is stated, but not one Typewright recognises. */
    OTHER,

    /** No licence is stated anywhere Typewright looks. */
    NONE,
}

/** Where the audited facts were read from, which decides how the audit names its fields. */
enum class AuditSource(
    val label: String,
) {
    /** A TrueType font file: the facts come from its `name` table. */
    FONT_FILE("name table"),

    /** A UFO: the facts come from `fontinfo.plist`. */
    UFO("fontinfo.plist"),
}

/**
 * The verdict of the licence and attribution audit on import (brief §4.5, SCREENS_V1 S03), each
 * with the shape that carries it so colour is never the only signal.
 */
enum class ImportVerdict(
    val shape: String,
    val title: String,
    val allowsImport: Boolean,
) {
    /** The licence lets the font be modified, and nothing needs doing. */
    FINE("✓", "Fine to modify", true),

    /** The licence lets the font be modified but reserves its name, so the new version must be renamed. */
    RENAME("◐", "Modify, but rename", true),

    /** There is no licence, or not one Typewright recognises: import is allowed, with a warning. */
    UNCERTAIN("◐", "Read the licence first", true),

    /** The licence withholds the right to modify: import stays off. */
    REFUSED("●", "Not licensed for modification", false),
}

/**
 * The licence and attribution audit of one font on import (brief §4.5, §12). Every field is what
 * the font itself says; [verdict] and [verdictWords] are the rules applied to it: an OFL or Apache
 * font is fine to modify; one that reserves a font name must be renamed; one with no licence, or
 * one Typewright does not recognise, is a warning; one whose licence withholds modification is
 * refused.
 */
data class ImportAudit(
    val fileName: String,
    val source: AuditSource,
    val familyName: String?,
    val licence: LicenceKind,
    /** The licence as a person would name it: `SIL Open Font License 1.1`, `Apache License 2.0`, or the words Typewright uses when it is neither. */
    val licenceName: String,
    /** The licence description as the font states it (name ID 13), when it states one. */
    val licenceText: String?,
    val licenceUrl: String?,
    val copyright: String?,
    val trademark: String?,
    val reservedFontNames: List<String>,
    val designer: String?,
    val designerUrl: String?,
    val vendorUrl: String?,
    val verdict: ImportVerdict,
    val verdictWords: String,
) {
    /** Whether [ImportVerdict.allowsImport]. */
    val canImport: Boolean get() = verdict.allowsImport

    /** Whether the new project must carry a new family name: the font reserves one. */
    val mustRename: Boolean get() = verdict == ImportVerdict.RENAME
}

/**
 * Audits [info], the font info read from [fileName], per brief §4.5. [licenceFiles] are the texts
 * of any licence files that sit beside a UFO (`OFL.txt`, `LICENSE`); a font file has none.
 *
 * Reading order: the OFL or Apache is recognised in the licence description, its URL, the licence
 * files or the copyright line; failing that, a licence description or URL that withholds
 * modification ("proprietary", "end user licence", "may not be modified", "personal use only") or
 * an `OS/2` embedding flag of *restricted licence* makes the licence [LicenceKind.PROPRIETARY];
 * failing that, any other licence text is [LicenceKind.OTHER], and no text at all is
 * [LicenceKind.NONE]. "All rights reserved" alone is not a marker: the OFL fonts' own copyright
 * lines carry it.
 */
fun auditLicence(
    fileName: String,
    info: UfoFontInfo,
    source: AuditSource,
    licenceFiles: List<String> = emptyList(),
): ImportAudit {
    val licenceText = info.openTypeNameLicense?.takeIf { it.isNotBlank() }
    val licenceUrl = info.openTypeNameLicenseURL?.takeIf { it.isNotBlank() }
    val copyright = info.copyright?.takeIf { it.isNotBlank() }
    val statedBy = listOfNotNull(licenceText, licenceUrl) + licenceFiles.filter { it.isNotBlank() }
    val restricted = hasRestrictedEmbedding(info)

    val (kind, name) = classifyLicence(statedBy, copyright, restricted)
    val reserved = reservedFontNames(copyright, licenceText, *licenceFiles.toTypedArray())
    val verdict =
        when {
            kind == LicenceKind.PROPRIETARY -> ImportVerdict.REFUSED
            reserved.isNotEmpty() -> ImportVerdict.RENAME
            kind == LicenceKind.OFL || kind == LicenceKind.APACHE -> ImportVerdict.FINE
            else -> ImportVerdict.UNCERTAIN
        }
    return ImportAudit(
        fileName = fileName,
        source = source,
        familyName = info.familyName,
        licence = kind,
        licenceName = name,
        licenceText = licenceText,
        licenceUrl = licenceUrl,
        copyright = copyright,
        trademark = info.trademark?.takeIf { it.isNotBlank() },
        reservedFontNames = reserved,
        designer = info.openTypeNameDesigner?.takeIf { it.isNotBlank() },
        designerUrl = info.openTypeNameDesignerURL?.takeIf { it.isNotBlank() },
        vendorUrl = info.openTypeNameManufacturerURL?.takeIf { it.isNotBlank() },
        verdict = verdict,
        verdictWords = verdictWords(verdict, kind, reserved),
    )
}

/** The licence [statedBy] and [copyright] name, with the words the audit shows for it. */
internal fun classifyLicence(
    statedBy: List<String>,
    copyright: String?,
    restrictedEmbedding: Boolean,
): Pair<LicenceKind, String> {
    val evidence = statedBy + listOfNotNull(copyright)
    if (evidence.any { OFL_PATTERN.containsMatchIn(it) }) return LicenceKind.OFL to oflName(evidence)
    if (evidence.any { APACHE_PATTERN.containsMatchIn(it) }) return LicenceKind.APACHE to apacheName(evidence)
    if (statedBy.any { text -> PROPRIETARY_MARKERS.any { it.containsMatchIn(text) } } || (restrictedEmbedding && statedBy.isNotEmpty())) {
        return LicenceKind.PROPRIETARY to "Proprietary"
    }
    if (restrictedEmbedding) return LicenceKind.PROPRIETARY to "Proprietary (restricted embedding)"
    return if (statedBy.isEmpty()) LicenceKind.NONE to "None stated" else LicenceKind.OTHER to "Not recognised"
}

private val OFL_PATTERN =
    Regex(
        """open\s+font\s+licen[sc]e|openfontlicense\.org|scripts\.sil\.org/ofl|\bSIL\s+OFL\b|\bOFL(?:[-\s]?1\.[01])?\b""",
        RegexOption.IGNORE_CASE,
    )

private val APACHE_PATTERN = Regex("""apache\s+licen[sc]e|apache\.org/licenses|\bapache[-\s]?2(?:\.0)?\b""", RegexOption.IGNORE_CASE)

private val PROPRIETARY_MARKERS =
    listOf(
        Regex("""\bproprietary\b""", RegexOption.IGNORE_CASE),
        Regex("""\beula\b""", RegexOption.IGNORE_CASE),
        Regex("""end[-\s]user\s+licen[sc]e""", RegexOption.IGNORE_CASE),
        Regex("""not\s+for\s+(?:re)?distribution""", RegexOption.IGNORE_CASE),
        Regex(
            """(?:may|must|can)\s*(?:not|n't)\s+be\s+(?:modified|altered|changed|copied|redistributed|reproduced)""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""(?:cannot|can't)\s+be\s+(?:modified|altered|changed|copied|redistributed)""", RegexOption.IGNORE_CASE),
        Regex("""personal\s+use\s+only|for\s+personal\s+use|non[-\s]?commercial""", RegexOption.IGNORE_CASE),
    )

private fun oflName(evidence: List<String>): String {
    val version =
        evidence.firstNotNullOfOrNull { text ->
            Regex("""(?:version|v\.?)\s*(1\.[01])""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        }
            ?: evidence.firstNotNullOfOrNull { text ->
                Regex("""\bOFL[-\s]?(1\.[01])\b""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
            }
    return if (version != null) "SIL Open Font License $version" else "SIL Open Font License"
}

private fun apacheName(evidence: List<String>): String {
    val version =
        evidence.firstNotNullOfOrNull { text ->
            Regex("""(?:version|v\.?)\s*(2\.0|1\.1|1\.0)|LICENSE-(2\.0|1\.1|1\.0)""", RegexOption.IGNORE_CASE)
                .find(text)
                ?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
        }
    return if (version != null) "Apache License $version" else "Apache License"
}

/** Whether the font's `OS/2` embedding bits (`openTypeOS2Type`) say *restricted licence embedding*, which forbids modifying or exchanging it. */
private fun hasRestrictedEmbedding(info: UfoFontInfo): Boolean {
    val bits = info.other.firstOrNull { it.first == "openTypeOS2Type" }?.second as? PlistValue.PArray ?: return false
    return bits.items.any { (it as? PlistValue.PInteger)?.value == RESTRICTED_EMBEDDING_BIT }
}

private const val RESTRICTED_EMBEDDING_BIT = 1L

private fun verdictWords(
    verdict: ImportVerdict,
    kind: LicenceKind,
    reserved: List<String>,
): String =
    when (verdict) {
        ImportVerdict.FINE -> {
            if (kind == LicenceKind.OFL) {
                "OFL lets you change and redistribute this font. Keep the copyright line; your changes join its Authors file."
            } else {
                "The Apache licence lets you change and redistribute this font. Keep its copyright and licence notices, and say what you changed."
            }
        }

        ImportVerdict.RENAME -> {
            val quoted = reserved.joinToString(" and ") { "“$it”" }
            val noun = if (reserved.size == 1) "the name" else "the names"
            "This font reserves $noun $quoted. Your version can't use ${if (reserved.size == 1) "it" else "them"}, " +
                "so give it a new name before it is imported. Keep the copyright line."
        }

        ImportVerdict.UNCERTAIN -> {
            if (kind == LicenceKind.NONE) {
                "This font states no licence, so you may not have the right to change or share it. " +
                    "Import it only if it is yours or you have permission."
            } else {
                "Typewright doesn't recognise this licence. Read it before you change or share the font."
            }
        }

        ImportVerdict.REFUSED -> {
            "This font's licence doesn't let you change it, so Import stays off. " +
                "If the font is yours, or its owner gives you permission, ask for a copy under a licence that allows changes."
        }
    }
