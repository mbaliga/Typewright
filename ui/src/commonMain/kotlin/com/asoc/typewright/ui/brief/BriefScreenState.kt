// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.brief

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.asoc.typewright.brief.AskPosition
import com.asoc.typewright.brief.BriefModel
import com.asoc.typewright.brief.Dimension
import com.asoc.typewright.brief.Door
import com.asoc.typewright.brief.Drawing
import com.asoc.typewright.brief.FontView
import com.asoc.typewright.brief.Level
import com.asoc.typewright.brief.Step
import com.asoc.typewright.brief.UseCase
import com.asoc.typewright.brief.UseQuestion
import com.asoc.typewright.brief.WrapUp
import com.asoc.typewright.brief.answering
import com.asoc.typewright.brief.previousStepId
import com.asoc.typewright.brief.questionFor
import com.asoc.typewright.brief.reopening
import com.asoc.typewright.brief.resolveBrief
import com.asoc.typewright.brief.skipping
import com.asoc.typewright.brief.withConfirmedStyleClass
import com.asoc.typewright.brief.withEthos
import com.asoc.typewright.brief.withFeelingToggled
import com.asoc.typewright.brief.withGenre
import com.asoc.typewright.brief.withIntentUse
import com.asoc.typewright.brief.withLevel
import com.asoc.typewright.brief.withMeasuredLevels
import com.asoc.typewright.brief.withReferenceToggled
import com.asoc.typewright.brief.withSignatureToggled
import com.asoc.typewright.brief.withUseOption
import com.asoc.typewright.brief.withUseToggled
import com.asoc.typewright.brief.withWordmark
import com.asoc.typewright.project.Brief
import com.asoc.typewright.project.BriefSource
import com.asoc.typewright.project.ContrastModel
import com.asoc.typewright.project.EditResult
import com.asoc.typewright.project.Ethos
import com.asoc.typewright.project.FontState
import com.asoc.typewright.project.MetaChange
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.Use
import com.asoc.typewright.qa.corpus.StyleAtlas
import com.asoc.typewright.qa.corpus.loadStyleAtlas
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.brief.askPosition as positionIn
import com.asoc.typewright.brief.wrapUp as wrapUpOf

/** The Brief screen's three tabs, left to right: the questions, what they add up to, and what to do about it. */
enum class BriefScreenTab(
    val label: String,
) {
    ASK("Ask"),
    BRIEF("Brief"),
    GUIDE("Guide"),
    ;

    companion object {
        /** Every tab, left to right. */
        val ORDERED: List<BriefScreenTab> = entries.toList()
    }
}

/** Said whenever the answers live only in this session (CLAUDE.md law 4: a stub says so). */
const val BRIEF_NOT_SAVED_NOTE: String = "Not saved: no project is open"

/** Said when the answers went into the open project. */
const val BRIEF_SAVED_NOTE: String = "Saved in the project"

/** Said when the open project would not take the answers. */
const val BRIEF_READ_ONLY_NOTE: String = "Not saved: the project is read-only"

/** Said where the style atlas cannot load, so no range is measured (CLAUDE.md law 5). */
const val BRIEF_ATLAS_UNAVAILABLE_NOTE: String =
    "Style atlas unavailable on this platform: questions work, measured ranges don't"

/** Said by the guide when there are no letters to measure. */
const val BRIEF_NO_DRAWING_NOTE: String = "Draw some letters and the guide will measure them"

/**
 * Opens [BriefScreen] on [Door], or on the doors (null) when nothing more specific is meant --
 * provided once, near the root, by [com.asoc.typewright.ui.TypewrightApp], the same shape
 * [com.asoc.typewright.ui.project.LocalProjectWorkspace] already uses so a composable two or more
 * levels down (WP4's "New project…" palette entry, WP5's Workbook Task 1 "Pick in the Brief")
 * can open the Brief without a widened parameter list the whole way up. Null in any composition
 * that never provides it (every existing screenshot test).
 */
