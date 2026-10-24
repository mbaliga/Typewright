// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

/** The scope a command acts on, which is also the section of the rich menu it sits in (INTERACTION_V1 §5). */
enum class DrawScope(
    val title: String,
) {
    NODE("This node"),
    SEGMENT("This segment"),
    CONTOUR("This contour"),
    GLYPH("This glyph"),
    EDIT("Edit"),
}

/**
 * One command as every view of the registry shows it: the radial, the rich menu, the palette and
 * the shortcut sheet. [consequence] is what running it would do, measured (the point-count change
 * is the real operation run on a copy, law 5). A command with a [reason] is disabled and the
 * reason is shown with it; a [destructive] command sits last and is marked. [gesture] is the radial
 * sweep that triggers it ("hold · ↘") and [keys] the chord ("Ctrl ⇧ E"); the screen shows whichever
 * suits the input in use.
 */
data class DrawCommand(
    val id: String,
    val label: String,
    val scope: DrawScope,
    val consequence: String?,
    val reason: String?,
    val keys: String?,
    val gesture: String?,
    val destructive: Boolean,
    val run: () -> Unit,
) {
    val enabled: Boolean get() = reason == null
}

/** What the registry's commands call back into. */
interface DrawHooks {
    /** An edit finished: select what it says, and show its note, or the lock prompt, or its reason. */
    fun applied(outcome: EditOutcome)

    fun fitGlyph()

    fun actualSize()

    fun selectAll()

    fun openPalette(goToGlyph: Boolean)
}

/** Everything the registry reads to decide what each command would do right now. */
class DrawCommandContext(
    val editor: DrawEditor,
    val selection: DrawSelection,
    val metrics: DrawMetrics,
    val hooks: DrawHooks,
)

/** The one registry (INTERACTION_V1 §5): ids, labels, scopes, availability with reasons, measured consequences and bindings. */
object DrawCommands {
    private const val LOCKED_REASON = "locked: unlock to edit"

    /** The trigger chip of Undo: two fingers tapped together on the sheet, whatever ring is open. */
    const val TWO_FINGER_TAP = "2-finger tap"

    /** The trigger chip of Redo. */
    const val THREE_FINGER_TAP = "3-finger tap"

