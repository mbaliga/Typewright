// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.learn.scenes

/**
 * The pure part of a scene's playback: given a scrub position, what should be on screen. This
 * file has no drawing in it — no Compose, no canvas, no face-fetching — only the numbers a
 * drawing layer needs (`docs/LESSONS_SCAFFOLD.md` section 1's "Renderer rules" paragraph, CANON).
 *
 * **Crossfade, not a shape morph — read plainly, not glossed over.** The scaffold's own word for
 * the transition is "crossfade", and this object takes that literally: [crossfadeAt] returns two
 * independent opacities, one for the `from` face's own rendering of [Stage.sample] and one for the
 * `to` face's own rendering of the same sample, both aligned at [Stage.align], dissolving from one
 * to the other. It is deliberately **not** a point-by-point interpolation between the two faces'
 * outlines. Two arbitrary OFL faces of the same letter essentially never share compatible point
 * topology — this build's own node-economy work is the proof already sitting in this repository:
 * the shipped Hyle Deco 'o' has 80 on-curve points (`CLAUDE.md`'s fixture table) against a
 * well-drawn target's 32 (16 on-curve, 16 off-curve) for the *same* glyph in one *single* font
 * family, before a second, unrelated typeface's own drawing choices enter the comparison at all.
 * There is no general, sound way to pair up two such outlines point-for-point and lerp between
 * them; a crossfade needs no such pairing, renders each face exactly as its own designer drew it,
 * and is the operation the scaffold's own text names. See `SceneRendererTest` for the crossfade
 * and stress-angle values checked here.
 *
 * **The scrub position `t` is always normalised to `0.0..1.0`, never seconds.** The schema's own
 * worked example says so about [Scene.duration] itself: `duration: 40  # seconds at the default
 * pace; scrubbing ignores it`. So every function here takes `t` already in `0.0..1.0`; a caller
 * driving *autoplay* off a wall clock converts seconds to that range with [normalizedScrub], and a
 * caller driving the *scrubber* the person drags never needs [normalizedScrub] at all — the drag
 * position already is `t`.
 *
 * **The 0.6 callout threshold, read against the same clue.** `docs/LESSONS_SCAFFOLD.md`: "callouts
 * fade in when the scrubber passes 0.6". Read together with the duration comment above (scrubbing
 * ignores seconds entirely, and works in the same normalised range the scrub bar itself uses),
 * 0.6 is a fraction of `t`, not of [Scene.duration] in seconds: a 20-second scene's callouts appear
 * 12 seconds into autoplay and a 60-second scene's appear 36 seconds in, but both appear at the
 * same scrub-bar position, six tenths of the way across. [calloutsVisibleAt] implements exactly
 * that: a hard cutoff at `t >= 0.6`, no ramp — the scaffold asks "whether each callout should be
 * visible yet" (a yes/no), and any fade animation on top of that boolean is a drawing-layer detail
 * for the later UI task, not a number this pure module should invent.
 */
public object SceneRenderer {
    /** The scrub fraction, `0.6`, past which callouts become visible (`docs/LESSONS_SCAFFOLD.md`). */
    public const val CALLOUT_VISIBLE_AT: Double = 0.6

    /** [Scene.duration] seconds of autoplay converted to a normalised scrub position in `0.0..1.0`. */
    public fun normalizedScrub(
        elapsedSeconds: Double,
        duration: Int,
    ): Double {
        require(duration > 0) { "duration must be positive, was $duration" }
        return (elapsedSeconds / duration).coerceIn(0.0, 1.0)
    }

    /**
     * The `from`/`to` face opacities for a dissolve at scrub position [t] (clamped to
     * `0.0..1.0`): a plain linear crossfade, `from` fading out as `to` fades in. Both faces are
     * rendered independently by the drawing layer at [Stage.align]; this returns only the mix.
     */
    public fun crossfadeAt(t: Double): Crossfade {
        val clamped = t.coerceIn(0.0, 1.0)
        return Crossfade(fromOpacity = 1.0 - clamped, toOpacity = clamped)
    }

    /**
     * The stress-dial angle at scrub position [t] (clamped to `0.0..1.0`): a plain linear
     * interpolation between [stress]'s `from` and `to` degrees, or null when [stress] is null
     * (the scene has no stress axis at all — the dial should not be drawn, not drawn at zero).
     */
    public fun stressAngleAt(
        stress: Pair<Double, Double>?,
        t: Double,
    ): Double? {
        if (stress == null) return null
        val clamped = t.coerceIn(0.0, 1.0)
        val (from, to) = stress
        return from + (to - from) * clamped
    }

    /** Whether callouts should be visible yet at scrub position [t] — `t >= `[CALLOUT_VISIBLE_AT]. */
    public fun calloutsVisibleAt(t: Double): Boolean = t.coerceIn(0.0, 1.0) >= CALLOUT_VISIBLE_AT

    /**
     * Every pure fact about one scrub position at once, for a caller that wants the whole picture
     * in one call rather than three.
     */
    public fun frameAt(
        scene: Scene,
        t: Double,
    ): SceneFrame {
        val clamped = t.coerceIn(0.0, 1.0)
        return SceneFrame(
            t = clamped,
            crossfade = crossfadeAt(clamped),
            stressAngle = stressAngleAt(scene.stage.stress, clamped),
            calloutsVisible = calloutsVisibleAt(clamped),
        )
    }
}

/** The `from`/`to` opacities of a crossfade; they sum to 1 by construction. */
public data class Crossfade(
    val fromOpacity: Double,
    val toOpacity: Double,
)

/** Every pure renderer fact about one scene at one scrub position. */
public data class SceneFrame(
    val t: Double,
    val crossfade: Crossfade,
    val stressAngle: Double?,
    val calloutsVisible: Boolean,
)
