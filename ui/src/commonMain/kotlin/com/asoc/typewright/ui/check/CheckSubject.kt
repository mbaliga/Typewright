// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import com.asoc.typewright.compile.ProjectDirectory
import com.asoc.typewright.project.ProjectState
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import com.asoc.typewright.qa.corpus.StyleAtlas
import com.asoc.typewright.qa.report.CheckInputs
import com.asoc.typewright.qa.report.CheckReport
import com.asoc.typewright.qa.report.CheckStyle
import com.asoc.typewright.qa.report.importProvenanceOf
import com.asoc.typewright.qa.report.otherMasterNames
import com.asoc.typewright.ui.deck.StationStatus
import com.asoc.typewright.ui.project.OpenProject
import com.asoc.typewright.ui.project.ProjectDataPacks
import com.asoc.typewright.ui.project.ProjectFont
import com.asoc.typewright.ui.project.projectStyle

/**
 * The open project as Check reads it: the [inputs] layer two measures (always the UFO, never a compiled
 * binary), a way to take a consistent [snapshot] of the project's files for layer one to compile, the path
 * of the master's UFO inside it, and a [version] that changes whenever the font does, so a layer-one result
 * can say which drawing it ran on.
 */
class CheckSubject(
    /** The name the page is titled with, `Hyle Deco Regular`. */
    val name: String,
    val inputs: CheckInputs,
    val masterPath: String,
    val snapshot: suspend () -> ProjectDirectory,
    val version: Any,
) {
    /** The file name layer one reports against. */
    val binaryName: String get() = name.replace(' ', '-') + ".ttf"

    /** Whether the project has an outline to check at all. */
    val hasGlyphs: Boolean get() = inputs.project.glyphs.isNotEmpty()
}

/**
 * Check's view of [project]: its default master's font, judged against [ProjectStyle] (the class the person
 * confirmed or declared, else the one the detector reads, which every line that uses it calls our heuristic),
 * with what the project records of where its outlines began. Null when the project has no master font.
 */
fun checkSubjectOf(
    project: OpenProject,
    corpus: NodeEconomyCorpus? = ProjectDataPacks.corpus,
    atlas: StyleAtlas? = ProjectDataPacks.atlas,
): CheckSubject? {
    val master = project.master ?: return null
    val font = ProjectFont.of(master)
    val style = projectStyle(project.manifest, font, atlas)
    val state: ProjectState = project.state
    val info = master.ufo.fontInfo
    val family = info.familyName?.takeIf { it.isNotBlank() } ?: project.manifest.name
    return CheckSubject(
        name = listOfNotNull(family, info.styleName ?: master.styleName).distinct().joinToString(" "),
        inputs =
            CheckInputs(
                project = master.ufo,
                style = style?.let { CheckStyle(it.key, it.name.lowercase(), it.isHeuristic) },
                corpus = corpus,
                provenance = importProvenanceOf(master, state.font.locks, project.manifest.brief.source),
                otherMasters = otherMasterNames(state.font.masters, master),
            ),
        masterPath = master.path,
        snapshot = { project.session.buildSnapshot(includeBuild = false) },
        version = state.font,
    )
}

/**
 * The shape the deck's Check label carries once the report is known: ● (the same mark as Trace's "to refit")
 * when any check fails, ✓ when none does, and no shape while there is no report to read.
 */
fun checkStationStatus(report: CheckReport?): StationStatus? =
    report?.let { if (it.counts.fail > 0) StationStatus.TO_REFIT else StationStatus.DONE }
