// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.deck

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The deck's Run again arrow is the board's, not an improvised one: its path data is read back out of ui/v1-screens/Deck.dc.html. */
class DeckGlyphsTest {
    private val board: String =
        File("v1-screens/Deck.dc.html").also { assertTrue(it.isFile, "run from the ui module: ${it.absolutePath}") }.readText()

    /** The `<path d="...">` the board draws for the refresh glyph: the one whose opacity is `{{rRefresh}}`. */
    private fun boardPath(): Pair<String, Float> {
        val line = board.lines().single { "{{rRefresh}}" in it }
        val d = Regex("""<path d="([^"]+)"""").find(line)!!.groupValues[1]
        val width = Regex("""stroke-width: ([0-9.]+)""").find(line)!!.groupValues[1].toFloat()
        return d to width
    }

    @Test
    fun theRefreshPathAndItsWidthAreExactlyTheBoards() {
        val (d, width) = boardPath()
        assertEquals(d, DeckGlyphs.REFRESH_PATH)
        assertEquals(width, DeckGlyphs.REFRESH_STROKE)
    }

    @Test
    fun theParsedPathHasTheArcAndTheArrowheadTheBoardDraws() {
        val nodes: List<PathNode> = PathParser().parsePathString(DeckGlyphs.REFRESH_PATH).toNodes()
        // M 7 -2, A 7.5 7.5 0 1 0 5.4 5.3, M 7.8 -8.5, L 7.4 -1.6, L 1 -2.8.
        assertEquals(5, nodes.size)
        val arc = nodes[1] as PathNode.ArcTo
        assertEquals(7.5f, arc.horizontalEllipseRadius)
        assertTrue(arc.isMoreThanHalf, "the arc is the long way round")
        assertEquals(false, arc.isPositiveArc, "and runs anticlockwise, as the board's sweep flag 0 says")
        assertEquals(5.4f, arc.arcStartX)
        assertEquals(5.3f, arc.arcStartY)
        assertTrue(DeckGlyphs.refresh().isEmpty.not(), "the path is something that can be drawn")
    }
}
