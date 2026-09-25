// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.learn.scenes.Callout
import com.asoc.typewright.learn.scenes.LineagesResources
import com.asoc.typewright.learn.scenes.Scene
import com.asoc.typewright.learn.scenes.SceneFrame
import com.asoc.typewright.learn.scenes.SceneRenderer
import com.asoc.typewright.learn.scenes.StrandSequencer
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.MeaningColors
import com.asoc.typewright.ui.tokens.MotionTokens
import com.asoc.typewright.ui.tokens.SpacingTokens
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.roundToInt

/**
 * The Learn screen's Lineages tab, real content and real interaction
 * (`ui/typewright-explorer.html`'s `#s-learn` station, its own `#ln-lin` markup and `.eras` /
 * `.stagetype` / `.stress` / `.scrub` / `.caption` / `.quiz` / `.qw` / `.opts` / `.reveal` CSS —
 * CLAUDE.md law 6, reproduced rather than improvised). This is its own standalone screen's own
 * tab (per this task's own instructions: the real Learn experience is a separate `#s-learn`
 * station, not the one-sheet's placeholder Learn room), fully self-contained: its own
 * `remember`ed scrub/era/quiz state, wired to this strand's real, already-built content —
 * [LineagesResources.loadFullBlockInPlayOrder] (the eleven real scenes, era order, the
 * vocabulary aside already spliced in at its correct position), [LineagesResources.loadIdentifyItBank]
 * and [StrandSequencer.plan] (which scene/bank exercises are actually safe to show, per
 * `docs/LESSONS_SCAFFOLD.md`'s own "never shows a face used on stage in that block" rule),
 * and [SceneRenderer]'s pure `frameAt`/`crossfadeAt`/`stressAngleAt`/`calloutsVisibleAt`.
 *
 * **The scrub bar drives `t` in `0.0..1.0` for the *current* scene, never a cross-scene
 * timeline.** [SceneRenderer]'s own KDoc is explicit that its `t` is always one scene's own
 * normalised scrub position, not `Scene.duration` seconds and not a block-wide position — unlike
 * the explorer's own JS mock (a single `0..900` range walking all ten eras at once, a
 * presentation-only shortcut this task does not carry over into the real renderer contract).
 * Here, the [ErasStrip] instead switches *which* scene is on stage (resetting that scene's own
 * `t` to `0.0`, mirroring the explorer's own click handler jumping the mock scrubber to that
 * era's start), and the `[0,1]` scrub bar animates within it — see this task's own final report
 * for the open question this reading leaves (the explorer's own single continuous scrubber is a
 * mock simplification, not a spec for the real multi-scene contract).
 *
 * **The identify-it quiz appears once the block is finished** (`docs/LESSONS_SCAFFOLD.md`
 * section 1: "the exercise appears after the last scene of a strand block") — read here as: the
 * eras strip's own selection has reached the block's last scene. All of [StrandSequencer.plan]'s
 * eligible cards render at once (stacked, scrollable), not one at a time behind unspecified
 * paging chrome the explorer's own static mock never designed — see [buildQuizItems]'s own KDoc
 * and this task's final report for the open question this leaves.
 */
@Composable
public fun LineagesTab(
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val scenes = remember { LineagesResources.loadFullBlockInPlayOrder() }
    val bank = remember { LineagesResources.loadIdentifyItBank() }
    val plan = remember(scenes, bank) { StrandSequencer.plan(scenes, bank) }
    val classNames = remember(scenes) { scenes.filter { it.era != null }.map { it.title } }
    val quizItems = remember(plan, classNames) { buildQuizItems(plan, classNames) }

    var sceneIndex by remember { mutableStateOf(0) }
    var t by remember { mutableStateOf(0.0) }

    val scene = scenes[selectScene(sceneIndex, scenes.size)]
    val frame = remember(scene, t) { SceneRenderer.frameAt(scene, t) }
    val isLastScene = sceneIndex == scenes.lastIndex

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(texture.canvas.toColor())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpacingTokens.GUTTER_DP.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ErasStrip(
            scenes = scenes,
            currentIndex = sceneIndex,
            texture = texture,
            onSelect = { index ->
                sceneIndex = selectScene(index, scenes.size)
                t = 0.0
            },
        )
        StageArea(scene = scene, frame = frame, texture = texture)
        ScrubBar(
            t = t,
            texture = texture,
            onScrub = { newT -> t = clampScrub(newT) },
        )
        CaptionBlock(scene = scene, texture = texture)
        if (isLastScene) {
            QuizSection(items = quizItems, texture = texture)
        }
        if (!learnFaceFontsAreReal()) {
            BasicText(
                text = "Real faces are not yet loadable on this target — showing the system font instead.",
                style = Typography.mono(sizeSp = 9.5).copy(color = texture.muted.toColor()),
            )
        }
    }
}

