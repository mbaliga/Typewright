// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.trace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asoc.typewright.engine.trace.TraceParameters
import com.asoc.typewright.engine.trace.TraceStage
import com.asoc.typewright.project.EditResult
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.Unlock
import com.asoc.typewright.qa.corpus.Quartiles
import com.asoc.typewright.ui.capture.CaptureLibraries
import com.asoc.typewright.ui.capture.CaptureLibrary
import com.asoc.typewright.ui.capture.LinkText
import com.asoc.typewright.ui.capture.LocalImageDecoder
import com.asoc.typewright.ui.capture.TraceSource
import com.asoc.typewright.ui.capture.mono
import com.asoc.typewright.ui.capture.sentence
import com.asoc.typewright.ui.check.BoxPlot
import com.asoc.typewright.ui.check.BoxPlotSpec
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.DeckToggle
import com.asoc.typewright.ui.project.NoProjectState
import com.asoc.typewright.ui.project.OpenProject
import com.asoc.typewright.ui.project.ProjectDataPacks
import com.asoc.typewright.ui.project.projectStyle
import com.asoc.typewright.ui.project.rememberOpenProject
import com.asoc.typewright.ui.shell.LocalGlyphNav
import com.asoc.typewright.ui.shell.LocalSectionNav
import com.asoc.typewright.ui.shell.SectionScaffold
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.toColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the person has chosen on Trace so far. */
@Stable
class TracePageState(
    all: Boolean = false,
    raster: Boolean = true,
    stage: TraceStage? = null,
    parameters: TraceParameters = TraceParameters(),
) {
    /** Every stage on the scrubber, not only Clean, Fit and Review. */
    var all by mutableStateOf(all)

    /** The drawing underneath the outline. */
    var raster by mutableStateOf(raster)

    /** The stage on the scrubber; null until the first outcome picks one. */
    var stage by mutableStateOf(stage)

    /** The settings of the chain; changing one traces again. */
    var parameters by mutableStateOf(parameters)

    /** The last thing Accept said, in a line. */
    var message by mutableStateOf<String?>(null)
}

/**
 * Trace (S10) for the glyph in hand, taken from the project's captured sheets and SVG drawings. The chain runs on a background
 * thread each time a setting changes; Accept replaces the glyph's outline and locks it, then moves on to the next captured glyph
 * that is not locked. With no project open, or no captured drawing for the glyph, it says so and points to Capture.
 */
