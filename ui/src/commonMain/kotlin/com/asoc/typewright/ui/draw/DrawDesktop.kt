// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.asoc.typewright.ui.glass.InspectorEdit
import com.asoc.typewright.ui.glass.InspectorField
import com.asoc.typewright.ui.glass.InspectorRow
import com.asoc.typewright.ui.puck.PuckUiState
import com.asoc.typewright.ui.puck.Tool
import com.asoc.typewright.ui.puck.ToolIcon
import com.asoc.typewright.ui.sheet.SheetLayout
import com.asoc.typewright.ui.tokens.Argb
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.MeaningColors
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor

/** The tint the desktop rail and column fade in from: the paper, a touch toward the ink, so the panels read as part of the sheet (the explorer: panels fade into the canvas, no borders). */
private fun panelColor(texture: CanvasTexture): Color = lerp(texture.canvas.toColor(), texture.ink.toColor(), PANEL_TINT)

/** The inspector fact [FieldSpec] as the row and the column show it; a value that is snapped to something is drawn in the snap role's colour. */
fun FieldSpec.toInspectorField(snapTint: Argb?): InspectorField =
    InspectorField(label = label, value = value, emphasize = emphasize, tint = if (snapped) snapTint else null, key = key)

/** The 46 dp bar across the top of the desktop frame (UI_SPEC §4): the app, the project and glyph, the glyph's measured counts and its lock, and where the palette and the shortcut sheet are. */
@Composable
fun DesktopTopBar(
    room: DrawRoom,
    texture: CanvasTexture,
    projectLine: String,
    modifier: Modifier = Modifier,
) {
    val muted = texture.muted.toColor()
    val state =
        room.session
            ?.state
            ?.collectAsState()
            ?.value
    val glyph = room.controller?.let { c -> state?.font?.glyph(c.editor.ref) }
    val locked = room.controller?.editor?.isLocked == true
    Row(
        modifier = modifier.fillMaxWidth().height(SheetLayout.TOP_BAR_DP.dp).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text = "TYPEWRIGHT", style = Typography.mono(sizeSp = 9.5).copy(color = muted))
        BasicText(
            text = if (glyph != null) "${projectName(projectLine)} · ${glyph.name}" else "Draw",
            style =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = texture.fg.toColor(),
                ),
        )
        if (glyph != null) {
            val unicode = room.directory.entry(glyph.name)?.unicodeLabel ?: "no unicode"
            BasicText(
                text = DrawSubject.glyphLine(glyph).substringAfter(" · ") + " · $unicode · " + (if (locked) "locked" else "unlocked"),
                style = Typography.mono(sizeSp = 9.5).copy(color = muted, letterSpacing = 0.06.em),
            )
        }
        Box(modifier = Modifier.weight(1f))
        BasicText(
            text = "Command… Ctrl K · shortcuts ?",
            style = Typography.mono(sizeSp = 9.5).copy(color = muted, letterSpacing = 0.06.em),
        )
    }
}

/** The project's name out of the status line "<name> · saved". */
private fun projectName(line: String): String = line.substringBefore(" · ")

/**
 * The tool rail (UI_SPEC §4): every tool with its icon, name and key letter, the current one an ink
 * block, then the layers, then the modifier keys. A tool whose behaviour is not on the sheet in this
 * build still lists, and says so the first time it is used.
 */
@Composable
fun ToolRail(
    puck: PuckUiState,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val panel = panelColor(texture)
    val ink = texture.ink.toColor()
    val canvas = texture.canvas.toColor()
    val muted = texture.muted.toColor()
    val fg = texture.fg.toColor()
    Column(
        modifier =
            modifier
                .width(SheetLayout.TOOL_RAIL_DP.dp)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(0f to panel, PANEL_SOLID to panel, 1f to panel.copy(alpha = 0f))),
    ) {
        RailHeading("Tools", muted)
        for (tool in puck.tools) {
            val current = tool == puck.currentTool
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(ROW_DP.dp)
                        .let { if (current) it.background(ink) else it }
                        .clickable { puck.selectTool(tool) }
                        .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolIcon(tool = tool, color = if (current) canvas else ink, sizeDp = 18.dp)
                BasicText(
                    text = tool.label,
                    style = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, color = if (current) canvas else fg),
                    modifier = Modifier.weight(1f),
                )
                BasicText(
                    text = tool.shortcut.toString(),
                    style =
                        Typography
                            .mono(
                                sizeSp = 10.0,
                            ).copy(color = if (current) canvas.copy(alpha = 0.7f) else muted, letterSpacing = 0.sp),
                )
            }
        }
        RailHeading("Layers", muted)
        RailRow("Master", "editing", fg, muted, bold = true)
        // Nothing can be loaded into a sketch or a background layer in this build, so the rows say so rather than offering a switch with nothing behind it.
        RailRow("Sketch", "none loaded", muted, muted)
        RailRow("Background", "none loaded", muted, muted)
        Box(modifier = Modifier.weight(1f))
        BasicText(
            text = "⇧ constrain · Alt break symmetry\nSpace pan · Ctrl select",
            style = Typography.mono(sizeSp = 8.5).copy(color = muted, letterSpacing = 0.06.em, lineHeight = 15.sp),
            modifier = Modifier.padding(start = 12.dp, end = 16.dp, bottom = 18.dp),
        )
    }
}

@Composable
private fun RailHeading(
    text: String,
    muted: Color,
) {
    BasicText(
        text = text.uppercase(),
        style = Typography.mono(sizeSp = 9.0).copy(color = muted),
        modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun RailRow(
    title: String,
    value: String,
    titleColor: Color,
    muted: Color,
    bold: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(ROW_DP.dp)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = title,
            style =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 13.sp,
                    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                    color = titleColor,
                ),
            modifier = Modifier.weight(1f),
        )
        BasicText(text = value, style = Typography.mono(sizeSp = 10.0).copy(color = muted, letterSpacing = 0.sp))
    }
}

/**
 * The inspector column (UI_SPEC §4): what is selected, then the glyph, each group a small heading over
 * a row of values that can be typed into or scrubbed exactly as the phone's row can, and the one
 * sentence that says what a field takes.
 */
@Composable
fun InspectorColumn(
    groups: List<FieldGroup>,
    texture: CanvasTexture,
    edit: InspectorEdit?,
    onEditingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val panel = panelColor(texture)
    val muted = texture.muted.toColor()
    val snapTint = MeaningColors.CYAN.forTexture(texture)
    Column(
        modifier =
            modifier
                .width(SheetLayout.INSPECTOR_COLUMN_DP.dp)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(0f to panel.copy(alpha = 0f), 1f - PANEL_SOLID to panel, 1f to panel))
                .padding(start = 30.dp, end = 18.dp, top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        for (group in groups) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(text = group.title.uppercase(), style = Typography.mono(sizeSp = 9.0).copy(color = muted))
                InspectorRow(
                    fields = group.fields.map { it.toInspectorField(snapTint) },
                    texture = texture,
                    edit = edit,
                    bottomInsetDp = 0.0,
                    horizontalPaddingDp = 0.0,
                    gapDp = COLUMN_GAP_DP,
                    onEditingChanged = onEditingChanged,
                )
            }
        }
        if (groups.isNotEmpty()) {
            BasicText(
                text = "Type a number, or = and a sum like =xheight+10, in a field. Drag sideways on a value to scrub it.",
                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, lineHeight = 17.sp, color = muted),
            )
        }
    }
}

private const val PANEL_TINT = 0.04f
private const val PANEL_SOLID = 0.8f
private const val ROW_DP = 30.0
private const val COLUMN_GAP_DP = 18.0