val LocalOpenBrief: ProvidableCompositionLocal<((Door?) -> Unit)?> = staticCompositionLocalOf { null }

/**
 * Opens [BriefScreen] on the new-project step ([BriefScreenUiState.beginNewProject]): what "New
 * project…" calls once the project exists. Null where nothing provided it.
 */
val LocalOpenNewProjectBrief: ProvidableCompositionLocal<(() -> Unit)?> = staticCompositionLocalOf { null }

/**
 * The Brief screen's state: the person's [ethos], what it resolves to ([model]), their own
 * letters ([drawing]), and which tab and door are showing. A plain class over Compose state, so a
 * test builds one and drives it with the same actions the screen's buttons call.
 *
 * Every change goes through one place, which resolves the model again and hands the new ethos to
 * [persist]. With no [persist] there is no open project: the answers stay in memory and
 * [saveNote] says so. [atlas] is null where the style atlas cannot load; the questions still work,
 * the measured ranges are absent.
 *
 * A brief that already has answers opens on the Guide, the page a person comes back to while
 * drawing; an empty one opens on the doors.
 */
class BriefScreenUiState(
    initialEthos: Ethos = Ethos(),
    val atlas: StyleAtlas? = null,
    initialDrawing: Drawing = Drawing.EMPTY,
    initialTab: BriefScreenTab = if (initialEthos.isEmpty) BriefScreenTab.ASK else BriefScreenTab.GUIDE,
    initialDoor: Door? = null,
    initialConfirmedStyleClass: String? = null,
    private val persist: ((Ethos) -> Boolean)? = null,
    private val persistConfirmedStyleClass: ((String?) -> Boolean)? = null,
) {
    var tab: BriefScreenTab by mutableStateOf(initialTab)
        private set

    /** The door the person went in by, or null while they are still choosing one. */
    var door: Door? by mutableStateOf(initialDoor)
        private set

    var ethos: Ethos by mutableStateOf(initialEthos)
        private set

    /** What [ethos] and [atlas] resolve to, recomputed on every change. */
    var model: BriefModel by mutableStateOf(resolveBrief(initialEthos, atlas))
        private set

    /** The person's own letters, or [Drawing.EMPTY] with no project font. */
    var drawing: Drawing by mutableStateOf(initialDrawing)
        private set

    /** True once the person asked for more questions than the door's own. */
    var refining: Boolean by mutableStateOf(false)
        private set

    /** A dimension the person chose to revisit (a tension to settle), asked before anything else; null otherwise. */
    var focus: Dimension? by mutableStateOf(null)
        private set

    /** Whether the last change reached a project, in words. */
    var saveNote: String by mutableStateOf(if (persist == null) BRIEF_NOT_SAVED_NOTE else BRIEF_SAVED_NOTE)
        private set

    /** The corpus class the person has actively confirmed reads as them (WP3 "Confirm"), or null. */
    var confirmedStyleClass: String? by mutableStateOf(initialConfirmedStyleClass)
        private set

    /** True when answers are written to an open project. */
    val saved: Boolean get() = persist != null

    /**
     * True while the Brief is the new-project flow's second step, laid out as the canvas's
     * `NewProject` board: name, scripts, intent, closest class and a line about it, instead of the
     * doors. Any other way into the Brief ends it.
     */
    var newProject: Boolean by mutableStateOf(false)
        private set

    /**
     * The Serif/Sans chip on that board: which classes "Closest class" offers. It starts as what
     * the ethos already implies, and it is a filter, not a second copy of the answer: the model the
     * brief records is read back from the ethos.
     */
    var intentModel: ContrastModel? by mutableStateOf(null)
        private set

    /** The model and use the ethos implies, the same ones [saveEthos] writes into the project's brief. */
    val implied: Brief get() = Brief(source = BriefSource.BLANK).withEthos(ethos)

    /** Opens the new-project step: the board first, then on to the Brief proper. */
    fun beginNewProject() {
        newProject = true
        tab = BriefScreenTab.ASK
        door = null
        focus = null
        refining = false
        intentModel = implied.model
    }

    /** Leaves the new-project step for the Brief itself: the guide once there are answers, otherwise the doors. */
    fun finishNewProject() {
        newProject = false
        tab = if (ethos.isEmpty) BriefScreenTab.ASK else BriefScreenTab.GUIDE
    }

    /** "Not sure" on Closest class: leaves the board for the other doors. */
    fun notSure() {
        newProject = false
        tab = BriefScreenTab.ASK
        chooseDoor(null)
    }

    /** Filters the classes by Serif or Sans; choosing the one already chosen clears it. */
    fun chooseIntentModel(model: ContrastModel?) {
        intentModel = if (intentModel == model) null else model
    }

    /** Makes [use] the main use (Text or Display); null clears it. */
    fun chooseIntentUse(use: Use?) = commit(ethos.withIntentUse(use))

    /** Shows [tab]. */
    fun selectTab(tab: BriefScreenTab) {
        newProject = false
        this.tab = tab
    }

    /** Goes in by [door]; null goes back to choosing one. The answers stay: another door skips what is already answered. */
    fun chooseDoor(door: Door?) {
        newProject = false
        this.door = door
        refining = false
        focus = null
    }

    /** Replaces the letters the guide measures. */
    fun updateDrawing(drawing: Drawing) {
        if (drawing != this.drawing) this.drawing = drawing
    }

    /**
     * The question to show and how far through the person is: the [focus] dimension first, then the
     * chosen door's first open question; null while no door is chosen and nothing is in focus.
     */
    fun askPosition(): AskPosition? {
        val revisit = focus
        val chosen = door ?: return revisit?.let { AskPosition(questionFor(it, model), 0, 0, focused = true) }
        return positionIn(chosen, model, drawing, refining, revisit)
    }

    /** What the chosen door's questions add up to once they are through; null while no door is chosen. */
    fun wrapUp(): WrapUp? = door?.let { wrapUpOf(it, model, drawing) }

    /** Asks the questions the door left out, after its own. */
    fun askMore() {
        refining = true
        focus = null
        tab = BriefScreenTab.ASK
    }

    /** Asks about [dimension] now, whatever the door: to settle a tension or change one answer. */
    fun askAbout(dimension: Dimension) {
        focus = dimension
        tab = BriefScreenTab.ASK
    }

    /** Leaves a revisited question without changing its answer. */
    fun leaveFocus() {
        focus = null
    }

    /** Clears every answer and goes back to the doors. */
    fun startOver() {
        door = null
        refining = false
        focus = null
        tab = BriefScreenTab.ASK
        commit(Ethos())
    }

    /** Sets the genre (null clears it). */
    fun chooseGenre(key: String?) = commit(ethos.withGenre(key))

    /** Adds or removes a use. */
    fun toggleUse(id: String) = commit(ethos.withUseToggled(id))

    /** Answers one of a use's own questions. */
    fun answerUse(
        use: UseCase,
        question: UseQuestion,
        optionId: String,
    ) = commit(ethos.withUseOption(use, question, optionId))

    /** Adds or removes a feeling; a fourth is refused. */
    fun toggleFeeling(key: String) = commit(ethos.withFeelingToggled(key))

    /** Adds or removes a reference face; a fourth is refused. */
    fun toggleReference(family: String) = commit(ethos.withReferenceToggled(family))

    /** Answers [dimension] with [level], or clears it. Answering a revisited question ends the visit. */
    fun answerLevel(
        dimension: Dimension,
        level: Level?,
    ) {
        if (focus == dimension) focus = null
        commit(ethos.withLevel(dimension, level))
    }

    /** Adopts what the person's own letters measure, keeping any answer already given. */
    fun adoptMeasured(measured: Map<Dimension, Level>) = commit(ethos.withMeasuredLevels(measured))

    /** Sets the wordmark's letters (blank clears them). */
    fun setWordmark(text: String?) = commit(ethos.withWordmark(text))

    /** Marks [dimension] as a deliberate departure from the genre, or unmarks it. */
    fun toggleSignature(dimension: Dimension) = commit(ethos.withSignatureToggled(dimension))

    /**
     * Confirms [key] (a node-economy corpus class) as the project's own style class, from a
     * reading in the Mirror door or the Brief tab -- never set by itself, only by a person's own
     * tap on "This is it" (WP3 "the style is never confirmed silently"); null un-confirms. A
     * no-op with no project open: there is nothing to confirm into.
     */
    fun confirmStyleClass(key: String?) {
        val save = persistConfirmedStyleClass ?: return
        if (save(key)) confirmedStyleClass = key
    }

    /** Moves on from [step] with what is chosen so far (for the multiple-choice steps, which have no single answer). */
    fun confirm(step: Step) = commit(ethos.answering(step.id))

    /** Moves on from [step] without answering it ("I'm not sure"); on a revisited question, keeps the answer it had. */
    fun skip(step: Step) {
        if (step is Step.AskDimension && step.dimension == focus) focus = null
        commit(ethos.skipping(step.id))
    }

    /**
     * The line under the page title (the canvas's page header): where in the Brief the person is, in
     * words. Questions adapt to the answers, so it counts what has been asked, never "of M".
     */
    val subtitle: String
        get() {
            val chosen = door
            val position = askPosition()
            return when {
                newProject -> "Step 2 of 3 · the brief"
                tab == BriefScreenTab.BRIEF -> "What your answers add up to"
                tab == BriefScreenTab.GUIDE -> "What to draw, and why"
                focus != null -> "Changing one answer · the others stay"
                chosen == null || position == null -> "Six ways in"
                position.step == null -> "${chosen.label} · ${position.total} asked"
                else -> "${chosen.label} · question ${position.answered + 1}"
            }
        }

    /**
     * What the deck's Back does: out of a revisited question, else back to Ask from another tab,
     * else the last question, and at the first the doors. False at the doors, where there is nowhere
     * left inside the Brief and the screen itself should close.
     */
    fun back(): Boolean =
        when {
            newProject -> {
                false
            }

            focus != null || tab != BriefScreenTab.ASK || door != null -> {
                if (focus == null && tab != BriefScreenTab.ASK) tab = BriefScreenTab.ASK else goBack()
                true
            }

            else -> {
                false
            }
        }

    /**
     * Goes back one question: out of a revisited question first; otherwise the last question with an
     * answer or a skip is asked again (the answers stay); at the first question, back to the doors.
     */
    fun goBack() {
        if (focus != null) {
            focus = null
            return
        }
        val chosen = door ?: return
        val last = previousStepId(chosen, model, drawing, refining)
        if (last == null) chooseDoor(null) else commit(ethos.reopening(last))
    }

    private fun commit(next: Ethos) {
        if (next == ethos) return
        ethos = next
        model = resolveBrief(next, atlas)
        val save = persist
        saveNote =
            when {
                save == null -> BRIEF_NOT_SAVED_NOTE
                save(next) -> BRIEF_SAVED_NOTE
                else -> BRIEF_READ_ONLY_NOTE
            }
    }
}

