package dev.aarso.typewright.ui.puck

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.ui.toIntOffset
import dev.aarso.typewright.ui.tokens.CanvasTexture
import dev.aarso.typewright.ui.tokens.SpacingTokens
import dev.aarso.typewright.ui.tokens.Typography
import dev.aarso.typewright.ui.tokens.toColor

/**
 * The puck's unfolded tool list (UI_SPEC §3 "Unfolded tool list"): "A column beside the puck (on
 * the side with room), canvas background, each row: 18 dp icon, mono uppercase name, shortcut
 * letter right-aligned in muted. Current tool row is an ink block. Tap selects and collapses."
 * A canvas-filled background is this component's own documented exception to "Nothing on the
 * glass has a background" (UI_SPEC §1): the fill is the canvas/ink token pair, not a bordered
 * card, shadow or blur.
 *
 * A thin wrapper over [GlassList] (P5b's own generalization of this exact visual structure, so
 * the puck's Primitives-tool menu -- [PrimitiveKindMenu], [PrimitiveEntryMethodMenu] -- reuses it
 * rather than duplicating this layout; see that file's own KDoc).
 */
@Composable
fun UnfoldedToolList(
    pin: PuckPinState,
    tools: List<Tool>,
    currentIndex: Int,
    texture: CanvasTexture,
    canvasSizeDp: Vec2,
    onSelect: (Int) -> Unit,
) {
    val ink = texture.ink.toColor()
    val canvas = texture.canvas.toColor()
    val normalizedIndex = ((currentIndex % tools.size) + tools.size) % tools.size
    val rows =
        tools.mapIndexed { index, tool ->
            val current = index == normalizedIndex
            GlassListRow(
                label = tool.label.uppercase(),
                shortcut = tool.shortcut.toString(),
                current = current,
                hasIcon = true,
                icon = { ToolIcon(tool = tool, color = if (current) canvas else ink, sizeDp = 18.dp) },
            )
        }
    GlassList(pin = pin, canvasSizeDp = canvasSizeDp, texture = texture, rows = rows, onSelect = onSelect)
}

/**
 * One row of [GlassList] (P5b: the shared visual structure [UnfoldedToolList] originated and this
 * task's own primitives menu reuses, unchanged, for its own plain-text rows). [hasIcon] controls
 * only the small leading gap [icon] otherwise needs -- a row with no icon (every primitive-kind
 * and entry-method row; task's own "keep the actual row content plain and functional... rather
 * than inventing extra visual flourish") sits flush left instead of leaving an empty icon-sized
 * hole.
 */
internal data class GlassListRow(
    val label: String,
    val shortcut: String? = null,
    val current: Boolean = false,
    val hasIcon: Boolean = false,
    val icon: @Composable () -> Unit = {},
)

/**
 * The unfolded-list-beside-the-puck layout itself (UI_SPEC §3 "Unfolded tool list"'s own
 * positioning rule: "beside the puck, on the side with room"), generalized over [rows] rather
 * than hard-coded to [Tool] -- [UnfoldedToolList] (P4b's own tool list), [PrimitiveKindMenu] and
 * [PrimitiveEntryMethodMenu] (P5b's own primitive-kind/entry-method lists) all call this one
 * function, so every unfolded list in this app shares one positioning rule, one row height, one
 * canvas-background/ink-current-row visual language.
 *
 * **Row layout, one documented difference from [UnfoldedToolList]'s own pre-P5b implementation:**
 * `icon, label (weighted to fill), shortcut` rather than `Arrangement.SpaceBetween` across exactly
 * three always-present children -- `SpaceBetween` only reads as "icon, name; shortcut right-
 * aligned" (UI_SPEC's own words) when all three children are always present, which stops being
 * true the moment a row has no icon or no shortcut (every primitive-menu row). Giving the label
 * `Modifier.weight(1f)` and rendering [GlassListRow.icon]/[GlassListRow.shortcut] only when
 * present reproduces UI_SPEC's own literal wording exactly (icon and name grouped together on the
 * left, the shortcut alone pinned to the right) in both cases, tool rows included -- not a visual
 * regression, a strictly closer match to the spec text than the arrangement it replaces.
 */
@Composable
internal fun GlassList(
    pin: PuckPinState,
    canvasSizeDp: Vec2,
    texture: CanvasTexture,
    rows: List<GlassListRow>,
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val ink = texture.ink.toColor()
    val canvas = texture.canvas.toColor()
    val muted = texture.muted.toColor()
    val rowWidthDp = 140.0
    // "beside the puck (on the side with room)": the side opposite the edge the puck is pinned to.
    val listLeftDp =
        when (pin.edge) {
            PuckEdge.RIGHT -> pin.center.x - PuckGestureDefaults.PUCK_DIAMETER_DP / 2.0 - SpacingTokens.GUTTER_DP - rowWidthDp
            PuckEdge.LEFT -> pin.center.x + PuckGestureDefaults.PUCK_DIAMETER_DP / 2.0 + SpacingTokens.GUTTER_DP
        }
    val listTopDp = pin.center.y - (rows.size * ROW_HEIGHT_DP) / 2.0

    Box(
        modifier =
            Modifier
                .offset { Vec2(listLeftDp, listTopDp).toIntOffset(density) }
                .width(rowWidthDp.dp)
                .background(canvas),
    ) {
        Column {
            rows.forEachIndexed { index, row ->
                Row(
                    modifier =
                        Modifier
                            .height(ROW_HEIGHT_DP.dp)
                            .width(rowWidthDp.dp)
                            .background(if (row.current) ink else canvas)
                            .clickable { onSelect(index) }
                            .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (row.hasIcon) row.icon()
                    BasicText(
                        text = row.label,
                        style = Typography.mono(sizeSp = 9.5).copy(color = if (row.current) canvas else ink),
                        modifier = Modifier.padding(start = if (row.hasIcon) 8.dp else 0.dp).weight(1f),
                    )
                    if (row.shortcut != null) {
                        BasicText(
                            text = row.shortcut,
                            style = Typography.mono(sizeSp = 9.5).copy(color = if (row.current) canvas.copy(alpha = 0.7f) else muted),
                        )
                    }
                }
            }
        }
    }
}

private const val ROW_HEIGHT_DP = 32.0
