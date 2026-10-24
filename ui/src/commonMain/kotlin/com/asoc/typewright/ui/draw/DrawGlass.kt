// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor
import kotlinx.coroutines.delay

/**
 * The header of the Draw room (SCREENS_V1 §2.5): the glyph as "o · 16 on · 16 off" with its unicode and
 * lock state under it, a padlock beside it while it is locked. While a radial is open it names what the
 * radial is about instead ("o · node 427, 382") and how the ring is being used. No buttons.
 */
@Composable
fun DrawHeader(
    room: DrawRoom,
    texture: CanvasTexture,
    projectLine: String,
    modifier: Modifier = Modifier,
) {
    val controller = room.controller
    val state =
        room.session
            ?.state
            ?.collectAsState()
            ?.value
    val glyph = controller?.let { c -> state?.font?.glyph(c.editor.ref) }
    val radial = controller?.radial
    val locked = controller?.editor?.isLocked == true
    val title: String
    val subtitle: String
    when {
        glyph == null -> {
            title = "Draw"
            subtitle = projectLine
        }

        radial != null -> {
            title = DrawSubject.headerLine(glyph, controller.selection)
            subtitle =
                if (radial.state is RadialState.Lifted) {
                    "Lifted · tap a choice, or More for everything"
                } else {
                    "Sweep · the choice sits opposite your thumb"
                }
        }

        else -> {
            title = DrawSubject.glyphLine(glyph)
            val unicode = room.directory.entry(glyph.name)?.unicodeLabel ?: "no unicode"
            subtitle = "$unicode · ${if (locked) "locked" else "unlocked"} · ${projectLine.substringBefore(" · ")}"
        }
    }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                text = title,
                style =
                    TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = texture.fg.toColor(),
                    ),
            )
            if (locked && glyph != null) PadlockMark(texture)
        }
        BasicText(
            text = subtitle,
            style = Typography.mono(sizeSp = 11.0).copy(color = texture.muted.toColor(), letterSpacing = 0.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A small padlock, drawn with lines (a body and a shackle) so the lock is a shape, not just a word. */
@Composable
private fun PadlockMark(texture: CanvasTexture) {
    val ink = texture.ink.toColor()
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.5.dp.toPx()
        drawRect(ink, Offset(w * 0.12f, h * 0.45f), Size(w * 0.76f, h * 0.48f), style = Stroke(strokeWidth))
        val shackle =
            Path().apply {
                moveTo(w * 0.3f, h * 0.45f)
                lineTo(w * 0.3f, h * 0.3f)
                arcTo(Rect(w * 0.3f, h * 0.05f, w * 0.7f, h * 0.55f), 180f, 180f, false)
                lineTo(w * 0.7f, h * 0.45f)
            }
        drawPath(shackle, ink, style = Stroke(strokeWidth, cap = StrokeCap.Square))
    }
}

/** What Draw says in the room when there is nothing to draw: no project, or a project with no glyphs. A sentence, never a fixture. */
@Composable
fun DrawEmptyState(
    room: DrawRoom,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val state =
        room.session
            ?.state
            ?.collectAsState()
            ?.value
    val glyph = room.controller?.let { c -> state?.font?.glyph(c.editor.ref) }
    val (headline, detail) =
        when {
            room.session == null -> "No project is open" to "Open or start a project and its letters appear here to draw."
            room.controller == null -> "This project has no glyphs yet" to "Add a letter from Home, or trace a cell, and it appears here."
            glyph != null && glyph.contours.isEmpty() -> "${glyph.name} is empty" to "Draw with the pen (P), or trace a cell."
            else -> return
        }
    Column(modifier = modifier.padding(horizontal = 36.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(
            text = headline,
            style =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = texture.fg.toColor(),
                ),
        )
        BasicText(
            text = detail,
            style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.5.sp, lineHeight = 18.sp, color = texture.muted.toColor()),
        )
    }
}

/** The line on the glass above the inspector: what just happened, or the locked prompt with its Unlock action. */
@Composable
fun DrawNoticeBar(
    controller: DrawController,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val notice = controller.notice ?: return
    if (notice is DrawNotice.Toast) {
        LaunchedEffect(notice) {
            delay(TOAST_MS)
            if (controller.notice == notice) controller.notice = null
        }
    }
    val onInk = texture.canvas.toColor()
    Row(
        modifier =
            modifier
                .padding(
                    horizontal = 18.dp,
                ).widthIn(max = 420.dp)
                .background(texture.ink.toColor())
                .padding(horizontal = 13.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (notice) {
            is DrawNotice.Toast -> {
                BasicText(
                    text = notice.text,
                    style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.5.sp, lineHeight = 17.sp, color = onInk),
                )
            }

            is DrawNotice.Locked -> {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    BasicText(
                        text = "${notice.glyph} is approved and locked. Unlock it to edit; the change is kept as a diff.",
                        style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.5.sp, lineHeight = 17.sp, color = onInk),
                    )
                }
                BasicText(
                    text = "Unlock",
                    style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = onInk),
                    modifier = Modifier.clickable { controller.unlockFromNotice() }.padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }
        }
    }
}

/** The numbers of a finger drag, above the finger (T1): "x 427 · y 382 · x-height 500". */
@Composable
fun DrawReadout(
    readout: Readout?,
    texture: CanvasTexture,
    canvasWidthDp: Double,
    modifier: Modifier = Modifier,
) {
    val r = readout ?: return
    val density = LocalDensity.current
    Box(
        modifier =
            modifier.offset {
                val x = (r.at.x - READOUT_HALF_WIDTH_DP).coerceIn(8.0, (canvasWidthDp - READOUT_HALF_WIDTH_DP * 2 - 8.0).coerceAtLeast(8.0))
                val y = (r.at.y - READOUT_RISE_DP).coerceAtLeast(60.0)
                IntOffset((x * density.density).toInt(), (y * density.density).toInt())
            },
    ) {
        BasicText(
            text = r.text.uppercase(),
            style = Typography.mono(sizeSp = 10.0).copy(color = texture.canvas.toColor()),
            modifier = Modifier.background(texture.ink.toColor()).padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

private const val TOAST_MS = 3600L
private const val READOUT_HALF_WIDTH_DP = 90.0
private const val READOUT_RISE_DP = 78.0
