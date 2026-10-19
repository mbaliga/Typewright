// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.project.Ethos
import com.asoc.typewright.qa.corpus.StyleAtlas
import kotlin.math.max
import kotlin.math.min

/**
 * Everything the app understands from a person's [ethos]: the genre and uses they named, what
 * their feelings look like measured, a target for every feature something has spoken to, and the
 * places where their choices pull against each other ([tensions]). Recomputed whenever the ethos
 * or the atlas changes; never stored.
 *
 * [atlas] is null where the style atlas can't load (the web preview today): the model still
 * knows the person's answers and their uses, but holds no measured ranges, and says so.
 */
data class BriefModel(
    val ethos: Ethos,
    val genre: Genre?,
    val uses: List<UseCase>,
    val feelings: List<FeelingProfile>,
    val targets: Map<Feature, Target>,
    val tensions: List<Tension>,
    val referenceSpread: Map<Feature, List<Pair<String, String>>>,
    val atlas: StyleAtlas?,
) {
    /** The answer the person gave for [dimension], or null. */
    fun answer(dimension: Dimension): Level? = ethos.levels[dimension.id]?.let { Level.of(dimension, it) }

    /** True when the person has said nothing yet. */
    val isEmpty: Boolean get() = ethos.isEmpty

    /** The dimensions the person marked as deliberate departures. */
    val signatures: Set<Dimension> get() = ethos.signatures.mapNotNull { Dimension.byId(it) }.toSet()

    /** The ethos in one sentence: "An art deco typeface for watch faces: monoline, squarish rounds and narrow." */
    val sentence: String get() = composeSentence(this)
}

