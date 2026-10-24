// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.imports

import com.asoc.typewright.core.font.sfnt.SFNT_VERSION_TRUETYPE
import com.asoc.typewright.core.font.sfnt.SFNT_VERSION_TRUE_TAG
import com.asoc.typewright.core.font.sfnt.SfntFont
import com.asoc.typewright.core.font.sfnt.readSfntFont
import com.asoc.typewright.core.font.sfnt.toUfoProject
import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.font.ufo.parsePlist
import com.asoc.typewright.core.font.ufo.readUfoProject
import com.asoc.typewright.core.font.ufo.writeUfoProject
import com.asoc.typewright.core.geometry.Glyph

/**
 * A font that has been read and audited and is ready to become a project, or to be adopted as one.
 * [ufo] is the font as the project will hold it, [audit] the licence verdict (never
 * [ImportVerdict.REFUSED] here: a refused font is an [ImportReview.Refused]), [report] what was
 * found, and [ufoFolder] the name of the `.ufo` folder when the font is a UFO that already exists
 * on disk and would be adopted where it lies.
 */
data class ImportCandidate(
    val fileName: String,
    val ufo: UfoProject,
    val audit: ImportAudit,
    val report: ImportReport,
    val ufoFolder: String? = null,
    val siblings: UfoSiblings? = null,
)

/**
 * What lies beside a UFO that is to be adopted in place: the folder it sits in, the other `.ufo`
 * folders there and the licence files. Adopting writes `typewright.json` and a few folders into
 * [parentPath] and nothing else, so the audit says exactly that.
 */
data class UfoSiblings(
    val parentPath: String,
    val otherUfos: List<String>,
)

/** The outcome of reading and auditing one font; it never throws. */
sealed interface ImportReview {
    val fileName: String

    /** The font can be imported (with the audit's warnings, if any). */
    data class Ready(
        val candidate: ImportCandidate,
    ) : ImportReview {
        override val fileName: String get() = candidate.fileName
    }

    /** The font's licence withholds modification; nothing was read beyond the audit. */
    data class Refused(
        val audit: ImportAudit,
    ) : ImportReview {
        override val fileName: String get() = audit.fileName
    }

    /** The file cannot be opened: not a font, an unsupported kind, damaged. [message] says why in plain words. */
    data class Unreadable(
        override val fileName: String,
        val message: String,
    ) : ImportReview
}

/**
 * Reads [bytes] as the font file [fileName] and audits it. A TrueType-outline font (`.ttf`, or an
 * `.otf` with `glyf` outlines) becomes a [UfoProject] with its quadratic curves kept exactly as
 * drawn; CFF outlines, web fonts and collections are refused with a plain message, because core-font
 * does not read them yet; a licence that withholds modification is [ImportReview.Refused]. Anything
 * the reader throws on a damaged or hostile font (a composite glyph that includes itself, a table
 * that overruns the file) comes back as [ImportReview.Unreadable], never as an exception.
 */
fun reviewFontFile(
    fileName: String,
    bytes: ByteArray,
): ImportReview {
    unreadableKind(fileName, bytes)?.let { return it }
    return try {
        val font = readSfntFont(bytes)
        val converted = font.toUfoProject()
        val project = nameGlyphs(converted, font)
        val notes = fontFileNotes(font, project.renamed)
        val audit = auditLicence(fileName, project.ufo.fontInfo, AuditSource.FONT_FILE)
        // The project must be writable as UFO files; finding out now keeps a bad font from half-creating a project.
        writeUfoProject(project.ufo)
        reviewed(fileName, project.ufo, audit, reportFor(ImportFormat.FONT_FILE, project.ufo, notes), null, null)
    } catch (e: Exception) {
        ImportReview.Unreadable(fileName, damagedMessage(e))
    }
}

/**
 * Reads the UFO folder [folderName] (its text files by path relative to the folder, [files]) and
 * audits it. [otherEntries] are the paths of everything else in it (images, data), [siblings]
 * what lies beside it, and [licenceFiles] the texts of any `OFL.txt` or `LICENSE` there. A UFO that
 * is not UFO 3, or that cannot be read, is [ImportReview.Unreadable].
 */
