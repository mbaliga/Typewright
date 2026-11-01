// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.trace

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.project.AcceptTrace
import com.asoc.typewright.project.AddGlyph
import com.asoc.typewright.project.Batch
import com.asoc.typewright.project.FontCommand
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.LockState
import com.asoc.typewright.project.Master
import com.asoc.typewright.project.ProjectState
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.SheetLayout
import com.asoc.typewright.ui.capture.TraceSource

/** The decisions around Trace that are not drawing: which lines a point snaps to, what Accept sends the project, and which glyph comes next. */
object TraceFlow {
    /**
     * The heights the Snap stage may move a point onto: the project's own baseline, x-height, cap height, ascender and descender
     * where its font info sets them, and otherwise the guides the template was drawn against. A value the font does not set is
     * never invented.
     */
    fun metricLines(
        info: UfoFontInfo,
        script: PrintScript,
    ): List<Int> {
        val own = listOfNotNull(0, info.xHeight, info.capHeight, info.ascender, info.descender)
        val fromTemplate =
            SheetLayout
                .of(PaperSize.A4, script)
                .guides.lines
                .map { it.y }
        return (if (info.xHeight != null || info.capHeight != null) own else fromTemplate).distinct().sorted()
    }

    /**
     * The words printed on each line of [metricLines]: the project's own names where it sets them ("X-HEIGHT 500"), and two names
     * joined where they share a height ("CAP HEIGHT 700 / ASCENDER 700"); the template's own guide names when the font sets none.
     */
    fun metricLabels(
        info: UfoFontInfo,
        script: PrintScript,
    ): Map<Int, String> {
        val own =
            listOfNotNull(
                0 to "baseline",
                info.xHeight?.let { it to "x-height" },
                info.capHeight?.let { it to "cap height" },
                info.ascender?.let { it to "ascender" },
                info.descender?.let { it to "descender" },
            )
        val labels = LinkedHashMap<Int, String>()
        if (info.xHeight != null || info.capHeight != null) {
            for ((y, name) in own) labels[y] = (labels[y]?.let { "$it / " } ?: "") + "$name $y"
        } else {
            for (line in SheetLayout.of(PaperSize.A4, script).guides.lines) labels[line.y] = line.label
        }
        return labels.mapValues { it.value.uppercase() }
    }

    /**
     * What Accept sends the project for [source]'s glyph: the outline replaced and the glyph approved, as one history entry. A
     * glyph the font does not have yet is added first (with its code point, and the advance the trace suggests), in the same
     * entry. A glyph that already has an outline keeps its advance; Space is where that is decided.
     */
    fun acceptCommand(
        master: Master,
        source: TraceSource,
        outcome: TraceOutcome,
    ): FontCommand {
        val ref = GlyphRef(master.id, source.glyphName)
        val existing = master.ufo.glyphs.firstOrNull { it.name == source.glyphName }
        val accept =
            AcceptTrace(
                ref,
                outcome.contours,
                if (existing == null ||
                    existing.contours.isEmpty()
                ) {
                    outcome.suggestedAdvance
                } else {
                    null
                },
            )
        if (existing != null) return accept
        val added = Glyph(source.glyphName, outcome.suggestedAdvance, emptyList(), unicodes = listOfNotNull(source.codePoint))
        return Batch("Accept trace", listOf(AddGlyph(master.id, added), accept))
    }

    /** Whether [glyph] is locked in [state]'s master [master]. */
    fun isLocked(
        state: ProjectState,
        master: String,
        glyph: String,
    ): Boolean = state.font.locks[GlyphRef(master, glyph)]?.state == LockState.LOCKED

    /**
     * The glyph to trace after [current]: the next captured drawing, in capture order and wrapping round, whose glyph is not
     * locked. Null when every captured glyph is locked (or none is captured).
     */
    fun nextUntraced(
        sources: List<TraceSource>,
        current: String?,
        locked: (String) -> Boolean,
    ): String? {
        val names = sources.map { it.glyphName }.distinct()
        if (names.isEmpty()) return null
        val start = names.indexOf(current).let { if (it < 0) 0 else it + 1 }
        return (names.drop(start) + names.take(start)).firstOrNull { it != current && !locked(it) }
    }

    /** The glyph Trace opens on: [current] if a drawing exists for it, otherwise the first captured glyph that is not locked, otherwise the first. */
    fun opening(
        sources: List<TraceSource>,
        current: String?,
        locked: (String) -> Boolean,
    ): String? {
        val names = sources.map { it.glyphName }.distinct()
        return when {
            current != null && current in names -> current
            else -> names.firstOrNull { !locked(it) } ?: names.firstOrNull()
        }
    }
}
