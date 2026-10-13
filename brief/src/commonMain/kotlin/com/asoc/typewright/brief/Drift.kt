// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.qa.corpus.style.StyleMeasurement
import com.asoc.typewright.qa.corpus.style.measureStyle

/**
 * The person's letters and what they measure. Measuring reads every style letter, so make one
 * per change of the glyphs, not per frame.
 */
data class Drawing(
    val font: FontView,
    val measurement: StyleMeasurement,
) {
    companion object {
        /** Nothing drawn. */
        val EMPTY = Drawing(FontView.EMPTY, StyleMeasurement.EMPTY)

        /** Measures [font]'s letters. */
        fun of(font: FontView): Drawing =
            Drawing(font, if (font.glyphs.isEmpty()) StyleMeasurement.EMPTY else measureStyle(font.glyphs, font.unitsPerEm))
    }
}

/** How a drawn feature stands against its target. Every status has a word; colour is never the signal. */
enum class DriftStatus(
    val word: String,
) {
    ON("on target"),
    NEAR("close"),
    OFF("off target"),
    UNMEASURED("not drawn yet"),
}

/**
 * One feature of the person's letters against the brief: what it [measured] (in words), how far
 * that is from the [target], and, when it is off or unmeasured, what to do about it.
 */
data class DriftItem(
    val feature: Feature,
    val target: Target,
    val measured: String?,
    val status: DriftStatus,
    val advice: String?,
)

/** Width is a mean over the style letters, so it means nothing until most of them are drawn. */
private const val WIDTH_MIN_LETTERS = 6

/**
 * Every target in [model] against [drawing], in question order. A numeric feature is on target
 * inside its range and close within half a corpus interquartile range of it; a categorical one is
 * on or off. Nothing is judged before it is drawn (law 5: no number without a measurement).
 */
fun drift(
    model: BriefModel,
    drawing: Drawing,
): List<DriftItem> {
    val maker = model.atlas?.let { TargetMaker.of(it) }
    val m = drawing.measurement
    val order = Dimension.entries.flatMap { it.features }
    return model.targets.values.sortedBy { order.indexOf(it.feature) }.map { target ->
        val feature = target.feature
        val widthUnready = feature == Feature.WIDTH && WIDTH_LETTERS.count { drawing.font.drawn(it) } < WIDTH_MIN_LETTERS
        if (feature.numeric) {
            val value = feature.numericValue(m)?.takeUnless { widthUnready }
            val range = target.range
            if (value == null || range == null || maker == null) {
                DriftItem(feature, target, null, DriftStatus.UNMEASURED, drawAdvice(feature))
            } else {
                val d = maker.distance(feature, value, range)
                val status =
                    when {
                        d == 0.0 -> DriftStatus.ON
                        d <= NEAR_DISTANCE -> DriftStatus.NEAR
                        else -> DriftStatus.OFF
                    }
                val advice = if (status == DriftStatus.ON) null else numericAdvice(feature, tooHigh = value > range.endInclusive)
                DriftItem(feature, target, feature.format(value), status, advice)
            }
        } else {
            val value = feature.categoricalValue(m)
            when {
                value == null -> DriftItem(feature, target, null, DriftStatus.UNMEASURED, drawAdvice(feature))
                target.contains(value) -> DriftItem(feature, target, feature.describe(value), DriftStatus.ON, null)
                else -> DriftItem(feature, target, feature.describe(value), DriftStatus.OFF, categoricalAdvice(feature, value, target))
            }
        }
    }
}

private val WIDTH_LETTERS = Feature.WIDTH.glyphs.filter { it != ' ' }

private fun drawAdvice(feature: Feature): String {
    val letters = feature.glyphs.split(' ')
    return if (letters.size == 1) "Draw ${letters.single()} to measure it." else "Draw ${joinWords(letters)} to measure it."
}

/** What to change when a measured feature sits above ([tooHigh]) or below its target. */
private fun numericAdvice(
    feature: Feature,
    tooHigh: Boolean,
): String =
    when (feature) {
        Feature.CONTRAST -> {
            if (tooHigh) {
                "Thicken the thin parts of o, its top and bottom, toward the weight of its sides."
            } else {
                "Thin the top and bottom of o, and the joins where curves meet stems; keep the stems as they are."
            }
        }

        Feature.STRESS -> {
            if (tooHigh) {
                "Stand the stress up: move the thickest parts of o to its sides, level with each other."
            } else {
                "Tilt the stress: move the thickest parts of o toward eight and two o'clock, as a broad pen held at an angle would."
            }
        }

        Feature.BRACKET -> {
            if (tooHigh) {
                "Let the serifs meet the stem more abruptly, with a smaller curve."
            } else {
                "Let the serifs flow into the stem with a curved bracket."
            }
        }

        Feature.APERTURE -> {
            if (tooHigh) {
                "Close c, e and s a little: bring their ends toward each other."
            } else {
                "Open c, e and s: cut their ends back so the gap widens."
            }
        }

        Feature.ROUNDNESS -> {
            if (tooHigh) {
                "Round the o out: let its corners go."
            } else {
                "Square the o up: flatten its sides and tighten its corners."
            }
        }

        Feature.X_HEIGHT -> {
            if (tooHigh) "Lower the x-height: bring the tops of x, n and o down." else "Raise the x-height: lift the tops of x, n and o."
        }

        Feature.WIDTH -> {
            if (tooHigh) "Narrow the letters, starting from o and n." else "Widen the letters, starting from o and n."
        }

        Feature.WEIGHT -> {
            if (tooHigh) {
                "Thin the stems of H, then bring the other letters to match."
            } else {
                "Thicken the stems of H, then bring the other letters to match."
            }
        }

        Feature.WAIST -> {
            if (tooHigh) "Lower the bar of H toward where the brief puts it." else "Raise the bar of H toward where the brief puts it."
        }

        Feature.SERIF, Feature.A_STOREYS, Feature.G_STOREYS, Feature.TERMINAL -> {
            ""
        }
    }

private fun categoricalAdvice(
    feature: Feature,
    value: String,
    target: Target,
): String {
    val want = target.values.joinToString(" or ") { feature.describe(it) }
    return when (feature) {
        Feature.SERIF -> {
            if (value ==
                "yes"
            ) {
                "Your T has serifs; the brief is sans-serif."
            } else {
                "Your T has no serifs; the brief asks for them."
            }
        }

        Feature.A_STOREYS -> {
            "Your a is ${feature.describe(value)}; the brief asks for $want."
        }

        Feature.G_STOREYS -> {
            "Your g is ${feature.describe(value)}; the brief asks for $want."
        }

        Feature.TERMINAL -> {
            "Your c ends ${feature.describe(value)}; the brief asks for $want terminals."
        }

        else -> {
            "Measured ${feature.describe(value)}; the brief asks for $want."
        }
    }
}