fun reviewUfoFolder(
    folderName: String,
    files: Map<String, String>,
    otherEntries: List<String> = emptyList(),
    siblings: UfoSiblings? = null,
    licenceFiles: List<String> = emptyList(),
): ImportReview =
    try {
        val ufo = readUfoProject(files)
        val audit = auditLicence(folderName, ufo.fontInfo, AuditSource.UFO, licenceFiles)
        writeUfoProject(ufo)
        reviewed(folderName, ufo, audit, reportFor(ImportFormat.UFO, ufo, ufoNotes(files, otherEntries)), folderName, siblings)
    } catch (e: Exception) {
        ImportReview.Unreadable(folderName, damagedMessage(e))
    }

private fun reviewed(
    fileName: String,
    ufo: UfoProject,
    audit: ImportAudit,
    report: ImportReport,
    ufoFolder: String?,
    siblings: UfoSiblings?,
): ImportReview =
    if (!audit.canImport) {
        ImportReview.Refused(audit)
    } else {
        ImportReview.Ready(ImportCandidate(fileName, ufo, audit, report, ufoFolder, siblings))
    }

private fun damagedMessage(e: Exception): String {
    val why = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "unknown error"
    return "Typewright couldn't read this file: $why. Nothing was imported."
}

/** A refusal for a file that is not a TrueType-outline font Typewright can read, or null when it might be one. */
private fun unreadableKind(
    fileName: String,
    bytes: ByteArray,
): ImportReview.Unreadable? {
    if (bytes.size < SFNT_HEADER_BYTES) {
        return ImportReview.Unreadable(fileName, "This file is too small to be a font. Nothing was imported.")
    }
    val tag = bytes.copyOfRange(0, 4).decodeToString()
    return when (tag) {
        "wOFF", "wOF2" -> {
            ImportReview.Unreadable(
                fileName,
                "This is a web font (WOFF). Typewright reads .ttf files, so open the .ttf it was made from. Nothing was imported.",
            )
        }

        "ttcf" -> {
            ImportReview.Unreadable(
                fileName,
                "This is a font collection (.ttc) holding several fonts. Typewright reads one font per file, " +
                    "so split it into .ttf files first. Nothing was imported.",
            )
        }

        "OTTO" -> {
            ImportReview.Unreadable(
                fileName,
                "This font has PostScript (CFF) outlines, and reading those is not built yet, so Typewright can't open it. " +
                    "A TrueType (.ttf) version of the same font would open. Nothing was imported.",
            )
        }

        else -> {
            val version =
                ((bytes[0].toLong() and 0xFF) shl 24) or ((bytes[1].toLong() and 0xFF) shl 16) or
                    ((bytes[2].toLong() and 0xFF) shl 8) or (bytes[3].toLong() and 0xFF)
            if (version == SFNT_VERSION_TRUETYPE || version == SFNT_VERSION_TRUE_TAG) {
                null
            } else {
                ImportReview.Unreadable(fileName, "This isn't a TrueType font file, so Typewright can't open it. Nothing was imported.")
            }
        }
    }
}

private const val SFNT_HEADER_BYTES = 12

/** What naming the glyphs did: [ufo] with every glyph named, and how many had to be renamed ([renamed]). */
private class NamedGlyphs(
    val ufo: UfoProject,
    val renamed: Int,
)

/**
 * Gives every glyph of [project] a usable, unique name. A font whose `post` table carries no names
 * (version 3) has only the placeholders `glyphN`; those become the production names a compiler and
 * the node-economy corpus both use: the letter itself for A-Z and a-z, `uniXXXX` for any other
 * mapped character, `.notdef` for glyph 0. A name that two glyphs share (the UFO file keys glyphs by
 * name) gets a numeric suffix on the second.
 */
