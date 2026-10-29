// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.glass

import com.asoc.typewright.ui.draw.GlyphDirectory
import com.asoc.typewright.ui.draw.GlyphEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PaletteModelTest {
    private val directory =
        GlyphDirectory(
            listOf(
                GlyphEntry("H", listOf(0x48)),
                GlyphEntry("n", listOf(0x6E)),
                GlyphEntry("o", listOf(0x6F)),
            ),
        )
    private val commands =
        listOf(
            entry("add-extremes", "Add extremes"),
            entry("reverse", "Reverse direction"),
            entry("go-to", "Go to glyph…"),
        )

    private fun entry(
        id: String,
        label: String,
    ) = PaletteEntry(id = id, label = label, description = "", run = { label })

    private fun labels(rows: List<PaletteItem>) =
        rows.map {
            when (it) {
                is PaletteItem.Command -> "cmd:" + it.entry.id
                is PaletteItem.GlyphRow -> "glyph:" + it.match.entry.name
            }
        }

    @Test
    fun anEmptyQueryListsEveryCommandAndNoGlyphs() {
        assertEquals(
            listOf("cmd:add-extremes", "cmd:reverse", "cmd:go-to"),
            labels(PaletteRanking.arrange(PaletteState(), commands, directory)),
        )
    }

    @Test
    fun aSingleCharacterQueryListsTheGlyphFirst() {
        val rows = PaletteRanking.arrange(PaletteState(query = "o"), commands, directory)
        assertEquals("glyph:o", labels(rows).first())
        // Commands that contain an "o" follow it.
        assertTrue(labels(rows).contains("cmd:go-to"))
    }

    @Test
    fun aWordQueryListsMatchingCommandsFirstAndGlyphsAfter() {
        val rows = PaletteRanking.arrange(PaletteState(query = "rev"), commands, directory)
        assertEquals("cmd:reverse", labels(rows).first())
    }

    @Test
    fun goToModeListsGlyphsFirstAndOnlyGlyphsWhenTheQueryIsEmpty() {
        val state = PaletteState().enteringGoTo()
        assertEquals(listOf("glyph:H", "glyph:n", "glyph:o"), labels(PaletteRanking.arrange(state, commands, directory)))
        val typed = PaletteRanking.arrange(state.withQuery("n"), commands, directory)
        assertEquals("glyph:n", labels(typed).first())
    }

    @Test
    fun theHighlightGoesBackToTheFirstRowWhenTheQueryChanges() {
        val moved = PaletteState(query = "re").moved(1, rowCount = 3)
        assertEquals(1, moved.highlighted)
        assertEquals(0, moved.withQuery("rev").highlighted)
        assertEquals(1, moved.withQuery("re").highlighted, "the same text is not a change")
    }

    @Test
    fun theHighlightWrapsAndIsZeroWhenThereAreNoRows() {
        assertEquals(0, PaletteState(highlighted = 2).moved(1, 3).highlighted)
        assertEquals(2, PaletteState(highlighted = 0).moved(-1, 3).highlighted)
        assertEquals(0, PaletteState(highlighted = 4).moved(1, 0).highlighted)
    }

    @Test
    fun enteringGoToClearsTheQueryAndTheHighlight() {
        val state = PaletteState(query = "xyz", highlighted = 2).enteringGoTo()
        assertEquals("", state.query)
        assertEquals(0, state.highlighted)
        assertTrue(state.goToMode)
    }

    @Test
    fun aGlyphRowCarriesTheGlyphToGoTo() {
        val rows = PaletteRanking.arrange(PaletteState(query = "H"), commands, directory)
        val first = rows.first()
        assertIs<PaletteItem.GlyphRow>(first)
        assertEquals("H", first.match.entry.name)
    }

    @Test
    fun withNoDirectoryNoGlyphRowsAppear() {
        assertEquals(listOf("cmd:go-to"), labels(PaletteRanking.arrange(PaletteState(query = "go"), commands, null)))
    }
}
