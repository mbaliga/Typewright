// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.parsePlistDict
import com.asoc.typewright.core.font.ufo.readGlyphFileNames
import com.asoc.typewright.core.font.ufo.readUfoProject
import com.asoc.typewright.core.font.ufo.writePlist
import com.asoc.typewright.core.font.ufo.writeUfoProject
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.degreeElevatedToCubic
import com.asoc.typewright.core.geometry.enforceContourDirections

/**
 * One UFO as fontmake is to read it: [files] by path relative to the `.ufo` folder, and the names of
 * the [elevatedGlyphs] whose outlines were rewritten in this copy (none when the project was cubic).
 */
internal class FontmakeUfo(
    val files: Map<String, ByteArray>,
    val elevatedGlyphs: List<String>,
)

/**
 * The copy of a project's UFO that is handed to fontmake.
 *
 * A font opened from a TrueType file keeps its quadratic curves (`qcurve`) in the project, exactly as
 * drawn. fontmake cannot clean overlaps from a `qcurve` source, so in the copy every quadratic
 * contour is rewritten as the cubic contour that traces the same curve (each quadratic's controls
 * move two thirds of the way to its off-curve point; coordinates are rounded to whole units, half a
 * unit at most), with the winding of outer and inner contours set to the cubic convention. The
 * compiler converts back to quadratic curves on its own. The project itself is never touched:
 * [ufoFiles] is only read, and the copy has the same glyphs, advances, code points, anchors,
 * kerning and file names. A UFO with no quadratic contour is returned as it is.
 *
 * Throws [IllegalArgumentException] when core-font cannot read the UFO.
 */
internal fun prepareUfoForFontmake(ufoFiles: Map<String, ByteArray>): FontmakeUfo {
    val texts =
        ufoFiles
            .filterKeys { path -> TEXT_SUFFIXES.any { path.endsWith(it) } }
            .mapValues { (_, bytes) -> bytes.decodeToString() }
    val project =
        try {
            readUfoProject(texts)
        } catch (refusal: IllegalArgumentException) {
            throw refusal
        } catch (broken: Exception) {
            // An XML parser reports a file that is not a plist in its own exception; callers get the one documented here.
            throw IllegalArgumentException("a file of the UFO could not be read: ${broken.message ?: broken}", broken)
        }
    val quadratic = project.glyphs.filter { glyph -> glyph.contours.any { it.format == CurveFormat.QUADRATIC } }
    if (quadratic.isEmpty()) return FontmakeUfo(ufoFiles, emptyList())

    val names = quadratic.map { it.name }.toSet()
    val elevated =
        project.copy(
            glyphs =
                project.glyphs.map { glyph ->
                    if (glyph.name !in names) {
                        glyph
                    } else {
                        glyph.copy(contours = enforceContourDirections(glyph.contours.map { it.degreeElevatedToCubic() }))
                    }
                },
            // Straight-sided contours are read back as the font's default format; the copy is cubic throughout.
            lib = project.lib.copy(lineContourFormat = CurveFormat.CUBIC),
        )
    val rewritten = writeUfoProject(elevated, fileNameHints = readGlyphFileNames(texts))
    val files = LinkedHashMap(ufoFiles)
    for ((path, text) in rewritten) files[path] = text.encodeToByteArray()
    return FontmakeUfo(files, quadratic.map { it.name })
}

private val TEXT_SUFFIXES = listOf(".plist", ".glif", ".fea")

/**
 * Whether every character of [name] is a printable ASCII one: the only file names every computer's file-name
 * encoding can hold, whatever language it was set up for.
 */
internal fun isPlainAscii(name: String): Boolean = name.all { it.code in ASCII_FIRST..ASCII_LAST }

/**
 * [files] with every glyph file named in plain ASCII, for the copy fontmake reads. A project's glyph files are named
 * after its glyphs (`é.glif`, `あ.glif`), and a Java that is run in a locale whose file-name encoding is ASCII writes
 * such a name as question marks, so different glyphs would share a file and `contents.plist` would point at files that
 * are not there. Each non-ASCII glyph file is renamed `typewright-glyph-1.glif`, `…-2.glif` and so on (skipping any
 * name already taken, compared without regard to case), and its layer's `contents.plist` is rewritten to match. Glyph
 * names, which are inside the files, are untouched, so the compiled font is the same. Files whose names are already
 * ASCII, and a UFO with no such file, are returned as they are.
 */
internal fun withAsciiFileNames(files: Map<String, ByteArray>): Map<String, ByteArray> {
    val result = LinkedHashMap(files)
    val layerContents = files.keys.filter { path -> path.endsWith("/$CONTENTS_FILE") && path.count { it == '/' } == 1 }
    for (contentsPath in layerContents) {
        val directory = contentsPath.substringBefore('/')
        val contents = runCatching { parsePlistDict(files.getValue(contentsPath).decodeToString()) }.getOrNull() ?: continue
        val taken = contents.entries.mapNotNull { (it.second as? PlistValue.PString)?.value?.lowercase() }.toMutableSet()
        var next = 0
        var changed = false
        val entries =
            contents.entries.map { (glyph, value) ->
                val file = (value as? PlistValue.PString)?.value
                if (file == null || isPlainAscii(file)) return@map glyph to value
                var safe: String
                do {
                    next++
                    safe = "$ASCII_GLYPH_FILE_PREFIX$next.glif"
                } while (safe.lowercase() in taken)
                taken += safe.lowercase()
                result.remove("$directory/$file")?.let { result["$directory/$safe"] = it }
                changed = true
                glyph to PlistValue.PString(safe)
            }
        if (changed) result[contentsPath] = writePlist(PlistValue.PDict(entries)).encodeToByteArray()
    }
    return result
}

private const val CONTENTS_FILE = "contents.plist"
private const val ASCII_GLYPH_FILE_PREFIX = "typewright-glyph-"
private const val ASCII_FIRST = 0x20
private const val ASCII_LAST = 0x7E
