// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Direction
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.direction
import com.asoc.typewright.core.geometry.polygonContour
import com.asoc.typewright.core.geometry.reverse
import com.asoc.typewright.core.geometry.sidebearings
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.project.ProjectSession
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
)

/** A line the screen shows on the glass about what just happened (or why nothing did). */
sealed interface DrawNotice {
    /** Said once and gone after a moment; [serial] makes two identical lines two notices. */
    data class Toast(
        val text: String,
        val serial: Long,
    ) : DrawNotice

    /** The glyph is locked: the screen says so and offers Unlock. */
    data class Locked(
        val glyph: String,
    ) : DrawNotice
}

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
    var selection: DrawSelection by mutableStateOf(DrawSelection.EMPTY)
    var snaps: List<SnapTarget> by mutableStateOf(emptyList())
    var sweep: SweepShape? by mutableStateOf(null)
    var readout: Readout? by mutableStateOf(null)
    var radial: RadialUi? by mutableStateOf(null)
    var menu: MenuUi? by mutableStateOf(null)
    var notice: DrawNotice? by mutableStateOf(null)
    var penPoints: List<Vec2> by mutableStateOf(emptyList())
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

    /** True when something is open over the glyph that wants the next tap (the radial waiting, the rich menu). */
    val overlayOpen: Boolean get() = radial != null || menu != null

    // ---- pointer ---------------------------------------------------------------------------

    fun onOutput(
        output: CanvasOutput,
        frame: DrawFrame,
    ) {
        lastFrame = frame
        when (output) {
            is CanvasOutput.Tap -> tap(output, frame)
            is CanvasOutput.DoubleTap -> doubleTap(output)
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
        if (!toolIsConnected(frame.tool)) return
        if (frame.tool == Tool.PEN) {
            penTap(output.position, output.hit, frame)
            return
        }
        when (val hit = output.hit) {
            is Hit.OnPoint -> selection = if (output.additive) selection.toggled(hit.ref) else selection.only(hit.ref)
            is Hit.OnSegment -> selection = selection.onlySegment(hit.key, hit.t)
            Hit.Nothing -> if (!output.additive) selection = DrawSelection.EMPTY
        }
    }

    private fun doubleTap(output: CanvasOutput.DoubleTap) {
        val current = glyph ?: return
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
        if (overlayOpen || !toolIsConnected(frame.tool) || frame.tool == Tool.PEN) return
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
                    primary =
                        current.contours[hit.ref.contour]
                            .points[hit.ref.point]
                            .point
                            .toVec2()
                    if (!hit.onCurve && selection.points == setOf(hit.ref)) {
                        DragOp.MoveHandle(hit.ref, breakSymmetry = frame.alt)
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
        sweepPoints = mutableListOf(output.from)
        sweepLasso = output.lasso
        sweepAdditive = output.additive
        sweep = SweepShape(output.from, output.from, if (output.lasso) sweepPoints.toList() else null)
    }

    private fun moveSweep(output: CanvasOutput.SweepMoved) {
        val shape = sweep ?: return
        if (sweepLasso) sweepPoints.add(output.to)
        sweep = SweepShape(shape.from, output.to, if (sweepLasso) sweepPoints.toList() else null)
    }

    private fun endSweep(
        output: CanvasOutput.EndSweep,
        frame: DrawFrame,
    ) {
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
        val current = glyph ?: return
        val font = frame.space.toFont(position)
        val point = Vec2(floor(font.x + 0.5), floor(font.y + 0.5))
        val first = penPoints.firstOrNull()
        val closesLoop =
            first != null && penPoints.size >= MIN_PEN_POINTS &&
                (frame.space.toScreen(first) - position).length() <= PEN_CLOSE_DP
        if (closesLoop) {
            finishPen(current)
            return
        }
        if (hit is Hit.OnPoint) return
        penPoints = penPoints + point
    }

    private fun finishPen(current: Glyph) {
        val points = penPoints.map { Point(it.x.toInt(), it.y.toInt()) }
        penPoints = emptyList()
        if (points.size < MIN_PEN_POINTS) return
        val format = current.contours.firstOrNull()?.format ?: projectFormat()
        var contour = polygonContour(points, format)
        val inner = current.contours.any { DrawSubject.encloses(it, contour) }
        // A traced TrueType outline winds clockwise outside and anticlockwise inside; a UFO's the other way round.
        val wantAnticlockwise = (format == CurveFormat.CUBIC) != inner
        val isAnticlockwise = contour.direction() == Direction.COUNTER_CLOCKWISE
        if (wantAnticlockwise != isAnticlockwise) contour = contour.reverse()
        applied(editor.perform(EditPlan.Change("Draw contour", current.contours + contour, DrawSelection.EMPTY)))
    }

    private fun projectFormat(): CurveFormat {
        val master =
            session.state.value.font
                .master(editor.ref.master)
        return master?.ufo?.lib?.lineContourFormat ?: CurveFormat.CUBIC
    }

    fun cancelPen() {
        penPoints = emptyList()
    }

    // ---- keys --------------------------------------------------------------------------------

    /** Carries out [action]; true when it was handled. */
    fun onKey(
        action: DrawKeyAction,
        frame: DrawFrame,
    ): Boolean {
        lastFrame = frame
        val current = glyph
        when (action) {
            is DrawKeyAction.PickTool -> {
                host.pickTool(action.tool)
            }

            is DrawKeyAction.Nudge -> {
                applied(editor.moveBy(selection, action.dx, action.dy, label = "Nudge", merge = true))
            }

            DrawKeyAction.Undo -> {
                editor.undo()
                afterHistory()
            }

            DrawKeyAction.Redo -> {
                editor.redo()
                afterHistory()
            }

            DrawKeyAction.ToggleSmooth -> {
                applied(editor.toggleSmooth(selection))
            }

            DrawKeyAction.DeleteSelection -> {
                applied(editor.deleteSelection(selection))
            }

            DrawKeyAction.AddExtremes -> {
                applied(editor.addExtremes(selection))
            }

            DrawKeyAction.Tidy -> {
                applied(editor.tidy(selection))
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
            penPoints.isNotEmpty() -> cancelPen()
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
        if (key == FieldKeys.X || key == FieldKeys.Y) {
            val refused = editor.beginDrag(editor.dragOpFor(selection))
            if (refused != null) {
                scrubKey = null
                applied(refused)
            }
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
            FieldKeys.ADVANCE -> editor.setAdvance(scrubStartValue + units)
            FieldKeys.LSB -> editor.setSidebearings(scrubStartValue + units, null)
            FieldKeys.RSB -> editor.setSidebearings(null, scrubStartValue + units)
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

    /** Unlocks the glyph from the notice's Unlock action. */
    fun unlockFromNotice() {
        applied(editor.unlock(selection))
    }

    private fun toast(text: String): DrawNotice.Toast = DrawNotice.Toast(text, ++toastSerial)

    private fun toolIsConnected(tool: Tool): Boolean {
        if (tool == Tool.SELECT || tool == Tool.PEN) return true
        if (warnedTools.add(tool)) {
            notice = toast("${tool.label} is not connected to the sheet in this build yet. Select (V) and Pen (P) are.")
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

        private const val MIN_PEN_POINTS = 3
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
