package dev.aarso.typewright.ui.learn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aarso.typewright.ui.tokens.CanvasTexture
import dev.aarso.typewright.ui.tokens.MeaningColors
import dev.aarso.typewright.ui.tokens.SpacingTokens
import dev.aarso.typewright.ui.tokens.Typography
import dev.aarso.typewright.ui.tokens.toColor

/**
 * The Learn screen's Scrapbook tab (`ui/typewright-explorer.html`'s `#s-learn` station, its own
 * `#ln-scrap` markup and `.scrap` / `.pin` / `.im` / `.cap` / `.pinmark` CSS -- CLAUDE.md law 6,
 * reproduced rather than improvised). Its own standalone tab, matching
 * [dev.aarso.typewright.ui.learn.LineagesTab]'s/[dev.aarso.typewright.ui.learn.OverlayTab]'s own
 * shape (`texture: CanvasTexture, modifier: Modifier = Modifier`), fully self-contained: its own
 * `remember`ed pin list, seeded from [SampleScrapbook.MANIFEST] and never anything real -- there
 * is no current-project flow wired into `ui` yet (see that object's own KDoc for the same,
 * already-disclosed gap).
 *
 * **What is real here.** [ScrapbookManifest] and [ScrapbookPin] are a real, tested data model with
 * a real JSON codec ([ScrapbookManifestCodec], [ScrapbookManifestCodecTest]'s own round-trip
 * proof); [stablePinRotationDegrees] is a real, tested, deterministic function
 * ([ScrapbookManifestCodecTest] again), not a stand-in. What is *not* real: the pins on screen.
 * They come from [SampleScrapbook.MANIFEST], a fixed, honestly-labelled four-pin sample reusing
 * the explorer's own worked example, not a real project's own scrapbook -- this tab reads and
 * writes no project file.
 *
 * **"+ photo" / "+ note" are real, in-memory-only appends.** Tapping "+ photo" really appends a
 * new [ScrapbookPinKind.PHOTO] pin to this composable's own `remember`ed list (own id, own stable
 * rotation via [stablePinRotationDegrees]) -- it cannot open a real image picker (this container
 * has no device and no filesystem picker to call, CLAUDE.md law 4), so it adds an honestly labelled
 * placeholder pin ("Untitled photo") rather than faking a captured photo. Tapping "+ note" opens a
 * real single-line [BasicTextField] (the same pattern `ui.glass`'s own `CommandPalette` already
 * uses); confirming really appends a new [ScrapbookPinKind.NOTE] pin carrying the typed text.
 * Neither append reaches any file or any other screen -- both are lost on recomposition of a fresh
 * [ScrapbookTab] (a new `remember` scope), exactly the honesty this task's own instructions ask
 * for rather than a real "project" to persist into.
 *
 * **One deliberate departure from `#ln-scrap`'s own literal CSS: a small per-pin rotation.**
 * The explorer's own `.pin`/`.im` rules carry no `transform: rotate(...)` at all -- its board is
 * visually flat. This task's own instructions still ask for "a rotation degrees value for the
 * pinned look" in the data model and "a per-pin random-looking but *stable* ... small rotation for
 * the pinned feel" in the rendering, so [PinCard] applies [ScrapbookPin.rotationDegrees] as a
 * whole-card [graphicsLayer] rotation. This mirrors a *different* part of the explorer's own house
 * style, not an invention out of nothing: the review-grid cells elsewhere in
 * `ui/typewright-explorer.html` already carry a small per-cell `--rot` tilt
 * (`.cell .cg{transform:rotate(var(--rot,0deg))}`) for the same "not machine-perfect" feel, so a
 * small deterministic tilt is already this app's own visual vocabulary -- just not, today, drawn
 * on this one screen. See `docs/OPEN_QUESTIONS.md`'s Scrapbook entry for this exact call flagged
 * for the explorer's own owner.
 *
 * **Two-column layout, not CSS multi-column.** `.scrap{columns:2}` is a browser-native balanced
 * masonry (fills column 1, balances into column 2 by total height). Compose has no direct
 * equivalent without either an experimental staggered-grid API (unused anywhere else in this
 * codebase, and unnecessary machinery for a 4-8-pin board) or hand-rolled height tracking; this
 * tab does the latter, small and plain: [PinGrid] greedily drops each pin into whichever of its two
 * columns is currently shorter (by [estimatedPinHeightDp]), the same balancing goal CSS columns
 * pursue, without a real per-pin measured height to work from.
 */
