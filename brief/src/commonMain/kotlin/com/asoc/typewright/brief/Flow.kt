// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.qa.corpus.AtlasFace
import com.asoc.typewright.qa.corpus.StyleAtlas
import com.asoc.typewright.qa.corpus.style.STYLE_MEASUREMENT_GLYPHS
import com.asoc.typewright.qa.corpus.style.StyleMeasurement
import kotlin.math.abs

/**
 * The ways into the Brief's questions, by what a person already knows. Every door ends at the
 * same brief; they differ in what is asked first and what the later questions are drawn from.
 */
enum class Door(
    val id: String,
    val label: String,
    val caption: String,
    val cue: Cue,
) {
    STYLE("style", "I know the style", "Deco, grotesk, a didone", Cue(CueFace.DECO, "Aa")),
    USE("use", "I know what it's for", "A watch face, an app, a sign", Cue(CueFace.SUPERELLIPSE, "10:08", tabular = true)),
    REFERENCES("references", "I know fonts I like", "Up to three, by name", Cue(CueFace.GARALDE, "Aa")),
    FEELING("feeling", "I know the feeling", "Calm, loud, vintage", Cue(CueFace.SCRIPT, "Aa")),
    MIRROR("mirror", "I've drawn some letters", "Read what they already say", Cue(CueFace.GROTESQUE, "Aa")),
    BLANK("blank", "I don't know yet", "Build it from first questions", Cue(CueFace.GEOMETRIC, "Aa")),
    ;

    companion object {
        /** The door with this id, or null. */
        fun byId(id: String?): Door? = entries.firstOrNull { it.id == id }
    }
}

/** Step ids recorded in [com.asoc.typewright.project.Ethos.answered]. */
const val STEP_GENRE = "genre"
const val STEP_USES = "uses"
const val STEP_FEELINGS = "feelings"
const val STEP_REFERENCES = "references"
const val STEP_MIRROR = "mirror"
const val STEP_WORDMARK = "wordmark"

/** The step id of a dimension's question. */
fun dimensionStepId(dimension: Dimension): String = "dim.${dimension.id}"

/** The step id of a use's own question. */
fun useStepId(
    use: UseCase,
    question: UseQuestion,
): String = "use.${use.id}.${question.id}"

/** A genre offered to a person, and why, in words. */
data class GenreSuggestion(
    val genre: Genre,
    val reason: String,
)

/** One question of the Brief. [id] is what [com.asoc.typewright.project.Ethos.answered] records once it is answered or skipped. */
sealed interface Step {
    val id: String

    /** Pick a genre; [suggestions] come first, then every genre by shelf. */
    data class PickGenre(
        val suggestions: List<GenreSuggestion>,
    ) : Step {
        override val id: String get() = STEP_GENRE
    }

    /** Pick what the font is for. */
    data object PickUses : Step {
        override val id: String get() = STEP_USES
    }

    /** One of a use's own questions. */
    data class AskUse(
        val use: UseCase,
        val question: UseQuestion,
    ) : Step {
        override val id: String get() = useStepId(use, question)
    }

    /** The letters of the wordmark. */
    data object EnterWordmark : Step {
        override val id: String get() = STEP_WORDMARK
    }

    /** Pick up to three feelings; [profiles] say what the chosen ones look like, measured. */
    data class PickFeelings(
        val profiles: List<FeelingProfile>,
    ) : Step {
        override val id: String get() = STEP_FEELINGS
    }

    /** Name up to three admired faces; [suggestions] are measured faces that fit the brief so far. */
    data class PickReferences(
        val suggestions: List<FaceMatch>,
    ) : Step {
        override val id: String get() = STEP_REFERENCES
    }

    /**
     * What the person's own letters already say: the genres they read as, and the answer each
     * measured dimension stands for. [toDraw] are the style letters still undrawn, which a
     * surer reading needs.
     */
    data class ReadDrawing(
        val reading: Reading?,
        val levels: Map<Dimension, Level>,
        val toDraw: String,
    ) : Step {
        override val id: String get() = STEP_MIRROR
    }

