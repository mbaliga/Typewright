// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.theme

import com.asoc.typewright.ui.tokens.Argb
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.GridToken
import com.asoc.typewright.ui.tokens.MeaningColors
import com.asoc.typewright.ui.tokens.TextureId
import com.asoc.typewright.ui.tokens.ThemeRole

/**
 * A theme: a ground, an ink and a muted, the accent and node, line and comparison colours
 * (SCREENS_V1 §3.1), a grain and a grid opacity, and whether the chrome is [dark]. App-level only,
 * never per project. Every role is always set in [colors]; a built-in theme is read-only
 * ([builtIn]) and is duplicated to be edited.
 */
data class Theme(
    val id: String,
    val name: String,
    val builtIn: Boolean,
    val dark: Boolean,
    /** Grain strength, 0 to [MAX_GRAIN]. */
    val grain: Double,
    /** The grid's opacity, 0 to [MAX_GRID_OPACITY]. */
    val gridOpacity: Double,
    val colors: Map<ThemeRole, Argb>,
) {
    /** The colour of [role]; every role is set, so this never falls back. */
    fun color(role: ThemeRole): Argb = colors.getValue(role)

    /** This theme with [role] set to [color]. */
    fun with(
        role: ThemeRole,
        color: Argb,
    ): Theme = copy(colors = colors + (role to color))

    /** [role] back to [from]'s value for it. */
    fun reset(
        role: ThemeRole,
        from: Theme,
    ): Theme = with(role, from.color(role))

    /** An editable copy under a new [id] and [name]. */
    fun duplicate(
        id: String,
        name: String,
    ): Theme = copy(id = id, name = name, builtIn = false)

    /**
     * The texture the screens draw with. A built-in is exactly its [CanvasTextures] entry; a custom
     * theme gets its own ground, ink and muted, a line at 20% (30% on a dark ground), and the rest
     * of its roles.
     */
    fun toTexture(): CanvasTexture {
        CanvasTextures.ALL.firstOrNull { it.id.name.lowercase() == id && builtIn }?.let { return it }
        val ink = color(ThemeRole.INK)
        return CanvasTexture(
            id = TextureId.CUSTOM,
            canvas = color(ThemeRole.CANVAS),
            ink = ink,
            fg = ink,
            muted = color(ThemeRole.MUTED),
            line = ink.withAlpha(if (dark) CUSTOM_LINE_OPACITY_DARK else CUSTOM_LINE_OPACITY_LIGHT),
            grainOpacity = grain,
            dark = dark,
            roles = colors - setOf(ThemeRole.CANVAS, ThemeRole.INK, ThemeRole.MUTED),
            gridOpacity = gridOpacity,
        )
    }

    companion object {
        const val MAX_GRAIN: Double = 0.40
        const val MAX_GRID_OPACITY: Double = 0.30
        private const val CUSTOM_LINE_OPACITY_LIGHT = 0.20
        private const val CUSTOM_LINE_OPACITY_DARK = 0.30
    }
}

/** The built-in themes: the five textures of UI_SPEC §2 with the roles the meaning colours give them. */
object Themes {
    val PAPER: Theme = builtIn(CanvasTextures.PAPER, "Paper")
    val VELLUM: Theme = builtIn(CanvasTextures.VELLUM, "Vellum")
    val BLUEPRINT: Theme = builtIn(CanvasTextures.BLUEPRINT, "Blueprint")
    val BLACK: Theme = builtIn(CanvasTextures.BLACK, "Black")
    val WHITE: Theme = builtIn(CanvasTextures.WHITE, "White")

    val BUILT_IN: List<Theme> = listOf(PAPER, VELLUM, BLUEPRINT, BLACK, WHITE)

    fun byId(id: String): Theme? = BUILT_IN.firstOrNull { it.id == id }

    private fun builtIn(
        texture: CanvasTexture,
        name: String,
    ): Theme =
        Theme(
            id = texture.id.name.lowercase(),
            name = name,
            builtIn = true,
            dark = texture.dark,
            grain = texture.grainOpacity,
            gridOpacity = if (texture.dark) GridToken.WHITE_OPACITY_ON_DARK else GridToken.INK_OPACITY,
            colors = defaultRoles(texture),
        )

    /** Every role's colour for [texture]: its own ground and text, the meaning colours, and ink and muted for geometry. */
    fun defaultRoles(texture: CanvasTexture): Map<ThemeRole, Argb> =
        mapOf(
            ThemeRole.CANVAS to texture.canvas,
            ThemeRole.INK to texture.ink,
            ThemeRole.MUTED to texture.muted,
            ThemeRole.ACCENT to MeaningColors.VIOLET.forTexture(texture),
            ThemeRole.CORNER_NODE to texture.ink,
            ThemeRole.SMOOTH_NODE to texture.ink,
            ThemeRole.OFF_CURVE to texture.muted,
            ThemeRole.HANDLE_LINE to texture.muted,
            ThemeRole.SELECTED_NODE to MeaningColors.VIOLET.forTexture(texture),
            ThemeRole.SNAP_TARGET to MeaningColors.CYAN.forTexture(texture),
            ThemeRole.METRIC_LINES to texture.muted,
            ThemeRole.GUIDES to texture.muted,
            ThemeRole.CONSTRUCTION_LINES to texture.muted,
            ThemeRole.GRID_LINES to texture.muted,
            ThemeRole.COMPARISON_1 to MeaningColors.AMBER.forTexture(texture),
            ThemeRole.COMPARISON_2 to MeaningColors.MAGENTA.forTexture(texture),
            ThemeRole.COMPARISON_3 to texture.ink,
            ThemeRole.COMPARISON_4 to texture.muted,
        )
}

/** The colour of [role] on this texture: the theme's own, or the built-in default for the texture. */
fun CanvasTexture.colorOf(role: ThemeRole): Argb = roles[role] ?: Themes.defaultRoles(this).getValue(role)
