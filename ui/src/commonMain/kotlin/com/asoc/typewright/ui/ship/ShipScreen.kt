// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.ship

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.asoc.typewright.compile.BackendAvailability
import com.asoc.typewright.compile.CompileLocation
import com.asoc.typewright.compile.platformCompileBackend
import com.asoc.typewright.project.TreeReplacement
import com.asoc.typewright.qa.platformLayerOneChecker
import com.asoc.typewright.qa.report.CheckReport
import com.asoc.typewright.qa.report.buildCheckReport
import com.asoc.typewright.qa.ship.GfCategory
import com.asoc.typewright.qa.ship.OWN_OFL
import com.asoc.typewright.qa.ship.OWN_PROPRIETARY
import com.asoc.typewright.qa.ship.ShipCapability
import com.asoc.typewright.qa.ship.ShipChoices
import com.asoc.typewright.qa.ship.ShipFontInfoField
import com.asoc.typewright.qa.ship.ShipGate
import com.asoc.typewright.qa.ship.ShipInput
import com.asoc.typewright.qa.ship.ShipItem
import com.asoc.typewright.qa.ship.ShipMark
import com.asoc.typewright.qa.ship.ShipMode
import com.asoc.typewright.qa.ship.ShipRoute
import com.asoc.typewright.ui.check.CheckMemory
import com.asoc.typewright.ui.check.LayerOneState
import com.asoc.typewright.ui.check.sentenceStyle
import com.asoc.typewright.ui.deck.DeckLeft
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.project.NoProjectState
import com.asoc.typewright.ui.project.OpenProject
import com.asoc.typewright.ui.project.ProjectFont
import com.asoc.typewright.ui.project.rememberOpenProject
import com.asoc.typewright.ui.shell.FontInfoEditor
import com.asoc.typewright.ui.shell.FontInfoField
import com.asoc.typewright.ui.shell.FontInfoRules
import com.asoc.typewright.ui.shell.FontInfoScreen
import com.asoc.typewright.ui.shell.LicenceChoice
import com.asoc.typewright.ui.shell.LocalSectionNav
import com.asoc.typewright.ui.shell.SectionScaffold
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.toColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a tap on the frame's controls does that only the stateful screen can: build, choose a place, cancel, copy. */
internal class ShipFrameActions(
    val onPrimary: () -> Unit,
    val onCancel: () -> Unit,
    val onChooseTarget: () -> Unit,
    val onTryAgain: () -> Unit,
    val onCopy: (String) -> Unit,
    val onDone: () -> Unit,
)

private const val PAGE_MAX_WIDTH_DP = 720
private const val NO_GLYPHS = "This project has no glyphs yet, so there is nothing to ship. Draw a letter or open a font, then come back."
private const val CHECKING = "Checking the outlines…"

/**
 * Ship (S16, with S17 as its building state) for the open project: the checklist of what the repository needs and which of it the project
 * states, the place the folder goes, and a build that makes the font, checks the binary and writes the folder only when everything has passed.
 * The right circle is ↑ build (↓ export on the web), at 35% while Check has fails, and says why on a tap. With no project open the page says
 * so; it never ships a stand-in font.
 *
 * [controller] and [destination] are made from the platform's compiler, checker and folder chooser unless a caller gives them. Leaving the page
 * while a build runs cancels it; the project and the target are untouched either way.
 */