    /**
     * One dimension's question. [suggested] is the answer the brief so far points to and
     * [suggestedBy] where that comes from; [genreCounts] is how many of the genre's [genreTotal]
     * measured faces give each answer, so a person sees what the genre does before choosing.
     */
    data class AskDimension(
        val dimension: Dimension,
        val options: List<Level>,
        val suggested: Level?,
        val suggestedBy: Origin?,
        val genreCounts: Map<Level, Int>,
        val genreTotal: Int,
    ) : Step {
        override val id: String get() = dimensionStepId(dimension)
    }
}

/** The order the blank door asks in: what is seen first, first. */
private val TEACHING_ORDER =
    listOf(
        Dimension.SERIFS,
        Dimension.CONTRAST,
        Dimension.STRESS,
        Dimension.WIDTH,
        Dimension.ROUNDNESS,
        Dimension.WEIGHT,
        Dimension.WAIST,
        Dimension.A_FORM,
        Dimension.G_FORM,
        Dimension.TERMINALS,
        Dimension.APERTURE,
        Dimension.X_HEIGHT,
    )

/** At most this many dimension questions come from a genre's own spread. */
private const val MAX_GENRE_QUESTIONS = 5

/** At most this many dimension questions in a door other than the blank one. */
private const val MAX_DIMENSION_QUESTIONS = 7

/** A genre "leaves a question open" when its most common answer covers no more than this share of its faces. */
private const val OPEN_SHARE = 0.6

/**
 * Every question [door] asks, given what has been chosen so far. The list adapts: choosing a
 * genre or a use adds the questions they leave open, so call it again after every answer.
 * Answered steps stay in the list (for going back); [nextStep] finds the first one still open.
 */
fun questionsFor(
    door: Door,
    model: BriefModel,
    drawing: Drawing = Drawing.EMPTY,
): List<Step> {
    val steps = mutableListOf<Step>()
    val atlas = model.atlas

    fun addUses() {
        steps += Step.PickUses
        for (use in model.uses) {
            for (question in use.questions) steps += Step.AskUse(use, question)
            if (use.id == "wordmark") steps += Step.EnterWordmark
        }
    }

    fun addDimensions(dimensions: List<Dimension>) {
        val wanted = dimensions.distinct().filter { it != Dimension.STRESS || stressMatters(model) }
        val limit = if (door == Door.BLANK) wanted.size else MAX_DIMENSION_QUESTIONS
        wanted.take(limit).forEach { steps += askDimension(it, model) }
    }
    val useDimensions = model.uses.flatMap { it.dimensions }
    // Where the genre's voice lives first, then whatever its own faces measure split on.
    val genreDimensions =
        model.genre?.let { g -> (g.askAbout + atlas?.let { a -> openDimensions(g, a).map { it.first } }.orEmpty()).distinct() }.orEmpty()
    when (door) {
        Door.STYLE -> {
            steps += Step.PickGenre(suggestGenres(model))
            addUses()
            addDimensions(genreDimensions.take(MAX_GENRE_QUESTIONS) + useDimensions)
        }

        Door.USE -> {
            addUses()
            steps += Step.PickGenre(suggestGenres(model))
            addDimensions(useDimensions + genreDimensions.take(MAX_GENRE_QUESTIONS))
        }

        Door.REFERENCES -> {
            steps += Step.PickReferences(facesLike(model))
            addUses()
            val disagreements = model.referenceSpread.keys.mapNotNull { f -> Dimension.entries.firstOrNull { f in it.features } }
            addDimensions(disagreements + useDimensions + genreDimensions.take(MAX_GENRE_QUESTIONS))
            steps += Step.PickGenre(suggestGenres(model))
        }

        Door.FEELING -> {
            steps += Step.PickFeelings(model.feelings)
            addUses()
            // Settle what the feelings disagree on, then confirm what they point to, strongest first.
            val pulledApart = model.tensions.filter { it.id.startsWith("feelings.") }.flatMap { it.dimensions }
            val pointedTo =
                model.feelings
                    .flatMap { p -> p.tendencies.map { it.feature to abs(it.effect) } + p.leanings.map { it.feature to it.share } }
                    .sortedByDescending { it.second }
                    .mapNotNull { (f, _) -> Dimension.entries.firstOrNull { f in it.features } }
            addDimensions(pulledApart + pointedTo + useDimensions + genreDimensions.take(MAX_GENRE_QUESTIONS))
            steps += Step.PickGenre(suggestGenres(model))
        }

        Door.MIRROR -> {
            val levels = atlas?.let { levelsOf(drawing.measurement, it) }.orEmpty()
            val toDraw = STYLE_MEASUREMENT_GLYPHS.filter { !drawing.font.drawn(it) }
            steps += Step.ReadDrawing(atlas?.let { readAs(drawing.measurement, it) }, levels, toDraw)
            addUses()
            val unmeasured = (useDimensions + TEACHING_ORDER).filter { it !in levels }
            addDimensions(unmeasured)
            steps += Step.PickGenre(suggestGenres(model))
        }

        Door.BLANK -> {
            addUses()
            addDimensions(TEACHING_ORDER)
            steps += Step.PickGenre(suggestGenres(model))
            steps += Step.PickFeelings(model.feelings)
        }
    }
    return steps
}

