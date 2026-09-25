// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.learn

import com.asoc.typewright.learn.scenes.ExerciseBankEntry
import com.asoc.typewright.learn.scenes.LearnFaceResources
import com.asoc.typewright.learn.scenes.StrandExercisePlan

/**
 * The Compose-free half of [LineagesTab]'s identify-it quiz (`ui/typewright-explorer.html`'s
 * `.quiz`/`.qw`/`.opts`/`.reveal`, `docs/LESSONS_SCAFFOLD.md` section 1: "the exercise appears
 * after the last scene of a strand block and never shows a face used on stage in that block").
 * Kept separate from [LineagesTab.kt] so this logic is unit-testable with plain `kotlin.test`,
 * no Compose runtime required — the same split [com.asoc.typewright.ui.sheet.SheetCamera] and
 * its own state classes already use.
 *
 * **Why this exists at all, not just [StrandSequencer.plan] read straight into the UI.**
 * [StrandExercisePlan.eligibleSceneExercises] is `List<Scene>`, each with its own real
 * `word`/`options` (`Scene.exercise`, CANON's own `Exercise` shape) — those need no work.
 * [StrandExercisePlan.eligibleBankEntries] is `List<ExerciseBankEntry>`, which
 * (`docs/OPEN_QUESTIONS.md` item 39, already logged) carries only `face`/`answer`/`giveaway` —
 * neither a `word` to set nor an `options` list, because that is the real, already-built type
 * [com.asoc.typewright.learn.scenes.StrandSequencer.plan] actually returns, not a paraphrase of
 * it. [buildQuizItems] is where both shapes become one uniform [LineagesQuizItem] a card can
 * render: a bank entry's word is [BANK_QUIZ_WORD] (item 39's own suggestion: reuse
 * "Hamburgefonstiv", the same word CANON's worked example and the explorer's own `.qw` element
 * both already use), and its three-option list is synthesised by [optionsFor] from the real era
 * class names already on stage in this same block — see its own KDoc for exactly how and why that
 * is a new, previously-undecided design choice worth its own `docs/OPEN_QUESTIONS.md` entry
 * (this task's own final report has the ready-to-paste text).
 */
internal const val BANK_QUIZ_WORD: String = "Hamburgefonstiv"

/** One identify-it card ready to render, whichever of [StrandExercisePlan]'s two sources it came from. */
internal data class LineagesQuizItem(
    val word: String,
    /** A real family name (e.g. `"Libre Baskerville"`) — resolved to a face key by [learnFaceKeyForFamily]. */
    val faceFamily: String,
    val options: List<String>,
    val answer: String,
    val giveaway: String,
)

/**
 * Every eligible identify-it card for one [StrandExercisePlan]: [StrandExercisePlan.eligibleSceneExercises]'
 * own inline exercises first (in block order), then [StrandExercisePlan.eligibleBankEntries]
 * (in bank order), each turned into a [LineagesQuizItem]. [classNames] is the full set of era
 * class names this block actually taught (its own on-stage scene titles, era order) — the pool
 * [optionsFor] draws bank-entry distractors from.
 */
internal fun buildQuizItems(
    plan: StrandExercisePlan,
    classNames: List<String>,
): List<LineagesQuizItem> {
    val fromScenes =
        plan.eligibleSceneExercises.mapNotNull { scene ->
            val exercise = scene.exercise ?: return@mapNotNull null
            LineagesQuizItem(
                word = exercise.word,
                faceFamily = exercise.face,
                options = exercise.options,
                answer = exercise.answer,
                giveaway = exercise.giveaway,
            )
        }
    val fromBank =
        plan.eligibleBankEntries.mapIndexed { index, entry ->
            LineagesQuizItem(
                word = BANK_QUIZ_WORD,
                faceFamily = entry.face,
                options = optionsFor(entry, classNames, index),
                answer = entry.answer,
                giveaway = entry.giveaway,
            )
        }
    return fromScenes + fromBank
}

