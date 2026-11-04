// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.CutPlace
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.PenAnchor
import com.asoc.typewright.core.geometry.PenClosed
import com.asoc.typewright.core.geometry.PenPath
import com.asoc.typewright.core.geometry.PenRefusal
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.pointAt
import com.asoc.typewright.core.geometry.segmentLayout
import com.asoc.typewright.core.geometry.sidebearings
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.ui.puck.PrimitiveDragPlan
import com.asoc.typewright.ui.puck.PrimitiveKind
import com.asoc.typewright.ui.puck.PrimitiveShape
import com.asoc.typewright.ui.puck.Tool
import kotlin.math.abs
import kotlin.math.floor

/** What the screen knows at the moment of one pointer output or key press. */
class DrawFrame(
    val space: DrawSpace,
    val canvasWidthDp: Double,
    val canvasHeightDp: Double,
    val topInsetDp: Double,
    val deckHeightDp: Double,
    val pointer: PointerKind,
    val tool: Tool,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val puckHeld: Boolean = false,
    /** The shape the Primitives tool makes, or null until one is picked. */
    val primitive: PrimitiveKind? = null,
)

/** A line the screen shows on the glass about what just happened (or why nothing did). */
sealed interface DrawNotice {
    /** Said once and gone after a moment; [serial] makes two identical lines two notices. */
    data class Toast(
        val text: String,
        val serial: Long,
    ) : DrawNotice

    /** An edit worked out but not made, with its measured effect in [text]: the screen offers Apply and Cancel (a tidy shows "n → m" here). */
    data class Proposal(
        val text: String,
        val serial: Long,
    ) : DrawNotice

    /** The glyph is locked: the screen says so and offers Unlock. */
    data class Locked(
        val glyph: String,
    ) : DrawNotice
}

/** The knife's state: armed, with the first end of the cut once it has been placed. */
data class KnifeState(
    val contour: Int? = null,
    val first: CutPlace? = null,
    val firstAt: Vec2? = null,
)

/** The numbers a finger drag shows above itself so the finger never covers them (INTERACTION_V1 T1). */
data class Readout(
    val text: String,
    val at: Vec2,
)

/**
 * The radial open on screen: its [center], its [state], and what each sector would do. The ring is
 * centred on [anchor], where the finger touched, unless that is too near an edge for the ring to fit, in
 * which case [center] is the touch shifted inward; the finger's travel is always measured from [anchor],
 * so a ring that was shifted still waits for a sweep instead of choosing a sector at once.
 */
data class RadialUi(
    val center: Vec2,
    val anchor: Vec2,
    val state: RadialState,
    val slots: List<RadialSlot>,
    val commands: Map<String, DrawCommand>,
    val subject: String,
    val hasSwept: Boolean,
    val pointer: PointerKind,
)

/** The rich menu open on screen, at the pointer [at] on a desktop. */
data class MenuUi(
    val at: Vec2,
    val subject: String,
    val commands: List<DrawCommand>,
    val pointer: PointerKind,
)

/** What the controller asks of the screen around it. */
interface DrawHost {
    fun pickTool(tool: Tool)

    fun fitGlyph()

    fun actualSize()

    fun stepGlyph(delta: Int)

    fun openPalette(goToGlyph: Boolean)

    fun openMap()

    /** Makes [kind] the shape the Primitives tool drags out, and the Primitives tool the current one. */
    fun pickPrimitive(kind: PrimitiveKind) = Unit

    fun showShortcuts()

    /** A detent under the finger: a snap took hold, or the radial's choice changed. A device with no way to tick does nothing. */
    fun tick() = Unit

    /** A choice committed: the radial ran a sector. */
    fun thump() = Unit
}

/**
 * The state of the Draw room for one open glyph and the glue between the pointer, the keys, the menus
 * and the [DrawEditor]. Everything it decides is a call into tested pure code ([CanvasGestureMachine],
 * [DrawHit], [Snapper], [DragPlan], [DrawCommands], [RadialGeometry]); what it holds is what the
 * screen draws: the selection, the snap targets, the sweep, the radial, the menu and the notice.
 *
 * A drag always ends through [DrawEditor.endDrag], and [end] is safe to call from a `finally`.
 */
