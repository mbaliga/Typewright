package dev.aarso.typewright.ui.puck

import androidx.compose.runtime.Composable
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.ui.tokens.CanvasTexture

/**
 * The Primitives tool's own unfolded list, stage one: every [PrimitiveKind], in
 * [PrimitiveKind.ORDERED] order. Reuses [GlassList], the exact visual structure
 * [UnfoldedToolList] already established (task's own instruction: "each row: icon, mono uppercase
 * name, shortcut letter... [generalize] so it can also list primitive kinds... keep the actual
 * row content plain and functional... rather than inventing extra visual flourish") -- these rows
 * carry no icon and no shortcut (the explorer names no shortcut letter for an individual
 * primitive kind), just the plain label.
 */
@Composable
fun PrimitiveKindMenu(
    pin: PuckPinState,
    texture: CanvasTexture,
    canvasSizeDp: Vec2,
    onSelect: (PrimitiveKind) -> Unit,
) {
    val rows =
        PrimitiveKind.ORDERED.map { kind ->
            GlassListRow(label = kind.label.uppercase())
        }
    GlassList(pin = pin, canvasSizeDp = canvasSizeDp, texture = texture, rows = rows) { index ->
        onSelect(PrimitiveKind.ORDERED[index])
    }
}

/**
 * The Primitives tool's own unfolded list, stage two: [kind]'s own [PrimitiveKind.entryMethodLabels],
 * shown after a kind with more than one entry method is picked from [PrimitiveKindMenu] (a kind
 * with exactly one, [PrimitiveKind.STEM]/[PrimitiveKind.BOWL], skips this stage entirely -- see
 * [Puck]'s own `PrimitivesMenuStage.KIND` handling). Same plain, iconless, shortcut-less row --
 * "two points" / "point, angle, length" / "point, tangent to curve" and so on, exactly the entry-
 * method vocabulary `engine-construct`'s own primitive files (`LinePrimitive.kt`, `Arc.kt`, ...)
 * document, restated once in [PrimitiveKind.entryMethodLabels] rather than duplicated here.
 */
@Composable
fun PrimitiveEntryMethodMenu(
    pin: PuckPinState,
    kind: PrimitiveKind,
    texture: CanvasTexture,
    canvasSizeDp: Vec2,
    onSelect: (Int) -> Unit,
) {
    val rows = kind.entryMethodLabels.map { label -> GlassListRow(label = label.uppercase()) }
    GlassList(pin = pin, canvasSizeDp = canvasSizeDp, texture = texture, rows = rows, onSelect = onSelect)
}
