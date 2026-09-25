// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.tokens

/**
 * The canvas textures, UI_SPEC.md §2's table reproduced exactly: canvas, ink, chrome foreground,
 * muted, line (a base colour plus an opacity, e.g. "ink 20%" -- [line] already bakes that
 * percentage in via [Argb.withAlpha], so a caller reads it directly rather than re-deriving the
 * percentage), and the grain tile's opacity. [id] is the explorer's own `data-texture` value.
 */
data class CanvasTexture(
    val id: TextureId,
    val canvas: Argb,
    val ink: Argb,
    val fg: Argb,
    val muted: Argb,
    val line: Argb,
    val grainOpacity: Double,
    /** Blueprint's own second grid ("0 (+ 24 dp grid at white 6%)" in UI_SPEC §2); null elsewhere. */
    val fineGrid: FineGrid? = null,
)

/** The explorer's `data-texture` values (UI_SPEC §2 / brief §6), in the table's own order. */
enum class TextureId { PAPER, VELLUM, BLUEPRINT, BLACK, WHITE }

/** Blueprint's extra grid, on top of the standard one [GridToken] describes (UI_SPEC §2's grain-column parenthetical). */
data class FineGrid(
    val sizeDp: Double,
    val color: Argb,
)

/** UI_SPEC.md §2's five canvas textures, values transcribed from its table exactly. */
object CanvasTextures {
    val PAPER =
        CanvasTexture(
            id = TextureId.PAPER,
            canvas = Argb.rgb(0xEFE9DC),
            ink = Argb.rgb(0x17150F),
            fg = Argb.rgb(0x1B1915),
            muted = Argb.rgb(0x6F6A5E),
            line = Argb.rgb(0x17150F).withAlpha(0.20),
            grainOpacity = 0.16,
        )

    val VELLUM =
        CanvasTexture(
            id = TextureId.VELLUM,
            canvas = Argb.rgb(0xE6DCC1),
            ink = Argb.rgb(0x1A160F),
            fg = Argb.rgb(0x1B1712),
            muted = Argb.rgb(0x766D5A),
            line = Argb.rgb(0x1A160F).withAlpha(0.22),
            grainOpacity = 0.30,
        )

    val BLUEPRINT =
        CanvasTexture(
            id = TextureId.BLUEPRINT,
            canvas = Argb.rgb(0x17417C),
            ink = Argb.rgb(0xFFFFFF),
            fg = Argb.rgb(0xEAF1FF),
            muted = Argb.rgb(0xA6B8DA),
            line = Argb.rgb(0xFFFFFF).withAlpha(0.38),
            grainOpacity = 0.0,
            fineGrid = FineGrid(sizeDp = 24.0, color = Argb.rgb(0xFFFFFF).withAlpha(0.06)),
        )

    val BLACK =
        CanvasTexture(
            id = TextureId.BLACK,
            canvas = Argb.rgb(0x000000),
            ink = Argb.rgb(0xF3F3F0),
            fg = Argb.rgb(0xECECEA),
            muted = Argb.rgb(0x8E8E89),
            line = Argb.rgb(0xFFFFFF).withAlpha(0.22),
            grainOpacity = 0.0,
        )

    val WHITE =
        CanvasTexture(
            id = TextureId.WHITE,
            canvas = Argb.rgb(0xFFFFFF),
            ink = Argb.rgb(0x000000),
            fg = Argb.rgb(0x141414),
            muted = Argb.rgb(0x6B6B6B),
            line = Argb.rgb(0x000000).withAlpha(0.18),
            grainOpacity = 0.0,
        )

    /** Every texture, in UI_SPEC.md §2's own table order. */
    val ALL: List<CanvasTexture> = listOf(PAPER, VELLUM, BLUEPRINT, BLACK, WHITE)

    /** brief §6 / §15 item 5: "Default: paper [CONFIRM]" -- undecided, paper stands until Madhav confirms. */
    val DEFAULT: CanvasTexture = PAPER

    fun byId(id: TextureId): CanvasTexture = ALL.first { it.id == id }
}

/**
 * A meaning colour (UI_SPEC §2 / brief §6, CLAUDE.md law 8 "colour is meaning only"): a
 * light-ground value, a dark-ground value, and blueprint's own variant where UI_SPEC gives one
 * ("violet ... #B3A6FF on blueprint", "amber ... #FFC247 blueprint", "magenta ... #FF7ADB
 * blueprint"). Cyan has no separate blueprint value in UI_SPEC §2, so [forTexture] falls back to
 * [dark] for it there, matching blueprint being a dark-ground texture.
 */
