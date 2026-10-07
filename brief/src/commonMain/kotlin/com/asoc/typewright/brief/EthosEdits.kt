// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.project.Ethos

/** At most this many feelings: more and they stop pointing anywhere. */
const val MAX_FEELINGS = 3

/** At most this many references, for the same reason. */
const val MAX_REFERENCES = 3

/** Records [stepId] as answered (once), so the questions resume after it. */
fun Ethos.answering(stepId: String): Ethos = if (stepId in answered) this else copy(answered = answered + stepId)

/** Moves on from [stepId] without answering it. */
fun Ethos.skipping(stepId: String): Ethos = answering(stepId)

/** Sets the genre (null clears it). */
fun Ethos.withGenre(key: String?): Ethos = copy(genre = key).answering(STEP_GENRE)

/** Adds the use, or removes it with its own answers. The first use chosen is the main one. */
fun Ethos.withUseToggled(id: String): Ethos =
    if (id in uses) {
        copy(
            uses = uses - id,
            useOptions = useOptions.filterKeys { !it.startsWith("$id.") },
            answered = answered.filter { !it.startsWith("use.$id.") },
            wordmark = if (id == "wordmark") null else wordmark,
        )
    } else {
        copy(uses = uses + id)
    }

/** Answers one of a use's own questions. */
fun Ethos.withUseOption(
    use: UseCase,
    question: UseQuestion,
    optionId: String,
): Ethos = copy(useOptions = useOptions + ("${use.id}.${question.id}" to optionId)).answering(useStepId(use, question))

/** Adds a feeling, or removes it; a fourth is refused (the ethos comes back unchanged). */
fun Ethos.withFeelingToggled(key: String): Ethos =
    when {
        key in feelings -> copy(feelings = feelings - key)
        feelings.size >= MAX_FEELINGS -> this
        else -> copy(feelings = feelings + key)
    }

/** Adds a reference face, or removes it; a fourth is refused. */
fun Ethos.withReferenceToggled(family: String): Ethos =
    when {
        family in references -> copy(references = references - family)
        references.size >= MAX_REFERENCES -> this
        else -> copy(references = references + family)
    }

/** Answers [dimension] with [level], or clears the answer when [level] is null. Either way the question counts as answered. */
fun Ethos.withLevel(
    dimension: Dimension,
    level: Level?,
): Ethos {
    require(level == null || level.dimension == dimension) { "${level?.id} does not answer ${dimension.id}" }
    val next = if (level == null) levels - dimension.id else levels + (dimension.id to level.id)
    return copy(levels = next).answering(dimensionStepId(dimension))
}

/** Adopts measured answers (the mirror door), keeping any the person already gave. */
fun Ethos.withMeasuredLevels(measured: Map<Dimension, Level>): Ethos {
    val fresh = measured.filterKeys { it.id !in levels }
    return copy(
        levels = levels + fresh.map { (d, l) -> d.id to l.id },
        answered = (answered + fresh.keys.map { dimensionStepId(it) }).distinct(),
    ).answering(STEP_MIRROR)
}

/** Marks [dimension] as a deliberate departure from the genre, or unmarks it. */
fun Ethos.withSignatureToggled(dimension: Dimension): Ethos =
    if (dimension.id in signatures) copy(signatures = signatures - dimension.id) else copy(signatures = signatures + dimension.id)

/** Sets the wordmark's letters (blank clears them). */
fun Ethos.withWordmark(text: String?): Ethos = copy(wordmark = text?.trim()?.takeIf { it.isNotEmpty() }).answering(STEP_WORDMARK)

/** Goes back to [stepId]: it and everything answered after it become open again; the answers themselves stay. */
fun Ethos.reopening(stepId: String): Ethos {
    val i = answered.indexOf(stepId)
    return if (i < 0) this else copy(answered = answered.take(i))
}
