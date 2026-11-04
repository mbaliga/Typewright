// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.glass

import com.asoc.typewright.ui.draw.GlyphDirectory
import com.asoc.typewright.ui.draw.GlyphMatch
import com.asoc.typewright.ui.draw.MatchKind

/** One row the palette can show: a command, or a glyph to go to. */
sealed interface PaletteItem {
    data class Command(
        val entry: PaletteEntry,
    ) : PaletteItem

    data class GlyphRow(
        val match: GlyphMatch,
    ) : PaletteItem
}

/**
 * The palette's own state, kept apart from Compose so its rules are tests: the typed [query], the
 * [highlighted] row, and whether the person asked to go to a glyph ([goToMode], from the "Go to
 * glyph…" command). The highlight returns to the first row whenever the query changes, so Enter
 * never runs a row that was only highlighted before the list was rewritten under it.
 */
data class PaletteState(
    val query: String = "",
    val highlighted: Int = 0,
    val goToMode: Boolean = false,
) {
    fun withQuery(text: String): PaletteState = if (text == query) this else copy(query = text, highlighted = 0)

    /** The highlight moved by [delta] rows, wrapping within [rowCount]; unchanged when there are none. */
    fun moved(
        delta: Int,
        rowCount: Int,
    ): PaletteState =
        if (rowCount <=
            0
        ) {
            copy(highlighted = 0)
        } else {
            copy(highlighted = ((highlighted + delta) % rowCount + rowCount) % rowCount)
        }

    /** Switches to choosing a glyph, with an empty query. */
    fun enteringGoTo(): PaletteState = PaletteState(goToMode = true)
}

/** Which rows the palette lists, and in what order. */
object PaletteRanking {
    /**
     * Glyph rows come FIRST when the query looks like a request for a glyph (the person chose Go to
     * glyph…, or typed a single character), and after the commands otherwise. Commands match by their
     * label containing the query, case aside. An empty query lists every command; in go-to mode it
     * lists the font's glyphs instead and no commands.
     */
    fun arrange(
        state: PaletteState,
        commands: List<PaletteEntry>,
        directory: GlyphDirectory?,
    ): List<PaletteItem> {
        val query = state.query.trim()
        val glyphMatches: List<GlyphMatch> =
            when {
                directory == null -> emptyList()
                query.isNotEmpty() -> directory.search(query)
                state.goToMode -> directory.glyphs.take(GlyphDirectory.DEFAULT_LIMIT).map { GlyphMatch(it, MatchKind.NAME_PREFIX) }
                else -> emptyList()
            }
        val glyphRows = glyphMatches.map { PaletteItem.GlyphRow(it) }
        val commandRows =
            when {
                state.goToMode && query.isEmpty() -> emptyList()
                else -> commands.filter { query.isEmpty() || it.label.contains(query, ignoreCase = true) }.map { PaletteItem.Command(it) }
            }
        return if (state.goToMode || GlyphDirectory.singleCodePoint(query) != null) glyphRows + commandRows else commandRows + glyphRows
    }
}
