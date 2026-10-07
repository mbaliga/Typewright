// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The style atlas (data/style-atlas-latin.json): every style feature the detector measures, read
 * off professional fonts by the detector itself, grouped by genre and by Google's feeling tags.
 * It is to the Brief what the node-economy pack is to Economy: the measured distribution a
 * target range comes from, so no range the app judges a drawing by is typed in by hand
 * (CLAUDE.md law 5). Built by data/scripts/fetch_style_atlas_fonts.py and
 * StyleAtlasGeneratorTest; never hand-edited.
 *
 * Feature keys ([numeric], [categorical]) are the pack's own: contrast, stress (degrees from
 * vertical, unsigned), bracket, aperture, roundness, xHeight, width, stem, crossbar; serif
 * (yes/no), aStoreys and gStoreys (single/double), terminal (flat/round/angled).
 *
 * [faces] are measured at their Regular instance with overlaps removed. [bundled] holds the
 * Brief's own cue faces (data/brief-cues), measured file for file as the app renders them, keyed
 * by cue key, so the face shown for an answer is known to have that answer.
 */
@Serializable
data class StyleAtlasPack(
    val source: String,
    val version: Int = 1,
    val numeric: List<String> = emptyList(),
    val categorical: List<String> = emptyList(),
    val classes: Map<String, AtlasGroup> = emptyMap(),
    val feelings: Map<String, AtlasFeeling> = emptyMap(),
    val faces: Map<String, AtlasFace> = emptyMap(),
    val bundled: Map<String, AtlasFace> = emptyMap(),
)

/**
 * One genre: its member faces, the quartiles of each numeric feature over them, and the count of
 * each categorical value. [corpus] is true for the ten node-economy classes, which also have
 * Economy boxes; the other genres have feature distributions only.
 */
@Serializable
data class AtlasGroup(
    val corpus: Boolean = false,
    val faces: List<String> = emptyList(),
    val n: Int = 0,
    val dist: Map<String, Quartiles> = emptyMap(),
    val share: Map<String, Map<String, Int>> = emptyMap(),
)

/** One face behind a feeling, with Google's 0-100 score for that feeling. */
@Serializable
data class AtlasFeelingFace(
    val family: String,
    val score: Double? = null,
)

/** One of Google's expressive tags: the top faces for it and their measured distributions. */
@Serializable
data class AtlasFeeling(
    val faces: List<AtlasFeelingFace> = emptyList(),
    val n: Int = 0,
    val dist: Map<String, Quartiles> = emptyMap(),
    val share: Map<String, Map<String, Int>> = emptyMap(),
)

/** One measured face: the genres it belongs to, Google's drawing score, and its own feature values. [family] is set on bundled cue faces, whose map key is the cue key. */
@Serializable
data class AtlasFace(
    val family: String? = null,
    val classes: List<String> = emptyList(),
    val drawing: Double? = null,
    val contrast: Double? = null,
    val stress: Double? = null,
    val bracket: Double? = null,
    val aperture: Double? = null,
    val roundness: Double? = null,
    val xHeight: Double? = null,
    val width: Double? = null,
    val stem: Double? = null,
    val crossbar: Double? = null,
    val serif: String? = null,
    val aStoreys: String? = null,
    val gStoreys: String? = null,
    val terminal: String? = null,
) {
    /** This face's value for a numeric feature key, or null when unmeasured or unknown. */
    fun numeric(key: String): Double? =
        when (key) {
            "contrast" -> contrast
            "stress" -> stress
            "bracket" -> bracket
            "aperture" -> aperture
            "roundness" -> roundness
            "xHeight" -> xHeight
            "width" -> width
            "stem" -> stem
            "crossbar" -> crossbar
            else -> null
        }

    /** This face's value for a categorical feature key, or null when unmeasured or unknown. */
    fun categorical(key: String): String? =
        when (key) {
            "serif" -> serif
            "aStoreys" -> aStoreys
            "gStoreys" -> gStoreys
            "terminal" -> terminal
            else -> null
        }
}

