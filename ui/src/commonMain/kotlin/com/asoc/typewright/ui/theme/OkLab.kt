// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.theme

import com.asoc.typewright.ui.tokens.Argb
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** A colour in OKLab: lightness [l] (0..1) and the two opponent axes [a] and [b]. */
data class OkLab(
    val l: Double,
    val a: Double,
    val b: Double,
) {
    /** Chroma, the distance from grey. */
    val chroma: Double get() = hypot(a, b)

    /** Hue in radians. */
    val hue: Double get() = atan2(b, a)
}

private fun srgbToLinear(v: Int): Double {
    val s = v / 255.0
    return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
}

private fun linearToSrgb(v: Double): Int {
    val c = v.coerceIn(0.0, 1.0)
    val s = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1.0 / 2.4) - 0.055
    return (s * 255.0).roundToInt().coerceIn(0, 255)
}

/** [this] colour in OKLab (Björn Ottosson's matrices), ignoring alpha. */
fun Argb.toOkLab(): OkLab {
    val r = srgbToLinear(red)
    val g = srgbToLinear(green)
    val b = srgbToLinear(blue)
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return OkLab(
        l = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
        a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
        b = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
    )
}

/** The opaque sRGB colour for [lab], clamped into gamut. */
fun OkLab.toArgb(): Argb {
    val l = (l + 0.3963377774 * a + 0.2158037573 * b).let { it * it * it }
    val m = (this.l - 0.1055613458 * a - 0.0638541728 * b).let { it * it * it }
    val s = (this.l - 0.0894841775 * a - 1.2914855480 * b).let { it * it * it }
    val r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    val bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    return Argb.rgb((linearToSrgb(r).toLong() shl 16) or (linearToSrgb(g).toLong() shl 8) or linearToSrgb(bl).toLong())
}

/** The same colour moved to hue [hue] (radians), chroma scaled by [chromaScale] and lightness shifted by [dl]. */
fun OkLab.moved(
    hue: Double,
    chromaScale: Double,
    dl: Double,
): OkLab {
    val c = chroma * chromaScale
    return OkLab((l + dl).coerceIn(0.0, 1.0), c * cos(hue), c * sin(hue))
}

/** [this] colour in CIELAB under D65, ignoring alpha: the space ΔE is measured in. */
private fun Argb.toLab(): Triple<Double, Double, Double> {
    val r = srgbToLinear(red)
    val g = srgbToLinear(green)
    val b = srgbToLinear(blue)
    val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
    val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883

    fun f(t: Double) = if (t > 216.0 / 24389.0) cbrt(t) else (24389.0 / 27.0 * t + 16.0) / 116.0
    val fx = f(x)
    val fy = f(y)
    val fz = f(z)
    return Triple(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
}

/** The distance between two colours as ΔE (CIE76 in CIELAB, D65): about 2 is the least a person can see, and 20 is plainly different. */
fun deltaE(
    x: Argb,
    y: Argb,
): Double {
    val (l1, a1, b1) = x.toLab()
    val (l2, a2, b2) = y.toLab()
    return hypot(hypot(l1 - l2, a1 - a2), b1 - b2)
}

/** The difference in OKLab lightness × 100. */
fun lightnessGap(
    x: Argb,
    y: Argb,
): Double = abs(x.toOkLab().l - y.toOkLab().l) * 100.0

/** WCAG's contrast ratio between two opaque colours. */
fun contrastRatio(
    x: Argb,
    y: Argb,
): Double {
    fun lum(c: Argb) = 0.2126 * srgbToLinear(c.red) + 0.7152 * srgbToLinear(c.green) + 0.0722 * srgbToLinear(c.blue)
    val (hi, lo) = listOf(lum(x), lum(y)).sortedDescending()
    return (hi + 0.05) / (lo + 0.05)
}
