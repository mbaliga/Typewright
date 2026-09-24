package dev.aarso.typewright.ui.tokens

import kotlin.math.abs
import kotlin.math.round

/**
 * A fixed-decimal [Double] to [String], with no platform `String.format`/`NumberFormat`: Kotlin's
 * `String.format` is JVM-only, unavailable on `wasmJs` (`ui`'s own third platform, per this
 * module's `build.gradle.kts`). Used by the inspector row (UI_SPEC §3 "Inspector": "tabular"
 * numeral values) to render the camera's own X/Y/zoom -- this task's placeholder inspector
 * content (real construction-tool values are P5b's job).
 */
fun Double.toFixedString(decimals: Int): String {
    require(decimals >= 0) { "decimals must be >= 0, was $decimals" }
    val scale = generateSequence(1.0) { it * 10.0 }.take(decimals + 1).last()
    val negative = this < 0.0
    val rounded = round(abs(this) * scale) / scale
    val whole = rounded.toLong()
    val fractionScale = generateSequence(1L) { it * 10 }.take(decimals + 1).last()
    val fraction = round((rounded - whole) * fractionScale).toLong().coerceIn(0, fractionScale - 1)
    val fractionStr = fraction.toString().padStart(decimals, '0')
    val sign = if (negative && (whole != 0L || fraction != 0L)) "-" else ""
    return if (decimals > 0) "$sign$whole.$fractionStr" else "$sign$whole"
}
