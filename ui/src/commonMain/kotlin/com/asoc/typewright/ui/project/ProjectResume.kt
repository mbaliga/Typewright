// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.project

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.asoc.typewright.project.ProjectState
import com.asoc.typewright.project.ResumePoint
import com.asoc.typewright.ui.deck.DeckSection

/**
 * The resume point for being on [section] with [glyph] in hand in the project [state] describes:
 * the section's lower-case name, the default master's id and the glyph, as `typewright.json`'s
 * sibling `recent-projects.json` records them (docs/PROJECT_MODEL.md §5.4).
 */
fun resumePointFor(
    section: DeckSection,
    glyph: String?,
    state: ProjectState,
): ResumePoint = ResumePoint(section.name.lowercase(), defaultMaster(state)?.id, glyph)

/**
 * The glyph a project should reopen on: [resume]'s glyph if the default master still has a glyph of
 * that name, else null (the glyph was deleted, or the project has not been left on one yet).
 */
fun glyphToRestore(
    resume: ResumePoint?,
    state: ProjectState,
): String? {
    val name = resume?.glyph ?: return null
    val master = defaultMaster(state) ?: return null
    return name.takeIf { wanted -> master.ufo.glyphs.any { it.name == wanted } }
}

private fun defaultMaster(state: ProjectState) = state.font.masters.firstOrNull { it.isDefault } ?: state.font.masters.firstOrNull()

/**
 * Keeps the open project's resume point current and restores it. When a project becomes current,
 * [onRestore] is called once with the glyph it was left on, or null when there is none to restore
 * (so a glyph left over from another project is dropped); only after that does every change of
 * [section] or [glyph] record a new resume point against the project's entry in the recent list.
 * The router calls it with its own section and current glyph, and `onRestore = { current = it }`.
 */
@Composable
fun ProjectResumeEffect(
    section: DeckSection,
    glyph: String?,
    onRestore: (String?) -> Unit,
) {
    val workspace = LocalProjectWorkspace.current ?: return
    val session = workspace.current.collectAsState().value ?: return
    var restored by remember(session) { mutableStateOf(false) }
    LaunchedEffect(session) {
        val recent = workspace.recents.value.firstOrNull { it.location == session.store.location }
        onRestore(glyphToRestore(recent?.resume, session.state.value))
        restored = true
    }
    LaunchedEffect(session, restored, section, glyph) {
        if (restored) workspace.rememberResume(resumePointFor(section, glyph, session.state.value))
    }
}
