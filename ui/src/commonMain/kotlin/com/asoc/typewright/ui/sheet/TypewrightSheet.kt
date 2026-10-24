// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.asoc.typewright.core.geometry.Axis
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.ui.deck.ControlDeck
import com.asoc.typewright.ui.deck.DeckLeft
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.DeckToggle
import com.asoc.typewright.ui.deck.SystemBackHandler
import com.asoc.typewright.ui.draw.DesktopTopBar
import com.asoc.typewright.ui.draw.DrawController
import com.asoc.typewright.ui.draw.DrawEmptyState
import com.asoc.typewright.ui.draw.DrawFrame
import com.asoc.typewright.ui.draw.DrawGestureEnv
import com.asoc.typewright.ui.draw.DrawGridLayer
import com.asoc.typewright.ui.draw.DrawHeader
import com.asoc.typewright.ui.draw.DrawInkLayer
import com.asoc.typewright.ui.draw.DrawInspectorModel
import com.asoc.typewright.ui.draw.DrawKeyAction
import com.asoc.typewright.ui.draw.DrawNoticeBar
import com.asoc.typewright.ui.draw.DrawPageMenu
import com.asoc.typewright.ui.draw.DrawPageMenuItems
import com.asoc.typewright.ui.draw.DrawPalette
import com.asoc.typewright.ui.draw.DrawRadialOverlay
import com.asoc.typewright.ui.draw.DrawReadout
import com.asoc.typewright.ui.draw.DrawRichMenu
import com.asoc.typewright.ui.draw.DrawRoom
import com.asoc.typewright.ui.draw.DrawSelection
import com.asoc.typewright.ui.draw.DrawShortcutSheet
import com.asoc.typewright.ui.draw.DrawSpace
import com.asoc.typewright.ui.draw.DrawSubject
import com.asoc.typewright.ui.draw.FieldGroup
import com.asoc.typewright.ui.draw.FieldSpec
import com.asoc.typewright.ui.draw.GlyphLines
import com.asoc.typewright.ui.draw.InspectorColumn
import com.asoc.typewright.ui.draw.PointerKind
import com.asoc.typewright.ui.draw.RadialState
import com.asoc.typewright.ui.draw.ToolRail
import com.asoc.typewright.ui.draw.drawKeys
import com.asoc.typewright.ui.draw.drawPointerInput
import com.asoc.typewright.ui.draw.rememberDrawRoom
import com.asoc.typewright.ui.draw.toInspectorField
import com.asoc.typewright.ui.glass.CommandPalette
import com.asoc.typewright.ui.glass.InspectorEdit
import com.asoc.typewright.ui.glass.InspectorRow
import com.asoc.typewright.ui.glass.commandPaletteShortcut
import com.asoc.typewright.ui.glass.constructInspectorFields
import com.asoc.typewright.ui.glass.projectStatusText
import com.asoc.typewright.ui.puck.Puck
import com.asoc.typewright.ui.puck.PuckGestureConfig
import com.asoc.typewright.ui.puck.PuckGestureState
import com.asoc.typewright.ui.puck.PuckPinState
import com.asoc.typewright.ui.puck.PuckUiState
import com.asoc.typewright.ui.puck.Tool
import com.asoc.typewright.ui.puck.rememberPuckPinState
import com.asoc.typewright.ui.puck.rememberPuckUiState
import com.asoc.typewright.ui.puck.viewCentreFontUnits
import com.asoc.typewright.ui.shell.LocalSectionNav
import com.asoc.typewright.ui.shell.ScreenHeader
import com.asoc.typewright.ui.theme.LocalAppearance
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.MeaningColors
import com.asoc.typewright.ui.tokens.toColor
import kotlinx.coroutines.launch