    /** Every Draw command for [ctx], in menu order. */
    fun build(ctx: DrawCommandContext): List<DrawCommand> {
        val editor = ctx.editor
        val glyph = editor.glyph ?: return emptyList()
        val locked = editor.isLocked
        val selection = ctx.selection
        val nodes = selection.nodes(glyph)
        val smoothNow = nodes.isNotEmpty() && nodes.all { glyph.contours[it.contour].points[it.point].smooth }
        // The radial sweep that triggers a command depends on what is selected: a node's ring is not a segment's.
        val ring = RadialLayouts.forSelection(selection)

        fun gesture(id: String): String? = gestureFor(id, listOf(ring))

        fun fromPlan(
            id: String,
            label: String,
            scope: DrawScope,
            plan: EditPlan,
            keys: String?,
            destructive: Boolean = false,
            consequenceOverride: String? = null,
        ): DrawCommand {
            val reason =
                when {
                    plan is EditPlan.Blocked -> plan.reason
                    locked -> LOCKED_REASON
                    else -> null
                }
            return DrawCommand(
                id = id,
                label = label,
                scope = scope,
                consequence = if (reason != null) null else consequenceOverride ?: editor.consequenceOf(plan),
                reason = reason,
                keys = keys,
                gesture = gesture(id),
                destructive = destructive,
                run = { ctx.hooks.applied(editor.perform(plan)) },
            )
        }

        val out = mutableListOf<DrawCommand>()
        // This node
        val smoothPlan = editor.planToggleSmooth(selection)
        out +=
            fromPlan(
                id = "toggle-smooth",
                label = if (smoothNow) "Make corner" else "Make smooth",
                scope = DrawScope.NODE,
                plan = smoothPlan,
                keys = "Enter",
                consequenceOverride = if (smoothNow) "smooth → corner · no change in points" else "corner → smooth · no change in points",
            )
        out +=
            fromPlan(
                "break-handles",
                "Break handles",
                DrawScope.NODE,
                editor.planBreakHandles(selection),
                "Ctrl ⇧ B",
                consequenceOverride = "handles become independent",
            )
        out +=
            fromPlan(
                "delete",
                if (nodes.isEmpty() &&
                    selection.handles(glyph).isNotEmpty()
                ) {
                    "Delete handle"
                } else {
                    "Delete node, keep the curve"
                },
                DrawScope.NODE,
                editor.planDelete(selection),
                "⌫",
            )
        out += fromPlan("align", "Align to a metric line", DrawScope.NODE, editor.planAlignToMetrics(selection, ctx.metrics), "Ctrl ⇧ A")
        // This segment
        val segment = selection.segment
        out +=
            fromPlan(
                id = "add-node",
                label = "Add node here",
                scope = DrawScope.SEGMENT,
                plan = if (segment == null) EditPlan.Blocked("select a segment first") else editor.planAddNode(segment, selection.segmentT),
                keys = null,
            )
        // This contour
        out += fromPlan("add-extremes", "Add extremes", DrawScope.CONTOUR, editor.planAddExtremes(selection), "Ctrl ⇧ E")
        out += fromPlan("reverse", "Reverse direction", DrawScope.CONTOUR, editor.planReverse(selection), "Ctrl Alt R")
        out += fromPlan("tidy", "Tidy with live count", DrawScope.CONTOUR, editor.planTidy(selection), "Ctrl ⇧ Y")
        // This glyph
        out += fromPlan("correct-direction", "Correct path direction", DrawScope.GLYPH, editor.planCorrectDirection(selection), "Ctrl ⇧ R")
        out +=
            if (locked) {
                DrawCommand(
                    "lock",
                    "Unlock glyph",
                    DrawScope.GLYPH,
                    "edits are kept as a diff",
                    null,
                    "Ctrl L",
                    gesture("lock"),
                    false,
                ) { ctx.hooks.applied(editor.unlock(ctx.selection)) }
            } else {
                DrawCommand(
                    "lock",
                    "Lock as approved",
                    DrawScope.GLYPH,
                    "edits then need an unlock and keep a diff",
                    null,
                    "Ctrl L",
                    gesture("lock"),
                    false,
                ) { ctx.hooks.applied(editor.approve(ctx.selection)) }
            }
        out += DrawCommand("fit", "Fit glyph", DrawScope.GLYPH, null, null, "Ctrl 0", gesture("fit"), false) { ctx.hooks.fitGlyph() }
        out +=
            DrawCommand(
                "actual-size",
                "Actual size",
                DrawScope.GLYPH,
                "1 unit to 1 dp",
                null,
                "Ctrl 1",
                null,
                false,
            ) { ctx.hooks.actualSize() }
        out +=
            DrawCommand(
                "select-all",
                "Select all",
                DrawScope.GLYPH,
                null,
                null,
                "Ctrl A",
                gesture("select-all"),
                false,
            ) { ctx.hooks.selectAll() }
        // Edit
        out +=
            DrawCommand(
                "undo",
                "Undo",
                DrawScope.EDIT,
                null,
                if (editor.canUndo) null else "nothing to undo",
                "Ctrl Z",
                TWO_FINGER_TAP,
                false,
            ) {
                editor.undo()
                ctx.hooks.applied(EditOutcome.Done(ctx.selection))
            }
        out +=
            DrawCommand(
                "redo",
                "Redo",
                DrawScope.EDIT,
                null,
                if (editor.canRedo) null else "nothing to redo",
                "Ctrl ⇧ Z",
                THREE_FINGER_TAP,
                false,
            ) {
                editor.redo()
                ctx.hooks.applied(EditOutcome.Done(ctx.selection))
            }
        out += fromPlan("clear", "Clear glyph", DrawScope.GLYPH, editor.planClear(), null, destructive = true)
        return out
    }

    /** The radial sweep that triggers [commandId] on the ring [layout] ("hold · ↘"), or null if it is not on the ring. */
    fun gestureFor(
        commandId: String,
        layouts: List<List<String?>> = listOf(RadialLayouts.NODE, RadialLayouts.SEGMENT, RadialLayouts.GLYPH),
    ): String? {
        val slot = layouts.firstNotNullOfOrNull { layout -> layout.indexOf(commandId).takeIf { it >= 0 } } ?: return null
        // The finger sweeps to the side opposite the sector it wants.
        val fingerAngle = (slot * 45 + 180) % 360
        return "hold · " + ARROWS[fingerAngle / 45]
    }

    private val ARROWS = listOf("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")
}