@Composable
internal fun ErasStrip(
    scenes: List<Scene>,
    currentIndex: Int,
    texture: CanvasTexture,
    onSelect: (Int) -> Unit,
) {
    val violet = MeaningColors.VIOLET.forTexture(texture.id).toColor()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        scenes.forEachIndexed { index, scene ->
            val selected = index == currentIndex
            Column(
                modifier =
                    Modifier
                        .clickable(onClick = { onSelect(index) })
                        .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                BasicText(
                    text = scene.title,
                    style =
                        Typography
                            .mono(sizeSp = 11.5, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            .copy(color = if (selected) texture.fg.toColor() else texture.fg.toColor().copy(alpha = 0.5f)),
                )
                BasicText(
                    text = scene.era ?: "—",
                    style =
                        Typography.mono(sizeSp = 9.5).copy(
                            color = if (selected) violet else texture.muted.toColor().copy(alpha = 0.7f),
                        ),
                )
            }
        }
    }
}

@Composable
internal fun StageArea(
    scene: Scene,
    frame: SceneFrame,
    texture: CanvasTexture,
) {
    val fromFamily = rememberLearnFaceFamily(scene.stage.from)
    val toFamily = rememberLearnFaceFamily(scene.stage.to)
    val ink = texture.ink.toColor()

    Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        StressDial(
            angle = frame.stressAngle,
            texture = texture,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
        )
        Box(modifier = Modifier.align(Alignment.Center)) {
            BasicText(
                text = scene.stage.sample,
                style =
                    TextStyle(
                        fontFamily = fromFamily,
                        fontSize = 88.sp,
                        color = ink.copy(alpha = frame.crossfade.fromOpacity.toFloat()),
                    ),
            )
            BasicText(
                text = scene.stage.sample,
                style = TextStyle(fontFamily = toFamily, fontSize = 88.sp, color = ink.copy(alpha = frame.crossfade.toOpacity.toFloat())),
            )
        }
        if (frame.calloutsVisible && scene.callouts.isNotEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (callout in scene.callouts) {
                    CalloutChip(callout = callout, texture = texture)
                }
            }
        }
    }
}

@Composable
internal fun CalloutChip(
    callout: Callout,
    texture: CanvasTexture,
) {
    val violet = MeaningColors.VIOLET.forTexture(texture.id).toColor()
    BasicText(
        text = "${callout.glyph} · ${callout.part}: ${callout.label}",
        style = Typography.mono(sizeSp = 9.5).copy(color = violet),
    )
}

@Composable
internal fun StressDial(
    angle: Double?,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val violet = MeaningColors.VIOLET.forTexture(texture.id).toColor()
    val trackColor = texture.line.toColor()
    val lineAlpha = if (angle == null) 0.25f else 1f
    val animatedAngle by
        animateFloatAsState(
            targetValue = (angle ?: 0.0).toFloat(),
            animationSpec = tween(durationMillis = MotionTokens.LINEAGES_STRESS_NEEDLE_MS, easing = stressDialEasing),
        )
    val label = stressDialLabel(angle)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(56.dp)) {
            val radius = size.minDimension * 0.42f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(color = trackColor, radius = radius, center = center, style = Stroke(width = 1.dp.toPx()))
            rotate(degrees = animatedAngle, pivot = center) {
                drawLine(
                    color = violet.copy(alpha = lineAlpha),
                    start = Offset(center.x, center.y - radius),
                    end = Offset(center.x, center.y + radius),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        BasicText(text = label, style = Typography.mono(sizeSp = 9.0).copy(color = texture.muted.toColor()))
    }
}

@Composable
internal fun ScrubBar(
    t: Double,
    texture: CanvasTexture,
    onScrub: (Double) -> Unit,
) {
    var widthPx by remember { mutableStateOf(0f) }
    val thumbColor = MeaningColors.VIOLET.forTexture(texture.id).toColor()
    val trackColor = texture.line.toColor()

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .onGloballyPositioned { widthPx = it.size.width.toFloat() }
                .pointerInputScrub(widthPx, onScrub),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(trackColor),
        )
        val thumbFraction = clampScrub(t).toFloat()
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        val x = (thumbFraction * widthPx).roundToInt() - 7.dp.roundToPx()
                        IntOffset(x = x, y = 0)
                    }.size(14.dp)
                    .clip(CircleShape)
                    .background(thumbColor),
        )
    }
}

@Composable
internal fun CaptionBlock(
    scene: Scene,
    texture: CanvasTexture,
) {
    val violet = MeaningColors.VIOLET.forTexture(texture.id).toColor()
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(
                text = scene.title,
                style =
                    TextStyle(
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = texture.fg.toColor(),
                    ),
            )
            scene.era?.let { era ->
                BasicText(text = era, style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, color = texture.muted.toColor()))
            }
        }
        BasicText(text = scene.caption.text.trim(), style = Typography.sentence.copy(color = texture.fg.toColor()))
        if (scene.caption.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (tag in scene.caption.tags) {
                    BasicText(text = tag, style = Typography.mono(sizeSp = 9.5, weight = FontWeight.SemiBold).copy(color = violet))
                }
            }
        }
        BasicText(
            text = "made with ${scene.caption.tool}",
            style = Typography.mono(sizeSp = 9.5).copy(color = texture.muted.toColor()),
        )
    }
}

