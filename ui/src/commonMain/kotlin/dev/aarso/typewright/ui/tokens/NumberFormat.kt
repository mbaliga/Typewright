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

/**
 * [this] with a thousands separator every three digits from the right (`1763` -> `"1,763"`),
 * matching TYPEWRIGHT_BUILD_BRIEF.md section 7's own fixture-table style ("1,763 -> 8") and
 * `ui/typewright-explorer.html`'s own worked node-economy counts (`#s-workbook`'s `.gate div b`,
 * e.g. `1,252`). No platform `NumberFormat`/`String.format`, for the same `wasmJs`-portability
 * reason [toFixedString] already states in its own KDoc -- this is plain digit-grouping, always
 * `,` (the explorer's own only separator; no locale switch exists anywhere in this app yet).
 */
fun Int.toThousandsString(): String {
    val negative = this < 0
    val digits = abs(this).toString()
    val grouped =
        digits
            .reversed()
            .chunked(3)
            .joinToString(",") { it }
            .reversed()
    return if (negative) "-$grouped" else grouped
}
