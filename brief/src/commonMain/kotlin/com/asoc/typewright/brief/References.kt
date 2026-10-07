// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.qa.corpus.AtlasFace
import com.asoc.typewright.qa.corpus.StyleAtlas
import com.asoc.typewright.qa.corpus.style.Storeys
import com.asoc.typewright.qa.corpus.style.StyleMeasurement
import com.asoc.typewright.qa.corpus.style.TerminalStyle
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** A measured professional face and how far it sits from the brief: [misses] are the targets it falls outside. */
data class FaceMatch(
    val family: String,
    val distance: Double,
    val misses: List<Feature>,
    val classes: List<String>,
)

/**
 * Atlas faces that already fit the brief's targets, closest first: references to study and
 * comparison fonts for Overlay (fetched only when the person asks, law 3). Firm targets count
 * double. With a genre chosen only its faces are searched, unless [anyGenre]. Faces the person
 * already named are left out. Empty without the atlas or targets.
 */
fun facesLike(
    model: BriefModel,
    limit: Int = 6,
    anyGenre: Boolean = false,
): List<FaceMatch> {
    val atlas = model.atlas ?: return emptyList()
    if (model.targets.isEmpty()) return emptyList()
    val maker = TargetMaker.of(atlas)
    val genreFaces =
        model.genre?.takeUnless { anyGenre }?.let {
            atlas.pack.classes[it.key]
                ?.faces
                ?.toSet()
        }
    return atlas.pack.faces
        .filterKeys { it !in model.ethos.references && (genreFaces == null || it in genreFaces) }
        .mapNotNull { (family, face) -> matchFace(family, face, model.targets.values, maker) }
        .sortedWith(compareBy<FaceMatch> { it.distance }.thenBy { it.family })
        .take(limit)
}

private fun matchFace(
    family: String,
    face: AtlasFace,
    targets: Collection<Target>,
    maker: TargetMaker,
): FaceMatch? {
    var total = 0.0
    var weights = 0.0
    val misses = mutableListOf<Feature>()
    for (target in targets) {
        val w = if (target.firm) 2.0 else 1.0
        val d =
            if (target.feature.numeric) {
                val value = target.feature.numericValue(face) ?: continue
                val range = target.range ?: continue
                maker.distance(target.feature, value, range)
            } else {
                val value = target.feature.categoricalValue(face) ?: continue
                if (target.contains(value)) 0.0 else 1.0
            }
        if (d > NEAR_DISTANCE) misses += target.feature
        total += w * min(d, DISTANCE_CAP)
        weights += w
    }
    if (weights == 0.0) return null
    return FaceMatch(family, total / weights, misses, face.classes)
}

/** One genre's share of a reading: how many of the nearest faces belong to it, and which. */
data class GenreVote(
    val genre: Genre,
    val votes: Int,
    val faces: List<String>,
)

/**
 * What a drawing reads as: the genres of the [k] measured faces nearest to it, most votes first,
 * read from [features] measured features. Few features make a weak reading, and the UI says so.
 */
data class Reading(
    val genres: List<GenreVote>,
    val nearest: List<String>,
    val k: Int,
    val features: Int,
)

/** How many neighbours a reading polls. */
const val READING_K = 9

/** Fewer measured features than this and there is nothing to read. */
const val READING_MIN_FEATURES = 3

/**
 * Reads [measurement] (the person's own letters, or any font) against every face in the atlas:
 * a k-nearest-neighbour vote over the measured features, each scaled by the corpus's middle half
 * (contrast on a log scale). Each atlas face read against all the others puts its own genre
 * first about half the time and in the top three five times in six among the ten corpus classes;
 * across all the atlas's genres, two times in five and seven in ten (`ReadingAccuracyTest`). So a
 * reading is always shown as votes, never as a verdict. Null when fewer than
 * [READING_MIN_FEATURES] features were measured. [exclude] leaves one face out of the vote.
 */
fun readAs(
    measurement: StyleMeasurement,
    atlas: StyleAtlas,
    k: Int = READING_K,
    exclude: String? = null,
): Reading? {
    val measured = Feature.entries.count { it.numericValue(measurement) != null || it.categoricalValue(measurement) != null }
    if (measured < READING_MIN_FEATURES) return null
    val scales = readingScales(atlas)
    val nearest =
        atlas.pack.faces
            .filter { (family, face) -> family != exclude && face.classes.isNotEmpty() }
            .mapNotNull { (family, face) -> readingDistance(measurement, face, scales)?.let { family to it } }
            .sortedWith(compareBy<Pair<String, Double>> { it.second }.thenBy { it.first })
            .take(k)
    if (nearest.isEmpty()) return null
    val votes = mutableMapOf<String, Double>()
    val members = mutableMapOf<String, MutableList<String>>()
    for ((family, d) in nearest) {
        for (genre in atlas.pack.faces
            .getValue(family)
            .classes) {
            votes[genre] = (votes[genre] ?: 0.0) + 1.0 / (1.0 + d)
            members.getOrPut(genre) { mutableListOf() } += family
        }
    }
    val genres =
        votes.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .mapNotNull { (key, _) -> genreByKey(key)?.let { GenreVote(it, members.getValue(key).size, members.getValue(key)) } }
    return Reading(genres, nearest.map { it.first }, nearest.size, measured)
}

