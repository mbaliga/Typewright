// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.project

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.asoc.typewright.project.Master
import com.asoc.typewright.project.ProjectManifest
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.ProjectState
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.StationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The open project as a screen reads it: the [session] that owns it, its current [state], and the
 * default master as a [ProjectFont]. "Your font" everywhere in the app means this and nothing else;
 * with no project open there is no [OpenProject], and a screen says so rather than showing a
 * stand-in.
 */
class OpenProject(
    val session: ProjectSession,
    val state: ProjectState,
) {
    /** `typewright.json`'s metadata. */
    val manifest: ProjectManifest get() = state.meta.manifest

    /** The default master (the only one in V1), or null for a project with none. */
    val master: Master? get() = state.font.masters.firstOrNull { it.isDefault } ?: state.font.masters.firstOrNull()

    /** The default master's font, or null for a project with no master. */
    val font: ProjectFont? get() = master?.let { ProjectFont.of(it) }
}

/** The open project, observed: recomposes when it is edited, closed or replaced. Null when none is open. */
@Composable
fun rememberOpenProject(): OpenProject? {
    val session =
        LocalProjectWorkspace.current
            ?.current
            ?.collectAsState()
            ?.value ?: return null
    val state = session.state.collectAsState().value
    return OpenProject(session, state)
}

/** [rememberOpenProject]'s project judged against its style class, or null when there is no project, no class to judge by, or this target cannot read the corpus. */
@Composable
fun rememberProjectEconomy(project: OpenProject?): ProjectEconomy? {
    val font = project?.font ?: return null
    return projectEconomy(project.manifest, font)
}

private val NO_STATUS: (DeckSection) -> StationStatus? = { null }

/** How long the project must stay unchanged before the deck re-reads Trace's status: judging every glyph against its class is not work to do on each point of a drag. */
private const val TRACE_SETTLE_MILLIS = 300L

/**
 * The function the deck's section arc reads each station's status shape from
 * (`SectionNav.statusOf`), kept current as the project changes. It recomposes its caller only when
 * a status actually changes, not on every edit. Draw's status is cheap and follows every edit; Trace's
 * is worked out on a background thread once the project has been still for a moment, and shows no
 * shape until it has. [projectStationStatus] holds the rules.
 */
@OptIn(FlowPreview::class)
@Composable
fun rememberProjectStationStatus(
    /** The app's workspace; the router builds its [com.asoc.typewright.ui.shell.SectionNav] above the place it provides [LocalProjectWorkspace], so it passes the one it holds. */
    inWorkspace: ProjectWorkspace? = LocalProjectWorkspace.current,
): (DeckSection) -> StationStatus? {
    val session =
        inWorkspace
            ?.current
            ?.collectAsState()
            ?.value ?: return NO_STATUS
    val state = session.state.collectAsState()
    val draw by remember(session) { derivedStateOf { stationStatusFor(DeckSection.DRAW, state.value) } }
    val trace by produceState<StationStatus?>(null, session) {
        session.state
            .debounce(TRACE_SETTLE_MILLIS)
            .map { stationStatusFor(DeckSection.TRACE, it) }
            .flowOn(Dispatchers.Default)
            .collect { value = it }
    }
    return remember(session, draw, trace) {
        { section ->
            when (section) {
                DeckSection.DRAW -> draw
                DeckSection.TRACE -> trace
                else -> null
            }
        }
    }
}