data class MeaningColor(
    val light: Argb,
    val dark: Argb,
    val blueprint: Argb = dark,
) {
    /** [light] on [TextureId.PAPER]/[TextureId.VELLUM]/[TextureId.WHITE], [dark] on [TextureId.BLACK], [blueprint] on [TextureId.BLUEPRINT]. */
    fun forTexture(id: TextureId): Argb =
        when (id) {
            TextureId.PAPER, TextureId.VELLUM, TextureId.WHITE -> light
            TextureId.BLACK -> dark
            TextureId.BLUEPRINT -> blueprint
        }
}

/** UI_SPEC.md §2's four meaning colours. Never used for anything but selection/snapping/comparison (CLAUDE.md law 8). */
object MeaningColors {
    /** Selected node or contour (CLAUDE.md law 8). */
    val VIOLET = MeaningColor(light = Argb.rgb(0x5F4BE0), dark = Argb.rgb(0x8E7BFF), blueprint = Argb.rgb(0xB3A6FF))

    /** The thing you are snapping to (CLAUDE.md law 8). */
    val CYAN = MeaningColor(light = Argb.rgb(0x0A9D8E), dark = Argb.rgb(0x08FED5))

    /** Comparison layers, paired with a line pattern (never colour alone). */
    val AMBER = MeaningColor(light = Argb.rgb(0xB57A00), dark = Argb.rgb(0xFFB300), blueprint = Argb.rgb(0xFFC247))

    /** Comparison layers, paired with a line pattern (never colour alone). */
    val MAGENTA = MeaningColor(light = Argb.rgb(0xB8248F), dark = Argb.rgb(0xFF5FD2), blueprint = Argb.rgb(0xFF7ADB))
}

/**
 * The sheet's 28 dp square grid (UI_SPEC §1 layer 2: "28 dp square grid at ink 8.5% (white 9% on
 * dark canvases)"). This is the *standard* grid every texture but blueprint draws; blueprint
 * draws its own [FineGrid] instead ([CanvasTexture.fineGrid]), not this one on top of it -- UI_SPEC
 * §2 lists blueprint's grain as "0 (+ 24 dp grid at white 6%)", i.e. one grid, not two.
 */
object GridToken {
    const val SIZE_DP: Double = 28.0
    const val INK_OPACITY: Double = 0.085
    const val WHITE_OPACITY_ON_DARK: Double = 0.09

    /** The grid line colour for [texture]: [texture]'s own ink at [INK_OPACITY], or white at [WHITE_OPACITY_ON_DARK] on [TextureId.BLACK]. */
    fun colorFor(texture: CanvasTexture): Argb =
        when (texture.id) {
            TextureId.BLACK -> Argb.rgb(0xFFFFFF).withAlpha(WHITE_OPACITY_ON_DARK)
            else -> texture.ink.withAlpha(INK_OPACITY)
        }
}

/**
 * The bloom's own gradient math (UI_SPEC §1 layer 4 / brief §5.3, `docs/ARCHITECTURE_REVIEW.md`
 * §4.1 "Gradients"): "radial gradients from canvas colour (opaque to 38-45%) to transparent".
 * The review's own finding is that "transparent" must not mean `Color.Transparent` (RGB
 * `0x000000`) -- "Skia interpolates unpremultiplied, while CSS interpolates premultiplied, so a
 * stop at `Color.Transparent` gives a grey halo. End on `canvas.copy(alpha = 0f)` instead." Kept
 * here as plain [Argb] math (no Compose dependency) so the fix is unit-testable
 * (`CanvasTokensTest`): both stops must carry the *same* RGB as [texture]'s own canvas colour,
 * differing only in alpha.
 */
object BloomToken {
    /** UI_SPEC §1: "opaque to 38-45%" -- the inner stop's alpha fraction. */
    const val INNER_ALPHA: Double = 0.42

    /** The bloom radial gradient's two stops for [texture]: canvas colour at [INNER_ALPHA], fading to the *same* canvas colour at alpha 0 (never `Argb.TRANSPARENT`, whose RGB is black). */
    fun stopsFor(texture: CanvasTexture): Pair<Argb, Argb> = texture.canvas.withAlpha(INNER_ALPHA) to texture.canvas.withAlpha(0.0)
}

/** UI_SPEC.md §2 "Spacing" and §6 "Accessibility" (touch target floor). All values in dp. */
object SpacingTokens {
    /** "18 dp side gutter inside the phone". */
    const val GUTTER_DP: Double = 18.0

    /** "14-22 dp vertical rhythm". */
    const val VERTICAL_RHYTHM_MIN_DP: Double = 14.0
    const val VERTICAL_RHYTHM_MAX_DP: Double = 22.0

    /** "the inspector row at 16 dp from the bottom plus safe area". */
    const val INSPECTOR_FROM_BOTTOM_DP: Double = 16.0

    /** "Targets >= 44 dp for finger; ... inspector values are tappable at 44 dp height". */
    const val MIN_TOUCH_TARGET_DP: Double = 44.0
}
