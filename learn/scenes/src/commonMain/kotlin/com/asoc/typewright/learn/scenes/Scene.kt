// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.learn.scenes

/**
 * The Learn scene domain model (`docs/LESSONS_SCAFFOLD.md` section 1, CANON). A lesson is a
 * sequence of [Scene]s; the canvas renders each one live from this data — never a video, never a
 * baked image of type (brief §9). Field names below match the YAML schema's own field names
 * one-for-one (the schema already uses single lowercase words, so no renaming was needed to reach
 * idiomatic Kotlin); [SceneParser] is what turns a scene's YAML text into one of these.
 *
 * **Craft-strand extension (this P6 content-authoring task, additive, documented here plainly
 * rather than left implicit).** CANON's own worked example (section 1) and section 2's ten
 * Lineages eras are all one shape: [Stage.from]/[Stage.to] crossfade between **two
 * independently-drawn OFL typefaces**, both fetched by [FaceRef.family] from the corpus.
 * `docs/LESSONS_SCAFFOLD.md` section 4's Craft scenes (`docs/KNOWLEDGE.md` as before/after) are a
 * genuinely different shape: **a single project's own glyph before and after one of this
 * codebase's own construction-pipeline stages** (a fit, an offset, a shear-then-restroke) — there
 * is no second typeface at all, and for several of these scenes no second *file* of any kind.
 * Forcing that shape through [FaceRef.family] alone would either fetch a nonexistent corpus family
 * or silently overload `family`'s meaning (a real, fetchable typeface name) into something else —
 * CLAUDE.md's "measured, not invented" cuts against inventing a family that will never resolve.
 * The fix taken is the smallest one that keeps every existing Lineages scene parsing
 * byte-identically: two new, both-default-valued fields, [FaceRef.source]/[FaceRef.path] and
 * [Stage.pipeline] — see their own KDoc for the exact contract. Nothing about [SceneRenderer]'s own
 * interpolation math changes: a Craft scene still crossfades a `from` rendering into a `to`
 * rendering exactly like a Lineages scene; the only difference is *where the renderer gets those
 * two renderings from*, which was already opaque to [SceneRenderer] (a drawing-layer concern, out
 * of this data/logic task's scope) before this extension existed.
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
 * @property scaffold whether this scene ships marked SCAFFOLD in the UI (`docs/LESSONS_SCAFFOLD.md`'s
 *   own header: "Everything in this file ships marked SCAFFOLD in the UI until Madhav's material
 *   replaces or augments it"). Not part of the CANON schema section 1 originally defined — added by
 *   the P6 content-authoring task (data/logic half) as a small, backward-compatible extension (a
 *   new field with a default, so every existing caller and test that builds a [Scene] without
 *   naming it keeps compiling and keeps its old meaning). Defaults to `false` so a scene with no
 *   opinion on the question is not silently marked SCAFFOLD; every scene this task's own YAML
 *   files describe sets it `true` explicitly. [SceneParser] reads an optional `scaffold: true/false`
 *   key for it and defaults to `false` when the key is absent, exactly like [callouts]/[exercise].
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
    val scaffold: Boolean = false,
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

/**
 * Where a [FaceRef]'s own glyph geometry actually comes from (Craft-strand extension — see
 * [Scene]'s own KDoc for the full "why").
 * - [CORPUS] is CANON's own, original, and still the default, shape: fetched by [FaceRef.family]
 *   from the OFL/Apache corpus at build time (`learn-faces`/`data/learn-faces`), exactly as every
 *   Lineages scene already works. Every existing [FaceRef] usage (positional or named, without
 *   `source`) keeps meaning exactly this.
 * - [PROJECT] is this task's own addition: not fetched from the corpus at all, because the face is
 *   this project's own material rather than a second, independently-drawn typeface. Two cases,
 *   told apart by [FaceRef.path]:
 *   - [FaceRef.path] set: a real, checked-in repository file, read directly (e.g.
 *     `fonts/HyleDeco-Regular.ttf`).
 *   - [FaceRef.path] null: no backing file at all. Either its glyph is computed live by this
 *     codebase's own construction/fit code — [Stage.pipeline] names which — or it is a fixed
 *     teaching illustration with no live mechanism behind it *yet*; the scene's own caption/tags
 *     say which case applies and flag any gap honestly, per CLAUDE.md law 5 ("measured, not
 *     invented" — this field only ever names code that is real and already tested, never a
 *     to-be-built one). A future rendering task tells the two apart the same way: [Stage.pipeline]
 *     null means "draw this as a fixed illustration"; non-null means "compute it live, following
 *     that recipe".
 */
public enum class FaceSource {
    CORPUS,
    PROJECT,
}

/**
 * One face a scene can put on stage: a short key scenes reference, and its family name.
 *
 * @property source where [family]'s own glyph geometry comes from; see [FaceSource]'s own KDoc.
 *   Defaults to [FaceSource.CORPUS], CANON's original and only shape, so every existing
 *   two-argument `FaceRef(key, family)` call (every Lineages scene, [SceneParserTest]'s own
 *   worked-example fixture included) keeps compiling and keeps its old meaning unchanged.
 * @property path a repository-relative path to a real, checked-in file this [FaceRef] reads
 *   directly, when [source] is [FaceSource.PROJECT] and such a file exists (e.g.
 *   `fonts/HyleDeco-Regular.ttf`); null otherwise, including always for [FaceSource.CORPUS] (a
 *   corpus face is fetched by [family], never read by a path this schema names).
 */
public data class FaceRef(
    val key: String,
    val family: String,
    val source: FaceSource = FaceSource.CORPUS,
    val path: String? = null,
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
 *
 * @property pipeline Craft-strand extension (see [Scene]'s own KDoc): non-null exactly for a scene
 *   whose [from]/[to] are not two independently-drawn typefaces but states this project's own
 *   construction pipeline produces — names, in short `module.function` notation, the real
 *   function(s) in this codebase responsible (e.g. `"core-geometry.fitPolylineToFinishedContour"`,
 *   already built and tested — never a function this scene merely wishes existed). Null for
 *   CANON's own Lineages shape (both faces [FaceSource.CORPUS]) and equally null for a Craft scene
 *   whose faces are [FaceSource.PROJECT] but illustrative only, with no live mechanism behind them
 *   yet — that gap belongs in the scene's own caption/tags, not invented here as a fake pipeline
 *   name. This field is read-only data for a later rendering task to act on; parsing and validating
 *   it (this task's own scope) does not itself call anything it names.
 */
public data class Stage(
    val sample: String,
    val align: Align,
    val from: String,
    val to: String,
    val stress: Pair<Double, Double>? = null,
    val pipeline: String? = null,
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