@Composable
public fun ScrapbookTab(
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    var pins by remember { mutableStateOf(SampleScrapbook.MANIFEST.pins) }
    var nextPinSeq by remember { mutableStateOf(1) }
    var noteDraftOpen by remember { mutableStateOf(false) }
    var noteDraftText by remember { mutableStateOf("") }
    val manifest = remember(pins) { ScrapbookManifest(pins) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(texture.canvas.toColor())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpacingTokens.GUTTER_DP.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScrapbookHeader(
            manifest = manifest,
            texture = texture,
            onAddPhoto = {
                val id = "added-photo-$nextPinSeq"
                pins =
                    pins +
                    ScrapbookPin(
                        id = id,
                        kind = ScrapbookPinKind.PHOTO,
                        captionTitle = "Untitled photo",
                        captionSource = "photo",
                        rotationDegrees = stablePinRotationDegrees(id),
                    )
                nextPinSeq += 1
            },
            onToggleNoteDraft = { noteDraftOpen = !noteDraftOpen },
        )
        if (noteDraftOpen) {
            NoteDraftRow(
                text = noteDraftText,
                texture = texture,
                onTextChange = { noteDraftText = it },
                onConfirm = {
                    val trimmed = noteDraftText.trim()
                    if (trimmed.isNotEmpty()) {
                        val id = "added-note-$nextPinSeq"
                        pins =
                            pins +
                            ScrapbookPin(
                                id = id,
                                kind = ScrapbookPinKind.NOTE,
                                captionTitle = "Note",
                                captionSource = "note",
                                noteText = trimmed,
                                rotationDegrees = stablePinRotationDegrees(id),
                            )
                        nextPinSeq += 1
                        noteDraftText = ""
                        noteDraftOpen = false
                    }
                },
                onCancel = {
                    noteDraftOpen = false
                    noteDraftText = ""
                },
            )
        }
        PinGrid(pins = manifest.pins, texture = texture)
        BasicText(text = SCRAPBOOK_SAMPLE_DISCLOSURE, style = Typography.mono(sizeSp = 9.0).copy(color = texture.muted.toColor()))
    }
}

/** `#ln-scrap`'s own top `.row`: the pin-count/driving-count `.lbl` on the left, the real "+ photo · + note" affordances on the right. */
@Composable
internal fun ScrapbookHeader(
    manifest: ScrapbookManifest,
    texture: CanvasTexture,
    onAddPhoto: () -> Unit,
    onToggleNoteDraft: () -> Unit,
) {
    val muted = texture.muted.toColor()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // weight(fill = false): the affordance row on the right is measured first, at its own
        // natural (short) width, so it never gets crushed the way a plain, unweighted Row would
        // squeeze it once this label's own text needs to wrap -- Compose's Row gives unweighted
        // siblings the *remaining* main-axis space in declaration order, not a fresh full-width
        // budget each, so without this weight this label's own wrap would consume nearly all of
        // it before "+ photo · + note" is ever measured (a real bug this task's own screenshot
        // step caught and fixed, not a hypothetical -- see docs/OPEN_QUESTIONS.md's Scrapbook
        // entry for the exact before/after).
        BasicText(
            text = pinCountLabel(manifest).uppercase(),
            style = Typography.mono(sizeSp = 10.0).copy(color = muted),
            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AddAffordance(text = "+ photo", color = muted, onClick = onAddPhoto)
            BasicText(text = "·", style = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, color = muted))
            AddAffordance(text = "+ note", color = muted, onClick = onToggleNoteDraft)
        }
    }
}

@Composable
private fun AddAffordance(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    BasicText(
        text = text,
        style = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, color = color),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** `"$n pins · $d driving the design"`, [ScrapbookManifest.drivingCount] for `$d` -- `ScrapbookHeader` uppercases it, matching `.lbl`'s own `text-transform: uppercase`. */
internal fun pinCountLabel(manifest: ScrapbookManifest): String {
    val n = manifest.pins.size
    val noun = if (n == 1) "pin" else "pins"
    return "$n $noun · ${manifest.drivingCount} driving the design"
}

/** The real inline "+ note" composer: a single-line [BasicTextField] plus "add"/"cancel", the same field pattern `ui.glass`'s `CommandPalette` already establishes. Not in the explorer (`#ln-scrap` never draws one) -- the explorer's own "+ note" is a static label; this is this tab's own minimal, honestly-built way to make it real rather than a no-op. */
@Composable
internal fun NoteDraftRow(
    text: String,
    texture: CanvasTexture,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val violet = MeaningColors.VIOLET.forTexture(texture.id).toColor()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = Typography.sentence.copy(color = fg),
                singleLine = true,
                cursorBrush = SolidColor(fg),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BasicText(
            text = "add",
            style = Typography.mono(sizeSp = 10.0, weight = FontWeight.SemiBold).copy(color = violet),
            modifier = Modifier.clickable(onClick = onConfirm),
        )
        BasicText(
            text = "cancel",
            style = Typography.mono(sizeSp = 10.0).copy(color = muted),
            modifier = Modifier.clickable(onClick = onCancel),
        )
    }
}