@Composable
fun ShipScreen(
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
    controller: ShipController? = null,
    destination: ShipDestination? = null,
) {
    val project = rememberOpenProject()
    val scope = rememberCoroutineScope()
    val backend = remember { platformCompileBackend() }
    val runner = controller ?: remember { ShipController(scope, backend, platformLayerOneChecker()) }
    val where = destination ?: remember { platformShipDestination() }
    val subject = remember(project?.state?.font) { project?.let { shipSubjectOf(it) } }
    val page = remember { ShipPageState() }
    val clipboard = LocalClipboardManager.current
    val nav = LocalSectionNav.current

    val layerTwo by produceState<CheckReport?>(null, subject?.version) {
        value = null
        val s = subject
        if (s != null && s.check.hasGlyphs) value = withContext(Dispatchers.Default) { buildCheckReport(s.check.inputs) }
    }
    val fromCheck = subject?.let { CheckMemory.shared.resultFor(it.version)?.report }
    val report = layerTwo?.withLayerOne(fromCheck)

    val platform by produceState(
        ShipPlatform(where.exit, ShipCapability(false, "Compiling", "Looking for a compiler…"), null),
        where,
        backend,
    ) {
        val availability = backend.availability()
        value =
            ShipPlatform(
                exit = where.exit,
                compile =
                    when (availability) {
                        BackendAvailability.Available -> {
                            ShipCapability(true, compileTitle(backend.location), "fontmake runs here when you build · nothing is sent")
                        }

                        is BackendAvailability.Unavailable -> {
                            ShipCapability(false, "Not compiled here", availability.reason)
                        }
                    },
                location = backend.location,
            )
    }
    val run by runner.state.collectAsState()
    val replacements by produceState(emptyList<TreeReplacement>(), page.target, subject?.version, page.mode, page.branch, page.dateAdded) {
        val target = page.target
        val s = subject
        value =
            if (target == null || s == null) {
                emptyList()
            } else {
                // What the chosen place already holds of what this build would write; the UFO's folder is replaced whole.
                val plan =
                    previewPlan(s, page.mode, page.chosenCategory ?: s.categoryReading, platform.compiles, page.branch, page.dateAdded)
                target.writer.replacements(plan.paths, plan.ownedDirectories)
            }
    }
    DisposableEffect(runner) { onDispose { runner.cancel() } }

    fun route(to: ShipRoute) {
        when (to) {
            is ShipRoute.FontInfoAt -> {
                val info = project?.master?.ufo?.fontInfo
                page.fontInfoField = info?.let { FontInfoRules.landing(to.target, it) }
                page.view = ShipView.FONT_INFO
            }

            ShipRoute.Check -> {
                nav?.goTo?.invoke(DeckSection.CHECK)
            }

            is ShipRoute.FontInfo -> {
                page.fontInfoField = fontInfoFieldOf(to.field)
                page.view = ShipView.FONT_INFO
            }
        }
    }

    fun start(chosen: ShipTarget?) {
        val s = subject ?: return
        val target = chosen ?: page.target ?: return
        page.target = target
        page.message = null
        scope.launch {
            val request =
                ShipRequest(
                    s,
                    page.mode,
                    page.chosenCategory ?: s.categoryReading,
                    target,
                    report,
                    fromCheck,
                    branch = page.branch,
                    dateAdded = page.dateAdded,
                )
            when (val started = runner.ship(request)) {
                ShipStart.Started -> Unit
                is ShipStart.Blocked -> page.message = started.gate.explanation()
                is ShipStart.CannotBuild -> page.message = started.reason
            }
        }
    }

    fun choose(thenShip: Boolean) {
        val s = subject ?: return
        scope.launch {
            val name = s.facts.folderName ?: "repository-gf"
            val chosen = where.choose(name, ShipTargetMemory.shared.placeFor(s.title))
            // A person who changed their mind chose nothing and nothing changes; a chooser that could not be shown says so.
            if (chosen is ShipChoice.Chosen) {
                page.message = null
                page.target = chosen.target
                ShipTargetMemory.shared.remember(s.title, chosen.target.label)
                if (thenShip) start(chosen.target)
            } else if (chosen is ShipChoice.Failed) {
                page.message = chosen.message
            }
        }
    }

    ShipFrame(
        texture = texture,
        project = project,
        subject = subject,
        report = report,
        run = run,
        page = page,
        platform = platform,
        actions =
            ShipFrameActions(
                onPrimary = { if (page.target == null) choose(thenShip = true) else start(page.target) },
                onCancel = runner::cancel,
                onChooseTarget = { choose(thenShip = false) },
                onTryAgain = { if (page.target == null) choose(thenShip = true) else start(page.target) },
                onCopy = { clipboard.setText(AnnotatedString(it)) },
                onDone = runner::reset,
            ),
        replacements = replacements,
        onChoice = { kind, id -> choice(project, page, kind, id) },
        onInput = { kind, typed -> input(project, page, kind, typed) },
        onRoute = ::route,
        modifier = modifier,
    )
}

private fun compileTitle(location: CompileLocation): String =
    when (location) {
        is CompileLocation.OnThisComputer -> "Compiled on this device"
        is CompileLocation.OnThisDevice -> "Compiled on this device"
        is CompileLocation.HostedEndpoint -> "Compiled by a hosted build"
    }

