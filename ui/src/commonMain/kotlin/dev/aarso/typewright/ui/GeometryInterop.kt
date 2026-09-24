package dev.aarso.typewright.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import dev.aarso.typewright.core.geometry.Vec2
import kotlin.math.roundToInt

// The one place raw Compose pixels convert to and from `core-geometry`'s Vec2 as screen dp. Every
// P4a file (`ui.sheet` / `.puck` / `.tokens`) is deliberately Compose-free and works only in
// screen dp (see `PuckGestureEvents.kt`'s own KDoc: "already density-converted by the caller");
// this file is that caller's conversion, and the only place in `ui` that mixes raw pixels with
// Vec2.

/** This raw-pixel [Offset] as screen dp, dividing out [density]. */
fun Offset.toVec2(density: Density): Vec2 = Vec2((x / density.density).toDouble(), (y / density.density).toDouble())

/** This screen-dp [Vec2] as a raw-pixel [Offset], multiplying by [density]. */
fun Vec2.toOffset(density: Density): Offset = Offset((x * density.density).toFloat(), (y * density.density).toFloat())

/** This screen-dp [Vec2] as a raw whole-pixel [androidx.compose.ui.unit.IntOffset], for `Modifier.offset { }`. */
fun Vec2.toIntOffset(density: Density): androidx.compose.ui.unit.IntOffset =
    androidx.compose.ui.unit
        .IntOffset((x * density.density).roundToInt(), (y * density.density).roundToInt())
