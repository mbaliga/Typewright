// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.signedArea
import com.asoc.typewright.qa.corpus.style.inkBounds
import com.asoc.typewright.qa.corpus.style.inkIntervals
import com.asoc.typewright.qa.corpus.style.lineCrossings
import com.asoc.typewright.qa.corpus.style.stemToHeightRatio
import kotlin.math.PI
import kotlin.math.abs

/** The project's own letters, keyed by the character each glyph draws, as the Brief reads them. */
data class FontView(
    val unitsPerEm: Int,
    val glyphs: Map<Char, Glyph>,
) {
    /** True when the glyph for [ch] exists and has at least one contour. */
    fun drawn(ch: Char): Boolean = glyphs[ch]?.contours?.isNotEmpty() == true

    companion object {
        /** No project open. */
        val EMPTY = FontView(1000, emptyMap())
    }
}

/** How a check came out. */
enum class CheckStatus(
    val word: String,
) {
    PASS("meets it"),
    WARN("close to the limit"),
    FAIL("does not meet it"),
    UNMEASURED("not drawn yet"),
}

/** One check's result: its [status] and a sentence saying what was measured. */
data class CheckResult(
    val status: CheckStatus,
    val detail: String,
)

/** A measurable requirement a use places on the letters, run against the project's own glyphs. */
sealed interface UseCheck {
    fun run(font: FontView): CheckResult

    /** Every figure 0 to 9 on one advance width. */
    data object TabularFigures : UseCheck {
        override fun run(font: FontView): CheckResult {
            val widths = DIGITS.filter { font.drawn(it) }.associateWith { font.glyphs.getValue(it).advanceWidth }
            if (widths.size < 2) return CheckResult(CheckStatus.UNMEASURED, "Draw the figures 0 to 9 to check their widths.")
            val distinct = widths.values.toSet()
            return if (distinct.size == 1) {
                CheckResult(CheckStatus.PASS, "All ${widths.size} figures drawn so far are ${distinct.single()} units wide.")
            } else {
                val narrow = widths.minBy { it.value }
                val wide = widths.maxBy { it.value }
                CheckResult(
                    CheckStatus.FAIL,
                    "Figures run from ${narrow.value} units (${narrow.key}) to ${wide.value} (${wide.key}); give them all one width.",
                )
            }
        }
    }

    /** Every letter and figure on one advance width. */
    data object FixedPitch : UseCheck {
        override fun run(font: FontView): CheckResult {
            val widths =
                font.glyphs
                    .filter { (ch, g) ->
                        ch.isLetterOrDigit() && g.contours.isNotEmpty()
                    }.mapValues { it.value.advanceWidth }
            if (widths.size < 2) return CheckResult(CheckStatus.UNMEASURED, "Draw some letters to check their widths.")
            val distinct = widths.values.toSet()
            return if (distinct.size == 1) {
                CheckResult(CheckStatus.PASS, "All ${widths.size} characters are ${distinct.single()} units wide.")
            } else {
                CheckResult(CheckStatus.FAIL, "${distinct.size} different widths among ${widths.size} characters; a monospace has one.")
            }
        }
    }

    /** The stroke of I (or the stem of H) between [min] and [max] of its height. */
    data class StrokeShareOfHeight(
        val min: Double,
        val max: Double,
    ) : UseCheck {
        override fun run(font: FontView): CheckResult {
            val ratio =
                font.glyphs['I']?.takeIf { it.contours.isNotEmpty() }?.let { singleStemRatio(it) }
                    ?: font.glyphs['H']?.takeIf { it.contours.isNotEmpty() }?.let { stemToHeightRatio(it) }
                    ?: return CheckResult(CheckStatus.UNMEASURED, "Draw I or H to measure the stroke.")
            val pct = percent(ratio)
            return when {
                ratio < min -> {
                    CheckResult(CheckStatus.FAIL, "The stroke is $pct of the letter's height; the minimum is ${percent(min)}.")
                }

                ratio > max -> {
                    CheckResult(CheckStatus.FAIL, "The stroke is $pct of the letter's height; the maximum is ${percent(max)}.")
                }

                else -> {
                    CheckResult(
                        CheckStatus.PASS,
                        "The stroke is $pct of the letter's height (${percent(min)} to ${percent(max)} allowed).",
                    )
                }
            }
        }
    }

