// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.ui.puck.Tool
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor

/** One line of the shortcut sheet: what it does and the keys that do it. */
data class ShortcutLine(
    val what: String,
    val keys: String,
)

/** A titled run of [ShortcutLine]s. */
data class ShortcutGroup(
    val title: String,
    val lines: List<ShortcutLine>,
)

/**
 * What the shortcut sheet lists. The tool keys come from [Tool], the commands from the one registry
 * ([DrawCommands]) with the chords it declares, and the rest are the fixed keys of [DrawKeyMap]; so the
 * sheet can only say what the keys do. Pure, so a test can compare it with the key map.
 */
object ShortcutSheetModel {
    fun groups(commands: List<DrawCommand>): List<ShortcutGroup> =
        listOf(
            ShortcutGroup("Tools", Tool.ORDERED.map { ShortcutLine(it.label, it.shortcut.toString()) }),
            ShortcutGroup(
                "Edit",
                commands.filter { it.keys != null }.map { ShortcutLine(it.label, it.keys.orEmpty()) } +
                    listOf(
                        ShortcutLine("Nudge the selection", "Arrows · ⇧ ×10 · Ctrl ×100"),
                        ShortcutLine("Next node · previous node", "Tab · ⇧ Tab"),
                        ShortcutLine("Clear the selection, or close a menu", "Esc"),
                        ShortcutLine("Pick a sector of an open ring", "1–8"),
                    ),
            ),
            ShortcutGroup(
                "View",
                listOf(
                    ShortcutLine("Command palette", "Ctrl K"),
                    ShortcutLine("Go to a glyph", "Ctrl G"),
                    ShortcutLine("Previous glyph · next glyph", "PgUp · PgDn"),
                    ShortcutLine("Map of the sections", "Ctrl M"),
                    ShortcutLine("Pan", "Space-drag · middle-drag"),
                    ShortcutLine("Zoom", "Wheel · pinch"),
                    ShortcutLine("Temporary Select tool", "hold Ctrl"),
                    ShortcutLine("Keep a move on one axis", "hold ⇧"),
                    ShortcutLine("Move a handle alone", "hold Alt"),
                    ShortcutLine("This sheet", "?"),
                ),
            ),
        )
}

/** The shortcut sheet: the keys of Draw, grouped, over the glass. A tap outside or Esc closes it. */
@Composable
fun DrawShortcutSheet(
    controller: DrawController?,
    texture: CanvasTexture,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(controller) { ShortcutSheetModel.groups(controller?.let(::commandsOf).orEmpty()) }
    val ink = texture.ink.toColor()
    Box(
        modifier =
            modifier.fillMaxSize().clickable(
                interactionSource =
                    remember {
                        MutableInteractionSource()
                    },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 18.dp, vertical = 70.dp)
                    .widthIn(max = 460.dp)
                    .heightIn(max = 560.dp)
                    .background(texture.canvas.toColor())
                    .border(1.dp, ink.copy(alpha = 0.3f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BasicText(
                text = "Keys in Draw",
                style =
                    TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = texture.fg.toColor(),
                    ),
            )
            for (group in groups) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    BasicText(text = group.title.uppercase(), style = Typography.mono(sizeSp = 9.0).copy(color = texture.muted.toColor()))
                    for (line in group.lines) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BasicText(
                                text = line.what,
                                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, color = texture.fg.toColor()),
                                modifier = Modifier.weight(1f),
                            )
                            BasicText(
                                text = line.keys,
                                style = Typography.mono(sizeSp = 10.0).copy(color = texture.muted.toColor(), letterSpacing = 0.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun commandsOf(controller: DrawController): List<DrawCommand> =
    DrawCommands.build(DrawCommandContext(controller.editor, controller.selection, controller.metrics(), controller))