/** The first question [door] still has open, or null when the person has been through them all. */
fun nextStep(
    door: Door,
    model: BriefModel,
    drawing: Drawing = Drawing.EMPTY,
): Step? = questionsFor(door, model, drawing).firstOrNull { it.id !in model.ethos.answered }

/** Stress only means something on a stroke with real contrast: ask about it only then. */
private fun stressMatters(model: BriefModel): Boolean {
    val answer = model.answer(Dimension.CONTRAST)
    if (answer != null) return answer == Level.CONTRAST_STRONG || answer == Level.CONTRAST_EXTREME
    val range = model.targets[Feature.CONTRAST]?.range ?: return false
    return range.endInclusive >= STRESS_MIN_CONTRAST
}

private fun askDimension(
    dimension: Dimension,
    model: BriefModel,
): Step.AskDimension {
    val atlas = model.atlas
    val genre = model.genre
    val counts =
        if (atlas != null && genre != null) {
            levelCounts(
                dimension,
                atlas.pack.classes[genre.key]
                    ?.faces
                    .orEmpty(),
                atlas,
            )
        } else {
            emptyMap()
        }
    val suggested = suggestedLevel(dimension, model)
    return Step.AskDimension(
        dimension = dimension,
        options = dimension.levels,
        suggested = suggested?.first,
        suggestedBy = suggested?.second,
        genreCounts = counts,
        genreTotal = counts.values.sum(),
    )
}

/** The answer the brief so far points to for [dimension], and where it comes from: never the person's own answer. */
private fun suggestedLevel(
    dimension: Dimension,
    model: BriefModel,
): Pair<Level, Origin>? {
    if (model.answer(dimension) != null) return null
    return when (dimension) {
        Dimension.SERIFS -> {
            val serif = model.targets[Feature.SERIF]
            when {
                serif?.values == setOf("no") -> {
                    Level.SERIFS_NONE to serif.origin
                }

                serif == null || serif.values == setOf("yes") -> {
                    model.targets[Feature.BRACKET]?.let { t ->
                        t.level?.let { it to t.origin }
                    }
                }

                else -> {
                    null
                }
            }
        }

        else -> {
            model.targets[dimension.features.first()]?.let { t -> t.level?.takeIf { it.dimension == dimension }?.let { it to t.origin } }
        }
    }
}

/** How many of [faces] would give each answer to [dimension], measured. */
fun levelCounts(
    dimension: Dimension,
    faces: Collection<String>,
    atlas: StyleAtlas,
): Map<Level, Int> {
    val maker = TargetMaker.of(atlas)
    return faces
        .mapNotNull { atlas.pack.faces[it] }
        .mapNotNull { face -> levelOfFace(dimension, face, maker) }
        .groupingBy { it }
        .eachCount()
}

private fun levelOfFace(
    dimension: Dimension,
    face: AtlasFace,
    maker: TargetMaker,
): Level? = maker.levelFor(dimension, { f -> f.numericValue(face) }, { f -> f.categoricalValue(face) })