    /** The width of O between [min] and [max] of the height of I. */
    data class OWidthShareOfIHeight(
        val min: Double,
        val max: Double,
    ) : UseCheck {
        override fun run(font: FontView): CheckResult {
            val o = font.glyphs['O']?.inkBounds()
            val i = font.glyphs['I']?.inkBounds()
            if (o == null || i == null ||
                i.height <= 0.0
            ) {
                return CheckResult(CheckStatus.UNMEASURED, "Draw O and I to measure their proportion.")
            }
            val ratio = o.width / i.height
            val pct = percent(ratio)
            return if (ratio in min..max) {
                CheckResult(CheckStatus.PASS, "O is $pct as wide as I is tall (${percent(min)} to ${percent(max)} allowed).")
            } else {
                CheckResult(CheckStatus.FAIL, "O is $pct as wide as I is tall; the rule allows ${percent(min)} to ${percent(max)}.")
            }
        }
    }

    /**
     * Our estimate of how much of a round watch dial the time [sample] lights, with the figures
     * set at [figureShareOfDial] of the dial's diameter, against a [budget] for the whole face.
     * The rule is about the average over the whole screen; this only says how much of that budget
     * the time alone would use.
     */
    data class AmbientLitShare(
        val sample: String = "10:08",
        val figureShareOfDial: Double = 0.28,
        val budget: Double = 0.15,
    ) : UseCheck {
        override fun run(font: FontView): CheckResult {
            val chars = sample.filter { it.isDigit() }
            if (chars.any {
                    !font.drawn(
                        it,
                    )
                }
            ) {
                return CheckResult(CheckStatus.UNMEASURED, "Draw ${chars.toSet().joinToString(" ")} to estimate it.")
            }
            val figureHeight =
                font.glyphs['0']?.inkBounds()?.height ?: font.glyphs
                    .getValue(chars.first())
                    .inkBounds()
                    ?.height
            if (figureHeight == null || figureHeight <= 0.0) return CheckResult(CheckStatus.UNMEASURED, "Draw 0 to estimate it.")
            val dialDiameter = figureHeight / figureShareOfDial
            val dialArea = PI * (dialDiameter / 2) * (dialDiameter / 2)
            val ink = chars.sumOf { inkArea(font.glyphs.getValue(it)) }
            val share = ink / dialArea
            val text = "With the figures at ${percent(figureShareOfDial)} of the dial, \"$sample\" lights about ${percent(share)} of it"
            return when {
                share <= budget * 0.5 -> {
                    CheckResult(
                        CheckStatus.PASS,
                        "$text, leaving most of the ${percent(budget)} budget for the rest of the face.",
                    )
                }

                share <= budget -> {
                    CheckResult(
                        CheckStatus.WARN,
                        "$text: more than half of the ${percent(budget)} budget on the time alone.",
                    )
                }

                else -> {
                    CheckResult(
                        CheckStatus.FAIL,
                        "$text, over the ${percent(budget)} budget on its own. Thin the strokes for ambient mode.",
                    )
                }
            }
        }
    }
}

private const val DIGITS = "0123456789"

/** The stroke of a single upright (I, l, 1) over its height, read at mid height where serifs can't reach. */
internal fun singleStemRatio(glyph: Glyph): Double? {
    val b = glyph.inkBounds() ?: return null
    if (b.height <= 0.0) return null
    val runs = inkIntervals(glyph.lineCrossings(Vec2(b.minX - 10.0, b.minY + b.height * 0.5), Vec2(1.0, 0.0)))
    val stem = runs.firstOrNull()?.length ?: return null
    return stem / b.height
}

/** A glyph's ink area: the outer contours' areas minus the counters', by their winding. */
internal fun inkArea(glyph: Glyph): Double = abs(glyph.contours.sumOf { it.signedArea() })

/** A share as a percentage with at most one decimal ("15%", "7.5%"), formatted the same on every target. */
internal fun percent(share: Double): String = oneDecimal(share * 100).removeSuffix(".0") + "%"
