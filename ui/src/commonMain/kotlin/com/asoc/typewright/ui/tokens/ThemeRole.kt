// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.tokens

/** The groups of colour roles in the custom theme editor (SCREENS_V1 §3.1), in its order. */
enum class ThemeGroup(
    val title: String,
) {
    GROUND("Ground"),
    INK("Ink"),
    ACCENT("Accent"),
    NODES("Nodes"),
    LINES("Lines"),
    COMPARISON("Comparison layers"),
}

/**
 * One colour a theme sets. Every role that is geometry carries a [shape] that always goes with its
 * colour, so colour is never the only signal (law 8). [text] roles are held to 4.5:1 against the
 * canvas and the rest to 3:1 (SCREENS_V1 §3.2); [CANVAS] is the ground they are held against.
 */
enum class ThemeRole(
    val key: String,
    val label: String,
    val group: ThemeGroup,
    val shape: String,
    val text: Boolean = false,
) {
    CANVAS("canvas", "Canvas", ThemeGroup.GROUND, "—"),
    INK("ink", "Ink", ThemeGroup.INK, "—", text = true),
    MUTED("muted", "Muted", ThemeGroup.INK, "—", text = true),
    ACCENT("accent", "Accent", ThemeGroup.ACCENT, "ink block for selection, never the accent"),
    CORNER_NODE("cornerNode", "On-curve corner", ThemeGroup.NODES, "square"),
    SMOOTH_NODE("smoothNode", "On-curve smooth", ThemeGroup.NODES, "round"),
    OFF_CURVE("offCurve", "Off-curve handle", ThemeGroup.NODES, "small hollow circle"),
    HANDLE_LINE("handleLine", "Handle line", ThemeGroup.NODES, "thin line"),
    SELECTED_NODE("selectedNode", "Selected node", ThemeGroup.NODES, "selection ring"),
    SNAP_TARGET("snapTarget", "Snap target", ThemeGroup.NODES, "crosshair"),
    METRIC_LINES("metricLines", "Metric lines", ThemeGroup.LINES, "solid"),
    GUIDES("guides", "Guides", ThemeGroup.LINES, "long dash"),
    CONSTRUCTION_LINES("constructionLines", "Construction lines", ThemeGroup.LINES, "short dash with centre marks"),
    GRID_LINES("gridLines", "Grid", ThemeGroup.LINES, "dots"),
    COMPARISON_1("comparison1", "Layer 1", ThemeGroup.COMPARISON, "solid"),
    COMPARISON_2("comparison2", "Layer 2", ThemeGroup.COMPARISON, "dashed"),
    COMPARISON_3("comparison3", "Layer 3", ThemeGroup.COMPARISON, "dotted"),
    COMPARISON_4("comparison4", "Layer 4", ThemeGroup.COMPARISON, "dash-dot"),
    ;

    companion object {
        fun byKey(key: String): ThemeRole? = entries.firstOrNull { it.key == key }
    }
}