/**
 * The person's own letters as the Brief reads them: the default master's glyphs, each keyed by its
 * first code point. [Drawing.EMPTY] when there is no font or nothing in it has a code point in the
 * Basic Multilingual Plane. Measuring reads every style letter, so call this once per change of
 * the font, not per frame.
 */
fun briefDrawingOf(font: FontState?): Drawing {
    val master = font?.masters?.firstOrNull { it.isDefault } ?: font?.masters?.firstOrNull() ?: return Drawing.EMPTY
    val glyphs =
        master.ufo.glyphs
            .mapNotNull { glyph ->
                glyph.unicodes
                    .firstOrNull()
                    ?.takeIf { it in 0..MAX_BMP_CODE_POINT }
                    ?.let { it.toChar() to glyph }
            }.toMap()
    if (glyphs.isEmpty()) return Drawing.EMPTY
    return Drawing.of(FontView(master.ufo.fontInfo.unitsPerEm ?: DEFAULT_UNITS_PER_EM, glyphs))
}

private const val MAX_BMP_CODE_POINT = 0xFFFF
private const val DEFAULT_UNITS_PER_EM = 1000

/** The style atlas, or null on a target that cannot load it (the browser build today); never an empty atlas. */
fun loadBriefAtlas(): StyleAtlas? = runCatching { loadStyleAtlas() }.getOrNull()

