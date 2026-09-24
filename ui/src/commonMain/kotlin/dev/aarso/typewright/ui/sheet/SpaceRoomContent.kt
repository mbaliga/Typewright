package dev.aarso.typewright.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aarso.typewright.core.font.ufo.UfoKerning
import dev.aarso.typewright.ui.tokens.CanvasTexture
import dev.aarso.typewright.ui.tokens.SpacingTokens
import dev.aarso.typewright.ui.tokens.Typography
import dev.aarso.typewright.ui.tokens.toColor

/**
 * The Space room's own glass content (P5b, this task): a kerning-pair demo word with a
 * before/after toggle, a plain kerning-groups list, and a plain, read-only `.fea` text view.
 * Glass, not world-space ink (`UI_SPEC §1` layer 6) -- it stays fixed to the viewport rather than
 * panning with the camera, like the header and inspector, since it is a reading/editing surface
 * for the *current* Space room's data, not letterform geometry that should scale and pan with the
 * sheet.
 *
 * **What the explorer shows, and what this reproduces honestly.** `ui/typewright-explorer.html`'s
 * separate `#s-space` screen (`UI_SPEC.md`'s own screen list: "Draw (one sheet...) · Space ..." --
 * two different things sharing the name "Space") has a real, worked kerning-pair demo (`#kern`/
 * `#kernSeg`, grep'd directly): a kerning word ("To AV Ty") with a before/after toggle that shifts
 * the second letter of each pair by a fixed CSS margin. This reproduces that same word, toggle and
 * selection-language ink-block button styling (`UI_SPEC §5.4`), but shifts by a **real** kerning
 * value read from [kerning] (`core-font`'s `UfoKerning`, the sibling P5b core-font report) rather
 * than the explorer's own fixed, hand-tuned CSS margins -- "extend it with real kerning data" per
 * this task's own brief. No project flows into `ui` at this layer yet (`docs/OPEN_QUESTIONS.md`'s
 * P5b UI entry), so [kerning] is a small, honestly-labelled in-memory sample
 * ([sampleKerning]), not a real project's `kerning.plist`.
 *
 * **What the explorer does not show at all**, per this task's own instructions to log such gaps
 * rather than invent a look for them: a kerning-*groups* panel, and a `.fea` text editor. Both are
 * built here as the plainest possible functional surface -- a list of group name/members rows
 * (the same mono-label-plus-value row language [dev.aarso.typewright.ui.glass.InspectorRow] and
 * [dev.aarso.typewright.ui.puck.UnfoldedToolList] already use, not a new list-row style) and a
 * read-only monospace text block for [UfoKerning.features] -- no tabs, no syntax highlighting, no
 * panel chrome. See `docs/OPEN_QUESTIONS.md`'s P5b entry for the plain statement of this scope cut.
 */
