// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.deck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.ui.theme.LocalAppearance
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CloseToken
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The control deck (SCREENS_V1 §2): the canvas `Deck` component, drawn as ui/v1-screens/Deck.dc.html
 * draws it. A section arc with an ink band behind the current label, a toggle arc above it with a
 * band behind each toggle that is on, a left circle (Back, or Close in red) and a right circle (the
 * page menu, or the filled primary action Next). The only round things in the app (C2).
 *
 * This is the deck's states, not its router: it draws what it is given and reports taps. Which
 * section is current, what the toggles are, and where Back and Next go belong to whatever hosts a
 * screen (the router is P13's). Only the right circles the Brief needs are drawn: the menu, Next and
 * none. The sections are dimmed on a page that is not in one, as the canvas draws S02.
 *
 * It is 390 × 172 dp, at the foot and centred, and scales down on a narrower screen. Its paper fades
 * up over the page (the grain and the page dissolve near the glass, C4), across the whole width.
 * Labels use the app's mono face, as the canvas's IBM Plex Mono is not bundled (OQ 140).
 */
@Composable
fun ControlDeck(
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
    section: DeckSection = DeckSection.HOME,
    left: DeckLeft = DeckLeft.BACK,
    right: DeckRight = DeckRight.MENU,
    toggles: List<DeckToggle> = emptyList(),
    dimSections: Boolean = false,
    rightDimmed: Boolean = false,
    rightLabel: String? = null,
    sectionStatus: (DeckSection) -> StationStatus? = { null },
    onLeft: () -> Unit = {},
    onRight: () -> Unit = {},
    onRightLong: (() -> Unit)? = null,
    onToggle: (Int) -> Unit = {},
    onSection: ((DeckSection) -> Unit)? = null,
    onMap: (() -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val hapticsOn = LocalAppearance.current?.settings?.haptics ?: true
    var dragged by remember { mutableStateOf(0.0) }
    val turned = if (onSection == null) 0.0 else DeckKeys.turned(dragged)
    val sectionLabels = DeckGeometry.sectionLabels(section, shift = turned, status = sectionStatus)
    val toggleLabels = DeckGeometry.toggleLabels(toggles)
    val ring = texture.ink.toColor()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(DeckGeometry.HEIGHT.dp)
            .pointerInput(section, onSection) {
                if (onSection == null) return@pointerInput
                var turning = false
                var lastApex = section
                detectHorizontalDragGestures(
                    onDragStart = { at ->
                        // Only a drag that starts on the section arc turns it.
                        turning = at.y > size.height * SECTION_ARC_TOP_FRACTION
                        lastApex = section
                    },
                    onDragEnd = {
                        if (turning) {
                            val to = DeckKeys.settle(section, dragged)
                            dragged = 0.0
                            if (to != section) {
                                if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSection(to)
                            }
                        }
                        turning = false
                    },
                    onDragCancel = {
                        dragged = 0.0
                        turning = false
                    },
                ) { _, dx ->
                    if (!turning) return@detectHorizontalDragGestures
                    dragged += dx / density.density
                    val apex = DeckKeys.settle(section, dragged)
                    if (apex != lastApex) {
                        lastApex = apex
                        if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            },
    ) {
        val k = min(1f, maxWidth.value / DeckGeometry.WIDTH.toFloat())
        val originX = (maxWidth - DeckGeometry.WIDTH.dp * k) / 2
        val originY = DeckGeometry.HEIGHT.dp * (1 - k)
        Canvas(Modifier.matchParentSize()) {
            drawBloom(texture)
            val ox = originX.toPx()
            val oy = originY.toPx()
            withTransform({
                translate(left = ox, top = oy)
                scale(scaleX = k * density.density, scaleY = k * density.density, pivot = Offset.Zero)
            }) {
                drawDeck(texture, measurer, sectionLabels, toggleLabels, section, left, right, dimSections, rightDimmed)
            }
        }
        if (left != DeckLeft.NONE) {
            HitArea(
                description = if (left == DeckLeft.CLOSE) "Close" else "Back",
                x = originX + 14.dp * k,
                y = originY + 45.dp * k,
                w = 52.dp * k,
                h = 52.dp * k,
                circular = true,
                ring = ring,
                onClick = onLeft,
            )
        }
        if (right != DeckRight.NONE) {
            HitArea(
                description =
                    rightLabel ?: if (right == DeckRight.MENU) {
                        "More"
                    } else if (right == DeckRight.PLUS) {
                        "New project"
                    } else if (right == DeckRight.REFRESH) {
                        "Run again"
                    } else {
                        "Next"
                    },
                x = originX + 324.dp * k,
                y = originY + 45.dp * k,
                w = 52.dp * k,
                h = 52.dp * k,
                circular = true,
                ring = ring,
                onClick = onRight,
                onLongClick = onRightLong,
                disabled = rightDimmed,
            )
        }
        toggleLabels.forEachIndexed { index, label ->
            val (cx, cy) = DeckGeometry.pointAt(DeckGeometry.TOGGLE_RADIUS, DeckGeometry.TOGGLE_HALF_ANGLE, label.centerLength)
            val w = max(label.bandLength, MIN_HIT)
            HitArea(
                description = label.text.lowercase().replaceFirstChar { it.uppercase() },
                x = originX + (cx - w / 2).dp * k,
                y = originY + (cy - TOGGLE_HIT_HEIGHT / 2).dp * k,
                w = w.dp * k,
                h = TOGGLE_HIT_HEIGHT.dp * k,
                circular = false,
                ring = ring,
                selected = label.current,
                disabled = !label.enabled,
                onClick = { onToggle(index) },
            )
        }
        if (onSection != null) {
            sectionLabels.filter { it.section != null }.forEach { label ->
                val (cx, cy) = DeckGeometry.pointAt(DeckGeometry.SECTION_RADIUS, DeckGeometry.SECTION_HALF_ANGLE, label.centerLength)
                val w = max(label.bandLength, MIN_HIT)
                HitArea(
                    description = label.section!!.label,
                    x = originX + (cx - w / 2).dp * k,
                    y = originY + (cy - MIN_HIT / 2).dp * k,
                    w = w.dp * k,
                    h = MIN_HIT.dp * k,
                    circular = false,
                    ring = ring,
                    selected = label.section == section,
                    selectedWord = "Current page",
                    onClick = { onSection(label.section) },
                    onLongClick = onMap,
                )
            }
        }
    }
}

/** Every hit target is at least this tall and wide (UI_SPEC §6's floor is 44 dp). */
private const val MIN_HIT = 44.0
private const val TOGGLE_HIT_HEIGHT = MIN_HIT

/** A drag that starts below this fraction of the deck's height is on the section arc. */
private const val SECTION_ARC_TOP_FRACTION = 0.5f

private fun degrees(radians: Double): Double = radians * 180.0 / PI

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HitArea(
    description: String,
    x: Dp,
    y: Dp,
    w: Dp,
    h: Dp,
    circular: Boolean,
    ring: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    selectedWord: String = "Selected",
    disabled: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = if (circular) CircleShape else RectangleShape
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(width = w, height = h)
            .let { if (circular) it.clip(CircleShape) else it }
            .let { if (focused) it.border(FOCUS_RING_WIDTH.dp, ring, shape) else it }
            .semantics {
                contentDescription = description
                if (selected) {
                    this.selected = true
                    stateDescription = selectedWord
                }
                if (disabled) stateDescription = "Not available yet"
            }.onFocusChanged { focused = it.isFocused }
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick),
    )
}

/** The visible focus ring on every deck control. */
private const val FOCUS_RING_WIDTH = 2

private fun DrawScope.drawBloom(texture: CanvasTexture) {
    val paper = texture.canvas.toColor()
    drawRect(Brush.verticalGradient(0f to paper.copy(alpha = 0f), 0.4f to paper))
}

private fun lineColor(texture: CanvasTexture): Color = texture.ink.toColor().copy(alpha = if (texture.dark) 0.24f else 0.22f)

/** The close circle's red: the dark-ground red on a dark ground (CLAUDE.md law 8: its only use). */
private fun closeRed(texture: CanvasTexture): Color = CloseToken.red(texture.dark).toColor()

/** Draws the deck in its own 390 × 172 space (the caller has already moved and scaled the canvas to it). */
private fun DrawScope.drawDeck(
    texture: CanvasTexture,
    measurer: TextMeasurer,
    sectionLabels: List<DeckLabel>,
    toggleLabels: List<DeckLabel>,
    section: DeckSection,
    left: DeckLeft,
    right: DeckRight,
    dimSections: Boolean,
    rightDimmed: Boolean,
) {
    val ink = texture.ink.toColor()
    val paper = texture.canvas.toColor()
    val line = lineColor(texture)
    val sectionsAlpha = if (dimSections) 0.3f else 1f
    val c = Offset(DeckGeometry.CENTER_X.toFloat(), DeckGeometry.CENTER_Y.toFloat())

    // The section arc: two hairlines either side of it, a band behind the current label, the labels.
    arcLine(c, 544.0, DeckGeometry.SECTION_HALF_ANGLE, line.copy(alpha = line.alpha * sectionsAlpha))
    arcLine(c, 510.0, DeckGeometry.SECTION_HALF_ANGLE, line.copy(alpha = line.alpha * sectionsAlpha))
    sectionLabels.firstOrNull { it.current && it.text.isNotEmpty() }?.let {
        band(
            c,
            DeckGeometry.SECTION_RADIUS,
            DeckGeometry.SECTION_HALF_ANGLE,
            DeckGeometry.SECTION_BAND_THICKNESS,
            it,
            ink.copy(
                alpha =
                    ink.alpha * sectionsAlpha,
            ),
        )
    }
    for (label in sectionLabels) {
        if (label.text.isEmpty()) continue
        val color = (if (label.current) paper else ink).copy(alpha = label.alpha.toFloat() * sectionsAlpha)
        textAlongArc(
            measurer,
            c,
            label,
            DeckGeometry.SECTION_RADIUS,
            DeckGeometry.SECTION_HALF_ANGLE,
            size = 10.5f,
            spacing = 1.5f,
            weight = if (label.current) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
        )
    }

    // The toggle arc: hairlines, a band behind each toggle that is on, the labels.
    arcLine(c, 600.0, DeckGeometry.TOGGLE_HALF_ANGLE, line)
    arcLine(c, 570.0, DeckGeometry.TOGGLE_HALF_ANGLE, line)
    for (label in toggleLabels) {
        if (label.current) {
            band(
                c,
                DeckGeometry.TOGGLE_RADIUS,
                DeckGeometry.TOGGLE_HALF_ANGLE,
                DeckGeometry.TOGGLE_BAND_THICKNESS,
                label.copy(
                    bandLength =
                        label.bandLength - 2,
                ),
                ink,
            )
        }
    }
    for (label in toggleLabels) {
        textAlongArc(
            measurer,
            c,
            label,
            DeckGeometry.TOGGLE_RADIUS,
            DeckGeometry.TOGGLE_HALF_ANGLE,
            size = 9f,
            spacing = 1f,
            weight = FontWeight.Medium,
            color = (if (label.current) paper else ink).copy(alpha = label.alpha.toFloat()),
        )
    }

    // The circles.
    val cy = DeckGeometry.CIRCLE_Y.toFloat()
    when (left) {
        DeckLeft.BACK -> {
            val at = Offset(DeckGeometry.LEFT_CIRCLE_X.toFloat(), cy)
            drawCircle(paper, DeckGeometry.CIRCLE_RADIUS.toFloat(), at)
            drawCircle(ink, DeckGeometry.CIRCLE_RADIUS.toFloat(), at, style = Stroke(1.5f))
            stroke(
                Path().apply {
                    moveTo(at.x + 3, at.y - 8)
                    lineTo(at.x - 5, at.y)
                    lineTo(at.x + 3, at.y + 8)
                },
                ink,
                2f,
            )
        }

        DeckLeft.CLOSE -> {
            val at = Offset(DeckGeometry.LEFT_CIRCLE_X.toFloat(), cy)
            drawCircle(closeRed(texture), DeckGeometry.FILLED_CIRCLE_RADIUS.toFloat(), at)
            stroke(
                Path().apply {
                    moveTo(at.x - 7, at.y - 7)
                    lineTo(at.x + 7, at.y + 7)
                    moveTo(at.x + 7, at.y - 7)
                    lineTo(
                        at.x - 7,
                        at.y + 7,
                    )
                },
                CloseToken.CROSS.toColor(),
                2.4f,
            )
        }

        DeckLeft.NONE -> {
            Unit
        }
    }
    val rightAlpha = if (rightDimmed) 0.35f else 1f
    val rightAt = Offset(DeckGeometry.RIGHT_CIRCLE_X.toFloat(), cy)
    val refreshPath = if (right == DeckRight.REFRESH) DeckGlyphs.refresh() else null
    when (right) {
        DeckRight.MENU -> {
            drawCircle(paper, DeckGeometry.CIRCLE_RADIUS.toFloat(), rightAt, alpha = rightAlpha)
            drawCircle(ink, DeckGeometry.CIRCLE_RADIUS.toFloat(), rightAt, alpha = rightAlpha, style = Stroke(1.5f))
            for (dx in listOf(-8f, 0f, 8f)) drawCircle(ink, 2.2f, Offset(rightAt.x + dx, rightAt.y), alpha = rightAlpha)
        }

        DeckRight.NEXT -> {
            drawCircle(ink, DeckGeometry.FILLED_CIRCLE_RADIUS.toFloat(), rightAt, alpha = rightAlpha)
            stroke(
                Path().apply {
                    moveTo(rightAt.x - 8, rightAt.y)
                    lineTo(rightAt.x + 7, rightAt.y)
                    moveTo(rightAt.x + 2, rightAt.y - 6)
                    lineTo(rightAt.x + 8, rightAt.y)
                    lineTo(rightAt.x + 2, rightAt.y + 6)
                },
                paper.copy(alpha = paper.alpha * rightAlpha),
                2.2f,
            )
        }

        DeckRight.CHECK -> {
            drawCircle(ink, DeckGeometry.FILLED_CIRCLE_RADIUS.toFloat(), rightAt, alpha = rightAlpha)
            stroke(
                Path().apply {
                    moveTo(rightAt.x - 8, rightAt.y + 1)
                    lineTo(rightAt.x - 2, rightAt.y + 7)
                    lineTo(rightAt.x + 9, rightAt.y - 7)
                },
                paper.copy(alpha = paper.alpha * rightAlpha),
                2.4f,
            )
        }

        DeckRight.PLUS -> {
            drawCircle(ink, DeckGeometry.FILLED_CIRCLE_RADIUS.toFloat(), rightAt, alpha = rightAlpha)
            stroke(
                Path().apply {
                    moveTo(rightAt.x - 9, rightAt.y)
                    lineTo(rightAt.x + 9, rightAt.y)
                    moveTo(rightAt.x, rightAt.y - 9)
                    lineTo(rightAt.x, rightAt.y + 9)
                },
                paper.copy(alpha = paper.alpha * rightAlpha),
                2.4f,
            )
        }

        DeckRight.REFRESH -> {
            drawCircle(ink, DeckGeometry.FILLED_CIRCLE_RADIUS.toFloat(), rightAt, alpha = rightAlpha)
            // The board's own path (Deck.dc.html), about the circle's centre, with the board's width and the plain ends it draws it with.
            withTransform({ translate(rightAt.x, rightAt.y) }) {
                drawPath(
                    checkNotNull(refreshPath),
                    paper.copy(alpha = paper.alpha * rightAlpha),
                    style = Stroke(width = DeckGlyphs.REFRESH_STROKE, cap = StrokeCap.Butt),
                )
            }
        }

        DeckRight.NONE -> {
            Unit
        }
    }
}

private fun DrawScope.stroke(
    path: Path,
    color: Color,
    width: Float,
) = drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Square))