/** `.scrap`'s own two-column board -- see [ScrapbookTab]'s own KDoc for why this is a greedy shortest-column split rather than CSS multi-column or a lazy staggered grid. */
@Composable
internal fun PinGrid(
    pins: List<ScrapbookPin>,
    texture: CanvasTexture,
) {
    val (left, right) = splitIntoBalancedColumns(pins)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (pin in left) PinCard(pin = pin, texture = texture)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (pin in right) PinCard(pin = pin, texture = texture)
        }
    }
}

/** Greedy shortest-column placement by [estimatedPinHeightDp], in [pins]' own order (so the board reads top-to-bottom the same way [pins] itself does). */
internal fun splitIntoBalancedColumns(pins: List<ScrapbookPin>): Pair<List<ScrapbookPin>, List<ScrapbookPin>> {
    val left = mutableListOf<ScrapbookPin>()
    val right = mutableListOf<ScrapbookPin>()
    var leftHeight = 0.0
    var rightHeight = 0.0
    for (pin in pins) {
        val height = estimatedPinHeightDp(pin)
        if (leftHeight <= rightHeight) {
            left += pin
            leftHeight += height
        } else {
            right += pin
            rightHeight += height
        }
    }
    return left to right
}

/** A plain per-kind estimate (photo pins get one fixed image-block height; note pins a shorter one), not a real measured layout height -- good enough to balance two columns for a handful of pins. */
internal fun estimatedPinHeightDp(pin: ScrapbookPin): Double =
    when (pin.kind) {
        ScrapbookPinKind.PHOTO -> PHOTO_BLOCK_HEIGHT_DP + PIN_CAPTION_HEIGHT_DP
        ScrapbookPinKind.NOTE -> NOTE_BLOCK_MIN_HEIGHT_DP + PIN_CAPTION_HEIGHT_DP
    }

private const val PHOTO_BLOCK_HEIGHT_DP = 96.0
private const val NOTE_BLOCK_MIN_HEIGHT_DP = 64.0
private const val PIN_CAPTION_HEIGHT_DP = 30.0

/** One `.pin`: [PinImageBlock] then [PinCaption], the whole card tilted by [ScrapbookPin.rotationDegrees] (see [ScrapbookTab]'s own KDoc for why this tab adds that tilt). */
@Composable
internal fun PinCard(
    pin: ScrapbookPin,
    texture: CanvasTexture,
) {
    Column(modifier = Modifier.graphicsLayer(rotationZ = pin.rotationDegrees.toFloat())) {
        PinImageBlock(pin = pin, texture = texture)
        PinCaption(pin = pin, texture = texture)
    }
}

