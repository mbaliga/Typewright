package dev.aarso.typewright.ui.learn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.aarso.typewright.core.font.sfnt.readSfntFont
import dev.aarso.typewright.ui.tokens.CanvasTexture
import dev.aarso.typewright.ui.tokens.MeaningColors
import dev.aarso.typewright.ui.tokens.SpacingTokens
import dev.aarso.typewright.ui.tokens.Typography
import dev.aarso.typewright.ui.tokens.toColor
import kotlin.math.roundToInt

/**
 * The Learn screen's Anatomy Lens tab (TYPEWRIGHT_BUILD_BRIEF.md section 9 strand 2;
 * `ui/typewright-explorer.html`'s `#s-learn` station, its own `#ln-lens` markup and `.lens` /
 * `.defs` / `.def` / `.def.on` CSS -- CLAUDE.md law 6, reproduced rather than improvised). Its own
 * standalone tab, matching [dev.aarso.typewright.ui.learn.OverlayTab]'s and
 * [dev.aarso.typewright.ui.learn.LineagesTab]'s own shape (`texture: CanvasTexture, modifier:
 * Modifier = Modifier`), fully self-contained: it reads this build's own real
 * [dev.aarso.typewright.ui.learn.HyleDecoProjectFontBytes] (the same "project" stand-in the
 * Overlay tab's own ink layer already uses, per that file's own KDoc), builds an
 * [dev.aarso.typewright.ui.learn.AnatomyLensGlyphSet] from it, and keeps its own selected-term
 * state -- no caller-supplied font, glyph set or selection needed.
 *
 * **Real geometry, real measurements, never invented (CLAUDE.md laws 1 and 5).** The glyph on
 * stage is drawn from real [dev.aarso.typewright.core.geometry.Glyph] contours through
 * `GeometryInterop.kt`'s own outline bridge ([dev.aarso.typewright.ui.toComposePath]) -- the exact
 * bridge the Overlay tab's own ink layer already uses, not a second one. Tapping a term (from the
 * definitions list below, or its own leader-line dot/label on the diagram) calls
 * [dev.aarso.typewright.ui.learn.anatomyLensEntry] (the sibling Foundations-stage task's own real
 * data API, wired straight through to `qa/corpus`'s style-measurement functions) and shows its
 * real definition, plus its real measured value where one exists -- an unmeasured, purely
 * structural term (stem, bowl, counter) shows the definition alone, with no invented number and no
 * "our heuristic" caveat (that caveat is for a judged number, and a definition-only term has none).
 * [LensScene.kt] and [LensRenderPlan.kt] carry the pure logic behind both of those (which real
 * glyph a term's leader line points at, and where); this file is only the `@Composable` shell
 * around them, kept thin on purpose so that logic stays unit-testable without a Compose test rule.
 */
@Composable
public fun LensTab(
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val glyphSetResult =
        remember {
            runCatching { AnatomyLensGlyphSet.fromSfntFont(readSfntFont(HyleDecoProjectFontBytes.bytes)) }
        }
    val ink = texture.ink.toColor()
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val lineColor = texture.line.toColor()
    val canvasColor = texture.canvas.toColor()
    val violet = MeaningColors.VIOLET.forTexture(texture.id).toColor()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(canvasColor)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpacingTokens.GUTTER_DP.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val glyphSet = glyphSetResult.getOrNull()
        if (glyphSet == null) {
            LensUnavailableNote(fg = fg, muted = muted, reason = glyphSetResult.exceptionOrNull()?.message)
            return@Column
        }

        var selectedTerm by remember { mutableStateOf(LENS_TAB_TERMS.first()) }

        LensDiagram(
            glyphSet = glyphSet,
            selectedTerm = selectedTerm,
            onSelect = { selectedTerm = it },
            ink = ink,
            fg = fg,
            muted = muted,
            lineColor = lineColor,
            violet = violet,
        )

        LensDefinitionsList(
            glyphSet = glyphSet,
            selectedTerm = selectedTerm,
            onSelect = { selectedTerm = it },
            fg = fg,
            muted = muted,
            violet = violet,
        )
    }
}

/** `#lensSvg`'s own `viewBox="-70 -600 560 700"` aspect ratio (width/height), reproduced exactly (560/700). */
private const val LENS_ASPECT_RATIO = 560f / 700f

/** A leader-line dot's tap target: bigger than the dot itself so a finger, not just a mouse, can hit it. */
private val LEADER_DOT_HIT_SIZE = 30.dp

/** One [LensLabelPlan] plus its own resolved on-screen text style and text position ([textPx], clamped by its real measured width -- see the "Each label's own real measured text width" comment where this is built). */
private data class ResolvedLensLabel(
    val label: LensLabelPlan,
    val style: TextStyle,
    val textPx: Offset,
)

