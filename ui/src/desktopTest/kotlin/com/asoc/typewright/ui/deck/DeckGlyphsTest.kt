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

    /** The `<path d="...">` and stroke width the board draws for the glyph whose opacity is `{{[flag]}}` (Ship's build and download arrows). */
    private fun boardArrow(flag: String): Pair<String, Float> {
        val line = board.lines().single { "{{$flag}}" in it }
        val d = Regex("""<path d="([^"]+)"""").find(line)!!.groupValues[1]
        val width = Regex("""stroke-width: ([0-9.]+)""").find(line)!!.groupValues[1].toFloat()
        return d to width
    }

    @Test
    fun shipsBuildAndDownloadArrowsAreExactlyTheBoards() {
        assertEquals(boardArrow("rBuild"), DeckGlyphs.BUILD_PATH to DeckGlyphs.ARROW_STROKE)
        assertEquals(boardArrow("rDown"), DeckGlyphs.DOWNLOAD_PATH to DeckGlyphs.ARROW_STROKE)
    }

    @Test
    fun theBuildArrowRisesFromItsBaseAndTheDownloadArrowFallsToItWithTheSameBase() {
        fun heads(path: String): List<Float> =
            PathParser()
                .parsePathString(path)
                .toNodes()
                .filterIsInstance<PathNode.LineTo>()
                .map { it.y }
        // Both arrows have the same base line (y 9), and their tips are at opposite ends of the shaft.
        val build = PathParser().parsePathString(DeckGlyphs.BUILD_PATH).toNodes()
        val download = PathParser().parsePathString(DeckGlyphs.DOWNLOAD_PATH).toNodes()
        assertEquals(build.filterIsInstance<PathNode.HorizontalTo>(), download.filterIsInstance<PathNode.HorizontalTo>(), "the same base")
        assertEquals(-8f, heads(DeckGlyphs.BUILD_PATH).minOrNull(), "the build arrow's tip is at the top")
        assertEquals(5f, heads(DeckGlyphs.DOWNLOAD_PATH).maxOrNull(), "the download arrow's tip is at the bottom")
        assertTrue(DeckGlyphs.build().isEmpty.not() && DeckGlyphs.download().isEmpty.not())
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
