// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.qa.corpus.StyleAtlas
import com.asoc.typewright.qa.corpus.quantileOfSorted
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/** Where a target came from, strongest first: the person's own answer beats their references, which beat a feeling, which beats the genre. */
enum class OriginKind(
    val word: String,
) {
    ANSWER("your answer"),
    REFERENCES("your references"),
    FEELING("the feeling"),
    GENRE("the genre"),
}

/** A target's provenance in words: "your answer · Even", "the genre · Art deco", with how many measured faces stand behind it. */
data class Origin(
    val kind: OriginKind,
    val label: String,
    val faces: Int,
)

/**
 * What the brief asks of one [feature]: a measured [range] for a numeric feature, or the allowed
 * [values] of a categorical one. [firm] targets are the person's own answers; the rest are
 * defaults they can override. [level] is the answer the target stands for, when there is one.
 */
data class Target(
    val feature: Feature,
    val range: ClosedFloatingPointRange<Double>?,
    val values: Set<String>,
    val origin: Origin,
    val firm: Boolean,
    val level: Level? = null,
) {
    fun contains(value: Double): Boolean = range?.let { value in it } ?: true

    fun contains(value: String): Boolean = values.isEmpty() || value in values

    /** The target in words: "1.1 : 1 to 1.3 : 1", "two-storey". */
    fun describe(): String =
        range?.let { r -> "${feature.format(r.start)} to ${feature.format(r.endInclusive)}".let(::compactRange) }
            ?: values.joinToString(" or ") { feature.describe(it) }
}

/** "0.70 of the cap height to 0.74 of the cap height" reads better as "0.70 to 0.74 of the cap height". */
private fun compactRange(text: String): String {
    val parts = text.split(" to ")
    if (parts.size != 2) return text
    val suffixA = parts[0].substringAfter(' ', "")
    val suffixB = parts[1].substringAfter(' ', "")
    return if (suffixA.isNotEmpty() && suffixA == suffixB) "${parts[0].substringBefore(' ')} to ${parts[1]}" else text
}

/**
 * Turns answers into measured ranges over the style atlas, and back: which answer a measured
 * value corresponds to. It caches the ranges and scales it computes, so get one with [of], which
 * reuses the last maker made for the same atlas.
 */
