// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor

/**
 * The rows the rich menu shows for [query], in the order it shows them (INTERACTION_V1 §5): with no
 * query a Recent group of the commands last used that are still on offer, then one section per scope
 * that applies to what is selected, then the destructive rows last. A query filters every command by
 * name and shows the matches in registry order. Pure, so the menu's content is a test.
 */
object RichMenuModel {
    /** A heading and the commands under it; an empty [title] is the unmarked destructive group. */
    data class Section(
        val title: String,
        val commands: List<DrawCommand>,
    )

    fun sections(
        commands: List<DrawCommand>,
        recent: List<String>,
        query: String,
        hasPoints: Boolean,
        hasSegment: Boolean,
    ): List<Section> {
        val needle = query.trim().lowercase()
        if (needle.isNotEmpty()) {
            val matches = commands.filter { needle in it.label.lowercase() || needle in it.id }
            return if (matches.isEmpty()) emptyList() else listOf(Section("Matches", matches.sortedBy { it.destructive }))
        }
        val out = mutableListOf<Section>()
        val recents = recent.mapNotNull { id -> commands.firstOrNull { it.id == id && it.enabled && !it.destructive } }
        if (recents.isNotEmpty()) out += Section("Recent", recents)
        for (scope in DrawScope.entries) {
            val applies =
                when (scope) {
                    DrawScope.NODE -> hasPoints
                    DrawScope.SEGMENT -> hasSegment
                    else -> true
                }
            val inScope = commands.filter { it.scope == scope && !it.destructive }
            if (applies && inScope.isNotEmpty()) out += Section(scope.title, inScope)
        }
        val destructive = commands.filter { it.destructive }
        if (destructive.isNotEmpty()) out += Section("", destructive)
        return out
    }
}

/**
 * The rich contextual menu (S20): the subject line, a find field, Recent, the scope sections, each row
 * a verb with its measured consequence, a trigger chip that follows the last input, and disabled rows
 * kept visible with their reason. On a phone it fills the glass above the deck; with a mouse it opens at
 * the pointer, keeps the find field focused so typing filters at once, and moves a highlight with the
 * arrow keys (Enter runs it). Tapping outside, or Esc, closes it.
 */
