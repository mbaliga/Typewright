// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.deck

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser

/**
 * The marks drawn inside the deck's filled right circle that are paths rather than a few straight lines, as ui/v1-screens/Deck.dc.html draws
 * them: the SVG path data exactly as the board has it, in the circle's own units (the origin at its centre, 26 units to its edge).
 */
internal object DeckGlyphs {
    /** Run again's arrow: an arc of radius 7.5 that stops short of closing, and a head at the open end. */
    const val REFRESH_PATH: String = "M 7 -2 A 7.5 7.5 0 1 0 5.4 5.3 M 7.8 -8.5 L 7.4 -1.6 L 1 -2.8"

    /** The width the board strokes [REFRESH_PATH] with. */
    const val REFRESH_STROKE: Float = 2.1f

    /** [REFRESH_PATH] as a path about the origin. */
    fun refresh(): Path = PathParser().parsePathString(REFRESH_PATH).toPath()

    /** Ship's build: an arrow rising from a base line. The board's own path data (`rBuild` in Deck.dc.html). */
    const val BUILD_PATH: String = "M 0 5 V -8 M -6 -2 L 0 -8 L 6 -2 M -8 9 H 8"

    /** Ship's export, where only a file can be offered: an arrow falling to a base line (`rDown` in Deck.dc.html). */
    const val DOWNLOAD_PATH: String = "M 0 -8 V 5 M -6 -1 L 0 5 L 6 -1 M -8 9 H 8"

    /** The width the board strokes [BUILD_PATH] and [DOWNLOAD_PATH] with. */
    const val ARROW_STROKE: Float = 2.2f

    /** [BUILD_PATH] as a path about the origin. */
    fun build(): Path = PathParser().parsePathString(BUILD_PATH).toPath()

    /** [DOWNLOAD_PATH] as a path about the origin. */
    fun download(): Path = PathParser().parsePathString(DOWNLOAD_PATH).toPath()
}
