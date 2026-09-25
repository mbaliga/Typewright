// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourLocation
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.CurveFormat
import dev.aarso.typewright.core.geometry.PaletteCommand
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.closePolylineToContour
import dev.aarso.typewright.core.geometry.count
import dev.aarso.typewright.core.geometry.direction
import dev.aarso.typewright.core.geometry.enforceContourDirections
import dev.aarso.typewright.core.geometry.harmoniseCurvatureAtJoin
import dev.aarso.typewright.core.geometry.insertExtremaOnCurvePoints
import dev.aarso.typewright.core.geometry.knifeContour
import dev.aarso.typewright.core.geometry.reverseContour
import dev.aarso.typewright.core.geometry.roundContourCoordinates
import dev.aarso.typewright.core.geometry.simplifyContour
import dev.aarso.typewright.ui.tokens.CanvasTexture
import dev.aarso.typewright.ui.tokens.Typography
import dev.aarso.typewright.ui.tokens.toColor

/**
 * The desktop command palette (`UI_SPEC.md` §4 and brief §5.2/§10 item 6: "command palette on
 * Ctrl/⌘ K"; the explorer's own worked example, `ui/typewright-explorer.html` `.palette`/`.li`/
 * `.in`, grepped directly, around line 928: a 520 dp-wide panel centred 70 dp from the top, an
 * input row with a `›_` prompt glyph and cursor, and a list of icon-less rows (label + small muted
 * description + right-aligned `kbd` shortcut), the current row a quiet ink tint).
 *
 * **Border radius.** The explorer's base `.palette` rule gives it `border-radius:12px`, but the
 * later "brutalist pass" stylesheet (around line 466) explicitly lists `.palette` among the
 * selectors it resets to `border-radius:0` -- a plain, unconditional, later `<style>` block with
 * no toggle, so it is what actually renders. This panel is drawn with square corners to match.
 *
 * **No desktop-only source set exists in `ui` yet** (only `commonMain`/`commonTest`/`desktopTest`;
 * confirmed by listing `ui/src` before writing this). Rather than invent one for a single
 * composable, this follows the precedent `SheetGestures.kt`'s own `roomKeyboardNavigation` already
 * set: a plain `commonMain` composable and keyboard modifier that only a physical keyboard's
 * Ctrl/⌘+K chord ever triggers in practice -- harmless to compile everywhere, meaningfully
 * reachable only where a keyboard exists. `docs/OPEN_QUESTIONS.md`'s P5b UI entry states this
 * plainly rather than claiming a real desktop/common split.
 *
 * **Wired to real functions, on a small demo shape.** This module has no live contour-selection
 * model yet (P5b's puck/primitives half is a sibling task, not this one), so each of the eight
 * Palette commands below runs its real `core-geometry` function against a small, fixed demo
 * contour ([demoSquare]/[demoCircle]/[demoTriangleForClose]) rather than a stub -- see
 * [paletteEntries]'s own KDoc for exactly which demo each command uses and why, and
 * `docs/OPEN_QUESTIONS.md` for the honest statement that this is a demo, not a real selection.
 * "Run the gate on this glyph" and "Compare with…" are the explorer's own two non-Palette rows
 * (`qa`'s Ship/gate pipeline and Google Fonts comparison) -- shown with the explorer's exact copy
 * for visual completeness, but left unwired (their `run` is `null`): both are out of this task's
 * reach in the time available, and this is stated here rather than pretended otherwise.
 */