/** Builds the [BriefModel] for [ethos] over [atlas] (null when the atlas could not load). */
fun resolveBrief(
    ethos: Ethos,
    atlas: StyleAtlas?,
): BriefModel {
    val genre = genreByKey(ethos.genre)
    val uses = ethos.uses.mapNotNull { useById(it) }
    val maker = atlas?.let { TargetMaker.of(it) }
    val feelings =
        if (atlas == null ||
            maker == null
        ) {
            emptyList()
        } else {
            ethos.feelings.mapNotNull { key -> feelingByKey(key)?.let { profileOf(it, atlas, maker) } }
        }
    val targets = linkedMapOf<Feature, Target>()
    val spread = linkedMapOf<Feature, List<Pair<String, String>>>()

    // 1. The person's own answers: firm.
    for ((dimId, levelId) in ethos.levels) {
        val dimension = Dimension.byId(dimId) ?: continue
        val level = Level.of(dimension, levelId) ?: continue
        if (maker == null) {
            level.anchors.filterIsInstance<Anchor.Value>().forEach { a ->
                targets[a.feature] =
                    Target(a.feature, null, setOf(a.value), Origin(OriginKind.ANSWER, level.label, 0), firm = true, level = level)
            }
        } else {
            maker.targetsFor(level).forEach { targets[it.feature] = it }
        }
    }

    if (atlas != null && maker != null) {
        // 2. References: what the admired faces share.
        val refs = ethos.references.filter { it in atlas.pack.faces }
        if (refs.isNotEmpty()) {
            for (feature in Feature.entries) {
                if (feature in targets) continue
                val label = refs.joinToString(", ")
                if (feature.numeric) {
                    val values =
                        refs.mapNotNull { ref ->
                            atlas.pack.faces[ref]
                                ?.let { feature.numericValue(it) }
                                ?.let { ref to it }
                        }
                    if (values.isEmpty()) continue
                    val lo = values.minOf { it.second }
                    val hi = values.maxOf { it.second }
                    if (values.size > 1 && maker.distance(feature, hi, lo..lo) > 1.5) {
                        spread[feature] = values.map { it.first to feature.format(it.second) }
                    }
                    val pad = REFERENCE_PAD * maker.scale(feature)
                    val range =
                        if (feature == Feature.CONTRAST) {
                            (lo / kotlin.math.exp(pad))..(hi * kotlin.math.exp(pad))
                        } else {
                            (lo - pad)..(hi + pad)
                        }
                    targets[feature] =
                        Target(
                            feature,
                            range,
                            emptySet(),
                            Origin(OriginKind.REFERENCES, label, values.size),
                            firm = false,
                            level =
                                maker.levelHolding(
                                    feature,
                                    (lo + hi) / 2,
                                ),
                        )
                } else {
                    val values =
                        refs.mapNotNull { ref ->
                            atlas.pack.faces[ref]
                                ?.let { feature.categoricalValue(it) }
                                ?.let { ref to it }
                        }
                    if (values.isEmpty()) continue
                    val distinct = values.map { it.second }.toSet()
                    if (distinct.size > 1) spread[feature] = values.map { it.first to feature.describe(it.second) }
                    targets[feature] =
                        Target(
                            feature,
                            null,
                            distinct,
                            Origin(OriginKind.REFERENCES, label, values.size),
                            firm = false,
                            level = distinct.singleOrNull()?.let { maker.levelWithValue(feature, it) },
                        )
                }
            }
        }

        // 3. Feelings: where the measured faces of every chosen feeling lean the same way.
        for (feature in Feature.entries) {
            if (feature in targets) continue
            val pulls = feelings.mapNotNull { p -> p.tendencies.firstOrNull { it.feature == feature }?.let { p.feeling to it } }
            if (pulls.isNotEmpty() && (pulls.all { it.second.effect > 0 } || pulls.all { it.second.effect < 0 })) {
                val strongest = pulls.maxBy { kotlin.math.abs(it.second.effect) }
                val level = strongest.second.level ?: continue
                val anchor = level.anchors.firstOrNull { it.feature == feature } ?: continue
                val range = maker.range(anchor) ?: continue
                targets[feature] =
                    Target(
                        feature,
                        range,
                        emptySet(),
                        Origin(
                            OriginKind.FEELING,
                            pulls.joinToString(", ") { it.first.word },
                            atlas.pack.feelings[strongest.first.key]?.n ?: 0,
                        ),
                        firm = false,
                        level = level,
                    )
            }
            val leans = feelings.mapNotNull { p -> p.leanings.firstOrNull { it.feature == feature }?.let { p.feeling to it } }
            if (feature !in targets && leans.isNotEmpty() && leans.map { it.second.value }.toSet().size == 1) {
                val lean = leans.first().second
                targets[feature] =
                    Target(
                        feature,
                        null,
                        setOf(lean.value),
                        Origin(
                            OriginKind.FEELING,
                            leans.joinToString(", ") {
                                it.first.word
                            },
                            0,
                        ),
                        firm = false,
                        level = lean.level,
                    )
            }
        }

        // 4. The genre: its own faces' middle half, and the values a quarter of them share.
        if (genre != null) {
            val group = atlas.pack.classes[genre.key]
            val faces = group?.faces.orEmpty()
            for (feature in Feature.entries) {
                if (feature in targets || faces.isEmpty()) continue
                if (feature.numeric) {
                    val q = atlas.quartiles(feature.key, faces) ?: continue
                    targets[feature] =
                        Target(
                            feature,
                            q.q1..q.q3,
                            emptySet(),
                            Origin(OriginKind.GENRE, genre.name, q.n),
                            firm = false,
                            level = maker.levelHolding(feature, q.med),
                        )
                } else {
                    val counts = atlas.counts(feature.key, faces)
                    val total = counts.values.sum()
                    if (total == 0) continue
                    val values = counts.filter { it.value.toDouble() / total >= GENRE_VALUE_SHARE }.keys
                    if (values.isEmpty()) continue
                    targets[feature] =
                        Target(
                            feature,
                            null,
                            values,
                            Origin(OriginKind.GENRE, genre.name, total),
                            firm = false,
                            level = values.singleOrNull()?.let { maker.levelWithValue(feature, it) },
                        )
                }
            }
        }
    }

    // Stress only means something on a stroke with contrast, and a bracket only on a serif.
    targets[Feature.CONTRAST]?.range?.let {
        if (it.endInclusive < STRESS_MIN_CONTRAST &&
            targets[Feature.STRESS]?.firm != true
        ) {
            targets.remove(Feature.STRESS)
        }
    }
    targets[Feature.SERIF]?.values?.let { if (it == setOf("no")) targets.remove(Feature.BRACKET) }

    val model = BriefModel(ethos, genre, uses, feelings, targets, emptyList(), spread, atlas)
    return model.copy(tensions = findTensions(model))
}

/** The overlap of two ranges, or null when they don't meet. */
internal fun ClosedFloatingPointRange<Double>.intersect(other: ClosedFloatingPointRange<Double>): ClosedFloatingPointRange<Double>? {
    val lo = max(start, other.start)
    val hi = min(endInclusive, other.endInclusive)
    return if (lo <= hi) lo..hi else null
}
