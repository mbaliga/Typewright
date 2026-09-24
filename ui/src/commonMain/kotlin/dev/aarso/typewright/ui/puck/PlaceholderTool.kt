package dev.aarso.typewright.ui.puck

/**
 * A fixed, small placeholder tool list, enough to prove the puck's swipe/tap/hold mechanism
 * (task P4b). Real tool content -- the actual construction tools brief §5.1 names (select, pen,
 * primitives, boolean, stroke, measure, anchors, metrics) with their own icons and shortcut
 * letters -- is P5b's job; wiring it in only needs a different `List<PlaceholderTool>` (or a real
 * `Tool` type altogether) passed to [dev.aarso.typewright.ui.puck.Puck], not a change to the
 * gesture machine, which already knows only sector/step indices, never tool identity.
 */
enum class PlaceholderTool(
    val label: String,
    /** The shortcut letter brief §5.1 assigns real tools; kept here only for the puck's desktop label, not wired to a keyboard handler in this task. */
    val shortcut: Char,
) {
    SELECT("SELECT", 'V'),
    PEN("PEN", 'P'),
    SHAPE("SHAPE", 'S'),
    ;

    companion object {
        val ORDERED: List<PlaceholderTool> = entries
    }
}
