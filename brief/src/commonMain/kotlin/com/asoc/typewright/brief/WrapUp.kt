// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

/**
 * What the Brief says once a door's questions are through: the ethos in one [sentence], how many
 * measurable [targets] it sets, the tensions still [toSettle] (a kept signature is settled), the
 * letters to [drawFirst], and how many questions are left ([moreQuestions]) for anyone who wants
 * to refine it. It is the hand-off from asking to drawing, so it always names a next step.
 */
data class WrapUp(
    val sentence: String,
    val targets: Int,
    val toSettle: List<Tension>,
    val drawFirst: PlanStep?,
    val moreQuestions: Int,
)

/** The wrap-up for [door] once its questions are through; see [WrapUp]. */
fun wrapUp(
    door: Door,
    model: BriefModel,
    drawing: Drawing = Drawing.EMPTY,
): WrapUp {
    val kept = model.signatures
    val toSettle = model.tensions.filter { it.signature == null || it.signature !in kept }
    val first = planFor(model).firstOrNull { it.glyphs.isNotEmpty() && it.remaining(drawing.font).isNotEmpty() }
    val answered = model.ethos.answered.toSet()
    val more = refineSteps(door, model, drawing).count { it.id !in answered }
    return WrapUp(model.sentence, model.targets.size, toSettle, first, more)
}