/**
 * The one-sheet UI: Draw and Space as two regions of one continuous camera space
 * (`docs/ARCHITECTURE_REVIEW.md` §4.1/§5 finding 25), assembled in the review's order: the grid, the
 * bloom, then the ink, then the glass. The grid is the only thing that dissolves near the glass; the
 * ink (metric lines, construction, the glyph and its nodes) is above the bloom and never fades (C4).
 *
 * Draw shows the glyph the open project has ([com.asoc.typewright.ui.draw.rememberDrawRoom]) and
 * edits it through the project's undo history; with no project open it says so and draws nothing. On a
 * phone the header, the inspector row, the puck and the deck are the glass; from 900 dp the frame is the
 * desktop one, a top bar with a tool rail and an inspector column, and the puck is left off.
 *
 * **Hit testing** (`docs/ARCHITECTURE_REVIEW.md` §4.1 "Hit testing on the glass"): Compose hit-tests a
 * `Box`'s children top-down and stops at the first one that consumes a pointer, so glass children
 * added after the glyph's pointer layer take priority over it with nothing extra. The deck is the
 * last child so a menu's scrim never covers the circle that closes the menu.
 *
 * GESTURE HONESTY (CLAUDE.md law 4): every touch gesture here is built on the tested pure machines
 * and runs in the desktop harness; none has run on a touchscreen in this container. They are
 * owner-verified on a device only.
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
    initialGestureConfig: PuckGestureConfig = remember { PuckGestureConfig() },
    /**
     * Exposed (rather than created unconditionally inside this function) so a test can pre-seed
     * the puck's gesture state -- e.g. into [com.asoc.typewright.ui.puck.PuckGestureState.
     * RadialOpen] -- and see the radial dial rendered in the sheet's full context on the very
     * first frame, with no simulated pointer sequence needed (`SheetScreenshotTest`). Ordinary
     * callers (the real apps) just take the default.
     */
    puckState: PuckUiState = rememberPuckUiState(),
    /** Whether the glyph starts filled rather than outlined; the deck's Outline and Fill toggles change it. */
    initialFill: Boolean = false,
    /** Where the puck is pinned; a host that wants to read or place it passes its own, otherwise the sheet keeps one at the default edge. */
    puckPin: PuckPinState? = null,
    /** Told about the Draw room whenever the glyph or the project changes, so a host or a test can reach its controller. */
    onDrawRoom: (DrawRoom) -> Unit = {},
    /** The camera the sheet moves; a host that wants to read or place it passes its own, otherwise the sheet keeps one at [initialRoom]. */
    cameraState: SheetCameraState =
        rememberSheetCameraState(
            initial =
                SheetCamera(
                    offset = initialRoom.worldBounds().let { Vec2(it.left, -RoomViews.REST_BASELINE_DP) },
                    zoom = RoomViews.REST_ZOOM,
                ),
        ),
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val nav = LocalSectionNav.current
    val haptics = LocalHapticFeedback.current
    val hapticsOn = LocalAppearance.current?.settings?.haptics ?: true

    val texture = initialTexture
    // A phone-sized guess until the first layout pass reports the real size.
    var canvasSizeDp by remember { mutableStateOf(Vec2(360.0, 780.0)) }
    var sizeMeasured by remember { mutableStateOf(false) }
    var paletteOpen by remember { mutableStateOf(false) }
    var paletteGoTo by remember { mutableStateOf(false) }
    var pageMenuOpen by remember { mutableStateOf(false) }
    var shortcutsOpen by remember { mutableStateOf(false) }
    var layersOpen by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf(false) }
    var cancelEditTick by remember { mutableStateOf(0) }
    var spaceHeld by remember { mutableStateOf(false) }
    var ctrlHeld by remember { mutableStateOf(false) }
    var shiftHeld by remember { mutableStateOf(false) }
    var altHeld by remember { mutableStateOf(false) }
    // C4: construction geometry is drawn at full strength; this switch only turns it off by choice. On by default.
    var construction by remember { mutableStateOf(true) }
    var fill by remember { mutableStateOf(initialFill) }

    // Read through derivedStateOf so a pan, which changes the camera every frame, recomposes the sheet only when the room changes.
    val currentRoom by remember(cameraState) { derivedStateOf { cameraState.currentRoom } }
    val pin = puckPin ?: rememberPuckPinState(canvasSizeDp)
    LaunchedEffect(pin, canvasSizeDp) { pin.fitTo(canvasSizeDp) }
    val layers = rememberLayersState()
    val statusBarDp = WindowInsets.statusBars.getTop(density) / density.density.toDouble()
    val layout = SheetLayout.of(canvasSizeDp, statusBarDp, if (canvasSizeDp.x >= SheetLayout.WIDE_DP) null else pin.edge)

    val drawRoom =
        rememberDrawRoom(
            cameraState = cameraState,
            canvasSizeDp = canvasSizeDp,
            sizeMeasured = sizeMeasured,
            insets = layout.free,
            puck = puckState,
            onOpenPalette = { goToGlyph ->
                paletteGoTo = goToGlyph
                paletteOpen = true
            },
            onOpenMap = { nav?.openMap?.invoke() },
            onShowShortcuts = { shortcutsOpen = true },
            onTick = { if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
            onThump = { if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
        )
    LaunchedEffect(drawRoom) { onDrawRoom(drawRoom) }
    val controller = drawRoom.controller
    val projectState =
        drawRoom.session
            ?.state
            ?.collectAsState()
            ?.value
    val glyph = controller?.let { c -> projectState?.font?.glyph(c.editor.ref) }

    LaunchedEffect(section) {
        val target = if (section == DeckSection.SPACE) Room.SPACE else Room.DRAW
        // A glyph that has not been fitted yet is fitted by the Draw room once the camera has arrived and come to rest.
        if (cameraState.currentRoom != target && (section == DeckSection.DRAW || section == DeckSection.SPACE)) cameraState.flyTo(target)
    }
    LaunchedEffect(currentRoom) {
        when (currentRoom) {
            Room.DRAW -> onSectionChanged(DeckSection.DRAW)
            Room.SPACE -> onSectionChanged(DeckSection.SPACE)
            Room.LEARN -> Unit
        }
    }
    LaunchedEffect(paletteOpen, editingField, shortcutsOpen) {
        if (!paletteOpen && !editingField) runCatching { focusRequester.requestFocus() }
    }

    val latestCanvas by rememberUpdatedState(canvasSizeDp)
    val latestLayout by rememberUpdatedState(layout)
    val latestCtrl by rememberUpdatedState(ctrlHeld)
    val latestSection by rememberUpdatedState(section)
    // While another screen covers the sheet it keeps its state but takes no pointer or key: a tap meant for Home must not edit a glyph under it.
    val latestVisible by rememberUpdatedState(deckVisible)
    val latestBlocked by rememberUpdatedState(paletteOpen || pageMenuOpen || shortcutsOpen)
    val latestRoom by rememberUpdatedState(drawRoom)
    val latestSpace by rememberUpdatedState(spaceHeld)
    val latestShift by rememberUpdatedState(shiftHeld)
    val latestAlt by rememberUpdatedState(altHeld)
    val gestureEnv =
        remember(cameraState, puckState) {
            DrawGestureEnv(
                cameraState = cameraState,
                frame = { pointer, shift, alt ->
                    DrawFrame(
                        space = DrawSpace.of(cameraState.camera),
                        canvasWidthDp = latestCanvas.x,
                        canvasHeightDp = latestCanvas.y,
                        topInsetDp = latestLayout.free.top,
                        // The ring clears the whole block of glass at the foot: the deck and, on a phone, the inspector row above it.
                        deckHeightDp = latestLayout.free.bottom,
                        pointer = pointer,
                        tool = if (latestCtrl) Tool.SELECT else puckState.currentTool,
                        shift = shift,
                        alt = alt,
                        puckHeld = puckState.modifierHeld,
                    )
                },
                puckHeld = { puckState.modifierHeld },
                holdPuckOff = { puckState.takeHoldAsModifier() },
                spaceHeld = { latestSpace },
                shiftHeld = { latestShift },
                altHeld = { latestAlt },
                enabled = { latestVisible && latestSection == DeckSection.DRAW && !latestBlocked && latestRoom.controller != null },
            )
        }

    val radial = controller?.radial
    val lifted = radial?.state is RadialState.Lifted || controller?.menu != null
    // While a ring is being swept, the object's or the puck's, the deck goes to 30% (S12).
    val ringOpen = radial?.state is RadialState.Sweeping || puckState.gestureState is PuckGestureState.RadialOpen
    // A numeric field being typed into is an edit mode (S11): the deck's left circle is Close until it is committed or put away.
    val closeable = lifted || paletteOpen || pageMenuOpen || shortcutsOpen || editingField

    fun closeAll() {
        paletteOpen = false
        pageMenuOpen = false
        shortcutsOpen = false
        if (editingField) cancelEditTick++
        controller?.closeOverlays()
    }
    SystemBackHandler(enabled = closeable && deckVisible) { closeAll() }
    LaunchedEffect(deckVisible) { if (!deckVisible) closeAll() }

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
                    sizeMeasured = true
                }.roomKeyboardNavigation(cameraState, scope)
                .sheetRootGestures(
                    cameraState = cameraState,
                    onGestureSettled = { target -> scope.launch { cameraState.flyTo(target) } },
                    firstFingerBusy = { puckState.fingerOnPuck },
                    onTwoFingerTap = {
                        if (latestVisible && latestSection == DeckSection.DRAW && !latestBlocked) {
                            latestRoom.controller?.onKey(DrawKeyAction.Undo, gestureEnv.frame(PointerKind.TOUCH, false, false))
                        }
                    },
                    onThreeFingerTap = {
                        if (latestVisible && latestSection == DeckSection.DRAW && !latestBlocked) {
                            latestRoom.controller?.onKey(DrawKeyAction.Redo, gestureEnv.frame(PointerKind.TOUCH, false, false))
                        }
                    },
                ).commandPaletteShortcut {
                    paletteGoTo = false
                    paletteOpen = !paletteOpen
                }.drawKeys(
                    controller = { latestRoom.controller },
                    frame = { gestureEnv.frame(PointerKind.MOUSE, false, false) },
                    enabled = {
                        latestSection == DeckSection.DRAW && !latestBlocked && !editingField && latestRoom.controller?.menu == null
                    },
                    onSpace = { spaceHeld = it },
                    onModifiers = { ctrl, shift, alt ->
                        ctrlHeld = ctrl
                        shiftHeld = shift
                        altHeld = alt
                    },
                ).onKeyEvent { event ->
                    // Esc closes what the glass has open before it does anything to the drawing.
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && (pageMenuOpen || shortcutsOpen)) {
                        closeAll()
                        true
                    } else {
                        false
                    }
                },
    ) {
        // --- The sheet: the grid, the bloom, the ink (never under the bloom), then the glyph's pointer layer ---
        DrawGridLayer(room = drawRoom, cameraState = cameraState, texture = texture)
        BloomLayer(zones = bloomZones(if (layout.wide) null else pin.center, canvasSizeDp), texture = texture)
        WorldInkPass(cameraState = cameraState, texture = texture, rooms = listOf(Room.SPACE, Room.LEARN))
        DrawInkLayer(
            room = drawRoom,
            cameraState = cameraState,
            texture = texture,
            fill = fill,
            construction = construction,
            labelBand = layout.free.top..(canvasSizeDp.y - layout.free.bottom + LABEL_BAND_SLACK_DP),
            labelEndInsetDp = layout.free.right,
            // While an object's radial is open, sweeping or waiting for a tap, the glyph steps back to 30% (S12); the metric lines do not (C4).
            glyphAlpha = if (controller?.radial != null) RADIAL_GLYPH_ALPHA else 1f,
        )
        if (section == DeckSection.DRAW) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .semantics {
                            // What a screen reader says about the drawing: the glyph, and what is selected in it.
                            contentDescription =
                                glyph?.let { "Drawing. " + DrawSubject.describe(it, controller?.selection ?: DrawSelection.EMPTY) }
                                    ?: "Drawing: nothing open"
                        }.drawPointerInput(drawRoom, gestureEnv),
            )
        }

        // --- Glass (drawn after the sheet: on top, and wins hit-testing) ---
        val projectLine = projectStatusText()
        if (deckVisible) {
            when {
                section == DeckSection.SPACE -> {
                    ScreenHeader(
                        title = "Space",
                        subtitle = projectLine,
                        texture = texture,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }

                layout.wide -> {
                    DesktopTopBar(
                        room = drawRoom,
                        texture = texture,
                        projectLine = projectLine,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }

                else -> {
                    DrawHeader(
                        room = drawRoom,
                        texture = texture,
                        projectLine = projectLine,
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
            }
        }

        if (layout.wide && section == DeckSection.DRAW) {
            ToolRail(
                puck = puckState,
                texture = texture,
                modifier = Modifier.align(Alignment.TopStart).padding(top = SheetLayout.TOP_BAR_DP.dp),
            )
            InspectorColumn(
                groups = inspectorGroups(glyph, controller, puckState),
                texture = texture,
                edit = controller?.let { inspectorEdit(it, puckState) },
                onEditingChanged = { editingField = it },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = SheetLayout.TOP_BAR_DP.dp),
                cancelEditing = cancelEditTick,
            )
        } else if (layersOpen) {
            LayersPanel(
                state = layers,
                texture = texture,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = LayersPanelTopPaddingDp.dp),
            )
        }

        if (section == DeckSection.DRAW) {
            DrawEmptyState(room = drawRoom, texture = texture, modifier = Modifier.align(Alignment.Center))
        }

        if (!layout.wide) {
            Puck(
                state = puckState,
                pin = pin,
                texture = texture,
                canvasSizeDp = canvasSizeDp,
                viewCentreFontUnits = { viewCentreFontUnits(cameraState.camera, latestCanvas) },
                config = initialGestureConfig,
            )
        }

        // The Space room's own glass content; top-centre, clear of the room's placeholder ink at screen centre.
        SpaceRoomGlass(
            room = currentRoom,
            texture = texture,
            kerning = sampleKerning(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = LayersPanelTopPaddingDp.dp),
        )

        if (section == DeckSection.DRAW && controller != null && glyph != null) {
            if (!layout.wide) {
                val construct = puckState.activeConstruction.takeIf { puckState.currentTool == Tool.PRIMITIVES }
                val snapTint = MeaningColors.CYAN.forTexture(texture)
                val fields =
                    if (construct != null) {
                        constructInspectorFields(construct)
                    } else {
                        val snaps = controller.snaps
                        DrawInspectorModel
                            .fields(
                                glyph = glyph,
                                selection = controller.selection,
                                lines = GlyphLines.of(glyph),
                                snapX = snaps.any { it.axis == Axis.X },
                                snapY = snaps.any { it.axis == Axis.Y },
                                dragging = controller.editor.isDragging,
                            ).map { it.toInspectorField(snapTint) }
                    }
                InspectorRow(
                    fields = fields,
                    texture = texture,
                    modifier = Modifier.align(Alignment.BottomStart),
                    edit = if (construct != null) null else inspectorEdit(controller, puckState),
                    bottomInsetDp = layout.inspectorBottomDp,
                    onEditingChanged = { editingField = it },
                    cancelEditing = cancelEditTick,
                )
            }
            DrawNoticeBar(
                controller = controller,
                texture = texture,
                modifier =
                    Modifier
                        .align(if (layout.wide) Alignment.BottomCenter else Alignment.BottomStart)
                        .padding(
                            bottom =
                                (
                                    if (layout.wide) {
                                        layout.inspectorBottomDp
                                    } else {
                                        layout.inspectorBottomDp + SheetLayout.INSPECTOR_ROW_DP
                                    } + NOTICE_GAP_DP
                                ).dp,
                        ),
            )
            DrawReadout(readout = controller.readout, texture = texture, canvasWidthDp = canvasSizeDp.x)
        }

        if (paletteOpen) {
            val extra = remember(drawRoom, glyph) { if (section == DeckSection.DRAW) DrawPalette.entries(drawRoom) else emptyList() }
            CommandPalette(
                visible = true,
                onDismiss = { paletteOpen = false },
                texture = texture,
                modifier = Modifier.align(Alignment.TopCenter),
                extraCommands = extra,
                directory = drawRoom.directory.takeIf { section == DeckSection.DRAW },
                currentGlyph = drawRoom.glyphName,
                onGoToGlyph = { name -> drawRoom.goTo(name) },
                startInGoTo = paletteGoTo,
            )
        }

        // --- Menus and the radial, below the deck so the deck's Close circle is always reachable ---
        controller?.menu?.let { menu -> DrawRichMenu(controller = controller, menu = menu, texture = texture) }
        if (controller != null) DrawRadialOverlay(controller = controller, texture = texture)
        if (pageMenuOpen) {
            DrawPageMenu(
                texture = texture,
                items =
                    DrawPageMenuItems.build(
                        room = drawRoom,
                        layersShown = layersOpen,
                        onCommand = {
                            paletteGoTo = false
                            paletteOpen = true
                        },
                        onGoTo = {
                            paletteGoTo = true
                            paletteOpen = true
                        },
                        onLayers = if (layout.wide) null else ({ layersOpen = !layersOpen }),
                    ),
                onDismiss = { pageMenuOpen = false },
            )
        }
        if (shortcutsOpen) DrawShortcutSheet(controller = controller, texture = texture, onClose = { shortcutsOpen = false })

        if (deckVisible) {
            ControlDeck(
                texture = texture,
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter,
                        ).alpha(if (ringOpen) DECK_DIMMED_ALPHA else 1f),
                section = section,
                left = if (closeable) DeckLeft.CLOSE else DeckLeft.BACK,
                right = DeckRight.MENU,
                toggles =
                    if (section == DeckSection.DRAW) {
                        listOf(
                            DeckToggle("Outline", on = !fill),
                            DeckToggle("Fill", on = fill),
                            DeckToggle("Construction", on = construction, groupStart = true),
                        )
                    } else {
                        emptyList()
                    },
                sectionStatus = nav?.statusOf ?: { null },
                onLeft = { if (closeable) closeAll() else nav?.goTo?.invoke(DeckSection.HOME) },
                onRight = { pageMenuOpen = !pageMenuOpen },
                onToggle = { index ->
                    when (index) {
                        0 -> fill = false
                        1 -> fill = true
                        else -> construction = !construction
                    }
                },
                onSection = nav?.goTo,
                onMap = nav?.openMap,
            )
        }
    }
}

