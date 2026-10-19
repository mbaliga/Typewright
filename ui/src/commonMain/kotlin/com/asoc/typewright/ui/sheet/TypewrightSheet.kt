// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.ui.deck.ControlDeck
import com.asoc.typewright.ui.deck.DeckLeft
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.DeckToggle
import com.asoc.typewright.ui.glass.CommandPalette
import com.asoc.typewright.ui.glass.InspectorField
import com.asoc.typewright.ui.glass.InspectorRow
import com.asoc.typewright.ui.glass.commandPaletteShortcut
import com.asoc.typewright.ui.glass.constructInspectorFields
import com.asoc.typewright.ui.glass.projectStatusText
import com.asoc.typewright.ui.puck.Puck
import com.asoc.typewright.ui.puck.PuckGestureConfig
import com.asoc.typewright.ui.puck.PuckUiState
import com.asoc.typewright.ui.puck.Tool
import com.asoc.typewright.ui.puck.rememberPuckPinState
import com.asoc.typewright.ui.puck.rememberPuckUiState
import com.asoc.typewright.ui.puck.viewCentreFontUnits
import com.asoc.typewright.ui.shell.LocalSectionNav
import com.asoc.typewright.ui.shell.PageMenu
import com.asoc.typewright.ui.shell.PageMenuItem
import com.asoc.typewright.ui.shell.ScreenHeader
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.toColor
import com.asoc.typewright.ui.tokens.toFixedString
import kotlinx.coroutines.launch

/**
 * The one-sheet UI (task P4b): Draw, Space and Learn as three regions of one continuous camera
 * space (`docs/ARCHITECTURE_REVIEW.md` §4.1/§5 finding 25), assembled exactly per the review's
 * recommendation 2: `Box { WorldPass(lines); Bloom(); WorldPass(ink); Glass() }` -- two world-space
 * passes so the bloom can sit between them in z-order without living inside either transformed
 * layer, glass drawn last so it both paints on top and wins hit-testing over the world beneath it
 * (this file's own "Hit testing" note below).
 *
 * **Hit testing** (`docs/ARCHITECTURE_REVIEW.md` §4.1 "Hit testing on the glass"): Compose hit-
 * tests a `Box`'s children top-down and stops at the first one that consumes a pointer, so simply
 * adding glass children *after* the two world passes already gives them priority, with no
 * `pointer-events:none` equivalent needed on the world passes themselves (they carry no pointer
 * modifier of their own beyond the one root arbiter on this outer `Box`, so they are "transparent"
 * to hit-testing exactly as the review describes).
 */
