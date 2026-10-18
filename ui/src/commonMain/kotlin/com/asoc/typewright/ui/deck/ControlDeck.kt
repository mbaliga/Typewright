// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.deck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.TextureId
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.PI
import kotlin.math.cos
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
    onLeft: () -> Unit = {},
    onRight: () -> Unit = {},
    onToggle: (Int) -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val sectionLabels = DeckGeometry.sectionLabels(section)
    val toggleLabels = DeckGeometry.toggleLabels(toggles)
    BoxWithConstraints(modifier.fillMaxWidth().height(DeckGeometry.HEIGHT.dp)) {
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
                onClick = onLeft,
            )
        }
        if (right != DeckRight.NONE) {
            HitArea(
                description = if (right == DeckRight.NEXT) "Next" else "More",
                x = originX + 324.dp * k,
                y = originY + 45.dp * k,
                w = 52.dp * k,
                h = 52.dp * k,
                circular = true,
                onClick = onRight,
            )
        }
        toggleLabels.forEachIndexed { index, label ->
            val (cx, cy) = DeckGeometry.pointAt(DeckGeometry.TOGGLE_RADIUS, DeckGeometry.TOGGLE_HALF_ANGLE, label.centerLength)
            HitArea(
                description = label.text.lowercase().replaceFirstChar { it.uppercase() },
                x = originX + (cx - label.bandLength / 2).dp * k,
                y = originY + (cy - TOGGLE_HIT_HEIGHT / 2).dp * k,
                w = label.bandLength.dp * k,
                h = TOGGLE_HIT_HEIGHT.dp * k,
                circular = false,
                onClick = { onToggle(index) },
            )
        }
    }
}

private const val TOGGLE_HIT_HEIGHT = 24.0

private fun degrees(radians: Double): Double = radians * 180.0 / PI

@Composable
private fun HitArea(
    description: String,
    x: Dp,
    y: Dp,
    w: Dp,
    h: Dp,
    circular: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(width = w, height = h)
            .let { if (circular) it.clip(CircleShape) else it }
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
    )
}

private fun DrawScope.drawBloom(texture: CanvasTexture) {
    val paper = texture.canvas.toColor()
    drawRect(Brush.verticalGradient(0f to paper.copy(alpha = 0f), 0.4f to paper))
}

private fun lineColor(texture: CanvasTexture): Color =
    texture.ink.toColor().copy(
        alpha =
            if (texture.id ==
                TextureId.BLACK
            ) {
                0.24f
            } else {
                0.22f
            },
    )

/** The close circle's red: #D0342C on light grounds, #FF5A4E on dark (CLAUDE.md law 8: its only use). */
private fun closeRed(texture: CanvasTexture): Color =
    if (texture.id == TextureId.BLACK ||
        texture.id == TextureId.BLUEPRINT
    ) {
        Color(0xFFFF5A4E)
    } else {
        Color(0xFFD0342C)
    }

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
            color = if (label.current) paper else ink,
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
                Color.White,
                2.4f,
            )
        }

        DeckLeft.NONE -> {
            Unit
        }
    }
    val rightAlpha = if (rightDimmed) 0.35f else 1f
    val rightAt = Offset(DeckGeometry.RIGHT_CIRCLE_X.toFloat(), cy)
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
