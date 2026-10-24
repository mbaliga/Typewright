// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.CurveSegment
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.handleOwner
import com.asoc.typewright.core.geometry.segmentLayout
import com.asoc.typewright.core.geometry.writtenCount
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor

/** One label and value of the inspector row. A [key] makes the value editable (typed, or scrubbed with a drag). */
data class FieldSpec(
    val label: String,
    val value: String,
    val key: String? = null,
    val emphasize: Boolean = false,
    val snapped: Boolean = false,
)

/** A titled run of fields in the desktop inspector column ("Node", "Glyph"). */
data class FieldGroup(
    val title: String,
    val fields: List<FieldSpec>,
)

/** The keys of the editable fields, shared by the row, the desktop column and the controller. */
object FieldKeys {
    const val X = "x"
    const val Y = "y"
    const val ADVANCE = "adv"
    const val LSB = "lsb"
    const val RSB = "rsb"
}

/**
 * What the inspector row shows (UI_SPEC §3: never more than six pairs): for one node its x, y, the
 * glyph's on and off counts and whether it is smooth or a corner, read from the flag the file stores,
 * never guessed; for one handle its x, y and its length and angle from the node; for several points
 * how many; for a segment what kind; and with nothing selected the glyph's advance and sidebearings,
 * measured as the project measures them. Pure, so every case is a test.
 */
object DrawInspectorModel {
    fun fields(
        glyph: Glyph,
        selection: DrawSelection,
        lines: GlyphLines?,
        snapX: Boolean = false,
        snapY: Boolean = false,
        dragging: Boolean = false,
    ): List<FieldSpec> {
        val count = glyph.writtenCount()
        val on = FieldSpec("on", count.onCurveEquivalent.toString())
        val off = FieldSpec("off", count.offCurve.toString())
        val single = selection.points.singleOrNull()
        val point =
            single?.let {
                glyph.contours
                    .getOrNull(it.contour)
                    ?.points
                    ?.getOrNull(it.point)
            }
        return when {
            single != null && point != null -> {
                val x = FieldSpec("x", point.point.x.toString(), FieldKeys.X, emphasize = dragging && !snapY, snapped = snapX)
                val y = FieldSpec("y", point.point.y.toString(), FieldKeys.Y, snapped = snapY)
                val kind =
                    if (point.onCurve) {
                        FieldSpec("node", if (point.smooth) "Smooth" else "Corner")
                    } else {
                        FieldSpec("handle", handleDescription(glyph, single.contour, single.point))
                    }
                listOf(x, y, on, off, kind)
            }

            selection.points.size > 1 -> {
                val nodes = selection.nodes(glyph).size
                val handles = selection.handles(glyph).size
                val text =
                    listOfNotNull(
                        nodes.takeIf { it > 0 }?.let { "$it node${plural(it)}" },
                        handles.takeIf { it > 0 }?.let { "$it handle${plural(it)}" },
                    )
                listOf(FieldSpec("selected", text.joinToString(" · ")), on, off)
            }

            selection.segment != null -> {
                val key = selection.segment
                val info =
                    glyph.contours
                        .getOrNull(key.contour)
                        ?.segmentLayout()
                        ?.getOrNull(key.segment)
                val kind =
                    when (info?.segment) {
                        is CurveSegment.Line -> "line"
                        is CurveSegment.Quadratic, is CurveSegment.Cubic -> if (info.controlPoints.isEmpty()) "line" else "curve"
                        null -> "—"
                    }
                listOf(FieldSpec("segment", kind), on, off)
            }

            else -> {
                listOf(
                    FieldSpec("adv", glyph.advanceWidth.toString(), FieldKeys.ADVANCE),
                    FieldSpec("lsb", lines?.lsb?.toString() ?: "—", if (lines?.lsb != null) FieldKeys.LSB else null),
                    FieldSpec("rsb", lines?.rsb?.toString() ?: "—", if (lines?.rsb != null) FieldKeys.RSB else null),
                    on,
                    off,
                )
            }
        }
    }

