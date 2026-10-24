// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.report

import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.project.ApprovalOrigin
import com.asoc.typewright.project.BriefSource
import com.asoc.typewright.project.GlyphLock
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.Master

/**
 * What the project records of where [master]'s outlines began, for the Source-is-a-fossil check, or null
 * when it records nothing that bears on it.
 *
 * A project records two things that bear on it: that it began from a font the person brought
 * ([source] is [BriefSource.FONT]) and, per glyph, who approved the outline ([ApprovalOrigin.IMPORT] for the
 * import itself). It does not record whether that font was a compiled file or a UFO folder, so this reads
 * the one trace a compiled TrueType font leaves in the outlines it was imported with: quadratic curves,
 * which a source drawn in Typewright (cubic) never has. That makes [ImportProvenance.fromCompiledFont] our
 * heuristic, and the check that uses it says so. A glyph is "still as imported" when its approval came from
 * the import and its outline is the one that was approved.
 */
fun importProvenanceOf(
    master: Master,
    locks: Map<GlyphRef, GlyphLock>,
    source: BriefSource,
    sourceFileName: String? = null,
): ImportProvenance? {
    if (source != BriefSource.FONT) return null
    val outlined = master.ufo.glyphs.filter { it.contours.isNotEmpty() }
    val imported =
        outlined.mapNotNull { glyph ->
            locks[GlyphRef(master.id, glyph.name)]?.takeIf { it.origin == ApprovalOrigin.IMPORT }?.let { glyph to it }
        }
    val compiled = imported.any { (_, lock) -> lock.approved.contours.any { it.format == CurveFormat.QUADRATIC } }
    val untouched = imported.count { (glyph, lock) -> glyph.contours == lock.approved.contours }
    return ImportProvenance(
        fromCompiledFont = compiled,
        untouchedGlyphs = untouched,
        outlineGlyphs = outlined.size,
        sourceFileName = sourceFileName,
    )
}

/** The family and style names of every master but [defaultMaster], for the Names check's uniqueness test. */
fun otherMasterNames(
    masters: List<Master>,
    defaultMaster: Master,
): List<MasterNames> =
    masters
        .filter { it.id != defaultMaster.id }
        .map { MasterNames(it.ufo.fontInfo.familyName, it.ufo.fontInfo.styleName) }