@Composable
fun TraceScreen(
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
    initialStage: TraceStage? = null,
) {
    val project = rememberOpenProject()
    val glyphNav = LocalGlyphNav.current
    val sectionNav = LocalSectionNav.current
    val decoder = LocalImageDecoder.current
    val library: CaptureLibrary? = project?.let { CaptureLibraries.forSession(it.session, decoder) }
    val items =
        library
            ?.items
            ?.collectAsState()
            ?.value
            .orEmpty()
    val sources = remember(items) { CaptureLibrary.sourcesOf(items) }
    val master = project?.master
    val locked: (String) -> Boolean = { name -> master != null && project != null && TraceFlow.isLocked(project.state, master.id, name) }
    val name = TraceFlow.opening(sources, glyphNav?.current, locked)
    val source = sources.lastOrNull { it.glyphName == name }
    // Keyed on what the drawing is, not on the object: the library hands out new objects whenever any capture finishes reading.
    val carried = remember { arrayOfNulls<String>(1) }
    val page =
        remember(source?.glyphName, source?.origin) {
            TracePageState(stage = initialStage).also {
                it.message = carried[0]
                carried[0] = null
            }
        }
    val metricLines =
        remember(source, master?.ufo?.fontInfo) {
            source
                ?.let { s ->
                    master?.let { TraceFlow.metricLines(it.ufo.fontInfo, s.script) }
                }.orEmpty()
        }
    val metricLabels =
        remember(source, master?.ufo?.fontInfo) {
            source
                ?.let { s ->
                    master?.let { TraceFlow.metricLabels(it.ufo.fontInfo, s.script) }
                }.orEmpty()
        }
    val params = page.parameters.copy(metricLines = metricLines)
    val outcome by produceState<TraceOutcome?>(null, source, params) {
        value = source?.let { s -> withContext(Dispatchers.Default) { TraceRunner.run(s, params) } }
    }
    val outcomeIsCurrent = TraceFlow.isCurrent(outcome, source, params)
    LaunchedEffect(outcome, page.all) {
        outcome?.let { page.stage = TraceView.defaultStage(it, page.all, page.stage) }
    }
    val style = project?.font?.let { projectStyle(project.manifest, it) }
    val box =
        if (style != null && name != null) {
            ProjectDataPacks.corpus?.takeIf { style.key in it.styleKeys }?.onCurveBox(style.key, name)
        } else {
            null
        }
    TraceFrame(
        texture = texture,
        hasProject = project != null,
        sourceCount = sources.size,
        source = source,
        outcome = outcome,
        outcomeIsCurrent = outcomeIsCurrent,
        page = page,
        locked = name != null && locked(name),
        box = box,
        styleName = style?.name,
        styleIsHeuristic = style?.isHeuristic ?: false,
        actions =
            TraceActions(
                back = { sectionNav?.goTo?.invoke(DeckSection.HOME) },
                capture = { sectionNav?.goTo?.invoke(DeckSection.CAPTURE) },
                handEdit = { name?.let { glyphNav?.open?.invoke(it) } },
                unlock = { unlock(project, master?.id, name, page) },
                accept = { acceptTrace(project, source, outcome, params, sources, glyphNav, page, carried) },
            ),
        modifier = modifier,
        metricLabels = metricLabels,
        metricLines = metricLines,
    )
}

private fun unlock(
    project: OpenProject?,
    master: String?,
    glyph: String?,
    page: TracePageState,
) {
    if (project == null || master == null || glyph == null) return
    val result = project.session.execute(Unlock(GlyphRef(master, glyph)))
    page.message = if (result is EditResult.Refused) result.refusal.message else "$glyph is unlocked. Accepting a trace locks it again."
}

/**
 * Accept for the glyph in hand. The outcome must have been made from exactly [source] and [params]: while a run is in flight the
 * outcome on screen belongs to the previous glyph or settings, and writing it would put the wrong outline into a glyph and lock it.
 * [carried] holds the confirmation across the move to the next glyph, whose page is a new one.
 */
internal fun acceptTrace(
    project: OpenProject?,
    source: TraceSource?,
    outcome: TraceOutcome?,
    params: TraceParameters,
    sources: List<TraceSource>,
    glyphNav: com.asoc.typewright.ui.shell.GlyphNav?,
    page: TracePageState,
    carried: Array<String?> = arrayOfNulls(1),
) {
    val master = project?.master
    if (project == null || master == null || source == null || outcome == null || !outcome.inkFound) return
    if (!TraceFlow.isCurrent(outcome, source, params)) {
        page.message = "This is still tracing ${source.glyphName} with the settings you just chose. Accept again when it has finished."
        return
    }
    when (val result = project.session.execute(TraceFlow.acceptCommand(master, source, outcome))) {
        is EditResult.Applied -> {
            val state = project.session.state.value
            val next = TraceFlow.nextUntraced(sources, source.glyphName) { TraceFlow.isLocked(state, master.id, it) }
            if (next != null) {
                val line = "${source.glyphName} accepted and locked. On to $next."
                page.message = line
                carried[0] = line
                glyphNav?.select?.invoke(next)
            } else {
                page.message = "${source.glyphName} accepted and locked. Every captured glyph is traced."
            }
        }

        EditResult.NoChange -> {
            page.message = "${source.glyphName} already has this outline."
        }

        is EditResult.Refused -> {
            page.message = result.refusal.message
        }
    }
}

