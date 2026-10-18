// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.project.Brief
import com.asoc.typewright.project.ContrastModel
import com.asoc.typewright.project.Ethos
import com.asoc.typewright.project.Use

/**
 * Re-derives [Brief]'s pre-ethos fields (`model`, `use`, `styleClass.declared`) from [ethos], the
 * single source of truth docs/HANDOFF_BRIEF_FOLD.md records the owner's decision for: "one record,
 * one reader". `styleClass.confirmed` and `note` are the person's own and are left alone; every
 * write to a project's brief should go through this rather than setting `ethos` by itself, so the
 * two older fields never drift from what the Brief's own doors actually say.
 *
 * - `model`: the serifs answer when one is given ("none" -> [ContrastModel.SANS], anything else
 *   -> [ContrastModel.SERIF]), else the chosen genre's [GenreGroup] ([GenreGroup.SANS] ->
 *   [ContrastModel.SANS], [GenreGroup.SERIF] -> [ContrastModel.SERIF], any other group -> null).
 * - `use`: the first (most important) use, `"body-text"` -> [Use.TEXT]; `"headline"`,
 *   `"signage"`, `"wordmark"` or `"watch-face"` -> [Use.DISPLAY]; any other use, or none chosen,
 *   -> null.
 * - `styleClass.declared`: [Ethos.genre] when it names one of the ten node-economy classes
 *   ([CORPUS_GENRE_KEYS]), else null -- a genre outside that set has no Economy box to declare.
 */
fun Brief.withEthos(ethos: Ethos): Brief =
    copy(
        ethos = ethos,
        model = modelFor(ethos),
        use = useFor(ethos),
        styleClass = styleClass.copy(declared = declaredStyleClassFor(ethos)),
    )

private fun modelFor(ethos: Ethos): ContrastModel? {
    val serifsAnswer = ethos.levels[Dimension.SERIFS.id]
    if (serifsAnswer != null) {
        return if (serifsAnswer == Level.SERIFS_NONE.id) ContrastModel.SANS else ContrastModel.SERIF
    }
    return when (genreByKey(ethos.genre)?.group) {
        GenreGroup.SANS -> ContrastModel.SANS
        GenreGroup.SERIF -> ContrastModel.SERIF
        else -> null
    }
}

/** Use ids (brief/Uses.kt) whose own display answers S02's "display" chip. */
private val DISPLAY_USE_IDS = setOf("headline", "signage", "wordmark", "watch-face")

private fun useFor(ethos: Ethos): Use? =
    when (ethos.uses.firstOrNull()) {
        "body-text" -> Use.TEXT
        in DISPLAY_USE_IDS -> Use.DISPLAY
        else -> null
    }

private fun declaredStyleClassFor(ethos: Ethos): String? = ethos.genre?.takeIf { it in CORPUS_GENRE_KEYS }
