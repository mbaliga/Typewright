// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.report

import kotlin.math.abs
import kotlin.math.roundToLong

/** [this] with a comma between thousands (`1,763`); common code has no locale formatting to lean on. */
fun Int.withThousands(): String {
    val digits = abs(this.toLong()).toString()
    val grouped =
        digits
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()
    return if (this < 0) "-$grouped" else grouped
}

/** A box statistic as a person reads it: whole when whole, otherwise to at most two places (`8`, `12.5`, `23.25`). */
fun Double.statText(): String {
    val rounded = (this * 100.0).roundToLong()
    val whole = rounded / 100
    val fraction = abs(rounded % 100)
    return when {
        fraction == 0L -> whole.toString()
        fraction % 10 == 0L -> "${if (rounded < 0 && whole == 0L) "-" else ""}$whole.${fraction / 10}"
        else -> "${if (rounded < 0 && whole == 0L) "-" else ""}$whole.${fraction.toString().padStart(2, '0')}"
    }
}

/** `1 point` or `3 points`. */
internal fun plural(
    count: Int,
    one: String,
    many: String = "${one}s",
): String = if (count == 1) "${count.withThousands()} $one" else "${count.withThousands()} $many"

/** [names] as `o`, `o and e`, or `o, e and s`; more than [limit] end in `and N more`. */
internal fun nameList(
    names: List<String>,
    limit: Int = 12,
): String {
    val shown = names.take(limit)
    val rest = names.size - shown.size
    if (rest > 0) return shown.joinToString(", ") + " and $rest more"
    return when (shown.size) {
        0 -> ""
        1 -> shown[0]
        else -> shown.dropLast(1).joinToString(", ") + " and " + shown.last()
    }
}

/** [names] separated by spaces (`e c s`), with `+N` when more than [limit]: the small line beside a title. */
internal fun nameStrip(
    names: List<String>,
    limit: Int = 8,
): String {
    val shown = names.take(limit).joinToString(" ")
    val rest = names.size - limit
    return if (rest > 0) "$shown +$rest" else shown
}

/** The first [limit] of [lines] joined, then `… and N more` when there are more; [empty] when there are none. */
internal fun rawLines(
    lines: List<String>,
    limit: Int = 200,
    empty: String = "nothing found",
): String {
    if (lines.isEmpty()) return empty
    val shown = lines.take(limit).joinToString("\n")
    val rest = lines.size - limit
    return if (rest > 0) "$shown\n… and ${rest.withThousands()} more" else shown
}
