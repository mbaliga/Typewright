// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

/**
 * Where a person is in the questions: the [step] still open (null once they are all through)
 * and how many of the [total] have an answer or a skip. [focused] is true when the step is one
 * the person chose to revisit (a tension to settle), not the next in line.
 */
data class AskPosition(
    val step: Step?,
    val answered: Int,
    val total: Int,
    val focused: Boolean = false,
) {
    /** True once every question in line has an answer or a skip. */
    val done: Boolean get() = step == null
}

/**
 * Every question a person goes through by [door]: the door's own ([questionsFor]), then, once
 * they ask for more ([refining]), the dimensions the door did not ask about ([refineSteps]).
 * Answered steps stay in the list, so going back can reach them.
 */
fun flowSteps(
    door: Door,
    model: BriefModel,
    drawing: Drawing = Drawing.EMPTY,
    refining: Boolean = false,
): List<Step> {
    val own = questionsFor(door, model, drawing)
    if (!refining) return own
    return own + refineSteps(door, model, drawing)
}

/**
 * The questions "ask me more" adds after [door]'s own: every dimension the door leaves out, in the
 * order a beginner sees them (serifs and contrast before apertures). Stress only on a stroke with
 * real contrast.
 */
fun refineSteps(
    door: Door,
    model: BriefModel,
    drawing: Drawing = Drawing.EMPTY,
): List<Step.AskDimension> {
    val own = questionsFor(door, model, drawing).map { it.id }.toSet()
    return TEACHING_ORDER
        .filter { (it != Dimension.STRESS || stressMatters(model)) && dimensionStepId(it) !in own }
        .map { askDimension(it, model) }
}

/**
 * Where the person is: [focus] first when they chose to revisit a dimension, otherwise the first
 * step of [flowSteps] still open.
 */
fun askPosition(
    door: Door,
    model: BriefModel,
    drawing: Drawing = Drawing.EMPTY,
    refining: Boolean = false,
    focus: Dimension? = null,
): AskPosition {
    val steps = flowSteps(door, model, drawing, refining)
    val done = model.ethos.answered.toSet()
    val answered = steps.count { it.id in done }
    if (focus != null) return AskPosition(questionFor(focus, model), answered, steps.size, focused = true)
    return AskPosition(steps.firstOrNull { it.id !in done }, answered, steps.size)
}

/** The step "back" reopens: the last one in line with an answer or a skip, or null at the first. */
fun previousStepId(
    door: Door,
    model: BriefModel,
    drawing: Drawing = Drawing.EMPTY,
    refining: Boolean = false,
): String? {
    val done = model.ethos.answered.toSet()
    return flowSteps(door, model, drawing, refining).lastOrNull { it.id in done }?.id
}

/** The question for [dimension] on its own, whatever the door: to settle a tension or change one answer. */
fun questionFor(
    dimension: Dimension,
    model: BriefModel,
): Step.AskDimension = askDimension(dimension, model)

/** The question a step asks, as the person reads it. The app and its design mockup share these words. */
val Step.prompt: String
    get() =
        when (this) {
            is Step.PickGenre -> "What style is it?"
            Step.PickUses -> "What is it for?"
            is Step.AskUse -> question.prompt
            Step.EnterWordmark -> "What does the wordmark say?"
            is Step.PickFeelings -> "What should it feel like?"
            is Step.PickReferences -> "Which fonts do you like?"
            is Step.ReadDrawing -> "What do your letters already say?"
            is Step.AskDimension -> dimension.prompt
        }

/** Why a step is asked, in a line under the question. */
val Step.why: String
    get() =
        when (this) {
            is Step.PickGenre -> "Pick the shelf it belongs on. The measured faces of that shelf set the first ranges."
            Step.PickUses -> "A use changes what to draw first and what the letters must meet. The first you choose is the main one."
            is Step.AskUse -> question.why
            Step.EnterWordmark -> "A wordmark is only its own letters: they are drawn first."
            is Step.PickFeelings -> "Up to three. Google Fonts' own feeling tags: the app measures what their top faces have in common."
            is Step.PickReferences -> "Up to three. What they share becomes a target where nothing else has spoken."
            is Step.ReadDrawing -> "Your own drawing, measured against real faces. A reading is votes, never a verdict."
            is Step.AskDimension -> dimension.why
        }

/** True for the steps whose answer is a set of choices, which the person ends with "Done". */
val Step.needsConfirm: Boolean
    get() = this is Step.PickUses || this is Step.PickFeelings || this is Step.PickReferences

/** True for the steps that take "I'm not sure" as an answer (it moves on, like a skip). */
val Step.offersNotSure: Boolean
    get() = this is Step.AskDimension || this is Step.AskUse
