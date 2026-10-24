// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
import com.asoc.typewright.ui.settings.AboutScreen
import com.asoc.typewright.ui.settings.SettingsScreen
import com.asoc.typewright.ui.settings.ThemeEditorScreen
import com.asoc.typewright.ui.settings.ThemeEditorState
import com.asoc.typewright.ui.sheet.TypewrightSheet
import com.asoc.typewright.ui.shell.GlyphNav
import com.asoc.typewright.ui.shell.HomeScreen
import com.asoc.typewright.ui.shell.LocalGlyphNav
import com.asoc.typewright.ui.shell.LocalOpenProjects
import com.asoc.typewright.ui.shell.LocalOpenSettings
import com.asoc.typewright.ui.shell.LocalSectionNav
import com.asoc.typewright.ui.shell.MapScreen
import com.asoc.typewright.ui.shell.ProjectsScreen
import com.asoc.typewright.ui.shell.SectionNav
import com.asoc.typewright.ui.shell.StubSection
import com.asoc.typewright.ui.theme.AppearanceState
import com.asoc.typewright.ui.theme.LocalAppearance
import com.asoc.typewright.ui.theme.Theme
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
    LaunchedEffect(workspace) {
        if (workspace.current.value == null) workspace.reopenLast()
        // First launch, or nothing to reopen: the app's root is Projects.
        if (workspace.current.value == null) navState.openProjects()
    }
    val briefKeeper = rememberBriefStateKeeper()
    val scope = rememberCoroutineScope()
    val appearance = remember(workspace) { AppearanceState(workspace.appConfig, scope) }
    LaunchedEffect(appearance) { appearance.load() }
    val texture = appearance.resolve(isSystemInDarkTheme()).toTexture()
    val density = LocalDensity.current
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
        LocalGlyphNav provides
            GlyphNav(
                current = navState.currentGlyph,
                open = navState::openGlyph,
                select = { navState.currentGlyph = it },
            ),
        LocalAppearance provides appearance,
        LocalOpenSettings provides navState::openSettings,
        LocalOpenProjects provides navState::openProjects,
        LocalDensity provides Density(density.density, density.fontScale * appearance.settings.textScale.toFloat()),
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            val onSheet = navState.section == DeckSection.DRAW || navState.section == DeckSection.SPACE
            TypewrightSheet(
                modifier = Modifier.fillMaxSize(),
                initialTexture = texture,
                section = if (onSheet) navState.section else DeckSection.DRAW,
                onSectionChanged = { if (onSheet && !navState.showBrief) navState.section = it },
                deckVisible = onSheet && !navState.showBrief && !navState.mapOpen,
            )
            when {
                navState.showProjects -> {
                    ProjectsScreen(texture = texture, onOpened = navState::goToSection, modifier = Modifier.fillMaxSize())
                }

                navState.overlay == AppOverlay.SETTINGS -> {
                    SettingsScreen(
                        appearance = appearance,
                        texture = texture,
                        section = navState.section,
                        onBack = navState::closeOverlay,
                        onCustomTheme = navState::openThemeEditor,
                        onAbout = navState::openAbout,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                navState.overlay == AppOverlay.THEME_EDITOR && navState.editorBase != null -> {
                    val base = navState.editorBase!!
                    val editor =
                        remember(navState.editorToken) {
                            ThemeEditorState(
                                base,
                                appearance.freshId(navState.editorExisting?.name ?: "${base.name} copy"),
                                navState.editorExisting,
                            )
                        }
                    ThemeEditorScreen(
                        state = editor,
                        section = navState.section,
                        onSave = {
                            appearance.saveTheme(it)
                            navState.closeOverlay()
                        },
                        onLeave = navState::closeOverlay,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                navState.overlay == AppOverlay.ABOUT -> {
                    AboutScreen(
                        texture = texture,
                        section = navState.section,
                        onBack = navState::closeOverlay,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

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

/** The states that sit under a section (SCREENS_V1 S21–S23). */
enum class AppOverlay { SETTINGS, THEME_EDITOR, ABOUT }

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

    /** The glyph Draw and Space are on, by name; null until one is chosen. The open project's resume point is where it is remembered. */
    var currentGlyph: String? by mutableStateOf(null)

    /** Makes [name] the current glyph and goes to Draw. */
    fun openGlyph(name: String) {
        currentGlyph = name
        goToSection(DeckSection.DRAW)
    }

    /** True while the Map (S24) covers the screen. */
    var mapOpen: Boolean by mutableStateOf(false)

    /** True while Projects (S01), the app's root, covers the screen. */
    var showProjects: Boolean by mutableStateOf(false)
        internal set

    fun openProjects() {
        showBrief = false
        mapOpen = false
        overlay = null
        showProjects = true
    }

    /** A state that sits under the current section, which stays current but dimmed and inert: Settings, the theme editor or About. */
    var overlay: AppOverlay? by mutableStateOf(null)
        internal set

    /** The theme the editor opened with, and the custom theme it is editing (null for a new one); [editorToken] changes on each open so its state starts fresh. */
    var editorBase: Theme? by mutableStateOf(null)
        private set
    var editorExisting: Theme? by mutableStateOf(null)
        private set
    var editorToken: Int by mutableStateOf(0)
        private set

    fun openSettings() {
        overlay = AppOverlay.SETTINGS
    }

    fun openAbout() {
        overlay = AppOverlay.ABOUT
    }

    fun openThemeEditor(
        base: Theme,
        existing: Theme? = null,
    ) {
        editorBase = base
        editorExisting = existing
        editorToken += 1
        overlay = AppOverlay.THEME_EDITOR
    }

    /** Back from an overlay: the editor and About go back to Settings, and Settings goes back to the screen under it. */
    fun closeOverlay() {
        overlay = if (overlay == AppOverlay.SETTINGS) null else AppOverlay.SETTINGS
    }

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
        overlay = null
        showProjects = false
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