/** How the inspector's values change the drawing: typed, or scrubbed with a sideways drag, one undo step each. */
private fun inspectorEdit(
    controller: DrawController,
    puckState: PuckUiState,
): InspectorEdit =
    InspectorEdit(
        onCommit = { key, text -> controller.commitField(key, text) },
        onScrubStart = { key -> controller.scrubStart(key) },
        onScrub = { key, units -> controller.scrubTo(key, units) },
        onScrubEnd = { commit -> controller.scrubEnd(commit) },
        slow = { puckState.modifierHeld },
    )

/** What the desktop inspector column shows: the construction being parameterised, or the selection and the glyph. */
private fun inspectorGroups(
    glyph: Glyph?,
    controller: DrawController?,
    puckState: PuckUiState,
): List<FieldGroup> {
    val construct = puckState.activeConstruction.takeIf { puckState.currentTool == Tool.PRIMITIVES }
    if (construct != null) {
        val fields = constructInspectorFields(construct).map { FieldSpec(it.label.lowercase(), it.value, emphasize = it.emphasize) }
        return listOf(FieldGroup("Construct", fields.take(CONSTRUCT_FIRST_GROUP)), FieldGroup("State", fields.drop(CONSTRUCT_FIRST_GROUP)))
    }
    if (glyph == null || controller == null) return emptyList()
    val snaps = controller.snaps
    return DrawInspectorModel.groups(
        glyph = glyph,
        selection = controller.selection,
        lines = GlyphLines.of(glyph),
        snapX = snaps.any { it.axis == Axis.X },
        snapY = snaps.any { it.axis == Axis.Y },
        dragging = controller.editor.isDragging,
    )
}