/** A tap on one of a line's choices: the category goes into the page's state; a licence goes into the project's font info, as one undo step. */
private fun choice(
    project: OpenProject?,
    page: ShipPageState,
    kind: ShipChoices.Kind,
    id: String,
) {
    when (kind) {
        ShipChoices.Kind.CATEGORY -> {
            page.chosenCategory = GfCategory.entries.firstOrNull { it.protoName == id }
        }

        ShipChoices.Kind.LICENCE -> {
            val master = project?.master ?: return
            val licence =
                when (id) {
                    OWN_OFL -> LicenceChoice.OFL
                    OWN_PROPRIETARY -> LicenceChoice.PROPRIETARY
                    else -> LicenceChoice.OTHER
                }
            val outcome = FontInfoEditor(project.session, master.id).chooseLicence(licence)
            page.message = outcome.problem
        }
    }
}

/** A short text typed on a checklist line: the vendor ID goes into the project's font info; the branch and the day stay with this page. */
private fun input(
    project: OpenProject?,
    page: ShipPageState,
    kind: ShipInput.Kind,
    typed: String,
): String? {
    val text = typed.trim()
    return when (kind) {
        ShipInput.Kind.VENDOR_ID -> {
            val master = project?.master ?: return "No project is open."
            FontInfoEditor(project.session, master.id).commit(FontInfoField.VENDOR_ID, text).problem
        }

        ShipInput.Kind.BRANCH -> {
            branchProblem(text) ?: run {
                page.branch = text.ifEmpty { null }
                null
            }
        }

        ShipInput.Kind.DATE_ADDED -> {
            if (text.isEmpty()) {
                page.dateAdded = null
                null
            } else {
                dayProblem(text) ?: run {
                    page.dateAdded = text
                    null
                }
            }
        }
    }
}

/** The word set at the top of the building page: a proofing word when the font can set it, else the family's own name. */
internal fun buildWord(
    font: ProjectFont?,
    family: String,
): String = if (font != null && font.canSet(PROOF_WORD)) PROOF_WORD else family

private const val PROOF_WORD = "Hamburg"

/**
 * The page itself, drawn from its state with no coroutines of its own: what [ShipScreen] shows and what a screenshot test draws.
 */