class DrawController(
    private val session: ProjectSession,
    val editor: DrawEditor,
    private val host: DrawHost,
) : DrawHooks {
    private var chosen: DrawSelection by mutableStateOf(DrawSelection.EMPTY)

    /**
     * What is selected, always read against the outline as it stands: an undo or redo, or an edit made in
     * another room while this controller was alive, can remove the points or the segment a selection
     * names, and a selection that names nothing must never reach a command, a menu or the canvas.
     */
    var selection: DrawSelection
        get() = glyph?.let { chosen.clippedTo(it) } ?: chosen
        set(value) {
            chosen = value
        }
    var snaps: List<SnapTarget> by mutableStateOf(emptyList())
    var sweep: SweepShape? by mutableStateOf(null)
    var readout: Readout? by mutableStateOf(null)
    var radial: RadialUi? by mutableStateOf(null)
    var menu: MenuUi? by mutableStateOf(null)
    private var shownNotice: DrawNotice? by mutableStateOf(null)

    /** What the glass says. A notice that is not the tidy proposal takes the proposal's crosses and its Apply with it: they would name an outline that has since changed. */
    var notice: DrawNotice?
        get() = shownNotice
        set(value) {
            if (value !is DrawNotice.Proposal) dropProposal()
            shownNotice = value
        }
    var penPath: PenPath by mutableStateOf(PenPath())

    /** The point being dragged out right now (pen down, handles being pulled), shown as a live preview. */
    var penLive: PenAnchor? by mutableStateOf(null)

    /** The anchors waiting in the pen's path, in font units. */
    val penPoints: List<Vec2> get() = penPath.anchors.map { Vec2(it.point.x.toDouble(), it.point.y.toDouble()) }

    /** The knife, once armed: the first place of a cut when it has been tapped, and where it is in font units. */
    var knife: KnifeState? by mutableStateOf(null)

    private var proposedRemoved: List<Point> by mutableStateOf(emptyList())

    /**
     * The nodes a tidy waiting for Apply would remove, in font units, drawn as crosses on the outline. Empty the moment the
     * outline is not the one the proposal was worked out on, whatever changed it.
     */
    val tidyRemoved: List<Point> get() = if (proposalIsCurrent()) proposedRemoved else emptyList()
    private var pendingTidy: PendingTidy? = null

    private class PendingTidy(
        val selection: DrawSelection,
        val preview: TidyPreview,
        val contours: List<com.asoc.typewright.core.geometry.Contour>,
    )

    /** The tool in hand, kept by [toolChanged]: what the header's mode line speaks of. */
    var activeTool: Tool by mutableStateOf(Tool.SELECT)
        private set

    /** The measure tool's two ends. Reading them never touches the glyph or its history. */
    var measureState: MeasureState by mutableStateOf(MeasureState())
        private set

    /** The shape being dragged out with the Primitives tool, as it would be committed. */
    var primitiveLive: PrimitiveShape? by mutableStateOf(null)
        private set
    private var primitiveFrom: Vec2? = null
    private var primitiveKind: PrimitiveKind? = null
    private var primitiveSnapper: Snapper? = null
    private var penDragFrom: Vec2? = null
    var hover: Hit? by mutableStateOf(null)
    var recent: List<String> by mutableStateOf(emptyList())

    private var toastSerial = 0L
    private val warnedTools = mutableSetOf<Tool>()

    private var dragAlive = false
    private var dragOp: DragOp? = null
    private var dragSnapper: Snapper? = null
    private var dragPrimary: Vec2? = null
    private var dragScale = 1.0
    private var sweepPoints = mutableListOf<Vec2>()
    private var sweepLasso = false
    private var sweepAdditive = false
    private var lastFrame: DrawFrame? = null

    val glyph: Glyph? get() = editor.glyph

    fun metrics(): DrawMetrics {
        val font = session.state.value.font
        val master = font.master(editor.ref.master) ?: return DrawMetrics.NONE
        return drawMetricsFor(master.ufo.fontInfo)
    }

    /** The selection whose retracted handles show marks to pull on: none on a locked glyph, which would only refuse the edit. */
    fun selectionForMarks(): DrawSelection = if (editor.isLocked) DrawSelection.EMPTY else selection

    /** True when something is open over the glyph that wants the next tap (the radial waiting, the rich menu). */
    val overlayOpen: Boolean get() = radial != null || menu != null

    // ---- pointer ---------------------------------------------------------------------------

    fun onOutput(
        output: CanvasOutput,
        frame: DrawFrame,
    ) {
        lastFrame = frame
        dropStaleProposal()
        when (output) {
            is CanvasOutput.Tap -> tap(output, frame)
            is CanvasOutput.DoubleTap -> doubleTap(output, frame)
            is CanvasOutput.BeginObjectDrag -> beginDrag(output, frame)
            is CanvasOutput.ObjectDragged -> drag(output, frame)
            is CanvasOutput.EndObjectDrag -> endDrag(output.committed)
            is CanvasOutput.BeginSweep -> beginSweep(output)
            is CanvasOutput.SweepMoved -> moveSweep(output)
            is CanvasOutput.EndSweep -> endSweep(output, frame)
            is CanvasOutput.HoldOpened -> openRadial(output, frame)
            is CanvasOutput.HeldMoved -> moveRadial(output.position)
            is CanvasOutput.HeldReleased -> releaseRadial(output.position)
            CanvasOutput.HeldCancelled -> radial = null
            is CanvasOutput.ContextMenu -> openMenu(output, frame)
        }
    }

    /** Ends anything open: a drag (cancelled, so nothing is half-edited), a sweep, the radial. Safe to call any time, as often as you like. */
    fun end() {
        editor.endDrag(commit = false)
        dragAlive = false
        dragOp = null
        dragSnapper = null
        snaps = emptyList()
        readout = null
        sweep = null
        penDragFrom = null
        penLive = null
        endPrimitiveDrag()
        if (radial?.state is RadialState.Sweeping) radial = null
    }

    private fun tap(
        output: CanvasOutput.Tap,
        frame: DrawFrame,
    ) {
        if (overlayOpen) {
            closeOverlays()
            return
        }
        if (knife != null) {
            knifeTap(output.hit, frame)
            return
        }
        if (!toolIsConnected(frame.tool)) return
        when (frame.tool) {
            Tool.PEN -> {
                penTap(output.position, output.hit, frame)
                return
            }

            Tool.MEASURE -> {
                measureTap(output.position, output.hit, frame)
                return
            }

            Tool.PRIMITIVES -> {
                return
            }

            else -> {
                Unit
            }
        }
        when (val hit = output.hit) {
            is Hit.OnPoint -> selection = if (output.additive) selection.toggled(hit.ref) else selection.only(hit.ref)
            is Hit.OnSegment -> selection = selection.onlySegment(hit.key, hit.t)
            Hit.Nothing -> if (!output.additive) selection = DrawSelection.EMPTY
        }
    }

    private fun doubleTap(
        output: CanvasOutput.DoubleTap,
        frame: DrawFrame,
    ) {
        val current = glyph ?: return
        // Two quick clicks are two points for the pen and two ends for the measure, never a refit of the view.
        if (knife == null && toolIsConnected(frame.tool)) {
            if (frame.tool == Tool.PEN) {
                penTap(output.position, output.hit, frame)
                return
            }
            if (frame.tool == Tool.MEASURE) {
                measureTap(output.position, output.hit, frame)
                return
            }
        }
        if (knife != null) {
            // Two quick taps on one place with the knife armed are the "same place twice" of a cut: a node and no cut.
            lastFrame?.let { knifeTap(output.hit, it) }
            return
        }
        when (val hit = output.hit) {
            is Hit.OnPoint -> selection = selection.wholeContour(current, hit.ref.contour)
            is Hit.OnSegment -> selection = selection.wholeContour(current, hit.key.contour)
            Hit.Nothing -> host.fitGlyph()
        }
    }

    private fun beginDrag(
        output: CanvasOutput.BeginObjectDrag,
        frame: DrawFrame,
    ) {
        dragAlive = false
        if (overlayOpen || !toolIsConnected(frame.tool) || frame.tool == Tool.PEN || frame.tool == Tool.PRIMITIVES ||
            frame.tool == Tool.MEASURE
        ) {
            return
        }
        val current = glyph ?: return
        dragScale = 1.0
        var primary: Vec2? = null
        val op: DragOp =
            when (val hit = output.hit) {
                is Hit.OnPoint -> {
                    if (hit.ref !in
                        selection.points
                    ) {
                        selection = if (output.additive) selection.toggled(hit.ref) else selection.only(hit.ref)
                    }
                    // A handle picked at its mark is dragged from there: it lands under the pointer, not on its node.
                    val lead = hit.markOffset ?: Vec2(0.0, 0.0)
                    primary =
                        current.contours[hit.ref.contour]
                            .points[hit.ref.point]
                            .point
                            .toVec2() + lead
                    if (!hit.onCurve && selection.points == setOf(hit.ref)) {
                        DragOp.MoveHandle(hit.ref, breakSymmetry = frame.alt, lead = lead)
                    } else {
                        DragOp.MoveSelection(selection.points)
                    }
                }

                is Hit.OnSegment -> {
                    selection = selection.onlySegment(hit.key, hit.t)
                    DragOp.BendSegment(hit.key, hit.t)
                }

                Hit.Nothing -> {
                    if (selection.points.isEmpty()) return
                    dragScale = PRECISION_SCALE
                    DragOp.MoveSelection(selection.points)
                }
            }
        val refused = editor.beginDrag(op)
        if (refused != null) {
            applied(refused)
            return
        }
        dragAlive = true
        dragOp = op
        dragPrimary = primary
        dragSnapper = null
        if (op !is DragOp.BendSegment) {
            val moving = (op as? DragOp.MoveSelection)?.points ?: setOf((op as DragOp.MoveHandle).handle)
            val band = (if (frame.pointer == PointerKind.TOUCH) SNAP_BAND_TOUCH_DP else SNAP_BAND_DP) / frame.space.dpPerUnit
            dragSnapper = Snapper(current, metrics(), current.advanceWidth, moving, band)
        }
        notice = null
    }

    private fun drag(
        output: CanvasOutput.ObjectDragged,
        frame: DrawFrame,
    ) {
        if (!dragAlive) return
        val dpu = frame.space.dpPerUnit
        var dx = output.dx / dpu * dragScale
        var dy = -output.dy / dpu * dragScale
        if ((frame.shift || frame.puckHeld) && dragScale == 1.0) {
            val held = constrainToAxes(dx, dy)
            dx = held.first
            dy = held.second
        }
        var snapLabel: String? = null
        val snapper = dragSnapper
        val primary = dragPrimary
        if (snapper != null && primary != null) {
            val result = snapper.snap(Vec2(primary.x + dx, primary.y + dy))
            // A snap that has just taken hold on a target it was not on a moment ago ticks once; staying on it, or letting go, does not.
            if (result.targets.any { it !in snaps }) host.tick()
            snaps = result.targets
            if (result.snapped) {
                dx += result.dx
                dy += result.dy
                snapLabel = result.targets.joinToString(" · ") { it.label }
            }
        } else {
            snaps = emptyList()
        }
        when (editor.dragTo(dx, dy)) {
            DragUpdate.FOLLOWED -> {
                Unit
            }

            DragUpdate.ENDED, DragUpdate.REFUSED -> {
                dragAlive = false
                snaps = emptyList()
                readout = null
                return
            }
        }
        if (frame.pointer != PointerKind.MOUSE) {
            val x = primary?.let { round(it.x + dx) }
            val y = primary?.let { round(it.y + dy) }
            val where = if (x != null && y != null) "x $x · y $y" else "Δx ${signed(round(dx))} · Δy ${signed(round(dy))}"
            readout = Readout(listOfNotNull(where, snapLabel).joinToString(" · "), output.at)
        }
    }

    private fun endDrag(committed: Boolean) {
        editor.endDrag(commit = committed)
        dragAlive = false
        dragOp = null
        dragSnapper = null
        snaps = emptyList()
        readout = null
        selection = selection.clippedTo(glyph ?: return)
    }

    // ---- sweeping --------------------------------------------------------------------------

    private fun beginSweep(output: CanvasOutput.BeginSweep) {
        if (overlayOpen) return
        val frame = lastFrame
        if (frame != null && frame.tool == Tool.PEN && knife == null) {
            // With the pen a drag from empty canvas pulls handles out of the new point; it is not a selection sweep.
            penDragFrom = output.from
            penLive = null
            return
        }
        if (frame != null && frame.tool == Tool.PRIMITIVES && knife == null) {
            beginPrimitiveDrag(output.from, frame)
            return
        }
        if (frame != null && frame.tool == Tool.MEASURE && knife == null) return
        sweepPoints = mutableListOf(output.from)
        sweepLasso = output.lasso
        sweepAdditive = output.additive
        sweep = SweepShape(output.from, output.from, if (output.lasso) sweepPoints.toList() else null)
    }

    private fun moveSweep(output: CanvasOutput.SweepMoved) {
        val from = penDragFrom
        val frame = lastFrame
        if (from != null && frame != null) {
            val anchor = penPointAt(from, frame)
            val pull = penPullTo(anchor, output.to, frame)
            penLive = PenAnchor(anchor, pull.takeIf { it.x != 0 || it.y != 0 })
            return
        }
        if (primitiveFrom != null && frame != null) {
            movePrimitiveDrag(output.to, frame)
            return
        }
        val shape = sweep ?: return
        if (sweepLasso) sweepPoints.add(output.to)
        sweep = SweepShape(shape.from, output.to, if (sweepLasso) sweepPoints.toList() else null)
    }

    private fun endSweep(
        output: CanvasOutput.EndSweep,
        frame: DrawFrame,
    ) {
        val penFrom = penDragFrom
        if (penFrom != null) {
            penDragFrom = null
            penLive = null
            if (output.committed) penPlace(penFrom, output.to, frame)
            return
        }
        if (primitiveFrom != null) {
            finishPrimitiveDrag(output, frame)
            return
        }
        val shape = sweep
        sweep = null
        val current = glyph
        if (shape == null || current == null || !output.committed) return
        val picked =
            if (sweepLasso) {
                DrawHit.pointsInPolygon(current, frame.space, sweepPoints.toList())
            } else {
                DrawHit.pointsInRect(current, frame.space, shape.from, output.to)
            }
        selection = if (sweepAdditive) DrawSelection(selection.points + picked) else DrawSelection(picked)
    }

    // ---- the radial and the menu -----------------------------------------------------------

    private fun openRadial(
        output: CanvasOutput.HoldOpened,
        frame: DrawFrame,
    ) {
        val current = glyph ?: return
        if (frame.tool != Tool.SELECT) return
        when (val hit = output.hit) {
            is Hit.OnPoint -> if (hit.ref !in selection.points) selection = selection.only(hit.ref)
            is Hit.OnSegment -> selection = selection.onlySegment(hit.key, hit.t)
            Hit.Nothing -> Unit
        }
        val commands = DrawCommands.build(DrawCommandContext(editor, selection, metrics(), this)).associateBy { it.id }
        val slots =
            RadialLayouts.forHit(output.hit).map { id ->
                val command = id?.let { commands[it] }
                if (command == null) {
                    RadialSlot(null, "")
                } else {
                    RadialSlot(command.id, RadialLabels.of(command), command.keys, command.enabled)
                }
            }
        val center =
            RadialGeometry.ringCenter(
                output.position,
                frame.canvasWidthDp,
                frame.canvasHeightDp,
                frame.topInsetDp,
                frame.deckHeightDp,
            )
        radial =
            RadialUi(
                center = center,
                anchor = output.position,
                state = RadialState.Sweeping(center, output.position, null),
                slots = slots,
                commands = commands,
                subject = DrawSubject.describe(current, selection),
                hasSwept = false,
                pointer = frame.pointer,
            )
    }

    private fun moveRadial(position: Vec2) {
        val open = radial ?: return
        val state = open.state as? RadialState.Sweeping ?: return
        val selected = RadialGeometry.sectorAt(open.anchor, position, RadialMapping.OPPOSITE)
        if (selected != null && selected != state.selected) host.tick()
        radial = open.copy(state = state.copy(finger = position, selected = selected), hasSwept = open.hasSwept || selected != null)
    }

    private fun releaseRadial(position: Vec2) {
        val open = radial ?: return
        val state = open.state as? RadialState.Sweeping ?: return
        val inDeadZone = (position - open.anchor).length() < RadialGeometry.DEAD_ZONE_DP
        when {
            !inDeadZone && state.selected != null -> {
                radial = null
                val command =
                    open.slots
                        .getOrNull(state.selected)
                        ?.commandId
                        ?.let { open.commands[it] }
                        ?.takeIf { it.enabled }
                if (command != null) {
                    host.thump()
                    run(command)
                }
            }

            inDeadZone && !open.hasSwept -> {
                radial = open.copy(state = RadialState.Lifted(open.center))
            }

            else -> {
                radial = null
            }
        }
    }

    /** A tap on the lifted ring at [position]: a sector commits, the hub opens More…, anywhere else cancels. */
    fun tapRadial(position: Vec2) {
        val open = radial ?: return
        if (open.state !is RadialState.Lifted) return
        val distance = (position - open.center).length()
        when {
            distance < RADIAL_HUB_TAP_DP -> {
                radial = null
                menu = MenuUi(open.center, open.subject, open.commands.values.toList(), open.pointer)
            }

            distance in RadialGeometry.INNER_DP..RadialGeometry.OUTER_DP -> {
                val sector = RadialGeometry.sectorAt(open.center, position, RadialMapping.DIRECT)
                radial = null
                val slot = sector?.let { open.slots.getOrNull(it) }
                slot
                    ?.commandId
                    ?.let { open.commands[it] }
                    ?.takeIf { it.enabled }
                    ?.let(::run)
            }

            else -> {
                radial = null
            }
        }
    }

    private fun openMenu(
        output: CanvasOutput.ContextMenu,
        frame: DrawFrame,
    ) {
        val current = glyph ?: return
        when (val hit = output.hit) {
            is Hit.OnPoint -> if (hit.ref !in selection.points) selection = selection.only(hit.ref)
            is Hit.OnSegment -> selection = selection.onlySegment(hit.key, hit.t)
            Hit.Nothing -> Unit
        }
        openMenuAt(output.position, frame.pointer, current)
    }

    /** Opens the rich menu for the current selection (the radial's More…, the deck's ⋯, a right-click). */
    fun openMenuAt(
        at: Vec2,
        pointer: PointerKind,
        current: Glyph? = glyph,
    ) {
        val g = current ?: return
        menu =
            MenuUi(
                at,
                DrawSubject.describe(g, selection),
                DrawCommands.build(DrawCommandContext(editor, selection, metrics(), this)),
                pointer,
            )
    }

    fun closeOverlays() {
        radial = null
        menu = null
    }

    /** Runs [command] and remembers it as the latest used. */
    fun run(command: DrawCommand) {
        recent = (listOf(command.id) + recent.filterNot { it == command.id }).take(RECENT_COUNT)
        command.run()
    }

    // ---- the pen -----------------------------------------------------------------------------

    private fun penTap(
        position: Vec2,
        hit: Hit,
        frame: DrawFrame,
    ) {
        // A click on the ringed first point closes the path even where another contour's node lies under it; only otherwise does a node eat the click.
        if (hit is Hit.OnPoint && !closesPath(position, frame)) return
        penPlace(position, position, frame)
    }

    /** True when a click at [screen] lands on the pen's first point with enough path behind it to close. */
    private fun closesPath(
        screen: Vec2,
        frame: DrawFrame,
    ): Boolean {
        val first = penPath.anchors.firstOrNull() ?: return false
        return penPath.anchors.size >= 2 && (frame.space.toScreen(first.point.toVec2()) - screen).length() <= PEN_CLOSE_DP
    }

    /** Where a pointer at [screen] puts a pen point: the nearest whole font unit. */
    private fun penPointAt(
        screen: Vec2,
        frame: DrawFrame,
    ): Point {
        val font = frame.space.toFont(screen)
        return Point(floor(font.x + 0.5).toInt(), floor(font.y + 0.5).toInt())
    }

    /** How far the handle was pulled, in whole font units, from the new point to where the pointer is now. */
    private fun penPullTo(
        anchor: Point,
        to: Vec2,
        frame: DrawFrame,
    ): Point {
        val end = penPointAt(to, frame)
        return Point(end.x - anchor.x, end.y - anchor.y)
    }

    /** The pen pointer went down at [from] and came up at [to] (the same place for a click): a corner, or a smooth point if it was pulled. */
    private fun penPlace(
        from: Vec2,
        to: Vec2,
        frame: DrawFrame?,
    ) {
        frame ?: return
        val anchor = penPointAt(from, frame)
        val dragged = (to - from).length() > PEN_DRAG_DP
        val pull = if (dragged) penPullTo(anchor, to, frame).takeIf { it.x != 0 || it.y != 0 } else null
        if (closesPath(from, frame)) {
            // The same format decides whether the path can close and builds the contour it closes into.
            val closed = penPath.closed(pull, glyphFormat())
            // Two plain corners are not a shape yet: the click is another point, not a close.
            if (closed !is PenClosed.Refused || closed.why != PenRefusal.TOO_FEW) {
                finishPen(pull)
                return
            }
        }
        penPath = penPath.plus(anchor, pull)
    }

    /** Closes the path in progress into a contour and adds it to the glyph, as one step; the path stays when it cannot close, so the person can go on. */
    override fun finishPen() = finishPen(null)

    /** Closes the path in progress with an optional [closingPull] on the closing handle. */
    fun finishPen(closingPull: Point?) {
        val plan = editor.planPen(penPath, closingPull, glyphFormat())
        if (plan is EditPlan.Blocked) {
            notice = toast(plan.reason)
            return
        }
        val outcome = editor.perform(plan)
        if (outcome !is EditOutcome.Locked) penPath = PenPath()
        applied(outcome)
    }

    private fun glyphFormat(): CurveFormat = glyph?.contours?.firstOrNull()?.format ?: projectFormat()

    private fun projectFormat(): CurveFormat {
        val master =
            session.state.value.font
                .master(editor.ref.master)
        return master?.ufo?.lib?.lineContourFormat ?: CurveFormat.CUBIC
    }

    fun cancelPen() {
        penPath = PenPath()
        penLive = null
        penDragFrom = null
    }

    override fun penInProgress(): Boolean = !penPath.isEmpty

    /** Undo while the pen is drawing steps back one point first, so a mis-click costs one point and the history is untouched. */
    override fun stepBackPen(): Boolean {
        if (penPath.isEmpty) return false
        penPath = penPath.withoutLast()
        return true
    }

    // ---- the knife ---------------------------------------------------------------------------

    /** Arms the knife for taps; a node or a picked spot already selected is the first end of the cut. */
    override fun startKnife() {
        val places = editor.splitPlaces(selection)
        val first = places.firstOrNull()
        knife = KnifeState(first?.first, first?.second, first?.let { placePosition(it.first, it.second) })
        closeOverlays()
        say(
            if (first == null) {
                "Knife: tap where the cut starts, then where it ends. Esc puts the knife down."
            } else {
                "Knife: tap where the cut ends. Tap the same place again to add a node only. Esc puts the knife down."
            },
        )
    }

    private fun placePosition(
        contour: Int,
        place: CutPlace,
    ): Vec2? {
        val c = glyph?.contours?.getOrNull(contour) ?: return null
        return when (place) {
            is CutPlace.AtNode -> {
                c.points
                    .getOrNull(place.pointIndex)
                    ?.point
                    ?.toVec2()
            }

            is CutPlace.OnSegment -> {
                c
                    .segmentLayout()
                    .getOrNull(place.segment)
                    ?.segment
                    ?.pointAt(place.t)
            }
        }
    }

    private fun knifeTap(
        hit: Hit,
        frame: DrawFrame,
    ) {
        val current = glyph ?: return
        val state = knife ?: return
        val picked: Triple<Int, CutPlace, Vec2>? =
            when (hit) {
                is Hit.OnPoint -> {
                    val point = current.contours[hit.ref.contour].points[hit.ref.point]
                    if (point.onCurve) Triple(hit.ref.contour, CutPlace.AtNode(hit.ref.point), point.point.toVec2()) else null
                }

                is Hit.OnSegment -> {
                    Triple(hit.key.contour, CutPlace.OnSegment(hit.key.segment, hit.t), hit.at)
                }

                Hit.Nothing -> {
                    null
                }
            }
        if (picked == null) {
            say("Tap the outline itself, on a node or on a curve, not on empty canvas or a handle.")
            return
        }
        val (contour, place, at) = picked
        val first = state.first
        val firstAt = state.firstAt
        if (first == null || firstAt == null || state.contour == null) {
            knife = KnifeState(contour, place, at)
            return
        }
        if (state.contour != contour) {
            say("Both ends of a cut must be on the same contour.")
            return
        }
        if ((frame.space.toScreen(firstAt) - frame.space.toScreen(at)).length() <= KNIFE_SAME_DP) {
            // The same place twice: no cut, only the node.
            if (place is CutPlace.OnSegment) {
                knife = null
                applied(editor.addNode(SegmentKey(contour, place.segment), place.t))
            } else {
                say("That is already a node. Tap a different place to cut.")
            }
            return
        }
        val outcome = editor.cutContour(contour, first, place)
        // A refused cut leaves the knife armed with its first end, so the person can pick a different second place.
        if (outcome is EditOutcome.Done) knife = null
        applied(outcome)
    }

    // ---- tidy with the live count ----------------------------------------------------------------

    /** Works out the tidy of what is selected, shows "n → m" and the nodes that would go, and waits for Apply. */
    override fun proposeTidy() {
        if (editor.isLocked) {
            applied(EditOutcome.Locked(glyph?.name ?: ""))
            return
        }
        val current = glyph ?: return
        val preview = editor.tidyPreview(selection)
        if (preview == null) {
            applied(editor.perform(editor.planTidy(selection)))
            return
        }
        closeOverlays()
        // A notice that is not a proposal clears any older one; the proposal is set after, so it is the one that stays.
        notice = null
        pendingTidy = PendingTidy(selection, preview, current.contours)
        proposedRemoved = preview.report.removed
        val caption = lastFrame?.let { TidyCrosses.caption(preview.report.removed, it.space) }
        notice = DrawNotice.Proposal("Tidy · ${preview.text}" + (caption?.let { " · $it" } ?: ""), ++toastSerial)
    }

    /** Apply on the tidy proposal: worked out again against the outline as it is now, and made only if it still says the same. */
    fun applyProposal() {
        val pending = pendingTidy ?: return
        val shown = pending.preview
        val chosen = pending.selection
        dropProposal()
        val fresh = editor.tidyPreview(chosen)
        if (fresh == null || fresh.report.after != shown.report.after || fresh.report.removed != shown.report.removed) {
            notice = toast("The outline changed since the count was shown. Ask for Tidy again.")
            return
        }
        applied(editor.perform(fresh.plan))
    }

    fun dismissProposal() {
        dropProposal()
        if (shownNotice is DrawNotice.Proposal) shownNotice = null
    }

    /** Forgets the pending tidy and its crosses without touching the notice. */
    private fun dropProposal() {
        pendingTidy = null
        proposedRemoved = emptyList()
    }

    private fun proposalIsCurrent(): Boolean = pendingTidy?.let { it.contours === glyph?.contours } ?: false

    /** A proposal worked out on an outline that has since changed (an edit, an undo, a drag) is dropped with its bar. */
    private fun dropStaleProposal() {
        if (pendingTidy != null && !proposalIsCurrent()) dismissProposal()
    }

    // ---- measure and the primitives ----------------------------------------------------------------

    private fun measureTap(
        position: Vec2,
        hit: Hit,
        frame: DrawFrame,
    ) {
        val current = glyph ?: return
        val place =
            when (hit) {
                is Hit.OnPoint -> {
                    current.contours[hit.ref.contour]
                        .points[hit.ref.point]
                        .point
                        .toVec2()
                }

                is Hit.OnSegment -> {
                    hit.at
                }

                Hit.Nothing -> {
                    MeasureState.wholeUnit(frame.space.toFont(position))
                }
            }
        measureState = measureState.tapped(place)
    }

    private fun beginPrimitiveDrag(
        from: Vec2,
        frame: DrawFrame,
    ) {
        val kind = frame.primitive
        val current = glyph
        if (kind == null || current == null) {
            say("Pick a shape first: tap the puck, pick Primitives on the rail and then a shape, or choose one in the command palette.")
            return
        }
        if (editor.isLocked) {
            applied(EditOutcome.Locked(current.name))
            return
        }
        primitiveFrom = from
        primitiveKind = kind
        val band = (if (frame.pointer == PointerKind.TOUCH) SNAP_BAND_TOUCH_DP else SNAP_BAND_DP) / frame.space.dpPerUnit
        primitiveSnapper = Snapper(current, metrics(), current.advanceWidth, emptySet(), band)
        primitiveLive = null
    }

    private fun primitiveShapeAt(
        to: Vec2,
        frame: DrawFrame,
    ): PrimitiveShape? {
        val from = primitiveFrom ?: return null
        val kind = primitiveKind ?: return null
        val snapper = primitiveSnapper
        val snap: (Vec2) -> Vec2 =
            if (snapper == null) {
                { it }
            } else {
                { v ->
                    val result = snapper.snap(v)
                    Vec2(v.x + result.dx, v.y + result.dy)
                }
            }
        return PrimitiveDragPlan.plan(kind, frame.space.toFont(from), frame.space.toFont(to), snap)
    }

    private fun movePrimitiveDrag(
        to: Vec2,
        frame: DrawFrame,
    ) {
        val shape = primitiveShapeAt(to, frame)
        primitiveLive = shape
        val snapper = primitiveSnapper
        snaps =
            if (snapper != null && shape != null) snapper.snap(Vec2(shape.maxX.toDouble(), shape.maxY.toDouble())).targets else emptyList()
        if (frame.pointer != PointerKind.MOUSE) readout = shape?.let { Readout(it.text, to) }
    }

    private fun finishPrimitiveDrag(
        output: CanvasOutput.EndSweep,
        frame: DrawFrame,
    ) {
        val shape = if (output.committed) primitiveShapeAt(output.to, frame) else null
        endPrimitiveDrag()
        if (!output.committed) return
        if (shape == null) {
            say("Drag further to size the shape: it needs at least ${PrimitiveDragPlan.MIN_SIDE_UNITS} units each way.")
            return
        }
        val plan = editor.planPrimitive(shape, glyphFormat())
        if (plan is EditPlan.Blocked) {
            say(plan.reason)
            return
        }
        applied(editor.perform(plan))
    }

    private fun endPrimitiveDrag() {
        primitiveFrom = null
        primitiveKind = null
        primitiveSnapper = null
        primitiveLive = null
        readout = null
        snaps = emptyList()
    }

    // ---- the tool in hand --------------------------------------------------------------------------

    /**
     * The tool changed (a key, the rail, the puck's list or its radial): whatever the old tool had open is put down, so a
     * path left by the pen, the armed knife, a measure or a half-dragged shape never carries into the next tool.
     */
    fun toolChanged(tool: Tool) {
        if (tool == activeTool) return
        activeTool = tool
        cancelPen()
        knife = null
        measureState = MeasureState()
        endPrimitiveDrag()
        dismissProposal()
    }

    /** The line the header speaks while a mode is open (the pen, the knife, the measure), or null when none is. */
    val modeLine: String?
        get() =
            when {
                knife != null -> {
                    "Knife · tap two places on the outline · Esc puts it down"
                }

                !penPath.isEmpty -> {
                    val n = penPath.anchors.size
                    "Pen · $n point${if (n == 1) "" else "s"} · click the first point or press Enter to close · Esc drops it"
                }

                activeTool == Tool.MEASURE -> {
                    measureState.measurement?.text ?: "Measure · tap two places"
                }

                else -> {
                    null
                }
            }

    // ---- keys --------------------------------------------------------------------------------

    /** Carries out [action]; true when it was handled. */
    fun onKey(
        action: DrawKeyAction,
        frame: DrawFrame,
    ): Boolean {
        lastFrame = frame
        dropStaleProposal()
        val current = glyph
        when (action) {
            is DrawKeyAction.PickTool -> {
                host.pickTool(action.tool)
                toolChanged(action.tool)
            }

            is DrawKeyAction.Nudge -> {
                applied(editor.moveBy(selection, action.dx, action.dy, label = "Nudge", merge = true))
            }

            DrawKeyAction.Undo -> {
                if (!stepBackPen()) editor.undo()
                afterHistory()
            }

            DrawKeyAction.Redo -> {
                editor.redo()
                afterHistory()
            }

            DrawKeyAction.ToggleSmooth -> {
                // Enter closes the pen's path when there is one; it is only the smooth toggle otherwise.
                if (penInProgress()) finishPen() else applied(editor.toggleSmooth(selection))
            }

            DrawKeyAction.Tangent -> {
                runCommand("tangent")
            }

            DrawKeyAction.Hobby -> {
                runCommand("hobby")
            }

            DrawKeyAction.DeleteSelection -> {
                applied(editor.deleteSelection(selection))
            }

            DrawKeyAction.AddExtremes -> {
                applied(editor.addExtremes(selection))
            }

            DrawKeyAction.Tidy -> {
                proposeTidy()
            }

            DrawKeyAction.Split -> {
                runCommand("split")
            }

            DrawKeyAction.CorrectDirection -> {
                applied(editor.correctDirection(selection))
            }

            DrawKeyAction.ReverseContour -> {
                applied(editor.reverseContours(selection))
            }

            DrawKeyAction.BreakHandles -> {
                applied(editor.breakHandles(selection))
            }

            DrawKeyAction.AlignToMetric -> {
                applied(editor.alignToMetrics(selection, metrics()))
            }

            DrawKeyAction.LockGlyph -> {
                applied(if (editor.isLocked) editor.unlock(selection) else editor.approve(selection))
            }

            DrawKeyAction.SelectAll -> {
                selectAll()
            }

            is DrawKeyAction.StepNode -> {
                if (current != null) selection = stepNode(current, selection, action.delta)
            }

            DrawKeyAction.FitGlyph -> {
                host.fitGlyph()
            }

            DrawKeyAction.ActualSize -> {
                host.actualSize()
            }

            DrawKeyAction.PreviousGlyph -> {
                host.stepGlyph(-1)
            }

            DrawKeyAction.NextGlyph -> {
                host.stepGlyph(1)
            }

            DrawKeyAction.GoToGlyph -> {
                host.openPalette(goToGlyph = true)
            }

            DrawKeyAction.OpenMap -> {
                host.openMap()
            }

            DrawKeyAction.ShortcutSheet -> {
                host.showShortcuts()
            }

            DrawKeyAction.Escape -> {
                escape()
            }

            is DrawKeyAction.RadialSector -> {
                pickSector(action.index)
            }
        }
        return true
    }

    /**
     * Runs the registry's command [id] for the selection as it stands. A command that cannot run says why: the lock prompt
     * (with Unlock) only when the glyph is locked, otherwise the command's own reason, so a refused cut is never called a lock.
     */
    private fun runCommand(id: String) {
        val command = DrawCommands.build(DrawCommandContext(editor, selection, metrics(), this)).firstOrNull { it.id == id } ?: return
        when {
            command.enabled -> run(command)
            editor.isLocked -> applied(EditOutcome.Locked(glyph?.name ?: ""))
            else -> say("${command.label}: ${command.reason}")
        }
    }

    /** A digit pressed with the ring open: the sector [index] clockwise from the top runs, if it holds a command that can run. */
    fun pickSector(index: Int) {
        val open = radial ?: return
        val command =
            open.slots
                .getOrNull(index)
                ?.commandId
                ?.let { open.commands[it] }
                ?.takeIf { it.enabled }
        radial = null
        if (command != null) {
            host.thump()
            run(command)
        }
    }

    private fun escape() {
        when {
            overlayOpen -> closeOverlays()
            pendingTidy != null -> dismissProposal()
            knife != null -> knife = null
            measureState.first != null -> measureState = MeasureState()
            !penPath.isEmpty -> cancelPen()
            !selection.isEmpty -> selection = DrawSelection.EMPTY
            notice != null -> notice = null
        }
    }

    private fun afterHistory() {
        selection = selection.clippedTo(glyph ?: return)
    }

    // ---- the inspector's fields ----------------------------------------------------------------

    /** What a numeric field's `=` expressions can name: the font's metrics, and any glyph's advance and sidebearings. */
    fun exprScope(): ExprScope =
        object : ExprScope {
            private val font get() = session.state.value.font
            private val info get() = font.master(editor.ref.master)?.ufo?.fontInfo

            override fun metric(name: String): Double? =
                when (name) {
                    "xheight" -> info?.xHeight?.toDouble()
                    "capheight" -> info?.capHeight?.toDouble()
                    "ascender" -> info?.ascender?.toDouble()
                    "descender" -> info?.descender?.toDouble()
                    "baseline" -> 0.0
                    "upm" -> (info?.unitsPerEm ?: DEFAULT_UPM).toDouble()
                    else -> null
                }

            override fun glyphValue(
                glyph: String?,
                property: String,
            ): Double? {
                val g =
                    if (glyph ==
                        null
                    ) {
                        this@DrawController.glyph
                    } else {
                        font
                            .master(editor.ref.master)
                            ?.ufo
                            ?.glyphs
                            ?.firstOrNull { it.name == glyph }
                    }
                g ?: return null
                val sb = g.sidebearings()
                return when (property) {
                    "adv" -> g.advanceWidth.toDouble()
                    "lsb" -> sb?.left?.toDouble()
                    "rsb" -> sb?.right?.toDouble()
                    else -> null
                }
            }
        }

    /** Applies what was typed into the field [key]; null when it went in, otherwise the plain-words reason it did not (the field keeps what it had). */
    fun commitField(
        key: String,
        text: String,
    ): String? {
        val units =
            when (val result = NumericExpr.evaluate(text, exprScope())) {
                is ExprResult.Error -> return result.message
                is ExprResult.Value -> result.units
            }
        val outcome =
            when (key) {
                FieldKeys.X -> editor.placePoint(selection, x = units, y = null)
                FieldKeys.Y -> editor.placePoint(selection, x = null, y = units)
                FieldKeys.ADVANCE -> editor.setAdvance(units)
                FieldKeys.LSB -> editor.setSidebearings(units, null)
                FieldKeys.RSB -> editor.setSidebearings(null, units)
                else -> return "That field cannot be edited."
            }
        applied(outcome)
        return null
    }

    private var scrubKey: String? = null
    private var scrubStartValue = 0

    /** A sideways drag on the field [key] begins: one undo step for the whole scrub. */
    fun scrubStart(key: String) {
        scrubEnd(commit = true)
        val current = glyph ?: return
        scrubStartValue = currentValue(current, key) ?: return
        scrubKey = key
        val refused =
            when (key) {
                FieldKeys.X, FieldKeys.Y -> editor.beginDrag(editor.dragOpFor(selection))
                FieldKeys.ADVANCE -> editor.beginMetricScrub("Set advance")
                FieldKeys.LSB, FieldKeys.RSB -> editor.beginMetricScrub("Set sidebearings")
                else -> null
            }
        if (refused != null) {
            scrubKey = null
            applied(refused)
        }
    }

    /** The scrub is now [units] font units from where it began. */
    fun scrubTo(
        key: String,
        units: Int,
    ) {
        if (scrubKey != key) return
        when (key) {
            FieldKeys.X -> if (editor.dragTo(units.toDouble(), 0.0) != DragUpdate.FOLLOWED) scrubKey = null
            FieldKeys.Y -> if (editor.dragTo(0.0, units.toDouble()) != DragUpdate.FOLLOWED) scrubKey = null
            FieldKeys.ADVANCE -> if (editor.scrubAdvance(scrubStartValue + units) != DragUpdate.FOLLOWED) scrubKey = null
            FieldKeys.LSB -> if (editor.scrubSidebearings(scrubStartValue + units, null) != DragUpdate.FOLLOWED) scrubKey = null
            FieldKeys.RSB -> if (editor.scrubSidebearings(null, scrubStartValue + units) != DragUpdate.FOLLOWED) scrubKey = null
        }
    }

    /** The scrub ends; with [commit] false the glyph goes back to how it was. */
    fun scrubEnd(commit: Boolean) {
        scrubKey = null
        editor.endDrag(commit)
    }

    private fun currentValue(
        current: Glyph,
        key: String,
    ): Int? {
        val point =
            selection.points.singleOrNull()?.let {
                current.contours
                    .getOrNull(it.contour)
                    ?.points
                    ?.getOrNull(it.point)
            }
        val sb = current.sidebearings()
        return when (key) {
            FieldKeys.X -> point?.point?.x
            FieldKeys.Y -> point?.point?.y
            FieldKeys.ADVANCE -> current.advanceWidth
            FieldKeys.LSB -> sb?.left
            FieldKeys.RSB -> sb?.right
            else -> null
        }
    }

    // ---- DrawHooks ---------------------------------------------------------------------------

    override fun applied(outcome: EditOutcome) {
        when (outcome) {
            is EditOutcome.Done -> {
                selection = outcome.selection.clippedTo(glyph ?: return)
                notice = outcome.note?.let { toast(it) }
            }

            is EditOutcome.Locked -> {
                notice = DrawNotice.Locked(outcome.glyph)
            }

            is EditOutcome.Unchanged -> {
                notice = toast(outcome.reason)
            }

            is EditOutcome.Refused -> {
                notice = toast(outcome.message)
            }
        }
        closeOverlays()
    }

    override fun fitGlyph() = host.fitGlyph()

    override fun actualSize() = host.actualSize()

    override fun selectAll() {
        selection = DrawSelection.EMPTY.all(glyph ?: return)
    }

    override fun openPalette(goToGlyph: Boolean) = host.openPalette(goToGlyph)

    /** Puts [text] on the glass as a one-line notice that goes after a moment. */
    fun say(text: String) {
        notice = toast(text)
    }

    /** Makes [kind] the shape the Primitives tool drags out (the palette's shape entries). */
    fun pickShape(kind: PrimitiveKind) = host.pickPrimitive(kind)

    /** Unlocks the glyph from the notice's Unlock action. */
    fun unlockFromNotice() {
        applied(editor.unlock(selection))
    }

    private fun toast(text: String): DrawNotice.Toast = DrawNotice.Toast(text, ++toastSerial)

    private fun toolIsConnected(tool: Tool): Boolean {
        if (tool in CONNECTED_TOOLS) return true
        if (warnedTools.add(tool)) {
            notice =
                toast(
                    "${tool.label} is not connected to the sheet in this build yet. Select (V), Pen (P), Primitives (S) and Measure (M) are.",
                )
        }
        return false
    }

    private fun round(value: Double): Int = floor(value + 0.5).toInt()

    private fun signed(n: Int): String = if (n >= 0) "+$n" else "−${abs(n)}"

    companion object {
        /** The snap band for a mouse, in screen dp; a finger gets a wider one. */
        const val SNAP_BAND_DP = 6.0
        const val SNAP_BAND_TOUCH_DP = 10.0

        /** How far a pen click may be from the first point and still close the contour. */
        const val PEN_CLOSE_DP = 14.0

        /** The tools that do something on the sheet; the puck, the rail and the radial offer these and no others. */
        val CONNECTED_TOOLS: List<Tool> = listOf(Tool.SELECT, Tool.PEN, Tool.PRIMITIVES, Tool.MEASURE)

        private const val PEN_DRAG_DP = 4.0
        private const val KNIFE_SAME_DP = 10.0
        private const val DEFAULT_UPM = 1000
        private const val PRECISION_SCALE = 0.25
        private const val RADIAL_HUB_TAP_DP = 30.0
        private const val RECENT_COUNT = 3

        /** The node after (or before) the selected one, in contour order, wrapping through every contour; the first node if none is selected. */
        fun stepNode(
            glyph: Glyph,
            selection: DrawSelection,
            delta: Int,
        ): DrawSelection {
            val all: List<PointRef> =
                glyph.contours.flatMapIndexed {
                    c,
                    contour,
                    ->
                    contour.points.indices
                        .filter { contour.points[it].onCurve }
                        .map { PointRef(c, it) }
                }
            if (all.isEmpty()) return selection
            val current = selection.nodes(glyph).lastOrNull() ?: selection.points.maxWithOrNull(compareBy({ it.contour }, { it.point }))
            val at = all.indexOf(current)
            val next = if (at < 0) 0 else ((at + delta) % all.size + all.size) % all.size
            return DrawSelection(setOf(all[next]))
        }
    }
}

