package dev.aarso.typewright.ui.tokens

import androidx.compose.ui.graphics.Color

/**
 * The one conversion from P4a's Compose-free [Argb] to a real [Color], kept in its own file so
 * `Argb.kt` itself stays exactly as P4a wrote it (that file's own KDoc explains why it avoids a
 * Compose dependency at all).
 *
 * Deliberately **not** `Color(value.toULong())` (what [Argb]'s own KDoc suggests as "an exact,
 * lossless conversion, since both types pack the same 32 ARGB bits into their low bits"): `Color`'s
 * `ULong`-taking constructor is its *internal* packed representation (colour-space id and
 * component bits laid out for `Color`'s own wide-gamut math), not a plain 0xAARRGGBB value in the
 * low 32 bits -- feeding it one directly produces a wrong colour. The public, unambiguous,
 * component-wise `Color(red, green, blue, alpha)` `Int` constructor (0-255 each, exactly what
 * [Argb]'s own accessors already expose) is what every other Compose call site uses for this and
 * is used here instead; [ArgbComposeTest] checks it round-trips exactly.
 */
fun Argb.toColor(): Color = Color(red = red, green = green, blue = blue, alpha = alpha)
