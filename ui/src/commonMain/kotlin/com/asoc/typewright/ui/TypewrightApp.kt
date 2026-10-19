// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.asoc.typewright.brief.Door
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.ui.brief.BriefScreen
import com.asoc.typewright.ui.brief.LocalOpenBrief
import com.asoc.typewright.ui.brief.LocalOpenNewProjectBrief
import com.asoc.typewright.ui.brief.rememberBriefScreenUiState
import com.asoc.typewright.ui.brief.rememberBriefStateKeeper
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.LocalSystemBack
import com.asoc.typewright.ui.deck.SystemBack
import com.asoc.typewright.ui.learn.LearnScreen
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.LocalZipTransferSupported
import com.asoc.typewright.ui.project.rememberProjectWorkspace
import com.asoc.typewright.ui.sheet.TypewrightSheet
import com.asoc.typewright.ui.shell.HomeScreen
import com.asoc.typewright.ui.shell.LocalSectionNav
import com.asoc.typewright.ui.shell.MapScreen
import com.asoc.typewright.ui.shell.SectionNav
import com.asoc.typewright.ui.shell.StubSection
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.workbook.WorkbookScreen
import com.asoc.typewright.ui.workbook.rememberWorkbookScreenUiState

/**
 * The app: one router, one control deck.
 *
 * The router ([TypewrightAppNavState]) holds which of the nine sections is current, in the order
 * the deck's section arc names them, and whether the Brief is open over it. Every screen draws the
 * deck itself and reaches the router through [LocalSectionNav], so a tap, a swipe, the arrow or digit
 * keys or the Map take the person to any section from any screen. Draw and Space are two regions of
 * one sheet ([TypewrightSheet], kept in composition so its camera and tool survive a trip to another
 * section); Home, Learn and Workbook are screens; Capture, Trace, Check and Ship are not built, and
 * say so inside the real deck rather than being hidden (law 4). The Brief opens over the section it
 * was opened from and keeps its answers when the person leaves it ([rememberBriefStateKeeper]).
 *
 * [workspace] is the app-wide project holder, reachable everywhere through [LocalProjectWorkspace];
 * its own default is an in-memory stopgap, and each platform's `main()` passes its own so its
 * lifecycle hooks can reach the same instance. [supportsZipTransfer] is passed by the web app only.
 * [systemBack] lets the platform's back reach whichever screen is on top.
 */