/** Loads the style atlas once per composition. */
@Composable
fun rememberBriefAtlas(): StyleAtlas? = remember { loadBriefAtlas() }

/**
 * Keeps the Brief's state while its screen is closed, so the answers, the door and the tab survive
 * a trip to Draw and back; with no project open the answers live only here. One per app: opening
 * another project starts a new state from that project's brief. The style atlas loads on first use.
 */
class BriefStateKeeper {
    private var atlasLoaded = false
    private var loadedAtlas: StyleAtlas? = null
    private var owner: ProjectSession? = null
    private var kept: BriefScreenUiState? = null

    /** The style atlas, loaded the first time it is asked for; null where it cannot load. */
    val atlas: StyleAtlas?
        get() {
            if (!atlasLoaded) {
                loadedAtlas = loadBriefAtlas()
                atlasLoaded = true
            }
            return loadedAtlas
        }

    /** The state kept for [session], or a new one from [make] when there is none or the project changed. */
    fun stateFor(
        session: ProjectSession?,
        make: () -> BriefScreenUiState,
    ): BriefScreenUiState {
        val current = kept
        if (current != null && owner === session) return current
        return make().also {
            kept = it
            owner = session
        }
    }
}

/** Remembers one [BriefStateKeeper] for the composition's lifetime. */
@Composable
fun rememberBriefStateKeeper(): BriefStateKeeper = remember { BriefStateKeeper() }

