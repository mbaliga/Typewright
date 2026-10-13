// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.qa.corpus.AtlasFace
import com.asoc.typewright.qa.corpus.style.Storeys
import com.asoc.typewright.qa.corpus.style.StyleMeasurement
import com.asoc.typewright.qa.corpus.style.TerminalStyle
import kotlin.math.round

/**
 * One measurable property of a typeface, as the style detector measures it and the style atlas
 * stores it. [key] is the atlas's own key; [glyphs] says which letters it is read from, so the
 * Brief can tell a person what to draw before it can measure anything.
 */
enum class Feature(
    val key: String,
    val label: String,
    val numeric: Boolean,
    val glyphs: String,
) {
    CONTRAST("contrast", "Contrast", true, "o"),
    STRESS("stress", "Stress", true, "o"),
    SERIF("serif", "Serifs", false, "T"),
    BRACKET("bracket", "Serif bracket", true, "T"),
    APERTURE("aperture", "Aperture", true, "c e s"),
    A_STOREYS("aStoreys", "Form of a", false, "a"),
    G_STOREYS("gStoreys", "Form of g", false, "g"),
    TERMINAL("terminal", "Terminals", false, "c"),
    ROUNDNESS("roundness", "Roundness", true, "o"),
    X_HEIGHT("xHeight", "x-height", true, "x H"),
    WIDTH("width", "Width", true, "o n a g e H T c s x"),
    WEIGHT("stem", "Weight", true, "H"),
    WAIST("crossbar", "Crossbar height", true, "H"),
    ;

    /** This feature's value in a measurement of the person's own letters, or null when unmeasured. */
    fun numericValue(m: StyleMeasurement): Double? =
        when (this) {
            CONTRAST -> m.contrastRatio
            STRESS -> m.stressAngleAbs
            BRACKET -> m.bracketScore
            APERTURE -> m.apertureOpenness
            ROUNDNESS -> m.oRoundnessExponent
            X_HEIGHT -> m.xHeightToCapHeightRatio
            WIDTH -> m.widthClass
            WEIGHT -> m.stemToCapHeight
            WAIST -> m.crossbarHeight
            SERIF, A_STOREYS, G_STOREYS, TERMINAL -> null
        }

    /** This feature's value as the atlas spells it (`yes`, `single`, `flat`), or null when unmeasured. */
    fun categoricalValue(m: StyleMeasurement): String? =
        when (this) {
            SERIF -> m.hasSerif?.let { if (it) "yes" else "no" }
            A_STOREYS -> m.aStoreys.atlasValue()
            G_STOREYS -> m.gStoreys.atlasValue()
            TERMINAL -> m.terminalStyle.atlasValue()
            else -> null
        }

    /** A face's value from the atlas. */
    fun numericValue(face: AtlasFace): Double? = if (numeric) face.numeric(key) else null

    /** A face's categorical value from the atlas. */
    fun categoricalValue(face: AtlasFace): String? = if (numeric) null else face.categorical(key)

    /** A number in this feature's own terms, for a sentence: "4.3 : 1", "15°", "0.72 of cap height". */
    fun format(value: Double): String =
        when (this) {
            CONTRAST -> "${oneDecimal(value)} : 1"
            STRESS -> "${round(value).toInt()}°"
            BRACKET -> twoDecimals(value)
            APERTURE -> "${twoDecimals(value)} of the x-height"
            ROUNDNESS -> twoDecimals(value)
            X_HEIGHT -> "${twoDecimals(value)} of the cap height"
            WIDTH -> "${twoDecimals(value)} em"
            WEIGHT -> "${twoDecimals(value)} of the cap height"
            WAIST -> "${twoDecimals(value)} of the height"
            SERIF, A_STOREYS, G_STOREYS, TERMINAL -> twoDecimals(value)
        }

    /** A categorical value in words: "two-storey", "serifs", "flat". */
    fun describe(value: String): String =
        when (this) {
            SERIF -> if (value == "yes") "serifs" else "no serifs"
            A_STOREYS, G_STOREYS -> if (value == "single") "one-storey" else "two-storey"
            TERMINAL -> value
            else -> value
        }
}

private fun Storeys.atlasValue(): String? =
    when (this) {
        Storeys.SINGLE -> "single"
        Storeys.DOUBLE -> "double"
        Storeys.UNKNOWN -> null
    }

private fun TerminalStyle.atlasValue(): String? =
    when (this) {
        TerminalStyle.FLAT -> "flat"
        TerminalStyle.ROUND -> "round"
        TerminalStyle.ANGLED -> "angled"
        TerminalStyle.UNKNOWN -> null
    }

internal fun oneDecimal(value: Double): String {
    val tenths = round(value * 10).toLong()
    val sign = if (tenths < 0) "-" else ""
    val abs = kotlin.math.abs(tenths)
    return "$sign${abs / 10}.${abs % 10}"
}

internal fun twoDecimals(value: Double): String {
    val hundredths = round(value * 100).toLong()
    val sign = if (hundredths < 0) "-" else ""
    val abs = kotlin.math.abs(hundredths)
    val frac = (abs % 100).toString().padStart(2, '0')
    return "$sign${abs / 100}.$frac"
}
