// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

/**
 * The ethos in one sentence, built only from what the person chose: their feelings as adjectives,
 * the genre, the uses, then their own answers in question order. Defaults the genre supplies are
 * left out, so the sentence never claims a choice nobody made.
 *
 * "A calm, businesslike geometric sans typeface for app interfaces: open apertures and a large x-height."
 */
internal fun composeSentence(model: BriefModel): String {
    if (model.isEmpty) return "Nothing chosen yet."
    val adjectives = model.ethos.feelings.mapNotNull { feelingByKey(it)?.adjective }
    val genre = model.genre
    val head =
        buildString {
            val words = listOfNotNull(adjectives.takeIf { it.isNotEmpty() }?.joinToString(", "), genre?.name?.lowercase(), "typeface")
            append(withArticle(words.joinToString(" ")))
            if (model.uses.isNotEmpty()) {
                append(" for ")
                append(joinWords(model.uses.map { forPhrase(it, model.ethos.wordmark) }))
            }
        }
    val phrases =
        Dimension.entries.mapNotNull { dimension ->
            val level = model.answer(dimension) ?: return@mapNotNull null
            // "A grotesque typeface: sans-serif" says nothing the genre didn't.
            if (level == Level.SERIFS_NONE && genre?.group == GenreGroup.SANS) null else level.phrase
        }
    return if (phrases.isEmpty()) "$head." else "$head: ${joinWords(phrases)}."
}

private fun forPhrase(
    use: UseCase,
    wordmark: String?,
): String = if (use.id == "wordmark" && !wordmark.isNullOrBlank()) "the wordmark “${wordmark.trim()}”" else use.forWhat

/** "a" or "an" by the first letter, which is right for every word the Brief starts a sentence with. */
internal fun withArticle(phrase: String): String {
    val first = phrase.firstOrNull()?.lowercaseChar() ?: return phrase
    val article = if (first in "aeiou") "An" else "A"
    return "$article $phrase"
}

/** "a", "a and b", "a, b and c". */
internal fun joinWords(words: List<String>): String =
    when (words.size) {
        0 -> ""
        1 -> words.single()
        else -> words.dropLast(1).joinToString(", ") + " and " + words.last()
    }