@Composable
fun TypewrightSheet(
    modifier: Modifier = Modifier,
    initialRoom: Room = Room.DRAW,
    /** Which of the sheet's two regions the router is on; Draw and Space are one sheet, so a change pans it. */
    section: DeckSection = DeckSection.DRAW,
    /** Called when a two-finger swipe or the arrow keys move the sheet to the other region, so the deck follows. */
    onSectionChanged: (DeckSection) -> Unit = {},
    /** False while another screen covers the sheet: it keeps its camera and tool but draws no deck. */
    deckVisible: Boolean = true,
    initialTexture: CanvasTexture = CanvasTextures.DEFAULT,
    initialGestureConfig: PuckGestureConfig = PuckGestureConfig(),
    /**
     * Exposed (rather than created unconditionally inside this function) so a test can pre-seed
     * the puck's gesture state -- e.g. into [com.asoc.typewright.ui.puck.PuckGestureState.
     * RadialOpen] -- and see the radial dial rendered in the sheet's full context on the very
     * first frame, with no simulated pointer sequence needed (`SheetScreenshotTest`). Ordinary
     * callers (the real apps) just take the default.
     */
    puckState: PuckUiState = rememberPuckUiState(),
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // Not yet mutable at runtime: this task has no texture-switch control (UI_SPEC names one but
    // it lives on a screen this task does not build). `initialTexture` is passed straight through
    // so `SheetScreenshotTest` can render each of the four textures task item 9 asks for.
    val texture = initialTexture
    val cameraState =
        rememberSheetCameraState(
            initial =
                SheetCamera(
                    // y = -DEFAULT_BASELINE_SCREEN_Y_DP, not 0: `GridAndMetrics.kt`'s
                    // `worldToScreenFontUp` puts font-unit y = 0 (the baseline) at screen y =
                    // -offset.y, so an offset.y of 0 would pin the baseline to the very top
                    // pixel row. This starting offset instead puts the baseline a sensible way
                    // down a phone-sized canvas, matching roughly where the explorer's own
                    // baseline sits (`draw-wide.png`) -- see that function's own KDoc for why
                    // cap-height still falls outside the default view at zoom 1 (a documented
                    // simplification: real font-unit-to-dp scaling is P5b's job).
                    offset = initialRoom.worldBounds().let { Vec2(it.left, -DEFAULT_BASELINE_SCREEN_Y_DP) },
                    zoom = 1.0,
                ),
        )
    // A reasonable phone-sized guess until the first layout pass reports the real size (task
    // P4b's own screenshot harness always renders at a known, fixed size, so this only matters
    // for the very first frame in a host that measures asynchronously).
    var canvasSizeDp by remember { mutableStateOf(Vec2(360.0, 780.0)) }
    // P5b: the desktop command palette's own open/closed state (UI_SPEC §4, Ctrl/⌘ K) -- see
    // CommandPalette.kt's own KDoc for why the shortcut lives in commonMain with no desktop gate.
    var paletteOpen by remember { mutableStateOf(false) }
    // C4: construction geometry is drawn at full strength; this switch only turns it off by choice. On by default.
    var construction by remember { mutableStateOf(true) }
    LaunchedEffect(section) {
        val target = if (section == DeckSection.SPACE) Room.SPACE else Room.DRAW
        if (cameraState.currentRoom != target && (section == DeckSection.DRAW || section == DeckSection.SPACE)) cameraState.flyTo(target)
    }
    LaunchedEffect(cameraState.currentRoom) {
        when (cameraState.currentRoom) {
            Room.DRAW -> onSectionChanged(DeckSection.DRAW)
            Room.SPACE -> onSectionChanged(DeckSection.SPACE)
            Room.LEARN -> Unit
        }
    }

    val pin = rememberPuckPinState(canvasSizeDp)
    // P5b item 4: background/sketch layers -- see Layers.kt's own KDoc for scope.
    val layers = rememberLayersState()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(texture.canvas.toColor())
                .focusRequester(focusRequester)
                .focusable()
                .onGloballyPositioned { coordinates ->
                    canvasSizeDp =
                        Vec2(coordinates.size.width / density.density.toDouble(), coordinates.size.height / density.density.toDouble())
                }.roomKeyboardNavigation(cameraState, scope)
                .sheetRootGestures(cameraState) { target -> scope.launch { cameraState.flyTo(target) } }
                .commandPaletteShortcut { paletteOpen = !paletteOpen },
    ) {
        WorldLinesPass(cameraState = cameraState, texture = texture)
        if (construction) WorldGuidesPass(cameraState = cameraState, texture = texture, guidelines = sampleGuidelines())
        BloomLayer(zones = bloomZones(pin.center, canvasSizeDp), texture = texture)
        WorldInkPass(cameraState = cameraState, texture = texture)

        // --- Glass (drawn last: on top, and wins hit-testing) ---
        if (deckVisible) {
            ScreenHeader(
                title = if (section == DeckSection.SPACE) "Space" else "Draw",
                subtitle = projectStatusText(),
                texture = texture,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        // P5b item 4: below the header, opposite the room-name/MAP corner.
        LayersPanel(
            state = layers,
            texture = texture,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = LayersPanelTopPaddingDp.dp),
        )

        Puck(
            state = puckState,
            pin = pin,
            texture = texture,
            canvasSizeDp = canvasSizeDp,
            viewCentreFontUnits = viewCentreFontUnits(cameraState.camera, canvasSizeDp),
            config = initialGestureConfig,
        )

        // TopCenter, not Center: this room's own world-space placeholder ink (RoomInk.kt's room
        // wordmark, positioned just below the baseline) sits at screen-centre by default, so a
        // centred glass panel visibly collided with it. Matches LayersPanel's own "just below the
        // header" offset, the one other fixed-position glass element that isn't pinned to an edge.
        SpaceRoomGlass(
            room = cameraState.currentRoom,
            texture = texture,
            kerning = sampleKerning(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = LayersPanelTopPaddingDp.dp),
        )

        InspectorRow(
            fields = inspectorFields(cameraState, puckState),
            texture = texture,
            modifier = Modifier.align(Alignment.BottomStart),
        )

        CommandPalette(
            visible = paletteOpen,
            onDismiss = { paletteOpen = false },
            texture = texture,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (deckVisible) {
            SheetDeck(
                texture = texture,
                section = section,
                construction = construction,
                onConstruction = { construction = it },
                onOpenPalette = { paletteOpen = true },
            )
        }
    }
}

/**
 * UI_SPEC §3 "Inspector": placeholder content for task P4b -- "show the camera's own X/Y/zoom as
 * a proof the row renders and updates live as you pan" (task P4b item 7) -- for every tool but
 * Primitives; P5b's own real Construct-inspector content
 * ([com.asoc.typewright.ui.glass.constructInspectorFields]) takes over when the Primitives tool
 * is active and has already created something ([PuckUiState.activeConstruction]). The two field
 * sets are not shown together: five Construct fields (stem/contrast/exponent/width/state) plus
 * four camera fields would exceed [InspectorRow]'s own "never more than six pairs" rule, and
 * camera X/Y/zoom is far less relevant while parameterizing a primitive than what that primitive
 * actually holds -- this task's own judged call to replace rather than append (P5b's own explicit
 * "alongside... unless you judge replacing is cleaner"). [InspectorRow] itself reads
 * [SheetCameraState.camera]/[PuckUiState.activeConstruction] each recomposition here (a plain
 * composable read, not the draw-phase-only discipline the world passes use), which is the right
 * trade for this small, cheap row of text -- unlike the grid/ink passes, there is no expensive
 * geometry behind it.
 */
private fun inspectorFields(
    cameraState: SheetCameraState,
    puckState: PuckUiState,
): List<InspectorField> {
    val active = puckState.activeConstruction
    if (puckState.currentTool == Tool.PRIMITIVES && active != null) {
        return constructInspectorFields(active)
    }
    val camera = cameraState.camera
    return listOf(
        InspectorField(label = "ROOM", value = cameraState.currentRoom.name),
        InspectorField(label = "X", value = camera.offset.x.toFixedString(0)),
        InspectorField(label = "Y", value = camera.offset.y.toFixedString(0)),
        InspectorField(label = "ZOOM", value = "${camera.zoom.toFixedString(2)}x", emphasize = true),
    )
}

/** How far down a phone-sized canvas the baseline (font-unit y = 0) starts, in dp -- see [TypewrightSheet]'s own initial-camera comment. */
private const val DEFAULT_BASELINE_SCREEN_Y_DP = 400.0

/**
 * Bloom zones (task P4b item 3): the puck always; header and inspector "if time allows" (done
 * here, approximated). UI_SPEC's own CSS uses an `ellipse closest-side` gradient for the wide,
 * short header/inspector bands (`docs/ARCHITECTURE_REVIEW.md` §4.1 "Gradients": "the inspector's
 * `ellipse closest-side` needs a `scale()`"); [BloomZone] only models a circle, so a header/
 * inspector band is approximated here with three overlapping circles spanning its width rather
 * than one true scaled ellipse -- a documented simplification, not a literal port of the CSS.
 */
private fun bloomZones(
    puckCenter: Vec2,
    canvasSizeDp: Vec2,
): List<BloomZone> {
    val zones = mutableListOf(BloomZone(center = puckCenter, radiusDp = 90.0))
    val bandRadius = (canvasSizeDp.x / 3.0).coerceAtLeast(60.0)
    for (fraction in listOf(0.2, 0.5, 0.8)) {
        zones += BloomZone(center = Vec2(canvasSizeDp.x * fraction, 24.0), radiusDp = bandRadius)
        zones += BloomZone(center = Vec2(canvasSizeDp.x * fraction, canvasSizeDp.y - 24.0), radiusDp = bandRadius)
    }
    return zones
}

/**
 * The sheet's deck: Back to Home on the left, ⋯ on the right (Command… first), and for Draw one
 * toggle, Construction, which shows the construction geometry (on by default). Space has no toggles
 * yet, because the views the explorer names for it (Spacing, Kerning) are not separate views here.
 */
@Composable
private fun SheetDeck(
    texture: CanvasTexture,
    section: DeckSection,
    construction: Boolean,
    onConstruction: (Boolean) -> Unit,
    onOpenPalette: () -> Unit,
) {
    val nav = LocalSectionNav.current
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        ControlDeck(
            texture = texture,
            modifier = Modifier.align(Alignment.BottomCenter),
            section = section,
            left = DeckLeft.BACK,
            right = DeckRight.MENU,
            toggles = if (section == DeckSection.DRAW) listOf(DeckToggle("Construction", on = construction)) else emptyList(),
            sectionStatus = nav?.statusOf ?: { null },
            onLeft = { nav?.goTo?.invoke(DeckSection.HOME) },
            onRight = { menuOpen = !menuOpen },
            onToggle = { onConstruction(!construction) },
            onSection = nav?.goTo,
            onMap = nav?.openMap,
        )
        if (menuOpen) {
            PageMenu(texture = texture, items = listOf(PageMenuItem("Command…", "Ctrl or ⌘ K", onOpenPalette)), onDismiss = {
                menuOpen =
                    false
            })
        }
    }
}