private fun nameGlyphs(
    project: UfoProject,
    font: SfntFont,
): NamedGlyphs {
    val synthesized = font.post?.glyphNames == null
    val used = HashSet<String>()
    var renamed = 0
    val glyphs =
        project.glyphs.mapIndexed { index, glyph ->
            var name = glyph.name
            if (synthesized) {
                val production = productionName(index, glyph)
                if (production != null && production != name) name = production
            }
            if (!used.add(name)) {
                var n = 1
                while (!used.add("$name.$n")) n++
                name = "$name.$n"
            }
            if (name != glyph.name) renamed++
            if (name == glyph.name) glyph else glyph.copy(name = name)
        }
    return NamedGlyphs(project.copy(glyphs = glyphs), renamed)
}

private fun productionName(
    index: Int,
    glyph: Glyph,
): String? {
    if (index == 0) return ".notdef"
    val cp = glyph.unicodes.firstOrNull() ?: return null
    return when {
        cp in 0x41..0x5A || cp in 0x61..0x7A -> cp.toChar().toString()
        cp == 0x20 -> "space"
        cp <= 0xFFFF -> "uni" + cp.toString(16).uppercase().padStart(4, '0')
        else -> "u" + cp.toString(16).uppercase()
    }
}

private fun fontFileNotes(
    font: SfntFont,
    renamedGlyphs: Int,
): List<String> =
    buildList {
        if ("fvar" in font.tableTags) add("This is a variable font: only its default instance is imported.")
        if (font.compositeGlyphCount > 0) {
            add("${font.compositeGlyphCount} glyphs are built from other glyphs in the file; each is imported as a plain outline.")
        }
        if (font.tableTags.any { it in HINTING_TABLES }) add("Hinting instructions are not carried over; a compiler can rebuild them.")
        if (font.tableTags.any { it in LAYOUT_TABLES }) {
            add("Kerning and layout features (GPOS, GSUB) are not read yet, so the project starts with none.")
        }
        if (font.tableTags.any { it in PICTURE_TABLES }) add("Colour, SVG and bitmap glyphs are not read.")
        if (renamedGlyphs > 0) add("$renamedGlyphs glyphs were renamed: the font had no names for them, or gave two glyphs one name.")
    }

private val POINT_NAME = Regex("""<point\s[^>]*\bname=""")

private val HINTING_TABLES = setOf("fpgm", "prep", "cvt ", "gasp")
private val LAYOUT_TABLES = setOf("GPOS", "GSUB", "kern")
private val PICTURE_TABLES = setOf("COLR", "CPAL", "SVG ", "CBDT", "CBLC", "sbix", "EBDT", "EBLC")

/**
 * What adopting a UFO leaves out or changes: the default layer is the only one read; a glyph
 * edited in Typewright is rewritten without the point names, notes, images and lib data the
 * original file held, while glyphs left alone keep their file exactly as it is.
 */
private fun ufoNotes(
    files: Map<String, String>,
    otherEntries: List<String>,
): List<String> =
    buildList {
        val layerNames = otherLayerNames(files["layercontents.plist"])
        if (layerNames.isNotEmpty()) {
            add("Only the default layer is read. Other layers (${layerNames.joinToString(", ")}) are left in the folder, untouched.")
        }
        val glifs = files.filterKeys { it.endsWith(".glif") && it.startsWith("glyphs/") }.values
        val carrying = glifs.count { it.contains("<note") || it.contains("<image") || POINT_NAME.containsMatchIn(it) }
        val withLib = glifs.count { it.contains("<lib>") && !it.contains("com.asoc.typewright.lineContourFormat") }
        val rewritten = carrying + withLib
        if (rewritten > 0) {
            add(
                "A glyph you edit is saved without its point names, notes, images and lib data " +
                    "(${if (carrying > 0) "$carrying glyphs carry some" else "$withLib glyphs carry lib data"}). Glyphs you leave alone are not rewritten.",
            )
        }
        if (otherEntries.any { it.startsWith("images/") || it.startsWith("data/") }) {
            add("The images and data folders are left in place and not read.")
        }
    }

private fun otherLayerNames(layerContents: String?): List<String> {
    if (layerContents == null) return emptyList()
    val root = runCatching { parsePlist(layerContents) }.getOrNull() as? PlistValue.PArray ?: return emptyList()
    return root.items
        .mapNotNull { entry -> ((entry as? PlistValue.PArray)?.items?.firstOrNull() as? PlistValue.PString)?.value }
        .filter { it != "public.default" }
}
