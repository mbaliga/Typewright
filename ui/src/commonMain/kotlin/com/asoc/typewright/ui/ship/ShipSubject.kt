// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.ship

import com.asoc.typewright.brief.genreByKey
import com.asoc.typewright.brief.useById
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import com.asoc.typewright.qa.corpus.StyleAtlas
import com.asoc.typewright.qa.report.CheckInputs
import com.asoc.typewright.qa.report.CheckReport
import com.asoc.typewright.qa.report.ReviewOrder
import com.asoc.typewright.qa.report.familyNamesCompared
import com.asoc.typewright.qa.ship.GfCategory
import com.asoc.typewright.qa.ship.ShipBrief
import com.asoc.typewright.qa.ship.ShipFacts
import com.asoc.typewright.qa.ship.ShipFont
import com.asoc.typewright.qa.ship.ShipNames
import com.asoc.typewright.qa.ship.firstSentence
import com.asoc.typewright.ui.check.CheckSubject
import com.asoc.typewright.ui.check.checkSubjectOf
import com.asoc.typewright.ui.deck.StationStatus
import com.asoc.typewright.ui.project.OpenProject
import com.asoc.typewright.ui.project.ProjectDataPacks
import com.asoc.typewright.ui.project.ProjectFont
import com.asoc.typewright.ui.project.StyleOrigin
import com.asoc.typewright.ui.project.projectStyle

/**
 * The open project as Ship reads it: the [check] subject (the UFO layer two measures, a consistent snapshot of the project's files, and a
 * version that changes whenever the font does), the [facts] its font info states, what the font covers, what the brief says in words, and the
 * category Typewright reads out of a class the person gave. Everything is the project's own; nothing is a stand-in.
 */
class ShipSubject(
    val check: CheckSubject,
    val facts: ShipFacts,
    val font: ShipFont,
    val brief: ShipBrief,
    /** The category the person's declared or confirmed style class reads as, or null (no class, or a class only our heuristic read). */
    val categoryReading: GfCategory?,
    /** The class that reading came from, in words (`art deco display`), for the line that says so. */
    val categoryFromClass: String?,
    /** The folder name of the master's UFO in the project (`HyleDeco-Regular.ufo`). */
    val ufoFolderName: String,
) {
    /** The path of the master's UFO in the project's files. */
    val masterPath: String get() = check.masterPath

    /** Changes whenever the font does. */
    val version: Any get() = check.version

    /** The name the page is titled with: the family, else the project's name. */
    val title: String get() = facts.family ?: check.name
}

/** [project] as Ship reads it, or null when the project has no master font. */
fun shipSubjectOf(
    project: OpenProject,
    corpus: NodeEconomyCorpus? = ProjectDataPacks.corpus,
    atlas: StyleAtlas? = ProjectDataPacks.atlas,
): ShipSubject? {
    val check = checkSubjectOf(project, corpus, atlas) ?: return null
    val master = project.master ?: return null
    val font = ProjectFont.of(master)
    val ufo = master.ufo
    val ethos = project.manifest.brief.ethos
    val genre = genreByKey(project.manifest.brief.styleClass.confirmed ?: project.manifest.brief.styleClass.declared ?: ethos?.genre)
    val uses = ethos?.uses.orEmpty().mapNotNull { useById(it)?.name }
    val style = projectStyle(project.manifest, font, atlas)
    val fromClass = style?.takeIf { it.origin != StyleOrigin.DETECTED }
    return ShipSubject(
        check = check,
        facts = ShipFacts.of(ufo.fontInfo),
        font =
            ShipFont(
                glyphCount = ufo.glyphs.size,
                codepoints = ufo.glyphs.flatMapTo(HashSet()) { it.unicodes },
                scripts = project.manifest.scripts,
                capHeight = ufo.fontInfo.capHeight,
                xHeight = ufo.fontInfo.xHeight,
            ),
        brief = ShipBrief(genre = genre?.name, uses = uses),
        categoryReading = fromClass?.let { GfCategory.fromStyleKey(it.key) },
        categoryFromClass = fromClass?.name?.lowercase(),
        ufoFolderName = master.path.substringAfterLast('/'),
    )
}

/**
 * The shape the deck's Ship label carries: ● while Check has fails (the same mark Check's label gets), ✓ once this exact drawing has been
 * written, otherwise ○, nothing started. [failCount] is null while there is no report to read.
 */
fun shipStationStatus(
    failCount: Int?,
    shippedThisDrawing: Boolean,
): StationStatus? =
    when {
        shippedThisDrawing -> StationStatus.DONE
        failCount == null -> null
        failCount > 0 -> StationStatus.TO_REFIT
        else -> StationStatus.NOT_STARTED
    }

/**
 * What the Names check found in [report]: its severity, its first sentence, and how many family names it compared this one with (from
 * [inputs]), or null when there is no report or Check has no Names item.
 */
internal fun namesFound(
    report: CheckReport?,
    inputs: CheckInputs,
): ShipNames? {
    val item = report?.item(ReviewOrder.NAMES) ?: return null
    return ShipNames(item.severity, firstSentence(item.summary), familyNamesCompared(inputs).takeIf { it > 0 })
}