@Composable
fun SpaceRoomGlass(
    room: Room,
    texture: CanvasTexture,
    kerning: UfoKerning,
    modifier: Modifier = Modifier,
    unitsPerEm: Int = 1000,
) {
    if (room != Room.SPACE) return

    var showAfter by remember { mutableStateOf(false) }
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val ink = texture.ink.toColor()
    val canvas = texture.canvas.toColor()

    Column(
        modifier =
            modifier
                .widthIn(max = 460.dp)
                // An opaque backing, like every other fixed glass element in this module
                // (Header's room pill, LayersPanel, CommandPalette, InspectorRow's chips) --
                // this was the one glass surface in `ui` without one (verify-p5b-ui finding),
                // and without it this content is legible only where it happens not to cross
                // world-space ink such as the room's own watermark text (`RoomInk.kt`).
                .background(canvas)
                .padding(horizontal = SpacingTokens.GUTTER_DP.dp, vertical = SpacingTokens.GUTTER_DP.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        // --- The kerning-pair demo word + before/after toggle ---
        BasicText(
            text = "KERNING",
            style = Typography.mono(sizeSp = 9.5).copy(color = muted),
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            for (segment in kerningWordSegments()) {
                val shiftDp =
                    if (showAfter && segment.pairKerning != null) {
                        kerningOffsetDp(segment.pairKerning, unitsPerEm, KERNING_WORD_SIZE_SP)
                    } else {
                        0.0
                    }
                BasicText(
                    text = segment.text,
                    style = Typography.sentence.copy(fontSize = KERNING_WORD_SIZE_SP.sp, color = fg),
                    modifier = Modifier.offset(x = shiftDp.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            KernSegButton(label = "before", selected = !showAfter, ink = ink, canvas = canvas, muted = muted) { showAfter = false }
            KernSegButton(label = "after", selected = showAfter, ink = ink, canvas = canvas, muted = muted) { showAfter = true }
        }
        BasicText(
            text = kerningPairSummary(kerning),
            style = Typography.mono(sizeSp = 9.5).copy(color = muted),
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(22.dp))

        // --- Kerning groups: a plain list, InspectorRow/UnfoldedToolList's own row language ---
        BasicText(text = "KERNING GROUPS", style = Typography.mono(sizeSp = 9.5).copy(color = muted))
        Spacer(Modifier.height(6.dp))
        if (kerning.groups.isEmpty()) {
            BasicText(text = "no groups in this sample", style = Typography.mono(sizeSp = 9.5).copy(color = muted))
        } else {
            for ((name, members) in kerning.groups) {
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    BasicText(
                        text = name,
                        style = Typography.mono(sizeSp = 9.5).copy(color = fg),
                        modifier = Modifier.width(150.dp),
                    )
                    BasicText(
                        text = members.joinToString(" · "),
                        style = Typography.mono(sizeSp = 9.5).copy(color = muted),
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // --- .fea text: plain, read-only, monospace -- no panel chrome, no tabs, no highlighting ---
        BasicText(text = "FEATURES.FEA", style = Typography.mono(sizeSp = 9.5).copy(color = muted))
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = kerning.features ?: "(no features.fea in this sample)",
            style = Typography.mono(sizeSp = 10.5).copy(color = fg),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun KernSegButton(
    label: String,
    selected: Boolean,
    ink: Color,
    canvas: Color,
    muted: Color,
    onClick: () -> Unit,
) {
    // UI_SPEC §5.4 selection language: selected = ink block, paper text; unselected = plain text.
    Box(
        modifier =
            Modifier
                .let { if (selected) it.background(ink) else it }
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        BasicText(
            text = label,
            style = Typography.mono(sizeSp = 11.0).copy(color = if (selected) canvas else muted),
        )
    }
}

/** One piece of the kerning-demo word: its own text, and (for the pair's second letter) the real kerning value against the letter before it, or `null` for a piece with no pair to shift. */
private data class KernSegment(
    val text: String,
    val pairKerning: Double?,
)

private const val KERNING_WORD_SIZE_SP = 32.0

/**
 * "To AV Ty" (the explorer's own `#kern` word, `T<span class="k1">o</span> A<span
 * class="k2">V</span> T<span class="k3">y</span>`), split into pieces this composable can shift
 * individually. The three shiftable letters ('o', 'V', 'y') each carry the real kerning value
 * [sampleKerning] gives their own pair, read off [sampleKerning] directly rather than hand-tuned
 * per-pair margins -- see this file's own top KDoc.
 */
private fun kerningWordSegments(): List<KernSegment> {
    val sample = sampleKerning()

    fun pair(
        first: String,
        second: String,
    ): Double? = sample.kerning[first]?.get(second)
    return listOf(
        KernSegment("T", null),
        KernSegment("o", pair("T", "o")),
        KernSegment(" A", null),
        KernSegment("V", pair("A", "V")),
        KernSegment(" T", null),
        KernSegment("y", pair("T", "y")),
    )
}

/** A kerning value in font units, as a horizontal dp shift at [fontSizeSp]: `(value / unitsPerEm) * fontSizeSp`, the plain definition of "1 em of kerning" at that type size. */
private fun kerningOffsetDp(
    value: Double,
    unitsPerEm: Int,
    fontSizeSp: Double,
): Double = (value / unitsPerEm) * fontSizeSp

private fun kerningPairSummary(kerning: UfoKerning): String {
    val pairCount = kerning.kerning.values.sumOf { it.size }
    val pairWord = if (pairCount == 1) "pair" else "pairs"
    val groupCount = kerning.groups.size
    val groupWord = if (groupCount == 1) "group" else "groups"
    return "$pairCount $pairWord · $groupCount $groupWord · real values from UfoKerning, not the explorer's fixed CSS margins"
}

/**
 * A small, honest in-memory [UfoKerning] sample -- **not** a real project (see this file's top
 * KDoc). Shaped exactly like a real `kerning.plist`/`groups.plist`/`features.fea` cluster would be
 * (`core-font`'s own sibling P5b report): two literal-glyph kerning pairs plus one group pair, a
 * `public.kern1`/`public.kern2` group, and a short, real-looking FEA snippet.
 */
fun sampleKerning(): UfoKerning =
    UfoKerning(
        groups =
            mapOf(
                "public.kern1.O" to listOf("O", "C", "G", "Q"),
                "public.kern2.O" to listOf("O", "C", "G", "Q"),
            ),
        kerning =
            mapOf(
                "T" to mapOf("o" to -60.0, "y" to -80.0),
                "A" to mapOf("V" to -90.0),
                "public.kern1.O" to mapOf("public.kern2.O" to -4.0),
            ),
        features =
            "# sample -- no project loaded yet (docs/OPEN_QUESTIONS.md, P5b UI)\n" +
                "feature kern {\n" +
                "    pos T o -60;\n" +
                "    pos T y -80;\n" +
                "    pos A V -90;\n" +
                "    pos @public.kern1.O @public.kern2.O -4;\n" +
                "} kern;\n",
    )