/** Feature weights for a reading: how much each feature says about genre, tuned left-one-out on the atlas. */
private val READING_WEIGHTS: Map<Feature, Double> =
    mapOf(
        Feature.CONTRAST to 1.5,
        Feature.STRESS to 0.5,
        Feature.SERIF to 1.5,
        Feature.BRACKET to 1.0,
        Feature.APERTURE to 1.0,
        Feature.A_STOREYS to 2.0,
        Feature.G_STOREYS to 1.0,
        Feature.TERMINAL to 0.25,
        Feature.ROUNDNESS to 1.0,
        Feature.X_HEIGHT to 1.0,
        Feature.WIDTH to 1.0,
        Feature.WEIGHT to 0.5,
        Feature.WAIST to 0.25,
    )

/** A squared, scaled difference counts at most this much, so one wild feature can't decide a reading. */
private const val READING_CAP = 9.0

private fun readingScales(atlas: StyleAtlas): Map<Feature, Double> {
    val maker = TargetMaker.of(atlas)
    return Feature.entries.filter { it.numeric }.associateWith { maker.scale(it) }
}

private fun readingDistance(
    m: StyleMeasurement,
    face: AtlasFace,
    scales: Map<Feature, Double>,
): Double? {
    var total = 0.0
    var weights = 0.0
    for ((feature, w) in READING_WEIGHTS) {
        if (feature.numeric) {
            val a = feature.numericValue(m) ?: continue
            val b = feature.numericValue(face) ?: continue
            val d = (readingTransform(feature, a) - readingTransform(feature, b)) / scales.getValue(feature)
            total += w * min(d * d, READING_CAP)
        } else {
            val a = feature.categoricalValue(m) ?: continue
            val b = feature.categoricalValue(face) ?: continue
            total += if (a == b) 0.0 else w
        }
        weights += w
    }
    return if (weights == 0.0) null else total / weights
}

private fun readingTransform(
    feature: Feature,
    value: Double,
): Double = if (feature == Feature.CONTRAST) ln(max(value, 1e-6)) else value

/** A face's values as a [StyleMeasurement], so a professional font can be read the same way as a drawing. */
fun AtlasFace.asMeasurement(): StyleMeasurement =
    StyleMeasurement(
        contrastRatio = contrast,
        stressAngleDegrees = stress,
        hasSerif = serif?.let { it == "yes" },
        bracketScore = bracket,
        storeys = Storeys.UNKNOWN,
        terminalStyle = terminalStyleOf(terminal),
        apertureOpenness = aperture,
        oRoundnessExponent = roundness,
        xHeightToCapHeightRatio = xHeight,
        widthClass = width,
        stemToCapHeight = stem,
        crossbarHeight = crossbar,
        aStoreys = storeysOf(aStoreys),
        gStoreys = storeysOf(gStoreys),
    )

private fun storeysOf(value: String?): Storeys =
    when (value) {
        "single" -> Storeys.SINGLE
        "double" -> Storeys.DOUBLE
        else -> Storeys.UNKNOWN
    }

private fun terminalStyleOf(value: String?): TerminalStyle =
    when (value) {
        "flat" -> TerminalStyle.FLAT
        "round" -> TerminalStyle.ROUND
        "angled" -> TerminalStyle.ANGLED
        else -> TerminalStyle.UNKNOWN
    }

/** A distance at or under this (in scale units) counts as near enough. */
internal const val NEAR_DISTANCE = 0.5

/** A single target's distance counts at most this much in a face match. */
private const val DISTANCE_CAP = 3.0

/** Measured faces whose name contains [query] (ignoring case), names starting with it first; the reference picker's search. */
fun searchFaces(
    atlas: StyleAtlas,
    query: String,
    limit: Int = 20,
): List<String> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return atlas.pack.faces.keys
        .filter { it.lowercase().contains(q) }
        .sortedWith(compareBy<String> { if (it.lowercase().startsWith(q)) 0 else 1 }.thenBy { it })
        .take(limit)
}

/**
 * A measured face in the brief's own words, as the answers it would give: "high-contrast,
 * hairline serifs, a two-storey a". [dimensions] picks and orders the answers shown.
 */
fun describeFace(
    face: AtlasFace,
    atlas: StyleAtlas,
    dimensions: List<Dimension> =
        listOf(Dimension.CONTRAST, Dimension.SERIFS, Dimension.WIDTH, Dimension.ROUNDNESS, Dimension.A_FORM, Dimension.WAIST),
): String {
    val maker = TargetMaker.of(atlas)
    val phrases =
        dimensions.mapNotNull { d ->
            maker.levelFor(d, { f -> f.numericValue(face) }, { f -> f.categoricalValue(face) })?.phrase
        }
    return joinWords(phrases)
}
