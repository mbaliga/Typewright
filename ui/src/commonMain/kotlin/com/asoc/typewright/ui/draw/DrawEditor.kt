// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.enforceContourDirections
import com.asoc.typewright.core.geometry.reverse
import com.asoc.typewright.core.geometry.segmentLayout
import com.asoc.typewright.core.geometry.tidied
import com.asoc.typewright.core.geometry.withExtremesAdded
import com.asoc.typewright.core.geometry.withHandlesRemoved
import com.asoc.typewright.core.geometry.withNodeDeleted
import com.asoc.typewright.core.geometry.withNodeInserted
import com.asoc.typewright.core.geometry.withSelectionMoved
import com.asoc.typewright.core.geometry.withSmooth
import com.asoc.typewright.core.geometry.writtenCount
import com.asoc.typewright.project.ApprovalOrigin
import com.asoc.typewright.project.Approve
import com.asoc.typewright.project.EditResult
import com.asoc.typewright.project.FontCommand
import com.asoc.typewright.project.Gesture
import com.asoc.typewright.project.GlyphLock
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.LockState
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.Refusal
import com.asoc.typewright.project.ReplaceOutline
import com.asoc.typewright.project.SetAdvance
import com.asoc.typewright.project.SetSidebearings
import com.asoc.typewright.project.Unlock
import kotlin.math.abs

/** What an edit did, in the terms the screen needs to answer with. */
sealed interface EditOutcome {
    /** The edit went in as one undo step; [selection] is what should be selected afterwards and [note], when set, says plainly what happened. */
    data class Done(
        val selection: DrawSelection,
        val note: String? = null,
    ) : EditOutcome

    /** The glyph is approved and locked, so the edit was refused (law 1); the screen offers Unlock. */
    data class Locked(
        val glyph: String,
    ) : EditOutcome {
        val message: String get() = "$glyph is approved and locked. Unlock it to edit; the change is kept as a diff."
    }

    /** Nothing was changed, and [reason] says why in a sentence. */
    data class Unchanged(
        val reason: String,
    ) : EditOutcome

    /** The project refused for another reason. */
    data class Refused(
        val message: String,
    ) : EditOutcome
}

/**
 * An edit worked out but not yet made: the outline it would produce ([contours]), what should be
 * selected afterwards, and the note to show. The menu reads a plan to say what a row would do (its
 * measured point-count change) and whether it can run; [DrawEditor.perform] makes it.
 */
sealed interface EditPlan {
    data class Change(
        val label: String,
        val contours: List<Contour>,
        val selection: DrawSelection,
        val note: String? = null,
        val coalesceKey: Any? = null,
    ) : EditPlan

    /** The edit cannot be made, for [reason] in a sentence. */
    data class Blocked(
        val reason: String,
    ) : EditPlan
}

/** What a drag update did. */
enum class DragUpdate {
    /** The outline followed the pointer. */
    FOLLOWED,

    /** The session ended the drag (an undo, an edit from elsewhere): nothing more is drawn, and the caller stops. */
    ENDED,

    /** The glyph is locked or the project refused; the drag is over. */
    REFUSED,
}

/**
 * Every edit Draw makes to one glyph, through the open [session] and nothing else (the user's
 * drawing is the source of truth; this never holds a second copy of it). The geometry is
 * `core-geometry`'s contour editing, so a drag, a typed value and a menu command move the same
 * points the same way. A discrete edit is one `execute`, one undo step; a drag is one session
 * gesture, committed once at the end.
 *
 * The drag's lifecycle is the editor's problem, not the caller's: [dragTo] asks the gesture whether
 * it is still open before every update (the session ends it on an undo, an execute or a new
 * gesture), and [endDrag] is safe to call any number of times, so a pointer loop can end the drag
 * in a `finally`.
 */