@Composable
fun TypewrightApp(
    modifier: Modifier = Modifier,
    navState: TypewrightAppNavState = rememberTypewrightAppNavState(),
    workspace: ProjectWorkspace = rememberProjectWorkspace(),
    supportsZipTransfer: Boolean = false,
    systemBack: SystemBack? = null,
) {
    // Guarded: a caller that already holds a live session across this composable's own recreate
    // must not have it silently replaced by whatever the workspace would reopen from disk.
    LaunchedEffect(workspace) { if (workspace.current.value == null) workspace.reopenLast() }
    val briefKeeper = rememberBriefStateKeeper()
    val texture = CanvasTextures.DEFAULT
    val nav =
        SectionNav(
            current = navState.section,
            goTo = navState::goToSection,
            openMap = { navState.mapOpen = true },
        )
    CompositionLocalProvider(
        LocalProjectWorkspace provides workspace,
        LocalZipTransferSupported provides supportsZipTransfer,
        LocalOpenBrief provides navState::openBrief,
        LocalSystemBack provides systemBack,
        LocalOpenNewProjectBrief provides navState::openNewProjectBrief,
        LocalSectionNav provides nav,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            val onSheet = navState.section == DeckSection.DRAW || navState.section == DeckSection.SPACE
            TypewrightSheet(
                modifier = Modifier.fillMaxSize(),
                section = if (onSheet) navState.section else DeckSection.DRAW,
                onSectionChanged = { if (onSheet && !navState.showBrief) navState.section = it },
                deckVisible = onSheet && !navState.showBrief && !navState.mapOpen,
            )
            when {
                navState.mapOpen -> {
                    MapScreen(
                        texture = texture,
                        current = navState.section,
                        onGo = {
                            navState.mapOpen = false
                            navState.goToSection(it)
                        },
                        onClose = { navState.mapOpen = false },
                    )
                }

                navState.showBrief -> {
                    val briefState = rememberBriefScreenUiState(briefKeeper)
                    // Callers can ask for a specific door (References from Workbook Task 1); applied once, then cleared.
                    LaunchedEffect(navState.pendingBriefDoor) {
                        navState.pendingBriefDoor?.let { door ->
                            briefState.chooseDoor(door)
                            navState.pendingBriefDoor = null
                        }
                    }
                    LaunchedEffect(navState.pendingNewProject) {
                        if (navState.pendingNewProject) {
                            briefState.beginNewProject()
                            navState.pendingNewProject = false
                        }
                    }
                    BriefScreen(
                        modifier = Modifier.fillMaxSize(),
                        texture = texture,
                        // Back from the doors leaves the Brief for the section it was opened from.
                        onBack = navState::closeBrief,
                        state = briefState,
                        // Start drawing leaves the Brief for the sheet.
                        onStartDrawing = { navState.goToSection(DeckSection.DRAW) },
                        onOpenLearn = { navState.goToSection(DeckSection.LEARN) },
                        section = navState.briefFrom,
                        onSection = navState::goToSection,
                        onMap = nav.openMap,
                    )
                }

                navState.section == DeckSection.HOME -> {
                    HomeScreen(
                        texture = texture,
                        modifier = Modifier.fillMaxSize(),
                        onOpenDraw = { navState.goToSection(DeckSection.DRAW) },
                        onOpenWorkbook = { navState.goToSection(DeckSection.WORKBOOK) },
                    )
                }

                navState.section == DeckSection.LEARN -> {
                    LearnScreen(modifier = Modifier.fillMaxSize(), texture = texture, onBack = navState::closeLearn)
                }

                navState.section == DeckSection.WORKBOOK -> {
                    WorkbookScreen(
                        modifier = Modifier.fillMaxSize(),
                        texture = texture,
                        onBack = navState::closeWorkbook,
                        uiState = rememberWorkbookScreenUiState(),
                    )
                }

                !onSheet -> {
                    StubSection(texture = texture, section = navState.section, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * The router's state: the current [section], whether the [Map][mapOpen] is open, and whether the
 * Brief is open over the section it was opened from ([briefFrom]). A plain class over Compose state,
 * constructible in any state with no simulated gesture. [showLearn], [showWorkbook] and [showBrief]
 * read the same state, so each screen is reachable and testable by its own name.
 */
class TypewrightAppNavState(
    initialShowLearn: Boolean = false,
    initialShowWorkbook: Boolean = false,
    initialShowBrief: Boolean = false,
    initialSection: DeckSection =
        when {
            initialShowLearn -> DeckSection.LEARN
            initialShowWorkbook -> DeckSection.WORKBOOK
            else -> DeckSection.HOME
        },
    initialBriefFrom: DeckSection = DeckSection.HOME,
) {
    /** The section the app is on: Home is where it starts. */
    var section: DeckSection by mutableStateOf(initialSection)
        internal set

    var showBrief: Boolean by mutableStateOf(initialShowBrief)
        internal set

    /** True while the Map (S24) covers the screen. */
    var mapOpen: Boolean by mutableStateOf(false)

    val showLearn: Boolean get() = section == DeckSection.LEARN && !showBrief
    val showWorkbook: Boolean get() = section == DeckSection.WORKBOOK && !showBrief

    /** A door [openBrief] was asked to open straight into, consumed (and cleared) by the Brief branch. */
    var pendingBriefDoor: Door? by mutableStateOf(null)
        internal set

    /** [openNewProjectBrief] was called: the Brief opens on the new-project step, consumed like [pendingBriefDoor]. */
    var pendingNewProject: Boolean by mutableStateOf(false)
        internal set

    /** The section the Brief was opened from, which stays current and live on its deck: Home, or Workbook when opened from Task 1. */
    var briefFrom: DeckSection by mutableStateOf(initialBriefFrom)
        internal set

    /**
     * Goes to [target] from anywhere, closing the Brief and the Map. The Brief's answers are kept
     * either way, because its state lives outside this screen.
     */
    fun goToSection(target: DeckSection) {
        showBrief = false
        mapOpen = false
        section = target
    }

    fun openLearn() = goToSection(DeckSection.LEARN)

    /** Back from Learn goes up one level: Home. */
    fun closeLearn() = goToSection(DeckSection.HOME)

    fun openWorkbook() = goToSection(DeckSection.WORKBOOK)

    /** Back from Workbook goes up one level: Home. */
    fun closeWorkbook() = goToSection(DeckSection.HOME)

    /**
     * Opens the Brief over [from] (Home, or Workbook from Task 1), on [door] when given, otherwise on
     * the doors, or the Guide once there are answers.
     */
    fun openBrief(
        door: Door? = null,
        from: DeckSection = DeckSection.HOME,
    ) {
        briefFrom = from
        section = from
        mapOpen = false
        showBrief = true
        pendingBriefDoor = door
    }

    /** What "New project…" calls once the project exists: opens the Brief on S02's second step, the new-project board. */
    fun openNewProjectBrief() {
        openBrief(null)
        pendingNewProject = true
    }

    /** Back from the Brief's doors: leaves it for the section it was opened from. */
    fun closeBrief() {
        showBrief = false
    }

    internal fun save(): List<Int> = listOf(section.ordinal, if (showBrief) 1 else 0, briefFrom.ordinal)

    internal companion object {
        fun restore(saved: List<Int>) =
            TypewrightAppNavState(
                initialShowBrief = saved[1] == 1,
                initialSection = DeckSection.entries[saved[0]],
                initialBriefFrom = DeckSection.entries[saved[2]],
            )

        val Saver = listSaver<TypewrightAppNavState, Int>(save = { it.save() }, restore = ::restore)
    }
}

/** Remembers a [TypewrightAppNavState] across recomposition and across the process being recreated, on Home unless told otherwise. */
@Composable
fun rememberTypewrightAppNavState(
    initialShowLearn: Boolean = false,
    initialShowWorkbook: Boolean = false,
    initialShowBrief: Boolean = false,
): TypewrightAppNavState =
    rememberSaveable(saver = TypewrightAppNavState.Saver) {
        TypewrightAppNavState(initialShowLearn, initialShowWorkbook, initialShowBrief)
    }