/** Path, relative to this module's resources root, of the style atlas pack. */
internal const val STYLE_ATLAS_RESOURCE_PATH = "typewright/corpus/style-atlas-latin.json"

private val atlasJson = Json { ignoreUnknownKeys = true }

/** Decodes a style atlas pack from its JSON text. */
fun decodeStyleAtlas(text: String): StyleAtlasPack = atlasJson.decodeFromString(StyleAtlasPack.serializer(), text)

/**
 * Loads the bundled style atlas. Like the node-economy packs it is a classpath (or Node file)
 * resource, so it loads on desktop, Android and in Node tests; in a browser it throws until the
 * web build has its own way to ship data packs. Callers treat a failure as "no measured ranges
 * on this target", never as an empty atlas.
 */
fun loadStyleAtlas(): StyleAtlas = StyleAtlas(decodeStyleAtlas(readCorpusResourceText(STYLE_ATLAS_RESOURCE_PATH)))

/**
 * Queries over a [StyleAtlasPack]: which faces belong to which genres, the values of a feature
 * over a set of faces, and quantiles of those values. Everything the Brief turns into a target
 * range goes through here, so every range is a statement about real, named fonts.
 */
class StyleAtlas(
    val pack: StyleAtlasPack,
) {
    /** Faces that belong to at least one of the ten node-economy classes: the reference population. */
    val corpusFaces: List<String> by lazy {
        val corpusKeys = pack.classes.filterValues { it.corpus }.keys
        pack.faces
            .filterValues { face -> face.classes.any { it in corpusKeys } }
            .keys
            .sorted()
    }

    /** Every genre key, corpus classes first, each group in the pack's own order. */
    val genreKeys: List<String> by lazy {
        pack.classes.entries
            .sortedBy { if (it.value.corpus) 0 else 1 }
            .map { it.key }
    }

    /** The member faces of the genres in [classKeys], without duplicates, in a stable order. */
    fun facesOf(classKeys: Collection<String>): List<String> = classKeys.flatMap { pack.classes[it]?.faces.orEmpty() }.distinct()

    /** The measured values of numeric feature [key] over [faces] (unmeasured faces are skipped). */
    fun values(
        key: String,
        faces: Collection<String>,
    ): List<Double> = faces.mapNotNull { pack.faces[it]?.numeric(key) }.filter { it.isFinite() }

    /** How many of [faces] have each value of categorical feature [key]. */
    fun counts(
        key: String,
        faces: Collection<String>,
    ): Map<String, Int> = faces.mapNotNull { pack.faces[it]?.categorical(key) }.groupingBy { it }.eachCount()

    /** Quartiles of numeric feature [key] over [faces], or null when none of them was measured. */
    fun quartiles(
        key: String,
        faces: Collection<String>,
    ): Quartiles? {
        val v = values(key, faces).sorted()
        if (v.isEmpty()) return null
        return Quartiles(v.first(), quantileOfSorted(v, 0.25), quantileOfSorted(v, 0.5), quantileOfSorted(v, 0.75), v.last(), v.size)
    }

    /** The [p] quantile (0..1) of numeric feature [key] over [faces], or null when none was measured. */
    fun quantile(
        key: String,
        faces: Collection<String>,
        p: Double,
    ): Double? {
        val v = values(key, faces).sorted()
        return if (v.isEmpty()) null else quantileOfSorted(v, p)
    }
}

/** Linear-interpolation quantile of an already sorted list, the same rule the generator scripts use. */
fun quantileOfSorted(
    sorted: List<Double>,
    p: Double,
): Double {
    require(sorted.isNotEmpty()) { "no values" }
    val k = (sorted.size - 1) * p.coerceIn(0.0, 1.0)
    val f = k.toInt()
    val c = minOf(f + 1, sorted.size - 1)
    return sorted[f] + (sorted[c] - sorted[f]) * (k - f)
}
