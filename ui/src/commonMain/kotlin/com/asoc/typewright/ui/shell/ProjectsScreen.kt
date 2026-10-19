// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asoc.typewright.project.OpenResult
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.RecentProject
import com.asoc.typewright.ui.brief.bodyStyle
import com.asoc.typewright.ui.brief.monoStyle
import com.asoc.typewright.ui.deck.DeckLeft
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.DeckToggle
import com.asoc.typewright.ui.project.InMemoryProjectFolderPicker
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.createUntitledProject
import com.asoc.typewright.ui.settings.APP_VERSION
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.accent
import com.asoc.typewright.ui.tokens.toColor
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** How long ago [then] was, in the mono line's own words: "just now", "5 min ago", "2 h ago", "3 d ago". */
fun agoText(
    now: Instant,
    then: Instant,
): String {
    val d: Duration = now - then
    return when {
        d.inWholeMinutes < 1 -> "just now"
        d.inWholeMinutes < 60 -> "${d.inWholeMinutes} min ago"
        d.inWholeHours < 24 -> "${d.inWholeHours} h ago"
        else -> "${d.inWholeDays} d ago"
    }
}

/** A project's folder as a muted line: the path on a desktop, a note where the platform keeps no path to show. */
fun locationText(location: ProjectLocation): String =
    when (location) {
        is ProjectLocation.FileSystem -> location.path
        is ProjectLocation.SafTree -> "a folder you chose on this device"
        is ProjectLocation.BrowserHandle -> "a folder you chose in this browser"
        is ProjectLocation.InMemory -> "held in memory only"
    }

/** The row's mono line: what the project holds, from the summary S01 caches, and when it was last edited. */
fun summaryLine(
    project: RecentProject,
    now: Instant,
): String =
    listOfNotNull(
        project.summary?.let { "${it.glyphs} glyphs · ${it.outliers} outliers" } ?: "not opened since it was listed",
        project.lastEdited?.let { "edited ${agoText(now, it)}" },
    ).joinToString(" · ")

/**
 * Projects (S01): the app's root, the only screen without a section. Each recent project is a
 * row: its name, a mono line from what S01 caches about it, and its folder, muted. A tap opens it on
 * the section it was left on; a long press shows Remove from list. The deck's left circle is hidden
 * (there is nowhere above), the toggle arc holds Recent · All, the right circle is + (a new
 * project), and the section arc is dimmed and inert until a project is open. With none listed it
 * says so and offers what exists today; Open a font and Try the sample are not built, and are not
 * offered as if they were.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectsScreen(
    texture: CanvasTexture,
    onOpened: (DeckSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val workspace = LocalProjectWorkspace.current
    val scope = rememberCoroutineScope()
    val recents =
        workspace
            ?.recents
            ?.collectAsState()
            ?.value
            .orEmpty()
            .sortedByDescending { it.lastOpened }
    val open = workspace?.current?.collectAsState()?.value
    val openNewProject = com.asoc.typewright.ui.brief.LocalOpenNewProjectBrief.current
    var all by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<ProjectLocation?>(null) }
    val now = remember { Clock.System.now() }
    val shown = if (all) recents else recents.take(RECENT_COUNT)
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()

    fun create() {
        val ws = workspace ?: return
        scope.launch { message = createUntitledProject(ws, InMemoryProjectFolderPicker(ws), onCreated = { openNewProject?.invoke() }) }
    }

    SectionScaffold(
        texture = texture,
        section = DeckSection.HOME,
        title = "Typewright",
        subtitle = "Version $APP_VERSION",
        modifier = modifier,
        toggles = listOf(DeckToggle("Recent", on = !all), DeckToggle("All", on = all)),
        onToggle = { all = it == 1 },
        left = DeckLeft.NONE,
        right = DeckRight.PLUS,
        rightLabel = "New project",
        onRight = ::create,
        dimSections = open == null,
        dimReason = "Open or start a project first",
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (message != null) BasicText(text = message.orEmpty(), style = monoStyle(muted))
            if (shown.isEmpty()) {
                BasicText(text = "No projects yet.", style = bodyStyle(fg, sizeSp = 15.0, weight = FontWeight.SemiBold))
                BasicText(
                    text = "Start a new project with the + below. Opening a font you already have is not built yet.",
                    style = bodyStyle(muted),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            for (project in shown) {
                ProjectRow(
                    project = project,
                    now = now,
                    texture = texture,
                    showRemove = removing == project.location,
                    onOpen = {
                        val ws = workspace
                        if (ws != null) {
                            scope.launch {
                                ws.closeCurrent()
                                message =
                                    when (val result = ws.open(project.location)) {
                                        is OpenResult.Opened, is OpenResult.NewerFormat -> {
                                            onOpened(sectionFor(project))
                                            null
                                        }

                                        is OpenResult.NotAProject -> {
                                            "That folder holds fonts but no Typewright project."
                                        }

                                        is OpenResult.Damaged -> {
                                            "Could not open it: ${result.message}"
                                        }
                                    }
                            }
                        }
                    },
                    onLongPress = { removing = if (removing == project.location) null else project.location },
                    onRemove = {
                        workspace?.let { ws -> scope.launch { ws.forget(project.location) } }
                        removing = null
                    },
                )
            }
        }
    }
}

private const val RECENT_COUNT = 5

/** One project: its name, a mono line from what S01 caches about it, and its folder, muted. A long press offers Remove from list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectRow(
    project: RecentProject,
    now: Instant,
    texture: CanvasTexture,
    showRemove: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onRemove: () -> Unit,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    Column(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onLongPress).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        BasicText(text = project.name, style = bodyStyle(fg, sizeSp = 15.0, weight = FontWeight.SemiBold))
        BasicText(text = summaryLine(project, now), style = monoStyle(fg, sizeSp = 9.5))
        BasicText(text = locationText(project.location), style = bodyStyle(muted, sizeSp = 11.0))
        if (showRemove) {
            BasicText(
                text = "Remove from list ›",
                style = bodyStyle(texture.accent.toColor(), weight = FontWeight.SemiBold),
                modifier = Modifier.combinedClickable(onClick = onRemove).padding(vertical = 6.dp),
            )
        }
    }
}

/** The section a project was left on, or Home when it has no resume point or the name is unknown. */
fun sectionFor(project: RecentProject): DeckSection =
    DeckSection.entries.firstOrNull { it.name.equals(project.resume?.section, ignoreCase = true) } ?: DeckSection.HOME
