// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.writtenCount
import com.asoc.typewright.ui.puck.Tool

/**
 * What the inspector shows while one of the four shape-and-font tools is in hand: Boolean (what is picked and what
 * Subtract keeps), Stroke (width, join, ends, points laid), Anchors (the selected anchor's name and place, and all of
 * them) and Metrics (the font's five lines, typed or dragged, and the overshoot zones, which are read-only). Any other tool
 * has no panel of its own and gets null, so the inspector stays what it was. Pure, so every case is a test.
 */
object DrawToolPanel {
    /** The inspector row's fields for [tool], at most six, or null when [tool] has no panel. */
    fun fields(
        tool: Tool,
        controller: DrawController,
        glyph: Glyph,
    ): List<FieldSpec>? {
        val count = glyph.writtenCount()
        val on = FieldSpec("on", count.onCurveEquivalent.toString())
        val off = FieldSpec("off", count.offCurve.toString())
        return when (tool) {
            Tool.BOOLEAN -> listOf(pickedField(controller, glyph), keepsField(controller, glyph), on, off)
            Tool.STROKE -> strokeFields(controller)
            Tool.ANCHORS -> anchorFields(controller, glyph)
            Tool.METRICS -> metricFields(controller)
            else -> null
        }
    }

    /** The desktop column's version of the same facts, grouped, ending in the glyph's own group, or null when [tool] has no panel. */
    fun groups(
        tool: Tool,
        controller: DrawController,
        glyph: Glyph,
        glyphGroup: FieldGroup?,
    ): List<FieldGroup>? {
        val tail = listOfNotNull(glyphGroup)
        return when (tool) {
            Tool.BOOLEAN -> listOf(FieldGroup("Boolean", listOf(pickedField(controller, glyph), keepsField(controller, glyph)))) + tail
            Tool.STROKE -> listOf(FieldGroup("Stroke", strokeFields(controller))) + tail
            Tool.ANCHORS -> anchorGroups(controller, glyph) + tail
            Tool.METRICS -> listOf(FieldGroup("Font metrics", metricFields(controller))) + listOfNotNull(zonesGroup(controller)) + tail
            else -> null
        }
    }

    /** The line under the tool strip: what the tool does and, where a number has no measurement behind it, that it is our heuristic. */
    fun hint(
        tool: Tool,
        controller: DrawController,
    ): String? =
        when (tool) {
            Tool.BOOLEAN -> {
                "Pick two or more contours. Subtract keeps the first one picked and takes the rest from it."
            }

            Tool.STROKE -> {
                "Width ${controller.strokeWidth.toInt()} units (a default of ours: no measurement says how wide a stroke should be). " +
                    "Ends are butt. Limits 2 to 2000 and the miter limit of 4 are our heuristic."
            }

            Tool.ANCHORS -> {
                "Tap empty canvas to add an anchor, tap one to select it, drag to move it. Rename it in the inspector."
            }

            Tool.METRICS -> {
                "Drag a line or type its number. This changes every glyph's alignment, not this glyph's outline. " +
                    "Overshoot zones are read-only here."
            }

            else -> {
                null
            }
        }

    private fun pickedField(
        controller: DrawController,
        glyph: Glyph,
    ): FieldSpec {
        val n = controller.selection.contours.count { it in glyph.contours.indices }
        return FieldSpec("picked", "$n contour${if (n == 1) "" else "s"}")
    }

    private fun keepsField(
        controller: DrawController,
        glyph: Glyph,
    ): FieldSpec {
        val picked =
            controller.selection.contours
                .filter { it in glyph.contours.indices }
                .toSet()
        val order = controller.booleanOrder().filter { it in picked } + picked.sorted().filter { it !in controller.booleanOrder() }
        return FieldSpec("subtract keeps", order.firstOrNull()?.let { "contour ${it + 1}" } ?: "—")
    }

    private fun strokeFields(controller: DrawController): List<FieldSpec> =
        listOf(
            FieldSpec("width", controller.strokeWidth.toInt().toString(), FieldKeys.STROKE_WIDTH),
            FieldSpec("join", controller.strokeJoin.name.lowercase()),
            FieldSpec("ends", "butt"),
            FieldSpec("points", if (controller.centreline.isEmpty()) "—" else controller.centreline.size.toString()),
        )

    private fun anchorFields(
        controller: DrawController,
        glyph: Glyph,
    ): List<FieldSpec> {
        val selected = glyph.anchors.firstOrNull { it.name == controller.selectedAnchor() }
        val total = FieldSpec("anchors", glyph.anchors.size.toString())
        return if (selected == null) {
            listOf(total, FieldSpec("tap", "empty canvas adds one"))
        } else {
            listOf(
                FieldSpec("name", selected.name, FieldKeys.ANCHOR_NAME),
                FieldSpec("x", selected.point.x.toString(), FieldKeys.ANCHOR_X),
                FieldSpec("y", selected.point.y.toString(), FieldKeys.ANCHOR_Y),
                total,
            )
        }
    }

    private fun anchorGroups(
        controller: DrawController,
        glyph: Glyph,
    ): List<FieldGroup> {
        val selected = glyph.anchors.firstOrNull { it.name == controller.selectedAnchor() }
        val one =
            selected?.let {
                FieldGroup(
                    "Anchor",
                    listOf(
                        FieldSpec("name", it.name, FieldKeys.ANCHOR_NAME),
                        FieldSpec("x", it.point.x.toString(), FieldKeys.ANCHOR_X),
                        FieldSpec("y", it.point.y.toString(), FieldKeys.ANCHOR_Y),
                    ),
                )
            }
        val all =
            FieldGroup(
                "Anchors",
                if (glyph.anchors.isEmpty()) {
                    listOf(FieldSpec("none", "tap empty canvas"))
                } else {
                    glyph.anchors.map { FieldSpec(it.name, "${it.point.x}, ${it.point.y}", emphasize = it.name == selected?.name) }
                },
            )
        return listOfNotNull(one, all)
    }

    private fun metricFields(controller: DrawController): List<FieldSpec> {
        val metrics = controller.metrics()
        // MetricKind is declared top to bottom: ascender, cap height, x-height, baseline, descender.
        return MetricKind.entries.map { kind ->
            val value =
                if (kind ==
                    MetricKind.BASELINE
                ) {
                    "0 · fixed"
                } else {
                    metrics.lines
                        .firstOrNull { it.kind == kind }
                        ?.y
                        ?.toString() ?: "—"
                }
            FieldSpec(kind.label, value, FieldKeys.keyOf(kind), emphasize = kind == controller.metricKind)
        }
    }

    private fun zonesGroup(controller: DrawController): FieldGroup? {
        val zones = controller.metrics().zones
        if (zones.isEmpty()) return null
        return FieldGroup("Overshoot zones", zones.map { FieldSpec("zone", "${it.bottom} to ${it.top} · read-only") })
    }
}