@Composable
internal fun ShipFrame(
    texture: CanvasTexture,
    project: OpenProject?,
    subject: ShipSubject?,
    report: CheckReport?,
    run: ShipRunState,
    page: ShipPageState,
    platform: ShipPlatform,
    actions: ShipFrameActions,
    replacements: List<TreeReplacement>,
    onChoice: (ShipChoices.Kind, String) -> Unit,
    onInput: (ShipInput.Kind, String) -> String?,
    onRoute: (ShipRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = LocalSectionNav.current
    if (page.view == ShipView.FONT_INFO && project != null) {
        val landing = page.fontInfoField ?: FontInfoField.DESIGNER
        FontInfoScreen(
            texture = texture,
            project = project,
            onBack = { page.view = ShipView.READY },
            modifier = modifier,
            focus = landing,
        )
        return
    }
    val sheet =
        if (subject != null && subject.check.hasGlyphs && report != null) {
            ShipSheet(subject, page.mode, report, page.chosenCategory, platform, page.branch, page.dateAdded, replacements)
        } else {
            null
        }
    val pageActions =
        ShipPageActions(
            onRoute = onRoute,
            onChoice = onChoice,
            onChooseTarget = actions.onChooseTarget,
            onToggleOutput = { page.showOutput = !page.showOutput },
            onToggleRequest = { page.showRequest = !page.showRequest },
            onCopyRequest = actions.onCopy,
            onTryAgain = actions.onTryAgain,
            onToggleDetails = { page.showDetails = !page.showDetails },
            onInput = onInput,
        )
    val title = subject?.title.orEmpty()
    val primary = sheet?.primary ?: shipPrimary(ShipGate(emptyList()), platform)
    val chrome =
        when {
            subject == null || sheet == null -> {
                ShipChrome(
                    title = if (subject == null) "Ship" else ShipText.title(title),
                    subtitle = "",
                    toggles = emptyList(),
                    left = DeckLeft.BACK,
                    right = DeckRight.NONE,
                    rightDimmed = false,
                    rightLabel = "",
                    rightWhy = null,
                )
            }

            run is ShipRunState.Building -> {
                buildingChrome(title, platform)
            }

            run is ShipRunState.Done -> {
                afterChrome(title, "Written to ${run.result.target}", platform, primary, "Build again")
            }

            run is ShipRunState.Failed -> {
                afterChrome(title, "Stopped at: ${run.step.title}", platform, primary, "Try again")
            }

            run is ShipRunState.Stopped -> {
                afterChrome(title, "Stopped: the check on the font found fails", platform, primary, "Build again")
            }

            else -> {
                readyChrome(title, page.mode, platform, primary)
            }
        }
    val showingAfter = run is ShipRunState.Done || run is ShipRunState.Failed || run is ShipRunState.Stopped
    SectionScaffold(
        texture = texture,
        section = DeckSection.SHIP,
        title = chrome.title,
        subtitle = chrome.subtitle,
        modifier = modifier,
        toggles = chrome.toggles,
        onToggle = { index -> ShipMode.entries.getOrNull(index)?.let { page.mode = it } },
        left = chrome.left,
        onBack = {
            when {
                run is ShipRunState.Building -> actions.onCancel()
                showingAfter -> actions.onDone()
                else -> nav?.goTo?.invoke(DeckSection.HOME)
            }
        },
        right = chrome.right,
        onRight = actions.onPrimary,
        rightDimmed = chrome.rightDimmed,
        rightLabel = chrome.rightLabel.ifEmpty { null },
        rightExplanation = chrome.rightWhy,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
            Box(modifier = Modifier.widthIn(max = PAGE_MAX_WIDTH_DP.dp).fillMaxSize()) {
                val scroll = rememberScrollState()
                val body = Modifier.verticalScroll(scroll).padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = BOTTOM_CLEARANCE_DP.dp)
                when {
                    project == null -> {
                        NoProjectState(
                            texture = texture,
                            what = "the repository folder it would write",
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
                    }

                    subject == null -> {
                        PageMessage(texture, "This project has no master font, so there is nothing to ship yet.")
                    }

                    !subject.check.hasGlyphs -> {
                        PageMessage(texture, NO_GLYPHS)
                    }

                    sheet == null -> {
                        PageMessage(texture, CHECKING)
                    }

                    run is ShipRunState.Building -> {
                        Box(body) {
                            BuildingPage(
                                texture = texture,
                                font = project.font,
                                word = buildWord(project.font, title),
                                steps = run.steps,
                                startedAt = run.startedAt,
                                log = run.log,
                                cancelling = run.cancelling,
                                showOutput = page.showOutput,
                                actions = pageActions,
                            )
                        }
                    }

                    run is ShipRunState.Done -> {
                        Box(body) {
                            DonePage(
                                texture = texture,
                                result = run.result,
                                stillNeedsYou = stillNeedsYou(sheet, run.result),
                                showRequest = page.showRequest,
                                actions = pageActions,
                            )
                        }
                    }

                    run is ShipRunState.Failed -> {
                        Box(body) { FailedPage(texture, run.step, run.cause, run.log, page.showOutput, pageActions) }
                    }

                    run is ShipRunState.Stopped -> {
                        Box(body) { StoppedPage(texture, run.blockers, run.log, page.showOutput, pageActions) }
                    }

                    else -> {
                        Box(body) {
                            ChecklistPage(
                                texture = texture,
                                sheet = sheet,
                                targetLabel = page.target?.label,
                                message = page.message ?: ShipText.CANCELLED.takeIf { run is ShipRunState.Cancelled },
                                showDetails = page.showDetails,
                                actions = pageActions,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The checklist lines that still need the person after [result] was written: the ones whose fact the project does not state. */
internal fun stillNeedsYou(
    sheet: ShipSheet,
    result: ShipResult,
): List<ShipItem> = sheet.checklistAfter(result).filter { it.mark == ShipMark.WARN }

private const val BOTTOM_CLEARANCE_DP = 200

@Composable
private fun PageMessage(
    texture: CanvasTexture,
    text: String,
) {
    BasicText(
        text = text,
        style = sentenceStyle(texture.fg.toColor(), size = 13.0),
        modifier = Modifier.padding(horizontal = 18.dp),
    )
}

/** The `FontInfoField` a [ShipFontInfoField] names; the names are the same, and a test holds them to it. */
internal fun fontInfoFieldOf(field: ShipFontInfoField): FontInfoField = FontInfoField.valueOf(field.name)