@Composable
fun DrawRichMenu(
    controller: DrawController,
    menu: MenuUi,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    var query by remember(menu) { mutableStateOf("") }
    var highlighted by remember(menu) { mutableStateOf(0) }
    val glyph = controller.glyph
    val selection = controller.selection
    val sections =
        RichMenuModel.sections(
            commands = menu.commands,
            recent = controller.recent,
            query = query,
            hasPoints = selection.points.isNotEmpty(),
            hasSegment = selection.segment != null,
        )
    val flat = sections.flatMap { it.commands }
    val current = highlighted.coerceIn(0, (flat.size - 1).coerceAtLeast(0))
    val touch = menu.pointer != PointerKind.MOUSE
    val canvas = texture.canvas.toColor()
    val scrim = remember { MutableInteractionSource() }

    fun runCommand(command: DrawCommand) {
        controller.closeOverlays()
        controller.run(command)
    }
    BoxWithConstraints(modifier.fillMaxSize().clickable(interactionSource = scrim, indication = null) { controller.closeOverlays() }) {
        val panelWidth = if (touch) maxWidth else MOUSE_PANEL_WIDTH_DP.dp
        val ceiling = (maxHeight - DECK_CLEARANCE_DP.dp).coerceAtLeast(MIN_PANEL_DP.dp)
        // A phone's menu hangs from a fixed line; a mouse's opens at the pointer, moved up only as far as it must to keep a usable height above the deck.
        val top =
            if (touch) {
                MENU_TOP_DP.dp
            } else {
                menu.at.y.dp
                    .coerceIn(MENU_EDGE_DP.dp, (ceiling - MIN_MOUSE_PANEL_DP.dp).coerceAtLeast(MENU_EDGE_DP.dp))
            }
        val maxPanel = (ceiling - top).coerceAtLeast(MIN_PANEL_DP.dp)
        val panelModifier =
            if (touch) {
                Modifier.offset(y = top)
            } else {
                Modifier.offset {
                    val x =
                        (
                            menu.at.x.dp
                                .toPx()
                                .toInt()
                        ).coerceIn(0, (maxWidth.roundToPx() - panelWidth.roundToPx()).coerceAtLeast(0))
                    IntOffset(x, top.roundToPx())
                }
            }
        Column(
            modifier =
                panelModifier
                    .width(panelWidth)
                    .heightIn(max = maxPanel)
                    .let {
                        if (touch) {
                            it.background(Brush.verticalGradient(0f to canvas.copy(alpha = 0f), FADE_STOP to canvas, 1f to canvas))
                        } else {
                            it.background(canvas).border(1.dp, texture.ink.toColor().copy(alpha = HAIRLINE_ALPHA))
                        }
                    }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(horizontal = if (touch) MENU_GUTTER_DP.dp else 14.dp, vertical = if (touch) 0.dp else 12.dp),
        ) {
            if (touch) Spacer(Modifier.height(FADE_PAD_DP.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                BasicText(
                    text = menu.subject,
                    style =
                        TextStyle(
                            fontFamily = FontFamily.Default,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = texture.fg.toColor(),
                        ),
                )
                val context = glyph?.let { subjectContext(it, selection) }
                if (context !=
                    null
                ) {
                    BasicText(
                        text = context,
                        style = Typography.mono(sizeSp = 9.5).copy(color = texture.muted.toColor(), letterSpacing = 0.04.em),
                    )
                }
            }
            FindField(
                value = query,
                onChange = {
                    query = it
                    highlighted = 0
                },
                texture = texture,
                autoFocus = !touch,
                onKey = { key ->
                    when (key) {
                        Key.Escape -> {
                            controller.closeOverlays()
                            true
                        }

                        Key.DirectionDown -> {
                            if (flat.isNotEmpty()) highlighted = (current + 1) % flat.size
                            true
                        }

                        Key.DirectionUp -> {
                            if (flat.isNotEmpty()) highlighted = (current - 1 + flat.size) % flat.size
                            true
                        }

                        Key.Enter, Key.NumPadEnter -> {
                            flat.getOrNull(current)?.takeIf { it.enabled }?.let(::runCommand)
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
            )
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (sections.isEmpty()) {
                    BasicText(
                        text = "Nothing is called that.",
                        style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.5.sp, color = texture.muted.toColor()),
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
                var index = 0
                for (section in sections) {
                    if (section.title.isNotEmpty()) SectionHeading(section.title, texture)
                    for (command in section.commands) {
                        val row = index++
                        CommandRow(command, touch, texture, highlighted = !touch && row == current) { runCommand(command) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun subjectContext(
    glyph: Glyph,
    selection: DrawSelection,
): String? {
    val ref = selection.points.firstOrNull()
    return when {
        ref != null -> if (DrawSubject.isInner(glyph, ref.contour)) "inner contour" else "outer contour"
        selection.segment != null -> DrawSubject.contourWord(glyph, selection.segment.contour)
        else -> null
    }
}

@Composable
private fun FindField(
    value: String,
    onChange: (String) -> Unit,
    texture: CanvasTexture,
    autoFocus: Boolean,
    onKey: (Key) -> Boolean,
) {
    val muted = texture.muted.toColor()
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).padding(bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(12.dp)) {
            val w = 1.3.dp.toPx()
            drawCircle(muted, 4.dp.toPx(), Offset(5.dp.toPx(), 5.dp.toPx()), style = Stroke(w))
            drawLine(muted, Offset(8.dp.toPx(), 8.dp.toPx()), Offset(11.5.dp.toPx(), 11.5.dp.toPx()), strokeWidth = w)
        }
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                BasicText(text = "Find a command", style = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, color = muted))
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, color = texture.fg.toColor()),
                cursorBrush = SolidColor(texture.fg.toColor()),
                modifier =
                    Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
                        event.type == KeyEventType.KeyDown && onKey(event.key)
                    },
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(texture.ink.toColor().copy(alpha = HAIRLINE_ALPHA)))
}

@Composable
private fun SectionHeading(
    title: String,
    texture: CanvasTexture,
) {
    BasicText(
        text = title.uppercase(),
        style = Typography.mono(sizeSp = 8.5).copy(color = texture.muted.toColor()),
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun CommandRow(
    command: DrawCommand,
    touch: Boolean,
    texture: CanvasTexture,
    highlighted: Boolean,
    onRun: () -> Unit,
) {
    val strength = if (command.enabled) 1f else DISABLED_ALPHA
    // The highlighted row is an ink block with paper text, the selection language everywhere else on the glass.
    val fg = if (highlighted) texture.canvas.toColor() else texture.fg.toColor()
    val muted = if (highlighted) texture.canvas.toColor().copy(alpha = ON_INK_NOTE_ALPHA) else texture.muted.toColor()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = ROW_MIN_DP.dp)
                .let { if (highlighted) it.background(texture.ink.toColor()) else it }
                .clickable(enabled = command.enabled, onClick = onRun)
                .padding(vertical = 3.dp, horizontal = if (highlighted) 8.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(
                text = (if (command.destructive) "● " else "") + command.label,
                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.5.sp, color = fg.copy(alpha = strength)),
            )
            val note = command.reason ?: command.consequence
            if (note != null) {
                BasicText(text = note, style = Typography.mono(sizeSp = 9.5).copy(color = muted, letterSpacing = 0.04.em))
            }
        }
        val chip = if (touch) command.gesture else command.keys
        if (chip != null) {
            BasicText(
                text = chip,
                style =
                    Typography.mono(sizeSp = 8.5).copy(
                        color = (if (highlighted) texture.ink.toColor() else texture.canvas.toColor()).copy(alpha = strength),
                        letterSpacing = 0.06.em,
                    ),
                modifier =
                    Modifier
                        .background((if (highlighted) texture.canvas.toColor() else texture.ink.toColor()).copy(alpha = strength))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        if (touch) {
            BasicText(
                text = command.keys ?: "—",
                style = Typography.mono(sizeSp = 8.5).copy(color = muted, letterSpacing = 0.06.em, textAlign = TextAlign.End),
                modifier = Modifier.width(KEY_COLUMN_DP.dp),
            )
        }
    }
}

/** One row of the Draw page's ⋯ menu: its [title], the [keys] that do it, the [reason] it cannot be done now (shown under it), and what it does. */
class PageMenuEntry(
    val title: String,
    val keys: String? = null,
    val reason: String? = null,
    val run: () -> Unit,
)

/**
 * The ⋯ menu of the Draw page (S20): a column rising from the deck's right circle, a row to a line of
 * ink with paper text, the title on the left and its keys on the right, and a mono line under a row that
 * cannot be done now saying why. It scrolls when it is longer than the glass above the deck, and a tap
 * outside closes it. A destructive row carries a ● in its title.
 */
@Composable
fun DrawPageMenu(
    texture: CanvasTexture,
    items: List<PageMenuEntry>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onInk = texture.canvas.toColor()
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        val available = (maxHeight - PAGE_MENU_BOTTOM_DP.dp - PAGE_MENU_TOP_DP.dp).coerceAtLeast(MIN_PANEL_DP.dp)
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = PAGE_MENU_BOTTOM_DP.dp)
                    .width(PAGE_MENU_WIDTH_DP.dp)
                    .heightIn(max = available)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (item in items) {
                val strength = if (item.reason == null) 1f else PAGE_MENU_DISABLED_ALPHA
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(texture.ink.toColor())
                            .clickable {
                                onDismiss()
                                item.run()
                            }.padding(horizontal = 13.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BasicText(
                            text = item.title,
                            style = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, color = onInk.copy(alpha = strength)),
                            modifier = Modifier.weight(1f),
                        )
                        item.keys?.let {
                            BasicText(
                                text = it.uppercase(),
                                style =
                                    Typography
                                        .mono(
                                            sizeSp = 8.5,
                                        ).copy(color = onInk.copy(alpha = NOTE_ALPHA * strength), letterSpacing = 0.1.em),
                            )
                        }
                    }
                    item.reason?.let {
                        BasicText(
                            text = it.uppercase(),
                            style = Typography.mono(sizeSp = 8.5).copy(color = onInk.copy(alpha = NOTE_ALPHA), letterSpacing = 0.08.em),
                        )
                    }
                }
            }
        }
    }
}

private const val MENU_GUTTER_DP = 22.0
private const val MENU_TOP_DP = 130.0
private const val DECK_CLEARANCE_DP = 180.0
private const val MIN_PANEL_DP = 160.0
private const val MIN_MOUSE_PANEL_DP = 360.0
private const val MENU_EDGE_DP = 8.0
private const val ON_INK_NOTE_ALPHA = 0.7f
private const val MOUSE_PANEL_WIDTH_DP = 380.0
private const val FADE_PAD_DP = 28.0
private const val FADE_STOP = 0.07f
private const val HAIRLINE_ALPHA = 0.3f
private const val DISABLED_ALPHA = 0.4f
private const val ROW_MIN_DP = 36.0
private const val KEY_COLUMN_DP = 64.0
private const val PAGE_MENU_WIDTH_DP = 250.0
private const val PAGE_MENU_BOTTOM_DP = 136.0
private const val PAGE_MENU_TOP_DP = 70.0
private const val NOTE_ALPHA = 0.72f
private const val PAGE_MENU_DISABLED_ALPHA = 0.55f