/** The short names the radial sets in its sectors (the board's DELETE, EXTREMA, BREAK, REVERSE, ALIGN, SMOOTH). */
object RadialLabels {
    fun of(command: DrawCommand): String =
        when (command.id) {
            "delete" -> "DELETE"
            "add-extremes" -> "EXTREMA"
            "break-handles" -> "BREAK"
            "reverse" -> "REVERSE"
            "align" -> "ALIGN"
            "toggle-smooth" -> if (command.label.startsWith("Make corner")) "CORNER" else "SMOOTH"
            "add-node" -> "ADD NODE"
            "tidy" -> "TIDY"
            "split" -> "SPLIT"
            "tangent" -> "TANGENT"
            "hobby" -> "HOBBY"
            "undo" -> "UNDO"
            "redo" -> "REDO"
            "fit" -> "FIT"
            "lock" -> if (command.label.startsWith("Unlock")) "UNLOCK" else "LOCK"
            "correct-direction" -> "DIRECTION"
            "select-all" -> "ALL"
            else -> command.label.uppercase()
        }

    /** The title line of a sector while the finger holds it: "SMOOTH ⇄ CORNER" for the toggle, else the command's own label. */
    fun title(command: DrawCommand): String = if (command.id == "toggle-smooth") "SMOOTH ⇄ CORNER" else command.label.uppercase()
}
