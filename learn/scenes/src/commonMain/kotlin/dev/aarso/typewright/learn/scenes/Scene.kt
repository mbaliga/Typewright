package dev.aarso.typewright.learn.scenes

/**
 * The Learn scene domain model (`docs/LESSONS_SCAFFOLD.md` section 1, CANON). A lesson is a
 * sequence of [Scene]s; the canvas renders each one live from this data — never a video, never a
 * baked image of type (brief §9). Field names below match the YAML schema's own field names
 * one-for-one (the schema already uses single lowercase words, so no renaming was needed to reach
 * idiomatic Kotlin); [SceneParser] is what turns a scene's YAML text into one of these.
 *
 * @property id the scene's stable identifier, e.g. `"lineages.transitional"`.
 * @property strand which of the five Learn strands this scene belongs to.
 * @property title the scene's display title.
 * @property era a human-readable date/period, e.g. `"1757"`; not every strand has one (Anatomy
 *   and Craft scenes, for instance, are not organised by era), so this is nullable.
 * @property duration the scene's length in seconds at the default autoplay pace. The schema's own
 *   worked example notes "seconds at the default pace; scrubbing ignores it" — [SceneRenderer]
 *   takes that literally: its interpolation is driven entirely by a normalised scrub position in
 *   `0.0..1.0`, and [duration] only matters for converting an autoplay clock into that position
 *   (see [SceneRenderer.normalizedScrub]).
 * @property faces the faces this scene can put on stage, by a short [FaceRef.key] scenes
 *   reference from [Stage.from]/[Stage.to] and exercises reference by [FaceRef.family] directly.
 * @property stage what is drawn on the canvas.
 * @property caption the scene's spoken/written copy.
 * @property callouts optional labels resolved on the [Stage.to] face; empty when the scene has
 *   none.
 * @property exercise this scene's own candidate identify-it question, if it has one. Whether it is
 *   actually safe to show is a property of the whole strand block, not of this scene alone — see
 *   [StrandSequencer].
 */
public data class Scene(
    val id: String,
    val strand: Strand,
    val title: String,
    val era: String?,
    val duration: Int,
    val faces: List<FaceRef>,
    val stage: Stage,
    val caption: Caption,
    val callouts: List<Callout> = emptyList(),
    val exercise: Exercise? = null,
) {
    /** [faces] looked up by [FaceRef.key]; the family for [Stage.from]/[Stage.to], if present. */
    public fun familyForKey(key: String): String? = faces.firstOrNull { it.key == key }?.family

    /** The family on stage at [Stage.from], resolved through [faces]; null if the key is unknown. */
    public val fromFamily: String? get() = familyForKey(stage.from)

    /** The family on stage at [Stage.to], resolved through [faces]; null if the key is unknown. */
    public val toFamily: String? get() = familyForKey(stage.to)
}

/** The five Learn strands (brief §9, `docs/LESSONS_SCAFFOLD.md` section 1's own comment). */
public enum class Strand {
    LINEAGES,
    ANATOMY,
    CRAFT,
    SCRIPTS,
    READING,
}

/** One face a scene can put on stage: a short key scenes reference, and its family name. */
public data class FaceRef(
    val key: String,
    val family: String,
)

/** Which metric line the [Stage.from]/[Stage.to] renderings are aligned at. */
public enum class Align {
    XHEIGHT,
    CAPHEIGHT,
    BASELINE,
}

/**
 * What the canvas draws: [sample] rendered in the [from] face crossfading to the [to] face
 * (never a point-by-point shape morph — see [SceneRenderer]'s KDoc for why), aligned at [align],
 * with an optional stress-dial axis that sweeps from [stress]'s first value to its second as the
 * scrubber moves.
 */
public data class Stage(
    val sample: String,
    val align: Align,
    val from: String,
    val to: String,
    val stress: Pair<Double, Double>? = null,
)

/** A scene's spoken/written copy: the tool that made the shape, the caption text, feature tags. */
public data class Caption(
    val tool: String,
    val text: String,
    val tags: List<String> = emptyList(),
)

/** One label on the stage, resolved on the [Stage.to] face. */
public data class Callout(
    val glyph: String,
    val part: String,
    val label: String,
)

/**
 * A candidate identify-it exercise: name the face that set [word], from [options], the correct
 * one being [answer], with [giveaway] read out once the learner answers (or gives up). Whether
 * this candidate is actually safe to show — [face] must not have appeared on stage anywhere in
 * the strand block — is decided by [StrandSequencer], not by this type.
 */
public data class Exercise(
    val word: String,
    val face: String,
    val options: List<String>,
    val answer: String,
    val giveaway: String,
)