/** A 1-unit hairline arc of [radius] about [c], spanning ±[half] radians from straight up. */
private fun DrawScope.arcLine(
    c: Offset,
    radius: Double,
    half: Double,
    color: Color,
) {
    val r = radius.toFloat()
    val start = (degrees(-half) - 90).toFloat()
    val sweep = degrees(2 * half).toFloat()
    drawArc(color, start, sweep, useCenter = false, topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r), style = Stroke(1f))
}

/** An ink band of [thickness] around [label]'s arc position: the annular sector the canvas draws with a dashed stroke. */
private fun DrawScope.band(
    c: Offset,
    radius: Double,
    half: Double,
    thickness: Double,
    label: DeckLabel,
    color: Color,
) {
    val a0 = DeckGeometry.angleAt(radius, half, label.centerLength - label.bandLength / 2)
    val a1 = DeckGeometry.angleAt(radius, half, label.centerLength + label.bandLength / 2)
    val outer = (radius + thickness / 2).toFloat()
    val inner = (radius - thickness / 2).toFloat()
    val start = (degrees(a0) - 90).toFloat()
    val sweep = degrees(a1 - a0).toFloat()
    val path =
        Path().apply {
            arcTo(Rect(c.x - outer, c.y - outer, c.x + outer, c.y + outer), start, sweep, true)
            arcTo(Rect(c.x - inner, c.y - inner, c.x + inner, c.y + inner), start + sweep, -sweep, false)
            close()
        }
    drawPath(path, color)
}

/** [label] set along the arc, centred on its [DeckLabel.centerLength], each character turned to the arc's tangent. */
private fun DrawScope.textAlongArc(
    measurer: TextMeasurer,
    c: Offset,
    label: DeckLabel,
    radius: Double,
    half: Double,
    size: Float,
    spacing: Float,
    weight: FontWeight,
    color: Color,
) {
    // Measured at one pixel per unit: the canvas is already scaled to the deck's own units.
    val unit = Density(1f, 1f)
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = size.sp, fontWeight = weight, color = color)
    val glyphs = label.text.map { measurer.measure(it.toString(), style, density = unit) }
    val total = glyphs.sumOf { (it.size.width + spacing).toDouble() }
    var s = label.centerLength - total / 2
    for (glyph in glyphs) {
        val w = glyph.size.width.toFloat()
        val theta = DeckGeometry.angleAt(radius, half, s + w / 2)
        val px = c.x + (radius * sin(theta)).toFloat()
        val py = c.y - (radius * cos(theta)).toFloat()
        rotate(degrees = degrees(theta).toFloat(), pivot = Offset(px, py)) {
            drawText(glyph, color = color, topLeft = Offset(px - w / 2, py - glyph.size.height / 2f))
        }
        s += w + spacing
    }
}