class DrawEditor(
    private val session: ProjectSession,
    val ref: GlyphRef,
) {
    private var gesture: Gesture? = null
    private var dragStart: Glyph? = null
    private var dragOp: DragOp? = null

    /** The glyph as the project has it now, or null if it no longer exists. */
    val glyph: Glyph? get() =
        session.state.value.font
            .glyph(ref)

    val lock: GlyphLock? get() = session.lockOf(ref)

    val isLocked: Boolean get() = lock?.state == LockState.LOCKED

    /** True while a drag is open in the session. */
    val isDragging: Boolean get() = gesture?.isOpen == true

    // ---- dragging --------------------------------------------------------------------------

    /** Opens a drag of [op]. Null means it began; anything else is why it did not. */
    fun beginDrag(op: DragOp): EditOutcome? {
        endDrag(commit = true)
        val start = glyph ?: return missing()
        if (isLocked) return EditOutcome.Locked(start.name)
        gesture = session.beginGesture(op.label)
        dragStart = start
        dragOp = op
        return null
    }

    /** Moves the open drag to a total movement of ([dxUnits], [dyUnits]) font units from where it began. */
    fun dragTo(
        dxUnits: Double,
        dyUnits: Double,
    ): DragUpdate {
        val open = gesture
        val start = dragStart
        val op = dragOp
        if (open == null || start == null || op == null || !open.isOpen) {
            clearDrag()
            return DragUpdate.ENDED
        }
        val result = open.update(ReplaceOutline(ref, DragPlan.apply(start, op, dxUnits, dyUnits), op.label))
        if (result is EditResult.Refused) {
            open.cancel()
            clearDrag()
            return DragUpdate.REFUSED
        }
        return DragUpdate.FOLLOWED
    }

    /**
     * Opens a scrub of the glyph's advance or sidebearings: one session gesture, so everything scrubbed
     * between here and [endDrag] is a single undo step by construction, however long the finger rests,
     * and cancelling it puts the glyph back. Width is not shape, so a locked glyph may be scrubbed.
     * Null means it began; anything else is why it did not.
     */
    fun beginMetricScrub(label: String): EditOutcome? {
        endDrag(commit = true)
        if (glyph == null) return missing()
        gesture = session.beginGesture(label)
        return null
    }

    /** Sets the advance width to [width] inside the open scrub. */
    fun scrubAdvance(width: Int): DragUpdate = scrubMetric(SetAdvance(ref, width))

    /** Sets the sidebearing(s) inside the open scrub; either may be null to leave that side as it is. */
    fun scrubSidebearings(
        left: Int?,
        right: Int?,
    ): DragUpdate = scrubMetric(SetSidebearings(ref, left, right))

    private fun scrubMetric(command: FontCommand): DragUpdate {
        val open = gesture
        // An outline drag is not a metric scrub: dragOp is only set by beginDrag.
        if (open == null || !open.isOpen || dragOp != null) {
            clearDrag()
            return DragUpdate.ENDED
        }
        if (open.update(command) is EditResult.Refused) {
            open.cancel()
            clearDrag()
            return DragUpdate.REFUSED
        }
        return DragUpdate.FOLLOWED
    }

    /** Ends the drag: one undo step when [commit], otherwise as if it never began. Does nothing if no drag is open. */
    fun endDrag(commit: Boolean) {
        val open = gesture
        if (open != null && open.isOpen) {
            if (commit) open.commit() else open.cancel()
        }
        clearDrag()
    }

    private fun clearDrag() {
        gesture = null
        dragStart = null
        dragOp = null
    }

    // ---- performing a plan -------------------------------------------------------------------

    /** Makes [plan] as one undo step, or says why it was not made. */
    fun perform(plan: EditPlan): EditOutcome {
        val current = glyph ?: return missing()
        return when (plan) {
            is EditPlan.Blocked -> {
                EditOutcome.Unchanged(plan.reason)
            }

            is EditPlan.Change -> {
                if (isLocked) return EditOutcome.Locked(current.name)
                if (plan.contours == current.contours) return EditOutcome.Unchanged("That would not change the outline.")
                when (val result = session.execute(ReplaceOutline(ref, plan.contours, plan.label, plan.coalesceKey))) {
                    is EditResult.Applied -> {
                        EditOutcome.Done(plan.selection.clippedTo(glyph ?: current), plan.note)
                    }

                    is EditResult.NoChange -> {
                        EditOutcome.Unchanged("That would not change the outline.")
                    }

                    is EditResult.Refused -> {
                        if (result.refusal is Refusal.Locked) {
                            EditOutcome.Locked(
                                current.name,
                            )
                        } else {
                            EditOutcome.Refused(result.refusal.message)
                        }
                    }
                }
            }
        }
    }

    // ---- moving ----------------------------------------------------------------------------

    /**
     * Moves what is selected by ([dx], [dy]) font units. This is what an arrow key, a typed x or y and
     * a drag all come down to: nodes take their handles with them and straight sides stay straight.
     * With [merge] a burst of nudges is one undo step.
     */
    fun planMove(
        selection: DrawSelection,
        dx: Int,
        dy: Int,
        label: String = "Move",
        merge: Boolean = false,
    ): EditPlan = planning { computeMove(selection, dx, dy, label, merge) }

    private fun computeMove(
        selection: DrawSelection,
        dx: Int,
        dy: Int,
        label: String,
        merge: Boolean,
    ): EditPlan {
        val current = glyph ?: return EditPlan.Blocked("There is no glyph to edit.")
        if (selection.points.isEmpty()) return EditPlan.Blocked("Select a node or a handle to move.")
        // The same plan a drag of this selection makes, so a typed value, an arrow key and a drag cannot disagree.
        val contours = DragPlan.apply(current, dragOpFor(selection), dx.toDouble(), dy.toDouble())
        // A smooth node keeps its handles in line wherever they can turn; where nothing of its own can, it becomes a corner, and that is said.
        val cornered = current.contours.sumOf { c -> c.points.count { it.smooth } } - contours.sumOf { c -> c.points.count { it.smooth } }
        val note =
            when {
                cornered <= 0 -> null
                cornered == 1 -> "A node is now a corner: nothing of its own could stay in line with its sides."
                else -> "$cornered nodes are now corners: nothing of their own could stay in line with their sides."
            }
        return EditPlan.Change(label, contours, selection, note, coalesceKey = if (merge) Triple(ref, "nudge", selection.points) else null)
    }

    /**
     * The drag that moves [selection]: one handle on its own, which swings the other handle of a smooth
     * node to stay in line (unless [breakSymmetry]), or otherwise everything selected together, nodes with
     * their handles.
     */
    fun dragOpFor(
        selection: DrawSelection,
        breakSymmetry: Boolean = false,
    ): DragOp {
        val only = selection.points.singleOrNull()
        val isHandle =
            only != null && glyph
                ?.contours
                ?.getOrNull(only.contour)
                ?.points
                ?.getOrNull(only.point)
                ?.onCurve == false
        return if (only != null && isHandle) DragOp.MoveHandle(only, breakSymmetry) else DragOp.MoveSelection(selection.points)
    }

    fun moveBy(
        selection: DrawSelection,
        dx: Int,
        dy: Int,
        label: String = "Move",
        merge: Boolean = false,
    ): EditOutcome = perform(planMove(selection, dx, dy, label, merge))

    /** Moves the single selected point so it sits at ([x], [y]); a null coordinate stays as it is. Same movement as a drag. */
    fun placePoint(
        selection: DrawSelection,
        x: Int?,
        y: Int?,
    ): EditOutcome {
        val current = glyph ?: return missing()
        val point = selection.points.singleOrNull() ?: return EditOutcome.Unchanged("Select one point to place it.")
        val at = current.contours[point.contour].points[point.point].point
        val wanted = Point(x ?: at.x, y ?: at.y)
        val outcome = moveBy(selection, wanted.x - at.x, wanted.y - at.y, label = "Set position")
        if (outcome !is EditOutcome.Done) return outcome
        val landed =
            glyph
                ?.contours
                ?.getOrNull(point.contour)
                ?.points
                ?.getOrNull(point.point)
                ?.point
        // The handle of a smooth node beside a straight side may only slide along it; say where it went rather than leave the field disagreeing.
        return if (landed != null && landed != wanted) {
            outcome.copy(
                note =
                    "Placed at ${landed.x}, ${landed.y}: a smooth node's handle stays on the straight side it is in line with. " +
                        "Make the node a corner to place it freely.",
            )
        } else {
            outcome
        }
    }

    // ---- nodes -----------------------------------------------------------------------------

    /** Smooth becomes corner and corner becomes smooth: if every selected node is smooth they all become corners, otherwise they all become smooth. */
    fun planToggleSmooth(selection: DrawSelection): EditPlan = planning { computeToggleSmooth(selection) }

    private fun computeToggleSmooth(selection: DrawSelection): EditPlan {
        val current = glyph ?: return EditPlan.Blocked("There is no glyph to edit.")
        val nodes = selection.nodes(current)
        if (nodes.isEmpty()) return EditPlan.Blocked("Select a node first.")
        val makeSmooth = !nodes.all { current.contours[it.contour].points[it.point].smooth }
        return smoothPlan(current, selection, nodes, makeSmooth, if (makeSmooth) "Make smooth" else "Make corner")
    }

    fun toggleSmooth(selection: DrawSelection): EditOutcome = perform(planToggleSmooth(selection))

    /** Makes every selected smooth node a corner, so its two handles are independent. */
    fun planBreakHandles(selection: DrawSelection): EditPlan = planning { computeBreakHandles(selection) }

    private fun computeBreakHandles(selection: DrawSelection): EditPlan {
        val current = glyph ?: return EditPlan.Blocked("There is no glyph to edit.")
        val nodes = selection.nodes(current).filter { current.contours[it.contour].points[it.point].smooth }
        if (nodes.isEmpty()) return EditPlan.Blocked("The handles are already independent.")
        return smoothPlan(current, selection, nodes, false, "Break handles")
    }

    fun breakHandles(selection: DrawSelection): EditOutcome = perform(planBreakHandles(selection))

    private fun smoothPlan(
        current: Glyph,
        selection: DrawSelection,
        nodes: List<PointRef>,
        smooth: Boolean,
        label: String,
    ): EditPlan {
        val contours = current.contours.toMutableList()
        var stayed = 0
        for (node in nodes) {
            val after = contours[node.contour].withSmooth(node.point, smooth)
            // A node that cannot be smooth without moving what is not its own stays a corner; the flag is never set where it would lie.
            if (after.points[node.point].smooth != smooth) stayed++
            contours[node.contour] = after
        }
        if (stayed == nodes.size) {
            return EditPlan.Blocked(
                if (nodes.size == 1) {
                    "This corner has no handle of its own to line up, so it cannot be smooth. Pull a handle out of one side first."
                } else {
                    "None of these corners has a handle of its own to line up, so none can be smooth. Pull a handle out of one side first."
                },
            )
        }
        val note =
            if (stayed > 0) {
                "$stayed of ${nodes.size} nodes stay corners: they have no handle of their own to line up. Pull a handle out of a side first."
            } else {
                null
            }
        return EditPlan.Change(label, contours, selection, note)
    }

    /**
     * Deletes what is selected. Nodes go and the curve is kept as close as one segment can; a contour
     * left with too little to draw goes with them, and the note says so. With only handles selected
     * they are taken away (retracted onto their node on a cubic outline, removed from a quadratic
     * one) and the note says which, rather than leaving the person to guess.
     */
    fun planDelete(selection: DrawSelection): EditPlan = planning { computeDelete(selection) }

    private fun computeDelete(selection: DrawSelection): EditPlan {
        val current = glyph ?: return EditPlan.Blocked("There is no glyph to edit.")
        val nodes = selection.nodes(current)
        if (nodes.isNotEmpty()) return deleteNodesPlan(current, nodes)
        val handles = selection.handles(current)
        if (handles.isEmpty()) return EditPlan.Blocked("Select a node or a handle to delete.")
        val contours = current.contours.toMutableList()
        var retracted = 0
        val quadraticNotes = mutableListOf<String>()
        var cornered = 0
        for (contourIndex in handles.map { it.contour }.toSet()) {
            val contour = contours[contourIndex]
            val picked = handles.filter { it.contour == contourIndex }.map { it.point }.toSet()
            val after = contour.withHandlesRemoved(picked)
            if (after != contour) {
                if (contour.format == CurveFormat.CUBIC) {
                    retracted += picked.size
                } else {
                    QuadraticHandleRemoval.of(contour, picked).sentence()?.let { quadraticNotes += it }
                }
                cornered += contour.points.count { it.smooth } - after.points.count { it.smooth }
            }
            contours[contourIndex] = after
        }
        val cornerNote =
            when (cornered) {
                0 -> ""
                1 -> " A node is now a corner: its sides no longer meet in line."
                else -> " $cornered nodes are now corners: their sides no longer meet in line."
            }
        val note =
            when {
                retracted > 0 || quadraticNotes.isNotEmpty() -> {
                    val cubic =
                        if (retracted > 0) {
                            "Retracted $retracted handle${plural(retracted)} onto ${if (retracted == 1) "its node" else "their nodes"}."
                        } else {
                            null
                        }
                    (listOfNotNull(cubic) + quadraticNotes).joinToString(" ") + cornerNote
                }

                else -> {
                    return EditPlan.Blocked("That handle is already retracted onto its node.")
                }
            }
        return EditPlan.Change("Delete handle", contours, DrawSelection.EMPTY, note)
    }

    fun deleteSelection(selection: DrawSelection): EditOutcome = perform(planDelete(selection))

    private fun deleteNodesPlan(
        current: Glyph,
        nodes: List<PointRef>,
    ): EditPlan {
        val contours = current.contours.toMutableList()
        val dropped = mutableSetOf<Int>()
        for (contourIndex in nodes.map { it.contour }.toSet()) {
            var contour: Contour? = contours[contourIndex]
            val targets =
                nodes
                    .filter { it.contour == contourIndex }
                    .map { current.contours[contourIndex].points[it.point].point }
                    .toMutableSet()
            while (contour != null && targets.isNotEmpty()) {
                val here: Contour = contour
                val index = here.points.indices.lastOrNull { here.points[it].onCurve && here.points[it].point in targets } ?: break
                targets.remove(here.points[index].point)
                contour = here.withNodeDeleted(index)
            }
            if (contour == null) dropped += contourIndex else contours[contourIndex] = contour
        }
        val kept = contours.filterIndexed { i, _ -> i !in dropped }
        val note =
            if (dropped.isNotEmpty()) {
                "Removed ${dropped.size} contour${plural(
                    dropped.size,
                )}: too few nodes were left to draw one."
            } else {
                null
            }
        return EditPlan.Change("Delete node${plural(nodes.size)}", kept, DrawSelection.EMPTY, note)
    }

    /** Adds a node to segment [key] at [t] without changing the curve; the new node is selected. */
    fun planAddNode(
        key: SegmentKey,
        t: Double,
    ): EditPlan =
        planning {
            val current = glyph ?: return@planning EditPlan.Blocked("There is no glyph to edit.")
            val contour = current.contours.getOrNull(key.contour) ?: return@planning EditPlan.Blocked(SEGMENT_GONE)
            // A segment picked before an undo or an edit elsewhere may no longer exist: say so, never ask the geometry for it.
            if (key.segment !in contour.segmentLayout().indices) return@planning EditPlan.Blocked(SEGMENT_GONE)
            val inserted = contour.withNodeInserted(key.segment, t.coerceIn(MIN_INSERT_T, 1.0 - MIN_INSERT_T))
            val contours = current.contours.toMutableList().also { it[key.contour] = inserted.contour }
            EditPlan.Change("Add node", contours, DrawSelection(setOf(PointRef(key.contour, inserted.nodeIndex))))
        }

    fun addNode(
        key: SegmentKey,
        t: Double,
    ): EditOutcome = perform(planAddNode(key, t))

    // ---- contours and the glyph --------------------------------------------------------------

    /** Reverses the selected contours (or every contour when none is selected). */
    fun planReverse(selection: DrawSelection): EditPlan = planning { computeReverse(selection) }

    private fun computeReverse(selection: DrawSelection): EditPlan {
        val current = glyph ?: return EditPlan.Blocked("There is no glyph to edit.")
        if (current.contours.isEmpty()) return EditPlan.Blocked("There is no contour to reverse.")
        val which = contoursFor(current, selection)
        val contours = current.contours.mapIndexed { i, c -> if (i in which) c.reverse() else c }
        // Reversing re-orders a contour's points, so a point selection would name different points; select the contour instead.
        val keep = selection.points.filter { it.contour !in which }.toMutableSet()
        for (c in which) {
            if (selection.indicesIn(c).isNotEmpty()) {
                keep +=
                    current.contours[c]
                        .points.indices
                        .map { PointRef(c, it) }
            }
        }
        return EditPlan.Change("Reverse direction", contours, DrawSelection(keep))
    }

    fun reverseContours(selection: DrawSelection): EditOutcome = perform(planReverse(selection))

    /** Makes outer contours wind the way the project's format expects and inner ones the other way (`enforceContourDirections`). */
    fun planCorrectDirection(selection: DrawSelection): EditPlan = planning { computeCorrectDirection(selection) }

    private fun computeCorrectDirection(selection: DrawSelection): EditPlan {
        val current = glyph ?: return EditPlan.Blocked("There is no glyph to edit.")
        if (current.contours.isEmpty()) return EditPlan.Blocked("There is no contour to correct.")
        val contours = enforceContourDirections(current.contours)
        if (contours == current.contours) return EditPlan.Blocked("Every contour already winds the right way.")
        return EditPlan.Change("Correct direction", contours, selection)
    }

    fun correctDirection(selection: DrawSelection): EditOutcome = perform(planCorrectDirection(selection))

    /** Adds a node at every curve extreme of the selected contours (cubic outlines only). */
    fun planAddExtremes(selection: DrawSelection): EditPlan = planning { computeAddExtremes(selection) }

    private fun computeAddExtremes(selection: DrawSelection): EditPlan {
        val current = glyph ?: return EditPlan.Blocked("There is no glyph to edit.")
        if (current.contours.isEmpty()) return EditPlan.Blocked("There is no contour to work on.")
        val which = contoursFor(current, selection).filter { current.contours[it].format == CurveFormat.CUBIC }
        if (which.isEmpty()) return EditPlan.Blocked("Adding extremes needs a cubic outline; this glyph's contours are quadratic.")
        val contours = current.contours.mapIndexed { i, c -> if (i in which) c.withExtremesAdded() else c }
        if (contours == current.contours) return EditPlan.Blocked("Already has its extremes.")
        return EditPlan.Change("Add extremes", contours, DrawSelection.EMPTY)
    }

    fun addExtremes(selection: DrawSelection): EditOutcome = perform(planAddExtremes(selection))

    /** Removes nodes that do not change the shape, from the selected contours (cubic outlines only). */
    fun planTidy(selection: DrawSelection): EditPlan = planning { computeTidy(selection) }

    private fun computeTidy(selection: DrawSelection): EditPlan {
        val current = glyph ?: return EditPlan.Blocked("There is no glyph to edit.")
        if (current.contours.isEmpty()) return EditPlan.Blocked("There is no contour to work on.")
        val which = contoursFor(current, selection).filter { current.contours[it].format == CurveFormat.CUBIC }
        if (which.isEmpty()) return EditPlan.Blocked("Tidying needs a cubic outline; this glyph's contours are quadratic.")
        val contours = current.contours.mapIndexed { i, c -> if (i in which) c.tidied().contour else c }
        if (contours == current.contours) return EditPlan.Blocked("Nothing to tidy: every node is needed.")
        return EditPlan.Change("Tidy", contours, DrawSelection.EMPTY)
    }

    fun tidy(selection: DrawSelection): EditOutcome = perform(planTidy(selection))

    /** Moves each selected node to the nearest metric line within [withinUnits] (vertically), carrying its handles. */
    fun planAlignToMetrics(
        selection: DrawSelection,
        metrics: DrawMetrics,
        withinUnits: Double = ALIGN_WITHIN_UNITS,
    ): EditPlan = planning { computeAlignToMetrics(selection, metrics, withinUnits) }

    private fun computeAlignToMetrics(
        selection: DrawSelection,
        metrics: DrawMetrics,
        withinUnits: Double,
    ): EditPlan {
        val current = glyph ?: return EditPlan.Blocked("There is no glyph to edit.")
        val nodes = selection.nodes(current)
        if (nodes.isEmpty()) return EditPlan.Blocked("Select a node first.")
        val contours = current.contours.toMutableList()
        var moved = 0
        for (node in nodes) {
            val at = contours[node.contour].points[node.point].point
            val line = metrics.nearestLine(at.y.toDouble(), withinUnits) ?: continue
            if (line.y == at.y) continue
            contours[node.contour] = contours[node.contour].withSelectionMoved(setOf(node.point), 0, line.y - at.y)
            moved++
        }
        if (moved == 0) return EditPlan.Blocked("Nothing within ${withinUnits.toInt()} units of a metric line.")
        return EditPlan.Change("Align to metric line", contours, selection)
    }

    fun alignToMetrics(
        selection: DrawSelection,
        metrics: DrawMetrics,
    ): EditOutcome = perform(planAlignToMetrics(selection, metrics))

    /** Takes every contour out of the glyph. One undo step brings them back. */
    fun planClear(): EditPlan = planning { computeClear() }

    private fun computeClear(): EditPlan {
        val current = glyph ?: return EditPlan.Blocked("There is no glyph to edit.")
        if (current.contours.isEmpty()) return EditPlan.Blocked("The glyph is already empty.")
        return EditPlan.Change("Clear glyph", emptyList(), DrawSelection.EMPTY)
    }

    fun clearGlyph(): EditOutcome = perform(planClear())

    // ---- the glyph's own metrics ------------------------------------------------------------

    /** Sets the advance width; repeated sets in a burst (a scrub) are one undo step. Allowed on a locked glyph: width is not shape. */
    fun setAdvance(width: Int): EditOutcome = metricCommand(SetAdvance(ref, width))

    /** Sets the sidebearing(s) as the project measures them; either may be null to leave that side as it is. */
    fun setSidebearings(
        left: Int?,
        right: Int?,
    ): EditOutcome = metricCommand(SetSidebearings(ref, left, right))

    private fun metricCommand(command: FontCommand): EditOutcome =
        when (val result = session.execute(command)) {
            is EditResult.Applied -> {
                EditOutcome.Done(DrawSelection.EMPTY)
            }

            is EditResult.NoChange -> {
                EditOutcome.Unchanged("That is the value already.")
            }

            is EditResult.Refused -> {
                if (result.refusal is Refusal.Locked) {
                    EditOutcome.Locked(
                        ref.glyph,
                    )
                } else {
                    EditOutcome.Refused(result.refusal.message)
                }
            }
        }

    // ---- locks -----------------------------------------------------------------------------

    /** Approves and locks the glyph as it is; what is [selected] stays selected. */
    fun approve(selected: DrawSelection = DrawSelection.EMPTY): EditOutcome =
        lockCommand(Approve(ref, ApprovalOrigin.DRAW), "${ref.glyph} is locked as approved.", selected)

    /** Unlocks the glyph for editing, the session recording the unlock and keeping the diff; what is [selected] stays selected. */
    fun unlock(selected: DrawSelection = DrawSelection.EMPTY): EditOutcome =
        lockCommand(Unlock(ref), "${ref.glyph} is unlocked. Edits are kept as a diff against the approved outline.", selected)

    private fun lockCommand(
        command: FontCommand,
        note: String,
        selected: DrawSelection,
    ): EditOutcome =
        when (val result = session.execute(command)) {
            is EditResult.Applied -> EditOutcome.Done(selected, note)
            is EditResult.NoChange -> EditOutcome.Unchanged("${ref.glyph} is already ${if (command is Unlock) "unlocked" else "locked"}.")
            is EditResult.Refused -> EditOutcome.Refused(result.refusal.message)
        }

    // ---- undo and redo ---------------------------------------------------------------------

    fun undo(): Boolean = session.undo()

    fun redo(): Boolean = session.redo()

    val canUndo: Boolean get() = session.history.value.canUndo || isDragging

    val canRedo: Boolean get() = session.history.value.canRedo

    // ---- consequence estimates ---------------------------------------------------------------

    /**
     * The measured effect of [plan]: "−1 on · −2 off", "no change in points", or the reason it cannot
     * be made. Run through the session's own `simulate`, so the number is what the edit would really
     * do, not a guess (law 5), and a locked glyph comes back as "locked".
     */
    fun consequenceOf(plan: EditPlan): String {
        if (plan is EditPlan.Blocked) return plan.reason
        plan as EditPlan.Change
        val simulation = session.simulate(ReplaceOutline(ref, plan.contours, plan.label))
        return when (val result = simulation.result) {
            is EditResult.Refused -> {
                if (result.refusal is Refusal.Locked) "locked" else result.refusal.message
            }

            is EditResult.NoChange -> {
                "no change"
            }

            is EditResult.Applied -> {
                // Counted as the file stores the outline, so a straight side of a cubic contour is not two off-curve points.
                val current = glyph
                if (current == null) {
                    "changes the outline"
                } else {
                    val before = current.writtenCount()
                    val after = current.copy(contours = plan.contours).writtenCount()
                    val on = after.onCurveEquivalent - before.onCurveEquivalent
                    val off = after.offCurve - before.offCurve
                    if (on == 0 && off == 0) "no change in points" else "${signed(on)} on · ${signed(off)} off"
                }
            }
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    /**
     * Runs a planner so that it can never throw into a menu, a key or a composition: a plan that cannot
     * be worked out (a selection that names something an undo removed, an outline the geometry cannot edit
     * that way) is "not available", with the reason a sentence, and the outline is not touched.
     */
    private inline fun planning(block: () -> EditPlan): EditPlan =
        try {
            block()
        } catch (_: IllegalArgumentException) {
            EditPlan.Blocked(NOT_AVAILABLE)
        } catch (_: IndexOutOfBoundsException) {
            EditPlan.Blocked(NOT_AVAILABLE)
        } catch (_: NoSuchElementException) {
            EditPlan.Blocked(NOT_AVAILABLE)
        } catch (_: IllegalStateException) {
            EditPlan.Blocked(NOT_AVAILABLE)
        }

    private fun missing(): EditOutcome = EditOutcome.Refused("There is no glyph named ${ref.glyph} to edit.")

    private fun contoursFor(
        glyph: Glyph,
        selection: DrawSelection,
    ): Set<Int> = if (selection.contours.isEmpty()) glyph.contours.indices.toSet() else selection.contours

    private fun signed(n: Int): String =
        if (n > 0) {
            "+$n"
        } else if (n < 0) {
            "−${abs(n)}"
        } else {
            "0"
        }

    private fun plural(n: Int): String = if (n == 1) "" else "s"

    companion object {
        /** How far a node may be from a metric line, in font units, and still align to it (the V1 menu says "within 20 units"). */
        const val ALIGN_WITHIN_UNITS: Double = 20.0

        private const val MIN_INSERT_T = 0.04
        private const val SEGMENT_GONE = "That segment is gone."
        private const val NOT_AVAILABLE = "That is not available for this outline."

        /** The editor for the glyph named [name] in the default master of [session], or null if the project has no such glyph. */
        fun forGlyph(
            session: ProjectSession,
            name: String,
        ): DrawEditor? {
            val font = session.state.value.font
            val master = font.masters.firstOrNull { it.isDefault } ?: font.masters.firstOrNull() ?: return null
            if (master.ufo.glyphs.none { it.name == name }) return null
            return DrawEditor(session, GlyphRef(master.id, name))
        }
    }
}