/** What Trace needs done, handed to [TraceFrame] so a test can drive the page without a project. */
class TraceActions(
    val back: () -> Unit = {},
    val capture: () -> Unit = {},
    val handEdit: () -> Unit = {},
    val unlock: () -> Unit = {},
    val accept: () -> Unit = {},
)

/** The page drawn from its inputs with no coroutines of its own: what [TraceScreen] shows and what a screenshot test draws. */
@Composable
fun TraceFrame(
    texture: CanvasTexture,
    hasProject: Boolean,
    sourceCount: Int,
    source: TraceSource?,
    outcome: TraceOutcome?,
    page: TracePageState,
    locked: Boolean,
    box: Quartiles?,
    styleName: String?,
    styleIsHeuristic: Boolean,
    actions: TraceActions,
    modifier: Modifier = Modifier,
    metricLabels: Map<Int, String> = emptyMap(),
    outcomeIsCurrent: Boolean = true,
    metricLines: List<Int> = emptyList(),
) {
    val stage = page.stage
    val count = if (outcome != null && stage != null) outcome.countAt(stage) else null
    val title = if (source == null) "Trace" else TraceView.title(source.glyphName, count?.total)
    val subtitle =
        when {
            !hasProject -> "no project open"
            source == null -> if (sourceCount == 0) "nothing captured yet" else "no drawing for this glyph"
            else -> TraceView.subtitle(outcome, count?.total, source.origin) + if (locked) " · locked" else ""
        }
    val canAccept = hasProject && source != null && outcome != null && outcomeIsCurrent && outcome.inkFound && !locked
    val why =
        when {
            !hasProject -> "Open or start a project first."
            source == null -> "Capture a drawing of this glyph first."
            locked -> "${source.glyphName} is locked. Unlock it to trace again."
            outcome == null || !outcomeIsCurrent -> "Tracing…"
            !outcome.inkFound -> "There is no ink in this drawing to accept."
            else -> null
        }
    SectionScaffold(
        texture = texture,
        section = DeckSection.TRACE,
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        toggles =
            listOf(
                DeckToggle("Simple", on = !page.all),
                DeckToggle("All stages", on = page.all),
                DeckToggle("Raster", on = page.raster, groupStart = true),
            ),
        onToggle = { index ->
            when (index) {
                0 -> page.all = false
                1 -> page.all = true
                else -> page.raster = !page.raster
            }
        },
        onBack = actions.back,
        right = DeckRight.CHECK,
        rightLabel = "Accept the glyph",
        onRight = actions.accept,
        rightDimmed = !canAccept,
        rightExplanation = why,
    ) {
        when {
            !hasProject -> {
                Column(Modifier.padding(horizontal = 14.dp)) { NoProjectState(texture, "a drawing to trace") }
            }

            source == null -> {
                NothingToTrace(texture, sourceCount, actions)
            }

            else -> {
                TraceBody(
                    texture,
                    source,
                    outcome,
                    page,
                    locked,
                    box,
                    styleName,
                    styleIsHeuristic,
                    count?.onCurve,
                    metricLabels,
                    metricLines,
                    actions,
                )
            }
        }
    }
}

@Composable
private fun NothingToTrace(
    texture: CanvasTexture,
    sourceCount: Int,
    actions: TraceActions,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(
            if (sourceCount == 0) "Nothing has been captured yet" else "This glyph has no captured drawing",
            style = sentence(fg, 14.0, FontWeight.SemiBold),
        )
        BasicText(
            "Trace turns a photographed or scanned drawing, or an SVG, into an outline. Capture a sheet first, and the glyphs on it come here.",
            style = sentence(muted),
        )
        LinkText("Capture", texture, actions.capture)
    }
}

private val DECK_CLEARANCE: Dp = 176.dp

