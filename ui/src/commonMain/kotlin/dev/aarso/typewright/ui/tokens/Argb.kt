package dev.aarso.typewright.ui.tokens

import kotlin.math.roundToInt

/**
 * A packed `0xAARRGGBB` colour. Used instead of `androidx.compose.ui.graphics.Color` so every
 * file in this task compiles and tests with zero Compose dependency (task P4a's own constraint)
 * on jvm and wasmJs alike -- `ui`'s own placeholder `PaperTokens.kt` already uses `Color` for two
 * values, and this module could have too (`Color` is itself a plain inline value class with no
 * Compose runtime dependency), but keeping every P4a file Compose-free avoids relying on that
 * being true of the *whole* transitive `ui-graphics` klib under Kotlin/Wasm, which this module's
 * own `build.gradle.kts` already flags as fragile (CMP-4906: "Skiko's Wasm runtime only loads in
 * a browser"). P4b converts a value here to a real `Color` at the Compose boundary with
 * `Color(argb.value.toULong())` -- an exact, lossless conversion, since both types pack the same
 * 32 ARGB bits into their low bits.
 *
 * UI_SPEC.md and the explorer only ever write opaque 6-digit hex, so [rgb] is the constructor
 * most call sites reach for; [withAlpha] derives the "ink 20%"-style translucent lines, grid and
 * bloom UI_SPEC §1-§2 describe as a base colour plus a percentage, not a second hex value.
 *
 * A `data class`, not a `value class`: `@JvmInline` (needed to compile a `value class` at all on
 * the JVM target) lives in `kotlin.jvm`, a JVM-only package unresolved on wasmJs commonMain --
 * confirmed by trying it (task P4a): `Unresolved reference 'JvmInline'` on `:ui:compileKotlinWasmJs`
 * without the annotation, `Value classes without '@JvmInline' annotation are not yet supported`
 * on `:ui:compileKotlinDesktop` with it omitted. A plain `data class` compiles identically on
 * every target and gives up only the JVM's own inlining optimisation, not any behaviour.
 */
data class Argb(
    val value: Long,
) {
    val alpha: Int get() = ((value shr 24) and 0xFF).toInt()
    val red: Int get() = ((value shr 16) and 0xFF).toInt()
    val green: Int get() = ((value shr 8) and 0xFF).toInt()
    val blue: Int get() = (value and 0xFF).toInt()

    /** This colour with its alpha channel replaced by [fraction] of `0xFF` (`0.0` transparent, `1.0` opaque). */
    fun withAlpha(fraction: Double): Argb {
        require(fraction in 0.0..1.0) { "alpha fraction must be in 0.0..1.0, was $fraction" }
        val alphaByte = (fraction * 255.0).roundToInt().coerceIn(0, 255)
        return Argb((alphaByte.toLong() shl 24) or (value and 0x00FFFFFFL))
    }

    override fun toString(): String {
        val hex = (value and 0xFFFFFFFFL).toString(16).padStart(8, '0')
        return "Argb(#$hex)"
    }

    companion object {
        /** An opaque colour from a 6-digit RGB hex value, e.g. `Argb.rgb(0xEFE9DC)`. */
        fun rgb(hex: Long): Argb = Argb(0xFF000000L or (hex and 0x00FFFFFFL))

        val TRANSPARENT = Argb(0L)
    }
}