@Composable
internal fun QuizSection(
    items: List<LineagesQuizItem>,
    texture: CanvasTexture,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BasicText(
            text = "Identify it",
            style = Typography.mono(sizeSp = 9.5, weight = FontWeight.SemiBold).copy(color = texture.muted.toColor()),
        )
        if (items.isEmpty()) {
            BasicText(
                text = "No identify-it faces are eligible for this block yet.",
                style = Typography.sentence.copy(color = texture.muted.toColor()),
            )
        } else {
            for (item in items) {
                QuizCard(item = item, texture = texture)
            }
        }
    }
}

@Composable
internal fun QuizCard(
    item: LineagesQuizItem,
    texture: CanvasTexture,
) {
    var selected by remember(item) { mutableStateOf<String?>(null) }
    val quizFontFamily = rememberLearnFaceFamilyForFamilyName(item.faceFamily)
    val violet = MeaningColors.VIOLET.forTexture(texture.id).toColor()

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(
            text = item.word,
            style = TextStyle(fontFamily = quizFontFamily, fontSize = 28.sp, color = texture.ink.toColor()),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (option in item.options) {
                QuizOption(
                    option = option,
                    isAnswer = option == item.answer,
                    isSelected = selected == option,
                    revealed = selected != null,
                    texture = texture,
                    violet = violet,
                    onClick = { selected = option },
                )
            }
        }
        if (selected != null) {
            BasicText(
                text = "Give-away: ${item.giveaway.trim()} — the face is ${item.faceFamily}.",
                style = Typography.mono(sizeSp = 10.0).copy(color = texture.muted.toColor()),
            )
        }
    }
}

@Composable
internal fun QuizOption(
    option: String,
    isAnswer: Boolean,
    isSelected: Boolean,
    revealed: Boolean,
    texture: CanvasTexture,
    violet: Color,
    onClick: () -> Unit,
) {
    val showCorrectMark = revealed && isAnswer
    val textColor =
        when {
            showCorrectMark -> texture.fg.toColor()
            isSelected -> violet
            else -> texture.muted.toColor()
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        // CLAUDE.md law 8: severity/correctness is a shape and a word, never colour alone --
        // a check mark for the right answer, a ring for a wrong pick the learner made, both on
        // top of ordinary text colour, not a red/green swap.
        if (showCorrectMark) {
            BasicText(text = "✓", style = Typography.mono(sizeSp = 11.0).copy(color = texture.fg.toColor()))
        } else if (isSelected && revealed) {
            BasicText(text = "○", style = Typography.mono(sizeSp = 11.0).copy(color = texture.muted.toColor()))
        }
        BasicText(
            text = option,
            style =
                Typography
                    .mono(
                        sizeSp = 12.5,
                        weight = if (showCorrectMark) FontWeight.SemiBold else FontWeight.Normal,
                    ).copy(color = textColor),
        )
    }
}

/**
 * [MotionTokens.EASING] ([com.asoc.typewright.ui.sheet.CubicBezierEasing], this codebase's own
 * plain-math curve, Compose-free by design — that class's own KDoc) bridged into Compose's
 * [Easing] functional interface, the one place [StressDial]'s `tween(..., easing = ...)` needs
 * it. [com.asoc.typewright.ui.sheet.CubicBezierEasing.transform] already takes/returns `Double`
 * in `0.0..1.0`; [Easing.transform] takes/returns `Float` in the same range, so this is a pure
 * unit conversion, not a different curve.
 */
private val stressDialEasing = Easing { fraction -> MotionTokens.EASING.transform(fraction.toDouble()).toFloat() }

/** [learnFaceFontFamily] for a [Scene]'s own short stage key (`scene.stage.from`/`to`), remembered per key. */
@Composable
private fun rememberLearnFaceFamily(key: String): FontFamily =
    remember(key) { runCatching { learnFaceFontFamily(key) }.getOrDefault(FontFamily.Default) }

/** [learnFaceFontFamily] for a real family name (an identify-it answer face), via [learnFaceKeyForFamily]. */
@Composable
private fun rememberLearnFaceFamilyForFamilyName(family: String): FontFamily =
    remember(family) {
        val key = learnFaceKeyForFamily(family)
        if (key == null) FontFamily.Default else runCatching { learnFaceFontFamily(key) }.getOrDefault(FontFamily.Default)
    }

/** The scrub track's own drag recogniser: absolute pointer x, fraction of [widthPx], reported through [onScrub] on every move (and on the initial touch-down, so a plain tap also repositions it). */
private fun Modifier.pointerInputScrub(
    widthPx: Float,
    onScrub: (Double) -> Unit,
): Modifier =
    this.pointerInput(widthPx) {
        if (widthPx <= 0f) return@pointerInput
        detectDragGestures(
            onDragStart = { offset -> onScrub((offset.x / widthPx).toDouble()) },
        ) { change, _ ->
            change.consume()
            onScrub((change.position.x / widthPx).toDouble())
        }
    }
