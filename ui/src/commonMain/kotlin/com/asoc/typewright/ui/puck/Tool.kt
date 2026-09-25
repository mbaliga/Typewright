// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.puck

/**
 * The eight construction tools (brief section 5.1's own puck description; UI_SPEC section 4's
 * desktop tool rail; `ui/typewright-explorer.html`'s own "drail" tool list -- grep `"drail"`,
 * around lines 908-919 -- and its `ic-*` icon defs, around lines 552-559). [label] is the
 * explorer's own literal button text ("Select", "Pen", ... -- `.uppercase()`'d at every render
 * site, the same convention [com.asoc.typewright.ui.tokens.Typography]'s own KDoc already
 * states for mono labels); [shortcut] is brief section 5.1's own desktop letter ("V select, P
 * pen, S primitives, B boolean, K stroke, M measure, A anchors, T metrics"). Declaration order
 * matches the explorer's own rail top to bottom, and therefore the puck's own swipe-cycle and
 * radial-dial sector order too.
 *
 * Replaces task P4b's own `PlaceholderTool` (see that type's retired KDoc: "Real tool content...
 * is P5b's job; wiring it in only needs a different `List<PlaceholderTool>` (or a real `Tool`
 * type altogether)") -- taken here as the latter, a real tool identity rather than placeholder
 * semantics, across every file that named it ([Puck], [RadialDial], [UnfoldedToolList]; a repo
 * grep for `PlaceholderTool` found no other references, including none in [PuckPinState] despite
 * this task's own instructions naming it as a candidate -- that file never depended on tool
 * identity in the first place).
 */
enum class Tool(
    val label: String,
    val shortcut: Char,
) {
    SELECT("Select", 'V'),
    PEN("Pen", 'P'),
    PRIMITIVES("Primitives", 'S'),
    BOOLEAN("Boolean", 'B'),
    STROKE("Stroke", 'K'),
    MEASURE("Measure", 'M'),
    ANCHORS("Anchors", 'A'),
    METRICS("Metrics", 'T'),
    ;

    companion object {
        /** Every tool, in the explorer's own rail order (top to bottom). */
        val ORDERED: List<Tool> = entries
    }
}