@Composable
private fun TraceBody(
    texture: CanvasTexture,
    source: TraceSource,
    outcome: TraceOutcome?,
    page: TracePageState,
    locked: Boolean,
    box: Quartiles?,
    styleName: String?,
    styleIsHeuristic: Boolean,
    liveOnCurve: Int?,
    metricLabels: Map<Int, String>,
    metricLines: List<Int>,
    actions: TraceActions,
) {
    val muted = texture.muted.toColor()
    val fg = texture.fg.toColor()
    Column(Modifier.fillMaxSize()) {
        if (box != null && liveOnCurve != null) {
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BoxPlot(BoxPlotSpec.of(box, liveOnCurve), source.glyphName, texture, Modifier.fillMaxWidth(), height = 24.dp)
                BasicText(
                    TraceView.boxLine(source.glyphName, liveOnCurve, box, styleName.orEmpty(), styleIsHeuristic).uppercase(),
                    style = mono(muted, 8.5, 0.08),
                )
            }
        } else if (liveOnCurve != null) {
            BasicText(
                "NO MEASURED BOX FOR ${source.glyphName.uppercase()} IN THIS CLASS, SO NOTHING JUDGES THE COUNT",
                style = mono(muted, 8.5, 0.08),
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
        TraceCanvas(
            texture,
            source,
            outcome,
            page.stage,
            page.raster,
            metricLabels,
            metricLines,
            Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = DECK_CLEARANCE),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (outcome == null) {
                BasicText("TRACING…", style = mono(muted, 9.5))
            } else {
                val stage = page.stage ?: TraceStage.FIT
                Scrubber(texture, outcome, page, stage)
                BasicText(outcome.stage(stage)?.sentence ?: "", style = sentence(fg, 12.5))
                if (outcome.flatRound && stage in OVERSHOOT_NOTE_STAGES) {
                    BasicText(
                        "Flat ${if (outcome.flatTop) "top" else "bottom"} on a round glyph: overshoot does not apply there.",
                        style = sentence(muted, 12.0),
                    )
                }
                Inspector(texture, outcome, stage, page)
            }
            page.message?.let { BasicText(it, style = sentence(fg, 12.5, FontWeight.SemiBold)) }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (locked) LinkText("Unlock to re-trace", texture, actions.unlock)
                LinkText("Hand edit", texture, actions.handEdit)
            }
        }
    }
}

private val OVERSHOOT_NOTE_STAGES = setOf(TraceStage.FIT, TraceStage.SNAP, TraceStage.REGULARISE)

/** One stage's settings as values that step with a tap and trace again at once. */
@Composable
private fun Inspector(
    texture: CanvasTexture,
    outcome: TraceOutcome,
    stage: TraceStage,
    page: TracePageState,
) {
    val specs = TraceView.parametersFor(outcome, stage, page.all)
    if (specs.isEmpty()) return
    val ink = texture.ink.toColor()
    val muted = texture.muted.toColor()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (spec in specs) {
            Row(
                modifier = Modifier.fillMaxWidth().height(34.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicText(spec.label.uppercase(), style = mono(muted, 9.0, 0.1), modifier = Modifier.weight(1f))
                Stepper("−", "Less ${spec.label}", ink, spec.value > spec.min) {
                    page.parameters = page.parameters.withParameter(spec.id, TraceView.stepped(spec, -1))
                }
                BasicText(
                    TraceView.valueText(spec),
                    style = mono(ink, 10.5, 0.04),
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
                Stepper("+", "More ${spec.label}", ink, spec.value < spec.max) {
                    page.parameters = page.parameters.withParameter(spec.id, TraceView.stepped(spec, 1))
                }
            }
            BasicText(spec.explanation, style = sentence(muted, 11.0))
        }
    }
}

@Composable
private fun Stepper(
    glyph: String,
    description: String,
    ink: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(34.dp)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics {
                    role = Role.Button
                    contentDescription = description
                },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(glyph, style = sentence(ink.copy(alpha = if (enabled) 1f else 0.3f), 16.0, FontWeight.Medium))
    }
}
