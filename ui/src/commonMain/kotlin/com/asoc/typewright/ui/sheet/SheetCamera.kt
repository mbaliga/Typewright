// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.sheet

import com.asoc.typewright.core.geometry.Vec2

/**
 * The sheet's camera (`docs/ARCHITECTURE_REVIEW.md` §4.1 recommendation 1: "A camera in common
 * code. `SheetCamera(offset, zoom)` in commonMain, with `worldToScreen` and `screenToFont`").
 * Every room's grid, metric lines, letters and proofs live in one continuous world coordinate
 * space, in dp; the camera is the one thing that changes when the sheet pans or zooms; the world
 * itself never moves (brief §4 law 8, "the sheet moves, the glass does not").
 *
 * [com.asoc.typewright.core.geometry.Vec2] is reused here for its plain double-precision 2D
 * vector algebra, not for font units: every value in this file is screen or world dp, never the
 * glyph's own font-unit space `core-geometry` defines [Vec2] for (see that module's own
 * `Point.kt` doc). Reusing it avoids a second, duplicate 2D vector type in this module for no
 * reason; `core-geometry` is already a dependency of `ui`.
 *
 * [offset] is the world-space point currently drawn at the screen origin `(0, 0)`; [zoom] is the
 * world-to-screen scale factor (screen dp per world dp; `1.0` is neither zoomed in nor out).
 * [worldToScreen] and [screenToFont] are exact inverses of each other by construction
 * (`SheetCameraTest.worldToScreenAndScreenToFontAreExactInverses`), so the camera is fully
 * described by these two numbers. Nothing here is a Compose type: per the review, a
 * `graphicsLayer`'s `translationX`/`scaleX` reads this camera during layout or draw, never
 * composition, and ink drawn on a `Canvas` (which Compose does not hit-test through any inverse
 * matrix automatically -- "Hit testing on the glass" in the same section) hit-tests a pointer
 * against font-unit ink through [screenToFont] instead.
 */
data class SheetCamera(
    val offset: Vec2,
    val zoom: Double,
) {
    init {
        require(zoom > 0.0) { "zoom must be positive, was $zoom" }
    }

    /** Maps a world-space point (the sheet's own coordinate space) to screen dp. */
    fun worldToScreen(world: Vec2): Vec2 = (world - offset) * zoom

    /**
     * Maps a screen dp point back to world space -- named for its primary caller (`docs/
     * ARCHITECTURE_REVIEW.md` §4.1, "Hit testing on the glass"), not because "world" and "font"
     * are different spaces in this simplified camera model. The exact inverse of [worldToScreen].
     */
    fun screenToFont(screen: Vec2): Vec2 = screen * (1.0 / zoom) + offset

    /** A new camera panned by [screenDelta] (screen dp, e.g. a pointer's raw drag delta). */
    fun pannedBy(screenDelta: Vec2): SheetCamera = copy(offset = offset - screenDelta * (1.0 / zoom))

    /** A new camera with its offset replaced outright, e.g. a room fly-to's target. */
    fun pannedTo(newOffset: Vec2): SheetCamera = copy(offset = newOffset)

    /**
     * A new camera zoomed to [newZoom], keeping the world point currently under [focusScreen]
     * fixed on screen -- the standard "zoom around a point" pinch behaviour.
     */
    fun zoomedTo(
        newZoom: Double,
        focusScreen: Vec2,
    ): SheetCamera {
        require(newZoom > 0.0) { "zoom must be positive, was $newZoom" }
        val worldFocus = screenToFont(focusScreen)
        val newOffset = worldFocus - focusScreen * (1.0 / newZoom)
        return SheetCamera(newOffset, newZoom)
    }

    /** A new camera zoomed by a multiplicative [factor] (`> 1` zooms in), around [focusScreen]. */
    fun zoomedBy(
        factor: Double,
        focusScreen: Vec2,
    ): SheetCamera {
        require(factor > 0.0) { "zoom factor must be positive, was $factor" }
        return zoomedTo(zoom * factor, focusScreen)
    }

    /** [SheetDepth.forZoom] of this camera's own [zoom]. */
    val depth: SheetDepth get() = SheetDepth.forZoom(zoom)

    companion object {
        /** Offset at the world origin, zoom `1.0`: the starting camera before any pan or zoom. */
        val IDENTITY = SheetCamera(Vec2(0.0, 0.0), 1.0)
    }
}

/**
 * "Zoom is depth" (brief §4.2): "Pinch out from a glyph → the word proof → the specimen (the
 * project's home) → the map of rooms. Pinch in reverses." `docs/ARCHITECTURE_REVIEW.md` §4.1
 * finding 29 notes the explorer implements none of this, so these four levels and their zoom
 * boundaries are built fresh here from the brief's own description, not ported from anything.
 *
 * The ordering below is zoomed-in ([GLYPH]) to zoomed-out ([MAP]), matching how [SheetCamera.zoom]
 * itself grows (more screen dp per world dp) as the camera zooms in.
 */
enum class SheetDepth {
    /** The map of rooms: the whole sheet, scaled down (UI_SPEC §3, "Map"). */
    MAP,

    /** The project's home (brief §4.4): the rendered specimen proof, not a file list. */
    SPECIMEN,

    /** A word-length proof of the glyph currently being edited. */
    WORD,

    /** A single glyph, at editing scale. */
    GLYPH,
    ;

    companion object {
        /**
         * Zoom-range boundaries between [SheetDepth] levels. `zoom == 1.0` is the specimen's own
         * natural scale (brief §4.4, the project's home); these are design placeholders --
         * nothing measured, since nothing in the explorer or the brief pins a number here -- and
         * are expected to move once P4b wires real glyph and room sizes against them (CLAUDE.md
         * law 5 "measured, not invented" governs numbers the app judges a *user* by; this is a
         * navigation threshold, not one of those, but it is still not a measured quantity and is
         * documented as such rather than presented as settled).
         */
        const val MAP_MAX_ZOOM: Double = 0.12
        const val SPECIMEN_MAX_ZOOM: Double = 0.35
        const val WORD_MAX_ZOOM: Double = 1.0

        /** Which [SheetDepth] a camera at this [zoom] is at. */
        fun forZoom(zoom: Double): SheetDepth =
            when {
                zoom <= MAP_MAX_ZOOM -> MAP
                zoom <= SPECIMEN_MAX_ZOOM -> SPECIMEN
                zoom <= WORD_MAX_ZOOM -> WORD
                else -> GLYPH
            }
    }
}
