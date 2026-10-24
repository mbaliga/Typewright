// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import com.asoc.typewright.core.font.ufo.readGlyphFileNames
import com.asoc.typewright.core.font.ufo.readUfoProject
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
    val project = readUfoProject(texts)
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