/**
 * A three-option identify-it list for one bank [entry]: [ExerciseBankEntry.answer] plus two
 * distractors drawn from [classNames] (this block's own real era class names, in era order),
 * the two classes immediately following [entry]'s own answer in that cycle — deterministic and
 * reproducible (no randomness, so the same content always renders the same options), and never
 * a class name this strand did not actually teach. [index] only spreads the answer's position
 * across the three slots (`index % 3`) so a learner scanning several cards does not see the
 * correct chip land in the same place every time; it never changes *which* distractors are
 * chosen. This exact scheme (cyclic-successor distractors, `index % 3` slotting) is this task's
 * own design choice — the explorer's own `.opts` markup is a single static example, never a
 * generation rule for the other six real bank entries — logged as a ready-to-paste
 * `docs/OPEN_QUESTIONS.md` entry in this task's final report rather than presented as settled.
 */
internal fun optionsFor(
    entry: ExerciseBankEntry,
    classNames: List<String>,
    index: Int,
): List<String> {
    require(classNames.size >= 3) { "need at least 3 class names to build a 3-option quiz, had ${classNames.size}" }
    val answerIndex = classNames.indexOf(entry.answer).let { if (it >= 0) it else 0 }
    val distractors = mutableListOf<String>()
    var offset = 1
    while (distractors.size < 2 && offset < classNames.size) {
        val candidate = classNames[(answerIndex + offset) % classNames.size]
        if (candidate != entry.answer && candidate !in distractors) distractors += candidate
        offset++
    }
    return when (index % 3) {
        0 -> listOf(entry.answer, distractors[0], distractors[1])
        1 -> listOf(distractors[0], entry.answer, distractors[1])
        else -> listOf(distractors[0], distractors[1], entry.answer)
    }
}

/**
 * [family] (e.g. `"Libre Bodoni"`) resolved to its short `data/learn-faces/manifest.json` key
 * (e.g. `"librebodoni"`) — [com.asoc.typewright.learn.scenes.Scene.faces]/[LearnFaceEntry] both
 * already carry this pairing; this just scans every known face (on-stage and exercise-bank
 * alike, [LearnFaceResources.allEntries]) for one whose [LearnFaceEntry.family] matches, since a
 * quiz card's face (an identify-it *answer*, by construction never on stage in this block) is
 * not necessarily in the current [com.asoc.typewright.learn.scenes.Scene.faces] list at all.
 *
 * Wrapped in [runCatching]: on wasmJs (browser), reading `manifest.json` at all throws (Node-`fs`-
 * based, `docs/OPEN_QUESTIONS.md` item 47) — the same failure [learnFaceFontFamily] itself now
 * guards against (see that function's own KDoc for the one-line fix this task made there); a
 * caller here gets `null` and falls back to [androidx.compose.ui.text.font.FontFamily.Default],
 * never a crash.
 */
internal fun learnFaceKeyForFamily(family: String): String? =
    runCatching { LearnFaceResources.allEntries().firstOrNull { it.family == family }?.key }.getOrNull()

/** [t] clamped to `0.0..1.0` — [com.asoc.typewright.learn.scenes.SceneRenderer]'s own scrub contract. */
internal fun clampScrub(t: Double): Double = t.coerceIn(0.0, 1.0)

/** [index] clamped to a valid position in a list of [sceneCount] scenes (never empty in practice — [sceneCount] is [com.asoc.typewright.learn.scenes.LineagesResources.loadFullBlockInPlayOrder]'s own fixed eleven). */
internal fun selectScene(
    index: Int,
    sceneCount: Int,
): Int = index.coerceIn(0, (sceneCount - 1).coerceAtLeast(0))

/** The stress dial's own label text, exactly matching `ui/typewright-explorer.html`'s `update()`: null → "no stress axis", a rounded angle of 0 → "vertical stress", else "stress N°". */
internal fun stressDialLabel(angle: Double?): String =
    when {
        angle == null -> "no stress axis"
        kotlin.math.round(angle).toInt() == 0 -> "vertical stress"
        else -> "stress ${kotlin.math.round(angle).toInt()}°"
    }
