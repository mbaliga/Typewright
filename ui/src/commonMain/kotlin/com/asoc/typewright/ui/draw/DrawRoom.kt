// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.asoc.typewright.brief.planFor
import com.asoc.typewright.brief.resolveBrief
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.outlineBounds
import com.asoc.typewright.project.Ethos
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.puck.PuckUiState
import com.asoc.typewright.ui.puck.Tool
import com.asoc.typewright.ui.sheet.SheetCameraState
import com.asoc.typewright.ui.shell.LocalGlyphNav

/** The glass the Draw room must leave free, in dp: below the header, above the inspector row and the deck, and beside the puck. */
data class DrawInsets(
    val top: Double,
    val bottom: Double,
    val left: Double,
    val right: Double,
)

/**
 * The Draw room's open glyph and everything built on it, or the reason there is none: [session] null
 * is "no project open", [glyphName] null is a project with no glyphs. [controller] exists exactly
 * when there is a glyph to edit.
 */
@Stable
class DrawRoom(
    val session: ProjectSession?,
    val glyphName: String?,
    val controller: DrawController?,
    val directory: GlyphDirectory,
    private val fitNow: () -> Unit,
    private val stepTo: (String) -> Unit,
    private val spaceNow: () -> DrawSpace,
) {
    /** Where the glyph is on screen right now: the map between font units and screen dp, for a host that needs to point at part of it. */
    fun space(): DrawSpace = spaceNow()

    /** Puts the glyph back in the middle of the free area at the size that fits (fit glyph, Ctrl 0, double-tap on empty canvas). */
    fun fit() = fitNow()

    /** Goes to the glyph named [name]. */
    fun goTo(name: String) = stepTo(name)

    /** Goes to the glyph [delta] places after (or before, when negative) the open one, in the font's order, wrapping at the ends. */
    fun stepGlyph(delta: Int) {
        directory.step(glyphName, delta)?.let(stepTo)
    }
}

/**
 * Finds the glyph Draw is on and builds its controller. Which glyph: the one the router holds
 * ([LocalGlyphNav]), if the open project has it; otherwise the first glyph of the brief's drawing
 * order that the project has, otherwise the project's first glyph; with no project open, none (never
 * a fixture). The camera is fitted to the glyph when the glyph changes and when the first real size
 * arrives, and when a person asks; never because the window changed shape.
 */
@Composable
fun rememberDrawRoom(
    cameraState: SheetCameraState,
    canvasSizeDp: Vec2,
    sizeMeasured: Boolean,
    insets: DrawInsets,
    puck: PuckUiState,
    onOpenPalette: (goToGlyph: Boolean) -> Unit,
    onOpenMap: () -> Unit,
    onShowShortcuts: () -> Unit,
    onTick: () -> Unit = {},
    onThump: () -> Unit = {},
): DrawRoom {
    val workspace = LocalProjectWorkspace.current
    val session: ProjectSession? = workspace?.current?.collectAsState()?.value
    val projectState = session?.state?.collectAsState()?.value
    val nav = LocalGlyphNav.current
    var localChoice by remember { mutableStateOf<String?>(null) }

    val master = projectState?.font?.let { font -> font.masters.firstOrNull { it.isDefault } ?: font.masters.firstOrNull() }
    val glyphs = master?.ufo?.glyphs.orEmpty()
    val directory = remember(glyphs) { GlyphDirectory.of(glyphs) }
    val planOrder =
        remember(session, glyphs.isEmpty()) {
            val ethos =
                projectState
                    ?.meta
                    ?.manifest
                    ?.brief
                    ?.ethos ?: Ethos()
            runCatching { planFor(resolveBrief(ethos, null)).flatMap { step -> step.glyphs.map { it.code } } }.getOrDefault(emptyList())
        }
    val wanted = nav?.current ?: localChoice
    val glyphName = if (directory.entry(wanted) != null) wanted else directory.defaultGlyph(planOrder)

    val latestInsets by rememberUpdatedState(insets)
    val latestSize by rememberUpdatedState(canvasSizeDp)
    val latestPalette by rememberUpdatedState(onOpenPalette)
    val latestMap by rememberUpdatedState(onOpenMap)
    val latestShortcuts by rememberUpdatedState(onShowShortcuts)
    val latestTick by rememberUpdatedState(onTick)
    val latestThump by rememberUpdatedState(onThump)
    val latestNav by rememberUpdatedState(nav)
    val latestGlyphName by rememberUpdatedState(glyphName)
    val latestDirectory by rememberUpdatedState(directory)

    val fit: () -> Unit = {
        val name = latestGlyphName
        val live = session?.state?.value
        val current =
            live?.font?.let { f -> f.masters.firstOrNull { it.isDefault } ?: f.masters.firstOrNull() }?.ufo?.glyphs?.firstOrNull {
                it.name ==
                    name
            }
        if (current != null && live != null) {
            val info = (live.font.masters.firstOrNull { it.isDefault } ?: live.font.masters.first()).ufo.fontInfo
            val bounds = current.outlineBounds()
            val (lowest, highest) = DrawFit.heights(bounds?.yMin, bounds?.yMax, drawMetricsFor(info))
            val area =
                FitArea(
                    widthDp = latestSize.x,
                    heightDp = latestSize.y,
                    topDp = latestInsets.top,
                    bottomDp = latestInsets.bottom,
                    leftDp = latestInsets.left,
                    rightDp = latestInsets.right,
                )
            cameraState.snapTo(fitCamera(current.advanceWidth, lowest, highest, area))
        }
    }
    val latestFit by rememberUpdatedState(fit)

    val step: (String) -> Unit = { name -> latestNav?.select?.invoke(name) ?: run { localChoice = name } }

    val host =
        remember(cameraState, puck) {
            object : DrawHost {
                override fun pickTool(tool: Tool) {
                    puck.selectTool(tool)
                }

                override fun fitGlyph() = latestFit()

                override fun actualSize() {
                    val centre = Vec2(latestSize.x / 2.0, (latestInsets.top + (latestSize.y - latestInsets.bottom)) / 2.0)
                    cameraState.snapTo(cameraState.camera.zoomedTo(1.0 / DrawWorld.UNIT_DP, centre))
                }

                override fun stepGlyph(delta: Int) {
                    latestDirectory.step(latestGlyphName, delta)?.let { step(it) }
                }

                override fun openPalette(goToGlyph: Boolean) = latestPalette(goToGlyph)

                override fun openMap() = latestMap()

                override fun showShortcuts() = latestShortcuts()

                override fun tick() = latestTick()

                override fun thump() = latestThump()
            }
        }

    val controller =
        remember(session, glyphName, host) {
            val name = glyphName
            val editor = if (session != null && name != null) DrawEditor.forGlyph(session, name) else null
            if (session != null && editor != null) DrawController(session, editor, host) else null
        }
    DisposableEffect(controller) { onDispose { controller?.end() } }

    // Fit when the glyph changes and when the first real size is known; never on a later resize.
    LaunchedEffect(session, glyphName, sizeMeasured) {
        if (sizeMeasured && controller != null) latestFit()
    }

    return remember(session, glyphName, controller, directory) {
        DrawRoom(session, glyphName, controller, directory, fitNow = {
            latestFit()
        }, stepTo = step, spaceNow = { DrawSpace.of(cameraState.camera) })
    }
}
