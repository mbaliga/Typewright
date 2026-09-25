// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.sheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.asoc.typewright.core.geometry.Guideline
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Guides at any angle (brief §10 v1-must-have item 5; `core-font`'s UFO 3 [Guideline] model,
 * P5b's sibling core-font report). Reproduces [WorldLinesPass]'s own metric-line approach exactly
 * -- same layer (UI_SPEC §1 layer 3, "Lines -- metric lines and guides ... SVG in sheet space; 1
 * dp hairline at ink 20%"), same [CanvasTexture.line] token, same "read the camera inside the draw
 * lambda, not composition" discipline, same mono label style -- rather than inventing a second
 * styling system for what UI_SPEC itself treats as one layer. A guide is visually a metric line
 * that is not necessarily horizontal.
 *
 * **Font-wide vs per-glyph.** [Guideline] (`core-geometry`) models both a project's font-wide
 * guidelines (`core-font`'s `UfoFontInfo.guidelines`) and one glyph's own local guidelines
 * (`Glyph.guidelines`) with the same six attributes -- this pass draws whatever [guidelines] list
 * it is handed, agnostic to which source they came from; a caller that wants both concatenates
 * them itself.
 *
 * **No creation UI.** The explorer shows no guide-creation interaction anywhere in
 * `ui/typewright-explorer.html` (checked directly, not assumed) -- see `docs/OPEN_QUESTIONS.md`'s
 * P5b UI entry for the honest statement that this is this task's own minimal design, not a
 * reproduction of a shown screen.
 */
@Composable
fun WorldGuidesPass(
    cameraState: SheetCameraState,
    texture: CanvasTexture,
    guidelines: List<Guideline>,
    modifier: Modifier = Modifier,
) {
    if (guidelines.isEmpty()) return
    val lineColor = texture.line.toColor()

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val camera = cameraState.camera
            val strokeWidthPx = 1.dp.toPx()
            for (guideline in guidelines) {
                val (from, to) = guideline.worldEndpoints()
                val screenFrom = camera.worldToScreenFontUp(from)
                val screenTo = camera.worldToScreenFontUp(to)
                drawLine(
                    lineColor,
                    Offset(screenFrom.x.dp.toPx(), screenFrom.y.dp.toPx()),
                    Offset(screenTo.x.dp.toPx(), screenTo.y.dp.toPx()),
                    strokeWidth = strokeWidthPx,
                )
            }
        }

        // Labels: same "mono, muted, screen space" treatment as WorldLinesPass's own metric
        // labels -- only their position tracks the camera (a layout-phase `Modifier.offset { }`
        // read), never their size, matching "grid lines... dissolve... letters and nodes never
        // fade" not applying to glass-adjacent text either way (UI_SPEC §5.3).
        for (guideline in guidelines) {
            val name = guideline.name ?: continue
            val anchorWorld = Vec2(guideline.x ?: 0.0, guideline.y ?: 0.0)
            Box(
                modifier =
                    Modifier.offset {
                        val screen = cameraState.camera.worldToScreenFontUp(anchorWorld)
                        IntOffset(screen.x.dp.roundToPx() + 6, screen.y.dp.roundToPx() - 14)
                    },
            ) {
                BasicText(
                    text = name,
                    style = Typography.mono(sizeSp = 9.5).copy(color = texture.muted.toColor()),
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

/** How far a guide's drawn segment extends past its own anchor, each direction, in font units -- long enough to cross any on-screen viewport this app's zoom range ([-0.05, 8]) reaches. */
private const val GUIDE_HALF_LENGTH_UNITS = 200_000.0

/**
 * The two font-unit endpoints of [this] guide's infinite line, clipped in practice by
 * [GUIDE_HALF_LENGTH_UNITS] rather than a true viewport intersection (the same "extend far past
 * the screen, let the `Canvas` clip" simplification [WorldLinesPass]'s grid lines already use for
 * their own full-width/height hairlines).
 *
 * Per [Guideline]'s own KDoc, a guideline is one of three shapes: vertical ([y] `null`),
 * horizontal ([x] `null`), or angled (all three of [x]/[y]/[angle] set). A fourth combination --
 * both [x] and [y] set with [angle] `null` -- is structurally permitted by [Guideline]'s `init`
 * block (which only forbids [angle] *without* both coordinates) but is not one of the spec's own
 * three named shapes; this function's honest fallback treats it as horizontal through [y], the
 * same convention an angle of exactly `0.0` would produce.
 */
private fun Guideline.worldEndpoints(): Pair<Vec2, Vec2> {
    // Captured into locals rather than smart-cast on `this.x`/`this.y`/`this.angle` directly:
    // Kotlin does not smart-cast a `val` property declared in a different module (`Guideline`
    // lives in `core-geometry`) from a plain null-check, even though the property is final.
    val guideX = x
    val guideY = y
    val guideAngle = angle
    return when {
        guideAngle != null && guideX != null && guideY != null -> {
            val radians = guideAngle * PI / 180.0
            val direction = Vec2(cos(radians), sin(radians))
            val anchor = Vec2(guideX, guideY)
            (anchor - direction * GUIDE_HALF_LENGTH_UNITS) to (anchor + direction * GUIDE_HALF_LENGTH_UNITS)
        }

        guideY == null && guideX != null -> {
            Vec2(guideX, -GUIDE_HALF_LENGTH_UNITS) to Vec2(guideX, GUIDE_HALF_LENGTH_UNITS)
        }

        else -> {
            val worldY = guideY ?: 0.0
            Vec2(-GUIDE_HALF_LENGTH_UNITS, worldY) to Vec2(GUIDE_HALF_LENGTH_UNITS, worldY)
        }
    }
}

/**
 * A small, honest, in-memory sample -- **not** real project data (`docs/OPEN_QUESTIONS.md`'s P5b
 * UI entry: no project/font flows into `ui` at this layer yet). Anchored near [RoomInk.kt]'s own
 * placeholder glyph position (`centerX = 0 + 180.0`, the Draw room's own left-edge convention) so
 * a guide actually crosses the placeholder ink it is meant to align, rather than floating
 * somewhere the placeholder never reaches.
 */
fun sampleGuidelines(): List<Guideline> =
    listOf(
        Guideline(y = -12.0, name = "overshoot"),
        Guideline(x = 180.0, name = "glyph origin"),
        Guideline(x = 180.0, y = 0.0, angle = 78.0, name = "italic 78°"),
    )