    /**
     * The desktop column's version of the same facts, grouped: what is selected first (a node with its two
     * handles, a handle, a count, a segment), then the glyph's advance, sidebearings and counts. The
     * values are the row's own, so the two inspectors never disagree.
     */
    fun groups(
        glyph: Glyph,
        selection: DrawSelection,
        lines: GlyphLines?,
        snapX: Boolean = false,
        snapY: Boolean = false,
        dragging: Boolean = false,
    ): List<FieldGroup> {
        val count = glyph.writtenCount()
        val glyphGroup =
            FieldGroup(
                "Glyph",
                listOf(
                    FieldSpec("adv", glyph.advanceWidth.toString(), FieldKeys.ADVANCE),
                    FieldSpec("lsb", lines?.lsb?.toString() ?: "—", if (lines?.lsb != null) FieldKeys.LSB else null),
                    FieldSpec("rsb", lines?.rsb?.toString() ?: "—", if (lines?.rsb != null) FieldKeys.RSB else null),
                    FieldSpec("on", count.onCurveEquivalent.toString()),
                    FieldSpec("off", count.offCurve.toString()),
                ),
            )
        val single = selection.points.singleOrNull()
        val contour = single?.let { glyph.contours.getOrNull(it.contour) }
        val point = single?.let { contour?.points?.getOrNull(it.point) }
        val selected: List<FieldGroup> =
            when {
                single != null && contour != null && point != null && point.onCurve -> {
                    val node =
                        FieldGroup(
                            "Node",
                            listOf(
                                FieldSpec("x", point.point.x.toString(), FieldKeys.X, emphasize = dragging && !snapY, snapped = snapX),
                                FieldSpec("y", point.point.y.toString(), FieldKeys.Y, snapped = snapY),
                                FieldSpec("type", if (point.smooth) "Smooth" else "Corner"),
                            ),
                        )
                    val size = contour.points.size
                    val out = (single.point + 1) % size
                    val into = (single.point - 1 + size) % size
                    val handles =
                        listOfNotNull(
                            handleSpec("handle out", glyph, single.contour, out, single.point),
                            handleSpec("in", glyph, single.contour, into, single.point),
                        )
                    if (handles.isEmpty()) listOf(node) else listOf(node, FieldGroup("Handles", handles))
                }

                single != null && point != null -> {
                    listOf(
                        FieldGroup(
                            "Handle",
                            listOf(
                                FieldSpec("x", point.point.x.toString(), FieldKeys.X, emphasize = dragging && !snapY, snapped = snapX),
                                FieldSpec("y", point.point.y.toString(), FieldKeys.Y, snapped = snapY),
                                FieldSpec("handle", handleDescription(glyph, single.contour, single.point)),
                            ),
                        ),
                    )
                }

                selection.points.size > 1 -> {
                    listOf(FieldGroup("Selection", listOf(fields(glyph, selection, lines).first())))
                }

                selection.segment != null -> {
                    listOf(FieldGroup("Segment", listOf(fields(glyph, selection, lines).first())))
                }

                else -> {
                    emptyList()
                }
            }
        return selected + glyphGroup
    }

    /** The field for the handle at [handleIndex] when it belongs to node [nodeIndex], else null (a node, or another node's handle). */
    private fun handleSpec(
        label: String,
        glyph: Glyph,
        contourIndex: Int,
        handleIndex: Int,
        nodeIndex: Int,
    ): FieldSpec? {
        val contour = glyph.contours[contourIndex]
        if (contour.points[handleIndex].onCurve || contour.handleOwner(handleIndex) != nodeIndex) return null
        return FieldSpec(label, handleDescription(glyph, contourIndex, handleIndex))
    }

    private fun handleDescription(
        glyph: Glyph,
        contourIndex: Int,
        pointIndex: Int,
    ): String {
        val contour = glyph.contours[contourIndex]
        val owner = contour.handleOwner(pointIndex) ?: return "shared"
        val from = contour.points[owner].point
        val to = contour.points[pointIndex].point
        val dx = (to.x - from.x).toDouble()
        val dy = (to.y - from.y).toDouble()
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        val angle = floor(atan2(dy, dx) * 180.0 / PI + 0.5).toInt().let { if (it < 0) it + FULL_TURN else it }
        return if (length == 0.0) "line" else "${floor(length + 0.5).toInt()} · $angle°"
    }

    private fun plural(n: Int) = if (n == 1) "" else "s"

    private const val FULL_TURN = 360
}
