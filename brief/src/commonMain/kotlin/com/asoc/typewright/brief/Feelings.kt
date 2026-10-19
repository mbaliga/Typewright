// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.qa.corpus.StyleAtlas
import kotlin.math.abs

/**
 * One of Google Fonts' feeling tags. The words are Google's; what each one looks like is measured
 * (the style atlas holds the top-scored faces for every feeling), never asserted. [adjective] is
 * the word as it reads before "typeface".
 */
data class Feeling(
    val key: String,
    val word: String,
    val gloss: String,
    val adjective: String = word.lowercase(),
)

/** Google Fonts' twenty feelings, in a quieter-to-louder order for the picker. */
val FEELINGS: List<Feeling> =
    listOf(
        Feeling("calm", "Calm", "quiet, unhurried"),
        Feeling("sincere", "Sincere", "warm, honest"),
        Feeling("business", "Business", "professional, plain", "businesslike"),
        Feeling("competent", "Competent", "capable, assured"),
        Feeling("sophisticated", "Sophisticated", "refined, worldly"),
        Feeling("fancy", "Fancy", "ornate, special"),
        Feeling("vintage", "Vintage", "of another time"),
        Feeling("artistic", "Artistic", "expressive, made"),
        Feeling("stiff", "Stiff", "rigid, formal"),
        Feeling("futuristic", "Futuristic", "technical, ahead"),
        Feeling("innovative", "Innovative", "new, inventive"),
        Feeling("rugged", "Rugged", "tough, outdoors"),
        Feeling("active", "Active", "moving, sporty"),
        Feeling("happy", "Happy", "light, cheerful"),
        Feeling("playful", "Playful", "having fun"),
        Feeling("cute", "Cute", "sweet, soft"),
        Feeling("childlike", "Childlike", "simple, young"),
        Feeling("excited", "Excited", "energetic, bright"),
        Feeling("loud", "Loud", "big, insistent"),
        Feeling("awkward", "Awkward", "odd on purpose"),
    )

/** The feeling with this key, or null. */
fun feelingByKey(key: String): Feeling? = FEELINGS.firstOrNull { it.key == key }

/**
 * Which way a feeling's faces lean on one numeric feature: their median against every corpus
 * face's, in units of the corpus's middle half ([effect]: +1 means one interquartile range above).
 * [level] is the answer whose band holds that median, when one does.
 */
data class Tendency(
    val feature: Feature,
    val effect: Double,
    val median: Double,
    val level: Level?,
)

/** A categorical leaning: [share] of the feeling's measured faces have [value]. */
data class Leaning(
    val feature: Feature,
    val value: String,
    val share: Double,
    val level: Level?,
)

/** What a feeling looks like, measured: the faces behind it and how they lean. */
data class FeelingProfile(
    val feeling: Feeling,
    val faces: List<String>,
    val tendencies: List<Tendency>,
    val leanings: List<Leaning>,
)

/** How far (in corpus interquartile ranges) a feeling's median must sit from the corpus median to count as a tendency. */
internal const val TENDENCY_THRESHOLD = 0.75

/** How much of a feeling's faces must share a categorical value to count as a leaning. */
internal const val LEANING_THRESHOLD = 0.6

/** Measures [feeling] in [atlas]: its tendencies, strongest first, and its leanings. */
fun profileOf(
    feeling: Feeling,
    atlas: StyleAtlas,
    targets: TargetMaker = TargetMaker.of(atlas),
): FeelingProfile? {
    val group = atlas.pack.feelings[feeling.key] ?: return null
    val faces = group.faces.map { it.family }
    val corpus = atlas.corpusFaces
    val tendencies =
        Feature.entries
            .filter { it.numeric && it != Feature.BRACKET && it != Feature.STRESS }
            .mapNotNull { feature ->
                val median = atlas.quantile(feature.key, faces, 0.5) ?: return@mapNotNull null
                val q = atlas.quartiles(feature.key, corpus) ?: return@mapNotNull null
                val iqr = (q.q3 - q.q1).takeIf { it > 1e-9 } ?: return@mapNotNull null
                val effect = (median - q.med) / iqr
                if (abs(effect) < TENDENCY_THRESHOLD) return@mapNotNull null
                Tendency(feature, effect, median, targets.levelHolding(feature, median))
            }.sortedByDescending { abs(it.effect) }
    val leanings =
        listOf(Feature.SERIF, Feature.TERMINAL, Feature.A_STOREYS, Feature.G_STOREYS).mapNotNull { feature ->
            val counts = atlas.counts(feature.key, faces)
            val total = counts.values.sum()
            if (total == 0) return@mapNotNull null
            val (value, n) = counts.maxBy { it.value }
            val share = n.toDouble() / total
            if (share < LEANING_THRESHOLD) return@mapNotNull null
            Leaning(feature, value, share, targets.levelWithValue(feature, value))
        }
    return FeelingProfile(feeling, faces, tendencies, leanings)
}

/** What this feeling's faces have in common, in the brief's own words, strongest first: "wide, heavy, a large x-height". */
val FeelingProfile.phrases: List<String>
    get() = (tendencies.mapNotNull { it.level?.phrase } + leanings.mapNotNull { it.level?.phrase }).distinct()
