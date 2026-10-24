// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.ui.draw.DrawSheetRig
import com.asoc.typewright.ui.draw.workspaceWith
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.ProjectFont
import com.asoc.typewright.ui.puck.PuckPinState
import com.asoc.typewright.ui.puck.PuckUiState
import com.asoc.typewright.ui.shell.GlyphNav
import com.asoc.typewright.ui.shell.LocalGlyphNav
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.abs

/**
 * The Space page alone (no sheet under it) over a real project, driven frame by frame with the pointer and
 * keyboard of the Draw rig. [state] is the page's own state; [session] the open project; [opened] counts how often
 * the page asked for the command palette; [selections] is every glyph the page told the router to make current.
 */
internal class SpacePageRig(
    val rig: DrawSheetRig,
    val state: SpaceRoomState,
    val session: ProjectSession,
    val texture: CanvasTexture,
    val widthDp: Double,
    val heightDp: Double,
    val selections: MutableList<String>,
    val palette: IntArray,
) {
    val master = "regular"

    fun font(): ProjectFont =
        ProjectFont(
            session.state.value.font
                .master(master)!!,
        )

    /** Changes the page's state from the test, and lets the page see the change before the next frame. */
    fun change(block: SpaceRoomState.() -> Unit) {
        state.block()
        Snapshot.sendApplyNotifications()
        rig.frames(8)
    }

    /** The control string as the page sets it now. */
    fun string(text: String = ControlStrings.WORKBOOK.first()): SpaceString = SpaceString.set(font(), text)

    /** The page's content column: the whole width on a phone, at most 720 dp centred on a wide screen, less its gutters on each side. */
    private val columnDp: Double get() = minOf(widthDp, CONTENT_MAX)

    /** Where the row's left edge is and how many dp each font unit takes, as the page lays the string out. */
    fun row(text: String = ControlStrings.WORKBOOK.first()): SpaceRowFit {
        val maxEm = if (widthDp >= 900.0) 96.0 else SpaceRowLayout.MAX_EM_DP
        return SpaceRowLayout.fit(string(text), font().unitsPerEm, columnDp - 2 * GUTTER, maxEm)
    }

    /** The x (dp) of the left edge of the row. */
    fun rowLeft(text: String = ControlStrings.WORKBOOK.first()): Double {
        val inset = if (widthDp >= 900.0) 0.0 else (columnDp - 2 * GUTTER - row(text).widthDp) / 2.0
        return (widthDp - columnDp) / 2.0 + GUTTER + inset
    }

    /** The x (dp) of font-unit position [units] along the row. */
    fun xAt(
        units: Double,
        text: String = ControlStrings.WORKBOOK.first(),
    ): Double = rowLeft(text) + units * row(text).dpPerUnit

    /** The y (dp) of the top of the row's ink block, found by looking down the middle of the first letter for the ink colour. */
    fun rowTop(columnDp: Double): Double {
        val image = rig.snapshot("space-probe")
        val pixels = image.toComposeImageBitmap().toPixelMap()
        val ink = texture.ink.toColor()
        val x = (columnDp * rig.density).toInt()
        // Below the header, whose title letters are ink too.
        for (y in (HEADER_DP * rig.density).toInt() until pixels.height) if (near(pixels[x, y], ink)) return y / rig.density.toDouble()
        error("no ink block found in column $columnDp dp")
    }

    /** The pixel colour at ([x], [y]) dp of a fresh frame. */
    fun colourAt(
        x: Double,
        y: Double,
    ): Color {
        val pixels = rig.snapshot("space-probe").toComposeImageBitmap().toPixelMap()
        return pixels[(x * rig.density).toInt(), (y * rig.density).toInt()]
    }

    /** The pixels between [top] and [bottom] dp of a fresh frame, across the whole width, to compare one frame's row with another's. */
    fun region(
        top: Double,
        bottom: Double,
    ): List<Int> {
        val pixels = rig.snapshot("space-probe").toComposeImageBitmap().toPixelMap()
        val from = (top * rig.density).toInt()
        val to = (bottom * rig.density).toInt().coerceAtMost(pixels.height)
        return buildList {
            for (y in from until to) for (x in 0 until pixels.width) add(pixels.buffer[y * pixels.width + x])
        }
    }

    /** The share (0..1) of the pixels in the rectangle from ([x0], [y0]) to ([x1], [y1]) dp that are ink-coloured, in a fresh frame. */
    fun inkShare(
        x0: Double,
        y0: Double,
        x1: Double,
        y1: Double,
    ): Double {
        val pixels = rig.snapshot("space-probe").toComposeImageBitmap().toPixelMap()
        val ink = texture.ink.toColor()
        var total = 0
        var inked = 0
        for (y in (y0 * rig.density).toInt() until (y1 * rig.density).toInt()) {
            for (x in (x0 * rig.density).toInt() until (x1 * rig.density).toInt()) {
                total++
                if (near(pixels[x, y], ink)) inked++
            }
        }
        return if (total == 0) 0.0 else inked.toDouble() / total
    }

    fun isInk(colour: Color): Boolean = near(colour, texture.ink.toColor())

    fun isCanvas(colour: Color): Boolean = near(colour, texture.canvas.toColor(), CANVAS_TOLERANCE)

    private fun near(
        a: Color,
        b: Color,
        tolerance: Float = TOLERANCE,
    ): Boolean = abs(a.red - b.red) < tolerance && abs(a.green - b.green) < tolerance && abs(a.blue - b.blue) < tolerance

    companion object {
        const val GUTTER = 22.0
        private const val HEADER_DP = 64.0
        private const val CONTENT_MAX = 720.0
        private const val TOLERANCE = 0.08f

        // The faint grid under the string is a few per cent of ink over the canvas: still the canvas, to the eye.
        private const val CANVAS_TOLERANCE = 0.12f
    }
}

/** Runs [block] against the Space page over [ufo], with [glyph] in the router's hand. */
internal fun withSpacePage(
    ufo: UfoProject,
    glyph: String = "n",
    width: Int = PHONE_W,
    height: Int = PHONE_H,
    density: Float = PHONE_DENSITY,
    texture: CanvasTexture = CanvasTextures.PAPER,
    block: SpacePageRig.() -> Unit,
) {
    val workspace = workspaceWith(ufo)
    val session = checkNotNull(workspace.current.value)
    val selections = mutableListOf<String>()
    val palette = IntArray(1)
    var pageState: SpaceRoomState? = null
    val widthDp = width / density.toDouble()
    val heightDp = height / density.toDouble()
    ImageComposeScene(width = width, height = height, density = Density(density)) {
        CompositionLocalProvider(
            LocalProjectWorkspace provides workspace,
            LocalGlyphNav provides GlyphNav(current = glyph, open = {}, select = { selections += it }),
        ) {
            SpaceRoom(
                active = true,
                texture = texture,
                onOpenPalette = { palette[0]++ },
                onState = { pageState = it },
            )
        }
    }.use { scene ->
        val rig =
            DrawSheetRig(
                scene,
                density,
                PuckUiState(),
                PuckPinState(PuckPinState.defaultFor(Vec2(widthDp, heightDp))),
                selections,
            ) { null }
        rig.frames(8)
        SpacePageRig(
            rig,
            checkNotNull(pageState) {
                "the page did not report its state"
            },
            session,
            texture,
            widthDp,
            heightDp,
            selections,
            palette,
        ).block()
    }
}