/** `internal`, not `private`: [LensTab]'s own diagram half, exposed only so `desktopTest`'s `LensTabHyleDecoTest` can render one specific term's diagram directly (a chosen [selectedTerm], not [LensTab]'s own default first term) for this task's own screenshot verification -- [LensTab]'s own public signature (`texture`, `modifier`) is unchanged. */
@Composable
internal fun LensDiagram(
    glyphSet: AnatomyLensGlyphSet,
    selectedTerm: AnatomyTerm,
    onSelect: (AnatomyTerm) -> Unit,
    ink: Color,
    fg: Color,
    muted: Color,
    lineColor: Color,
    violet: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightDp = maxWidth / LENS_ASPECT_RATIO
            val heightPx = with(density) { heightDp.toPx() }
            val textMeasurer = rememberTextMeasurer()

            val plan =
                remember(glyphSet, selectedTerm, widthPx, heightPx) {
                    buildLensRenderPlan(glyphSet, selectedTerm, widthPx, heightPx)
                }

            // Each label's own real measured text width (`textMeasurer`, not a guessed character
            // count) decides its final on-canvas x position: [buildLensRenderPlan]'s own
            // `labelAnchorPx` already keeps the *anchor point* on-canvas (a fraction-of-size offset
            // plus its own cheap clamp, see `LensRenderPlan.kt`), but a real rendered word like
            // "roundness" is wide enough at this tab's own actual (narrower than first assumed)
            // width that anchoring its *left* edge there still ran the rest of the word off the
            // right side -- confirmed by a real screenshot, fixed here by clamping the text's own
            // measured right edge to stay on-canvas too, not just its anchor.
            val labelEdgeMarginPx = with(density) { 4.dp.toPx() }
            val resolvedLabels =
                plan.labels.map { label ->
                    val style = Typography.mono(sizeSp = 9.5).copy(color = if (label.selected) violet else fg)
                    val measuredWidthPx =
                        textMeasurer
                            .measure(label.term.name.lowercase(), style)
                            .size.width
                            .toFloat()
                    val maxX = (widthPx - labelEdgeMarginPx - measuredWidthPx).coerceAtLeast(labelEdgeMarginPx)
                    val textPx = Offset(label.labelAnchorPx.x.coerceIn(labelEdgeMarginPx, maxX), label.labelAnchorPx.y)
                    ResolvedLensLabel(label, style, textPx)
                }

            Box(Modifier.fillMaxWidth().height(heightDp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val metricStrokePx = 1f
                    plan.baselineYPx?.let { y ->
                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = metricStrokePx)
                    }
                    plan.xHeightYPx?.let { y ->
                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = metricStrokePx)
                    }
                    plan.glyphPath?.let { path -> drawPath(path, color = ink) }

                    val dotRadiusPx = with(density) { 3.5.dp.toPx() }
                    val leaderStrokePx = with(density) { 1.2.dp.toPx() }
                    for (resolved in resolvedLabels) {
                        val color = if (resolved.label.selected) violet else fg
                        drawLine(color, resolved.textPx, resolved.label.dotPx, strokeWidth = leaderStrokePx)
                        drawCircle(color, radius = dotRadiusPx, center = resolved.label.dotPx)
                    }
                }

                val hitPx = with(density) { LEADER_DOT_HIT_SIZE.toPx() }
                for (resolved in resolvedLabels) {
                    val label = resolved.label
                    // The dot's own tap target (CLAUDE.md: "tapping ... a leader line/label selects it").
                    Box(
                        modifier =
                            Modifier
                                .offset {
                                    IntOffset((label.dotPx.x - hitPx / 2f).roundToInt(), (label.dotPx.y - hitPx / 2f).roundToInt())
                                }.size(LEADER_DOT_HIT_SIZE)
                                .clickable { onSelect(label.term) },
                    )
                    Box(
                        modifier =
                            Modifier
                                .offset { IntOffset(resolved.textPx.x.roundToInt(), resolved.textPx.y.roundToInt()) }
                                .clickable { onSelect(label.term) },
                    ) {
                        BasicText(text = label.term.name.lowercase(), style = resolved.style)
                    }
                }
            }
        }

        if (glyphSet.glyphs.isEmpty()) {
            BasicText(
                text = "this font has none of the lens's own letters (o n a g e H T c s x).",
                style = Typography.mono(sizeSp = 9.5).copy(color = muted),
            )
        }
    }
}

@Composable
private fun LensDefinitionsList(
    glyphSet: AnatomyLensGlyphSet,
    selectedTerm: AnatomyTerm,
    onSelect: (AnatomyTerm) -> Unit,
    fg: Color,
    muted: Color,
    violet: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (term in LENS_TAB_TERMS) {
            val entry = remember(glyphSet, term) { anatomyLensEntry(term, glyphSet) }
            val selected = term == selectedTerm
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(term) },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicText(
                    text = term.name.lowercase(),
                    style = Typography.mono(sizeSp = 11.0, weight = FontWeight.SemiBold).copy(color = if (selected) violet else fg),
                    modifier = Modifier.width(84.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                    BasicText(text = entry.definition, style = Typography.sentence.copy(color = muted))
                    val valueLine = lensValueLine(entry)
                    if (valueLine != null) {
                        BasicText(text = valueLine, style = Typography.mono(sizeSp = 9.5, weight = FontWeight.SemiBold).copy(color = fg))
                    }
                }
            }
        }
    }
}

@Composable
private fun LensUnavailableNote(
    fg: Color,
    muted: Color,
    reason: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(text = "The Anatomy Lens is not available on this target yet.", style = Typography.sentence.copy(color = fg))
        BasicText(
            text = reason ?: "could not read this build's own font resources.",
            style = Typography.mono(sizeSp = 9.5).copy(color = muted),
        )
    }
}