private const val CONSTRUCT_FIRST_GROUP = 3
private const val NOTICE_GAP_DP = 8.0
private const val LABEL_BAND_SLACK_DP = 4.0
private const val DECK_DIMMED_ALPHA = 0.3f
private const val RADIAL_GLYPH_ALPHA = 0.3f

/**
 * Bloom zones (UI_SPEC §1 layer 4): the puck when there is one, and the header and inspector bands,
 * approximated with overlapping circles because [BloomZone] only models a circle (the CSS uses an
 * `ellipse closest-side` gradient; a true scaled ellipse is a documented simplification away).
 */
private fun bloomZones(
    puckCenter: Vec2?,
    canvasSizeDp: Vec2,
): List<BloomZone> {
    val zones = mutableListOf<BloomZone>()
    if (puckCenter != null) zones += BloomZone(center = puckCenter, radiusDp = 90.0)
    val bandRadius = (canvasSizeDp.x / 3.0).coerceAtLeast(60.0)
    for (fraction in listOf(0.2, 0.5, 0.8)) {
        zones += BloomZone(center = Vec2(canvasSizeDp.x * fraction, 24.0), radiusDp = bandRadius)
        zones += BloomZone(center = Vec2(canvasSizeDp.x * fraction, canvasSizeDp.y - 24.0), radiusDp = bandRadius)
    }
    return zones
}