@Composable
fun CommandPalette(
    visible: Boolean,
    onDismiss: () -> Unit,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    var query by remember { mutableStateOf("") }
    var highlighted by remember { mutableStateOf(0) }
    var lastResult by remember { mutableStateOf<String?>(null) }
    val entries = remember { paletteEntries() }
    val filtered = remember(query) { entries.filter { it.label.contains(query, ignoreCase = true) } }
    if (highlighted >= filtered.size) highlighted = 0

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(visible) { if (visible) focusRequester.requestFocus() }

    val panelBg = texture.canvas.toColor()
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val ink = texture.ink.toColor()

    fun runHighlighted() {
        val entry = filtered.getOrNull(highlighted) ?: return
        lastResult = entry.run?.invoke() ?: "${entry.label}: not wired in this build"
    }

    Box(
        modifier =
            modifier
                .width(PALETTE_WIDTH_DP.dp)
                .padding(top = PALETTE_TOP_DP.dp)
                .background(panelBg)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Escape -> {
                            onDismiss()
                            true
                        }

                        Key.DirectionDown -> {
                            if (filtered.isNotEmpty()) highlighted = (highlighted + 1) % filtered.size
                            true
                        }

                        Key.DirectionUp -> {
                            if (filtered.isNotEmpty()) highlighted = (highlighted - 1 + filtered.size) % filtered.size
                            true
                        }

                        Key.Enter, Key.NumPadEnter -> {
                            runHighlighted()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
    ) {
        Column {
            // --- Input row: ">_" prompt, typed query, blinking cursor (from BasicTextField's own
            // cursor), "esc" hint right-aligned -- the explorer's own `.in` row exactly.
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(text = "›_", style = Typography.sentence.copy(color = muted))
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        textStyle = Typography.sentence.copy(color = fg),
                        singleLine = true,
                        cursorBrush = SolidColor(fg),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                }
                BasicText(text = "esc", style = Typography.sentence.copy(color = muted))
            }

            // --- The command rows themselves ---
            for ((index, entry) in filtered.withIndex()) {
                val current = index == highlighted
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .let { if (current) it.background(ink.copy(alpha = PALETTE_CURRENT_ROW_TINT)) else it }
                            .clickable {
                                highlighted = index
                                runHighlighted()
                            }.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(text = entry.label, style = Typography.sentence.copy(fontSize = PALETTE_ROW_SIZE_SP.sp, color = fg))
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        text = "· ${entry.description}",
                        style = Typography.sentence.copy(color = muted),
                        modifier = Modifier.weight(1f),
                    )
                    entry.shortcut?.let { shortcut ->
                        BasicText(text = shortcut, style = Typography.mono(sizeSp = 11.0).copy(color = muted))
                    }
                }
            }

            lastResult?.let { result ->
                BasicText(
                    text = result,
                    style = Typography.mono(sizeSp = 10.0).copy(color = muted),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Opens/closes [CommandPalette] on Ctrl+K (desktop/Linux/Windows) or ⌘+K (macOS) key-down, per UI_SPEC's own repeated "command palette on Ctrl/⌘ K" -- see [CommandPalette]'s own KDoc on why this lives in commonMain with no desktop-only gate. */
fun Modifier.commandPaletteShortcut(onToggle: () -> Unit): Modifier =
    this.onKeyEvent { event: KeyEvent ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        if (event.key == Key.K && (event.isCtrlPressed || event.isMetaPressed)) {
            onToggle()
            true
        } else {
            false
        }
    }

private const val PALETTE_WIDTH_DP = 520.0
private const val PALETTE_TOP_DP = 70.0
private const val PALETTE_ROW_SIZE_SP = 12.5
private const val PALETTE_CURRENT_ROW_TINT = 0.06f

/** One command-palette row: what it shows, and what running it actually does (`null` = shown but not wired, per [CommandPalette]'s own KDoc). */
data class PaletteEntry(
    val id: String,
    val label: String,
    val description: String,
    val shortcut: String? = null,
    val run: (() -> String)?,
)

/**
 * The eight `PaletteCommand`s (`core-geometry`, the sibling P5b report) plus the explorer's own two
 * extra rows, in the explorer's own displayed order first (`Add extremes`, `Correct path
 * direction`, `Round coordinates`, `Run the gate on this glyph`, `Compare with…` -- copy and
 * shortcuts reused verbatim from `ui/typewright-explorer.html` lines 929-933), then the five brief
 * commands the explorer's own worked example does not show (`harmonise curvature`, `tidy/simplify`,
 * `reverse contour`, `cut/knife`, `close/open contour`) in new rows written in the same
 * label-plus-"· description" copy style, with no shortcut invented for any of them (this task's own
 * choice, not the explorer's).
 *
 * **The demo shapes each `run` closure uses**, since none of them operate on a live selection yet:
 * [demoSquare] (a plain 4-corner square, [closePolylineToContour]) for add-extremes, correct-
 * direction, reverse, round-coordinates and knife; [demoSquareWithRedundantPoint] (the same square
 * with one extra, genuinely collinear point on its bottom edge) for tidy, so there is something
 * real to remove; [demoCircle] (a standard 4-arc kappa-circle approximation, the same construction
 * `ArcTest`/`HobbySpline.kt` already use elsewhere in this codebase) for harmonise-curvature, since
 * a straight-edged square has zero curvature on both sides of every join and so nothing to
 * harmonise; [demoTriangleForClose] (a bare 3-point polyline, not yet a `Contour`) for
 * close-contour, the one command whose own input is a point list rather than a contour
 * ([dev.aarso.typewright.core.geometry.PaletteTarget.POINT_LIST]).
 */
private fun paletteEntries(): List<PaletteEntry> =
    listOf(
        PaletteEntry(
            id = PaletteCommand.ADD_EXTREMES.id,
            label = "Add extremes",
            description = "insert on-curve points at the 4 extremes",
            shortcut = "⇧E",
            run = {
                val before = demoCircle().count()
                val after = insertExtremaOnCurvePoints(demoCircle()).count()
                "add extremes: ${before.onCurveEquivalent}·${before.offCurve} → ${after.onCurveEquivalent}·${after.offCurve}"
            },
        ),
        PaletteEntry(
            id = PaletteCommand.CORRECT_DIRECTION.id,
            label = "Correct path direction",
            description = "outer counter-clockwise",
            shortcut = "⇧R",
            run = {
                val reversedFirst = reverseContour(demoSquare())
                val before = reversedFirst.direction()
                val corrected = enforceContourDirections(listOf(reversedFirst)).first()
                val after = corrected.direction()
                "correct direction: $before → $after"
            },
        ),
        PaletteEntry(
            id = PaletteCommand.ROUND_COORDINATES.id,
            label = "Round coordinates",
            description = "to integer units",
            shortcut = null,
            run = {
                roundContourCoordinates(demoSquare())
                "round coordinates: identity (Point.x/y are already Int -- core-geometry's own honesty note)"
            },
        ),
        PaletteEntry(
            id = "gate",
            label = "Run the gate on this glyph",
            description = "node economy, extrema, tangents",
            shortcut = "⌘⏎",
            run = null,
        ),
        PaletteEntry(
            id = "compare",
            label = "Compare with…",
            description = "overlay another font at cap height",
            shortcut = null,
            run = null,
        ),
        PaletteEntry(
            id = PaletteCommand.HARMONISE_CURVATURE.id,
            label = "Harmonise curvature",
            description = "match curvature across a smooth join",
            shortcut = null,
            run = {
                val before = demoCircle()
                val after = harmoniseCurvatureAtJoin(before, onCurveIndex = 0)
                // index 1 is the outgoing segment's own near handle -- the one point
                // harmoniseCurvatureAtJoin is allowed to move for this join (see its own KDoc:
                // "without moving the anchor" -- index 0 -- "and without changing... tangent
                // direction" -- the far control at index 2 is untouched either).
                val moved = before.points[1].point != after.points[1].point
                "harmonise curvature: join 0's outgoing handle ${if (moved) "adjusted" else "already matched"}"
            },
        ),
        PaletteEntry(
            id = PaletteCommand.TIDY.id,
            label = "Tidy / simplify",
            description = "remove redundant points, with a live count",
            shortcut = null,
            run = {
                val result = simplifyContour(demoSquareWithRedundantPoint())
                "tidy: ${result.before.onCurveEquivalent}·${result.before.offCurve} → " +
                    "${result.after.onCurveEquivalent}·${result.after.offCurve}"
            },
        ),
        PaletteEntry(
            id = PaletteCommand.REVERSE_CONTOUR.id,
            label = "Reverse contour",
            description = "flip point winding order",
            shortcut = null,
            run = {
                val before = demoSquare()
                val after = reverseContour(before)
                "reverse contour: ${before.direction()} → ${after.direction()}"
            },
        ),
        PaletteEntry(
            id = PaletteCommand.KNIFE.id,
            label = "Cut / knife",
            description = "split a contour at two picked points",
            shortcut = null,
            run = {
                val (a, b) = knifeContour(demoSquare(), ContourLocation(0, 0.5), ContourLocation(2, 0.5))
                "knife: split into ${a.count().onCurveEquivalent} + ${b.count().onCurveEquivalent} on-curve points"
            },
        ),
        PaletteEntry(
            id = PaletteCommand.CLOSE_CONTOUR.id,
            label = "Close contour",
            description = "connect the last point back to the first",
            shortcut = null,
            run = {
                val closed = closePolylineToContour(demoTriangleForClose())
                "close contour: ${closed.count().onCurveEquivalent}·${closed.count().offCurve} points"
            },
        ),
    )

/** A plain 4-corner square, closed with [closePolylineToContour] -- straight (degenerate-cubic) edges, no curvature. */
private fun demoSquare(): Contour = closePolylineToContour(listOf(Point(0, 0), Point(200, 0), Point(200, 200), Point(0, 200)))

/** [demoSquare]'s own corners plus one genuinely collinear extra point on the bottom edge, for [simplifyContour] to have something real to remove. */
private fun demoSquareWithRedundantPoint(): Contour =
    closePolylineToContour(listOf(Point(0, 0), Point(100, 0), Point(200, 0), Point(200, 200), Point(0, 200)))

/** A bare 3-point polyline (not yet a [Contour]) for [closePolylineToContour]'s own demo. */
private fun demoTriangleForClose(): List<Point> = listOf(Point(0, 0), Point(150, 0), Point(150, 150))

private const val DEMO_CIRCLE_RADIUS = 200
private const val DEMO_CIRCLE_KAPPA = 0.5522847498307936

/**
 * A 4-arc circle-*like* approximation (radius [DEMO_CIRCLE_RADIUS], the textbook
 * `kappa = 0.5522847498307936` constant this codebase already cites elsewhere -- `HobbySpline.kt`'s
 * own top KDoc, `ArcTest`'s own fixture) -- genuinely curved and tangent-continuous at every join
 * (every handle is purely horizontal or vertical, so direction always matches exactly), unlike
 * [demoSquare]. The east->north segment's own handle length is deliberately shortened
 * ([kShort], not the shared [k] the other three segments use), so the join at east (on-curve index
 * `0`, the one [PaletteEntry.run] for harmonise-curvature harmonises) has a real, non-zero
 * curvature mismatch to correct -- a perfectly uniform circle would already be curvature-matched
 * everywhere and give that demo nothing to do.
 */
private fun demoCircle(): Contour {
    val r = DEMO_CIRCLE_RADIUS
    val k = kotlin.math.round(DEMO_CIRCLE_RADIUS * DEMO_CIRCLE_KAPPA).toInt()
    val kShort = kotlin.math.round(k * 0.6).toInt()
    val e = Point(r, 0)
    val n = Point(0, r)
    val w = Point(-r, 0)
    val s = Point(0, -r)
    val points =
        listOf(
            ContourPoint(e, true),
            ContourPoint(Point(r, kShort), false),
            ContourPoint(Point(kShort, r), false),
            ContourPoint(n, true),
            ContourPoint(Point(-k, r), false),
            ContourPoint(Point(-r, k), false),
            ContourPoint(w, true),
            ContourPoint(Point(-r, -k), false),
            ContourPoint(Point(-k, -r), false),
            ContourPoint(s, true),
            ContourPoint(Point(k, -r), false),
            ContourPoint(Point(r, -k), false),
        )
    return Contour(points, CurveFormat.CUBIC)
}