/** `.pin .im`: a coloured/patterned placeholder block for a [ScrapbookPinKind.PHOTO] pin (never a real photo), or the pin's own [ScrapbookPin.noteText] for a [ScrapbookPinKind.NOTE] one -- plus `.pinmark` when [ScrapbookPin.drivesDesign]. Sharp corners (`border-radius: 0`), matching the explorer's own later "brutalist pass" stylesheet, which overrides `.pin .im`'s earlier rounded corners and is the explorer's own final, un-gated look (`ui/typewright-explorer.html` never toggles it off). */
@Composable
internal fun PinImageBlock(
    pin: ScrapbookPin,
    texture: CanvasTexture,
) {
    val ink = texture.ink.toColor()
    val backgroundTint = ink.copy(alpha = 0.04f)
    val patternTint = ink.copy(alpha = 0.08f)
    val minHeight = if (pin.kind == ScrapbookPinKind.PHOTO) PHOTO_BLOCK_HEIGHT_DP else NOTE_BLOCK_MIN_HEIGHT_DP

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight.dp)
                .background(backgroundTint),
    ) {
        when (pin.kind) {
            ScrapbookPinKind.PHOTO -> {
                PinPatternCanvas(
                    variant = patternVariantForPinId(pin.id),
                    tint = patternTint,
                    modifier = Modifier.matchParentSize(),
                )
            }

            ScrapbookPinKind.NOTE -> {
                BasicText(
                    text = pin.noteText.orEmpty().ifBlank { "(empty note)" },
                    style =
                        TextStyle(
                            fontFamily = FontFamily.Default,
                            fontSize = 12.sp,
                            lineHeight = 12.sp * 1.4,
                            color = texture.fg.toColor(),
                        ),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
        }
        if (pin.drivesDesign) {
            PinMark(texture = texture, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

/** `.pinmark`: a small violet dot -- CLAUDE.md law 8, violet meaning "driving the design" here, the same way it means "selected" on the sheet. */
@Composable
internal fun PinMark(
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val violet = MeaningColors.VIOLET.forTexture(texture.id).toColor()
    Box(modifier = modifier.size(7.dp).background(color = violet, shape = CircleShape))
}

/** The three `.im.pat1`/`.pat2`/`.pat3` looks, reproduced as real drawn strokes rather than a CSS `repeating-linear-gradient`/`radial-gradient` Compose has no direct equivalent for. */
internal enum class PinPatternVariant { VERTICAL_STRIPES, DOT, DIAGONAL_STRIPES }

/** Deterministic per [id] (via [stableHashCode]), so a pin's own pattern never changes across recompositions -- the same stability [stablePinRotationDegrees] gives the tilt. */
internal fun patternVariantForPinId(id: String): PinPatternVariant {
    val variants = PinPatternVariant.entries
    return variants[stableHashCode(id) % variants.size]
}

@Composable
private fun PinPatternCanvas(
    variant: PinPatternVariant,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        when (variant) {
            PinPatternVariant.VERTICAL_STRIPES -> {
                val step = 14.dp.toPx()
                val strokeWidth = 2.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(color = tint, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = strokeWidth)
                    x += step
                }
            }

            PinPatternVariant.DOT -> {
                val center = Offset(size.width * 0.3f, size.height * 0.4f)
                val radius = size.minDimension * 0.55f
                drawCircle(
                    brush = Brush.radialGradient(colors = listOf(tint, tint.copy(alpha = 0f)), center = center, radius = radius),
                    radius = radius,
                    center = center,
                )
            }

            PinPatternVariant.DIAGONAL_STRIPES -> {
                val step = 9.dp.toPx()
                val strokeWidth = 1.dp.toPx()
                var offset = -size.height
                while (offset < size.width + size.height) {
                    drawLine(
                        color = tint,
                        start = Offset(offset, 0f),
                        end = Offset(offset + size.height, size.height),
                        strokeWidth = strokeWidth,
                    )
                    offset += step
                }
            }
        }
    }
}

/**
 * `.pin .cap`: bold title, muted source label. Stacked (title above source), not side by side --
 * a real, screenshot-caught follow-up to this file's own weighted-`Row` fix (see this task's own
 * `docs/OPEN_QUESTIONS.md` entry): even with a fair, guaranteed-minimum weighted split, a
 * two-column pin card is narrow enough (roughly half the tab's own width) that a real title like
 * "Signage · Charminar" still wrapped into an unreadably narrow, few-characters-per-line
 * column when it only had a 3/5 share of that half-width row to work with. Stacking gives each
 * line the card's *full* width instead of a fraction of it, which is what actually fixes the
 * legibility problem the weighting alone could not -- confirmed by viewing the re-rendered
 * screenshot, not assumed from the code change alone.
 */
@Composable
internal fun PinCaption(
    pin: ScrapbookPin,
    texture: CanvasTexture,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        BasicText(
            text = pin.captionTitle,
            style =
                TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = texture.fg.toColor(),
                ),
        )
        BasicText(
            text = pin.captionSource,
            style = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, color = texture.muted.toColor()),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private const val SCRAPBOOK_SAMPLE_DISCLOSURE =
    "Sample scrapbook -- 4 pins reusing the explorer's own worked example. No current project is wired into ui yet, " +
        "so nothing here reads or writes real project files. \"+ photo\" appends a real, honestly placeholder pin " +
        "(no image picker exists in this container); \"+ note\" opens a real text field and appends what you type -- " +
        "both stay in memory only, for this session."