/**
 * The dimensions a genre leaves open, most open first: those where its own faces split between
 * answers, so the person's choice decides. Measured as one minus the share of the most common
 * answer; a dimension is open when that answer covers no more than [OPEN_SHARE] of the faces.
 * Stress is left out for genres that are mostly monoline.
 */
fun openDimensions(
    genre: Genre,
    atlas: StyleAtlas,
): List<Pair<Dimension, Double>> {
    val faces =
        atlas.pack.classes[genre.key]
            ?.faces
            .orEmpty()
    val contrastMedian = atlas.quantile(Feature.CONTRAST.key, faces, 0.5)
    return Dimension.entries
        .filter { it != Dimension.STRESS || (contrastMedian != null && contrastMedian >= STRESS_MIN_CONTRAST) }
        .mapNotNull { dimension ->
            val counts = levelCounts(dimension, faces, atlas)
            val total = counts.values.sum()
            if (total < MIN_FACES_FOR_SPREAD) return@mapNotNull null
            val top = counts.values.max().toDouble() / total
            if (top > OPEN_SHARE) null else dimension to (1.0 - top)
        }.sortedByDescending { it.second }
}

/** Fewer measured faces than this and a genre's spread means nothing. */
private const val MIN_FACES_FOR_SPREAD = 4

/**
 * Genres to offer, best first. With anything to go on (answers, references, feelings) genres are
 * ranked by how many of their measured faces fit it; otherwise the uses' usual genres are offered.
 * When the font has a use, only genres usual for it are ranked, so a watch face is never offered
 * a formal script because its answers happen to match one.
 */
fun suggestGenres(
    model: BriefModel,
    limit: Int = 4,
): List<GenreSuggestion> {
    val atlas = model.atlas
    val targets = model.targets.values.filter { it.origin.kind != OriginKind.GENRE }
    val usual = model.uses.flatMap { it.suggestedGenres }.toSet()
    if (atlas != null && targets.isNotEmpty()) {
        return GENRES
            .filter { usual.isEmpty() || it.key in usual }
            .mapNotNull { genre ->
                val faces =
                    atlas.pack.classes[genre.key]
                        ?.faces
                        .orEmpty()
                        .mapNotNull { atlas.pack.faces[it] }
                if (faces.size < MIN_FACES_FOR_SPREAD) return@mapNotNull null
                val fit = agreement(faces, targets)
                genre to fit
            }.sortedByDescending { it.second }
            .take(limit)
            .map { (genre, fit) -> GenreSuggestion(genre, "${percent(fit)} of its faces fit what you've chosen") }
    }
    val fromUses = model.uses.flatMap { use -> use.suggestedGenres.map { it to use } }.distinctBy { it.first }
    return fromUses.mapNotNull { (key, use) -> genreByKey(key)?.let { GenreSuggestion(it, "Often used for ${use.forWhat}") } }.take(limit)
}

/** The average share of [faces] meeting each target (the person's own answers count double). */
private fun agreement(
    faces: List<AtlasFace>,
    targets: List<Target>,
): Double {
    var total = 0.0
    var weights = 0.0
    for (target in targets) {
        val measured =
            faces.mapNotNull { face ->
                if (target.feature.numeric) {
                    target.feature.numericValue(face)?.let { v -> target.contains(v) }
                } else {
                    target.feature.categoricalValue(face)?.let { v -> target.contains(v) }
                }
            }
        if (measured.isEmpty()) continue
        val w = if (target.firm) 2.0 else 1.0
        total += w * measured.count { it } / measured.size
        weights += w
    }
    return if (weights == 0.0) 0.0 else total / weights
}

/** What a measurement says about each dimension, as the answer it stands for: the mirror door's reading of a drawing. */
fun levelsOf(
    measurement: StyleMeasurement,
    atlas: StyleAtlas,
): Map<Dimension, Level> {
    val maker = TargetMaker.of(atlas)
    return Dimension.entries
        .mapNotNull { d ->
            maker.levelFor(d, { f -> f.numericValue(measurement) }, { f -> f.categoricalValue(measurement) })?.let { d to it }
        }.toMap()
}