class TargetMaker private constructor(
    val atlas: StyleAtlas,
) {
    private val allFaces: List<String> by lazy {
        atlas.pack.faces.keys
            .toList()
    }
    private val rangeCache = HashMap<Anchor, ClosedFloatingPointRange<Double>?>()
    private val scaleCache = HashMap<Feature, Double>()

    /** The faces an anchor's quantiles are taken over. */
    fun population(anchor: Anchor): List<String> =
        when (anchor) {
            is Anchor.Pooled -> atlas.facesOf(anchor.genres)
            is Anchor.Band -> if (anchor.population == Population.CORPUS) atlas.corpusFaces else allFaces
            is Anchor.Value -> emptyList()
        }

    /** The measured range an anchor stands for, or null when none of its faces measured that feature. */
    fun range(anchor: Anchor): ClosedFloatingPointRange<Double>? =
        rangeCache.getOrPut(anchor) {
            val (lo, hi) =
                when (anchor) {
                    is Anchor.Pooled -> anchor.lo to anchor.hi
                    is Anchor.Band -> anchor.lo to anchor.hi
                    is Anchor.Value -> return null
                }
            val faces = population(anchor)
            val a = atlas.quantile(anchor.feature.key, faces, lo)
            val b = atlas.quantile(anchor.feature.key, faces, hi)
            if (a == null || b == null) null else a..b
        }

    /** The targets one answer sets, firm, labelled with the answer. */
    fun targetsFor(level: Level): List<Target> =
        level.anchors.mapNotNull { anchor ->
            when (anchor) {
                is Anchor.Value -> {
                    Target(anchor.feature, null, setOf(anchor.value), Origin(OriginKind.ANSWER, level.label, 0), firm = true, level = level)
                }

                else -> {
                    range(anchor)?.let {
                        Target(
                            anchor.feature,
                            it,
                            emptySet(),
                            Origin(OriginKind.ANSWER, level.label, population(anchor).size),
                            firm = true,
                            level = level,
                        )
                    }
                }
            }
        }

    /**
     * The answer whose measured band of [feature] holds [value] (the narrowest when bands overlap),
     * or the nearest band's. For the serif bracket that is one of the three serif answers.
     */
    fun levelHolding(
        feature: Feature,
        value: Double,
    ): Level? {
        val candidates =
            Level.entries.mapNotNull { level ->
                val anchor = level.anchors.firstOrNull { it.feature == feature && it !is Anchor.Value } ?: return@mapNotNull null
                range(anchor)?.let { level to it }
            }
        if (candidates.isEmpty()) return null
        val holding = candidates.filter { value in it.second }
        if (holding.isNotEmpty()) return holding.minBy { it.second.endInclusive - it.second.start }.first
        return candidates.minBy { (_, r) -> distance(feature, value, r) }.first
    }

    /**
     * The answer to [dimension] that a set of measured values stands for: what a face (or a
     * drawing) would have answered. Null when the values needed are unmeasured, and for stress
     * when the stroke has too little contrast for its angle to mean anything.
     */
    fun levelFor(
        dimension: Dimension,
        numeric: (Feature) -> Double?,
        categorical: (Feature) -> String?,
    ): Level? =
        when (dimension) {
            Dimension.SERIFS -> {
                when (categorical(Feature.SERIF)) {
                    "no" -> Level.SERIFS_NONE
                    "yes" -> serifLevel(numeric(Feature.BRACKET), numeric(Feature.CONTRAST))
                    else -> null
                }
            }

            Dimension.STRESS -> {
                val contrast = numeric(Feature.CONTRAST)
                if (contrast == null ||
                    contrast < STRESS_MIN_CONTRAST
                ) {
                    null
                } else {
                    numeric(Feature.STRESS)?.let { levelHolding(Feature.STRESS, it) }
                }
            }

            else -> {
                val feature = dimension.features.first()
                if (feature.numeric) {
                    numeric(
                        feature,
                    )?.let { levelHolding(feature, it) }
                } else {
                    categorical(feature)?.let { levelWithValue(feature, it) }
                }
            }
        }

    /** The answer that means exactly [value] for a categorical [feature], or null when several answers share it (serif "yes"). */
    fun levelWithValue(
        feature: Feature,
        value: String,
    ): Level? {
        val matches =
            Level.entries.filter { level ->
                level.anchors.any { it is Anchor.Value && it.feature == feature && it.value == value }
            }
        return matches.singleOrNull()
    }

    /**
     * Which serif a face has. A curved bracket at least as full as the bracketed genres' lower
     * quartile makes it bracketed; unbracketed serifs are hairlines on a contrasted stroke (as
     * strong as the transitional and old-style faces' lower quartile) and slabs on an even one.
     * The bracket alone can't tell those two apart: both measure near zero.
     */
    private fun serifLevel(
        bracket: Double?,
        contrast: Double?,
    ): Level? {
        val bracketedFrom =
            Level.SERIFS_BRACKETED.anchors
                .firstOrNull { it.feature == Feature.BRACKET }
                ?.let { range(it)?.start }
        val contrastedFrom =
            Level.CONTRAST_STRONG.anchors
                .firstOrNull()
                ?.let { range(it)?.start }
        return when {
            bracket != null && bracketedFrom != null && bracket >= bracketedFrom -> Level.SERIFS_BRACKETED
            contrast != null && contrastedFrom != null -> if (contrast >= contrastedFrom) Level.SERIFS_HAIRLINE else Level.SERIFS_SLAB
            bracket != null -> levelHolding(Feature.BRACKET, bracket)
            else -> null
        }
    }

    /**
     * The yardstick for distances on [feature]: the corpus's middle half (on a log scale for
     * contrast, which is a ratio). A categorical mismatch always counts 1.
     */
    fun scale(feature: Feature): Double =
        scaleCache.getOrPut(feature) {
            if (!feature.numeric) return 1.0
            val values = atlas.values(feature.key, atlas.corpusFaces).map { transform(feature, it) }.sorted()
            if (values.size < 4) return 1.0
            max(quantileOfSorted(values, 0.75) - quantileOfSorted(values, 0.25), 1e-6)
        }

    /** How far [value] sits outside [range], in [scale] units of [feature] (0 inside). */
    fun distance(
        feature: Feature,
        value: Double,
        range: ClosedFloatingPointRange<Double>,
    ): Double {
        val v = transform(feature, value)
        val lo = transform(feature, range.start)
        val hi = transform(feature, range.endInclusive)
        val outside =
            when {
                v < lo -> lo - v
                v > hi -> v - hi
                else -> 0.0
            }
        return outside / scale(feature)
    }

    private fun transform(
        feature: Feature,
        value: Double,
    ): Double = if (feature == Feature.CONTRAST) ln(max(value, 1e-6)) else value

    companion object {
        private var last: TargetMaker? = null

        /** The maker for [atlas]: the last one made when it was for the same atlas, otherwise a new one. */
        fun of(atlas: StyleAtlas): TargetMaker {
            last?.let { if (it.atlas === atlas) return it }
            return TargetMaker(atlas).also { last = it }
        }
    }
}

/** Below this contrast an o's thickest diameter is noise, so its stress angle means nothing. */
internal const val STRESS_MIN_CONTRAST = 1.5

/** Whether a categorical value in [values] counts as part of a genre: at least this share of its faces. */
internal const val GENRE_VALUE_SHARE = 0.25

/** How much a reference consensus range is padded on each side, in [TargetMaker.scale] units. */
internal const val REFERENCE_PAD = 0.25

internal fun ClosedFloatingPointRange<Double>.overlaps(other: ClosedFloatingPointRange<Double>): Boolean =
    start <= other.endInclusive && other.start <= endInclusive

internal fun Double.near(
    other: Double,
    tolerance: Double,
): Boolean = abs(this - other) <= tolerance