/**
 * The [BriefScreenUiState] for the open project, kept by [keeper] across closing and reopening the
 * screen: its answers are read from the project's brief and written back with every change. With
 * no project open the answers stay in memory. The letters follow the project's font while the
 * screen is open; measuring them reads every style letter, so it does not run while it is closed.
 */
@Composable
fun rememberBriefScreenUiState(keeper: BriefStateKeeper = rememberBriefStateKeeper()): BriefScreenUiState {
    val session: ProjectSession? =
        LocalProjectWorkspace.current
            ?.current
            ?.collectAsState()
            ?.value
    val projectState = session?.state?.collectAsState()?.value
    val drawing = remember(projectState?.font) { briefDrawingOf(projectState?.font) }
    val state =
        remember(keeper, session) {
            keeper.stateFor(session) {
                val brief =
                    session
                        ?.state
                        ?.value
                        ?.meta
                        ?.manifest
                        ?.brief
                BriefScreenUiState(
                    initialEthos = brief?.ethos ?: Ethos(),
                    atlas = keeper.atlas,
                    initialDrawing = drawing,
                    initialConfirmedStyleClass = brief?.styleClass?.confirmed,
                    persist = session?.let { live -> { ethos: Ethos -> saveEthos(live, ethos) } },
                    persistConfirmedStyleClass = session?.let { live -> { key: String? -> saveConfirmedStyleClass(live, key) } },
                )
            }
        }
    SideEffect { state.updateDrawing(drawing) }
    return state
}

/** Writes [ethos] into [session]'s brief; false when the project refuses it. */
private fun saveEthos(
    session: ProjectSession,
    ethos: Ethos,
): Boolean {
    val brief = session.state.value.meta.manifest.brief
    return session.update(MetaChange.SetBrief(brief.withEthos(ethos))) !is EditResult.Refused
}

/** Writes [key] into [session]'s brief as its confirmed style class; false when the project refuses it. */
private fun saveConfirmedStyleClass(
    session: ProjectSession,
    key: String?,
): Boolean {
    val brief = session.state.value.meta.manifest.brief
    return session.update(MetaChange.SetBrief(brief.withConfirmedStyleClass(key))) !is EditResult.Refused
}
