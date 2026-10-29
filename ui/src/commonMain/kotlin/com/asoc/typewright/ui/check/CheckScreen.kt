// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asoc.typewright.brief.genreByKey
import com.asoc.typewright.compile.platformCompileBackend
import com.asoc.typewright.qa.checkNodeEconomy
import com.asoc.typewright.qa.corpus.NodeEconomyCorpus
import com.asoc.typewright.qa.platformLayerOneChecker
import com.asoc.typewright.qa.report.CheckRoute
import com.asoc.typewright.qa.report.EconomySort
import com.asoc.typewright.qa.report.FontInfoTarget
import com.asoc.typewright.qa.report.Severity
import com.asoc.typewright.qa.report.economyListing
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.project.NoProjectState
import com.asoc.typewright.ui.project.OpenProject
import com.asoc.typewright.ui.project.ProjectDataPacks
import com.asoc.typewright.ui.project.projectStyle
import com.asoc.typewright.ui.project.rememberOpenProject
import com.asoc.typewright.ui.shell.FontInfoRules
import com.asoc.typewright.ui.shell.FontInfoScreen
import com.asoc.typewright.ui.shell.LocalGlyphNav
import com.asoc.typewright.ui.shell.LocalSectionNav
import com.asoc.typewright.ui.shell.SectionScaffold
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.toColor

/** The views of Check: the deck's two toggles, and Font info, which a Names or Licence finding opens over them. */
enum class CheckView {
    REPORT,
    ECONOMY,
    FONT_INFO,
}

/** What the person has chosen on the page so far, held where a tap and a screenshot test can both set it. */
@Stable
class CheckPageState(
    view: CheckView = CheckView.REPORT,
    raw: Boolean = false,
    sort: EconomySort = EconomySort.WORST_FIRST,
    styleKey: String? = null,
    openRow: OpenRow = OpenRow(),
    showOutput: Boolean = false,
    showPasses: Boolean = false,
    fontInfoTarget: FontInfoTarget? = null,
) {
    var view by mutableStateOf(view)
    var raw by mutableStateOf(raw)
    var sort by mutableStateOf(sort)

    /** The class chosen to compare against, or null for the project's own. */
    var styleKey by mutableStateOf(styleKey)
    var openRow by mutableStateOf(openRow)
    var showOutput by mutableStateOf(showOutput)

    /** Whether layer one lists the checks that passed, which it counts and otherwise leaves out. */
    var showPasses by mutableStateOf(showPasses)

    /** The part of Font info a finding's action asked for, so the page opens on the value to change. */
    var fontInfoTarget by mutableStateOf(fontInfoTarget)
}

/** Where a finding's action goes: another view of this page, or another page. */
sealed interface CheckDestination {
    data class InPage(
        val view: CheckView,
    ) : CheckDestination

    data class Draw(
        val glyph: String,
    ) : CheckDestination

    /** Font info, opened on the value [target] is about. */
    data class FontInfo(
        val target: FontInfoTarget,
    ) : CheckDestination

    data class Section(
        val section: DeckSection,
        val glyph: String? = null,
    ) : CheckDestination
}

/** The one place that says where each [CheckRoute] leads. */
fun destinationOf(route: CheckRoute): CheckDestination =
    when (route) {
        CheckRoute.OpenEconomy -> CheckDestination.InPage(CheckView.ECONOMY)
        is CheckRoute.OpenFontInfo -> CheckDestination.FontInfo(route.target)
        is CheckRoute.OpenDraw -> CheckDestination.Draw(route.glyph)
        is CheckRoute.OpenTrace -> CheckDestination.Section(DeckSection.TRACE, route.glyphs.firstOrNull())
        CheckRoute.OpenCapture -> CheckDestination.Section(DeckSection.CAPTURE)
        CheckRoute.OpenShip -> CheckDestination.Section(DeckSection.SHIP)
    }

/**
 * Check (S14 Report, S15 Economy) for the open project. Layer two runs as the page opens; layer one, where this
 * platform has a compiler and a checker, compiles the project and checks the binary, with progress that can be
 * cancelled (the project is untouched either way). The deck's right circle runs both again. With no project open
 * it says so; it never shows a stand-in font.
 *
 * [controller] is made from the platform's compiler and checker unless a caller gives one.
 */
@Composable
fun CheckScreen(
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
    initialView: CheckView = CheckView.REPORT,
    controller: CheckController? = null,
) {
    val project = rememberOpenProject()
    val scope = rememberCoroutineScope()
    val runner = controller ?: remember { CheckController(scope, platformCompileBackend(), platformLayerOneChecker()) }
    val subject = remember(project?.state?.font) { project?.let { checkSubjectOf(it) } }
    val page = remember { CheckPageState(initialView) }
    LaunchedEffect(subject?.version) { if (subject != null && subject.hasGlyphs) runner.open(subject) }
    DisposableEffect(runner) { onDispose { runner.cancel() } }
    val run by runner.state.collectAsState()
    CheckFrame(
        texture = texture,
        project = project,
        subject = subject,
        run = run,
        page = page,
        corpus = ProjectDataPacks.corpus,
        onRunAgain = { subject?.let(runner::runAgain) },
        onCancel = runner::cancel,
        modifier = modifier,
    )
}

private const val NO_GLYPHS = "This project has no glyphs yet, so there is nothing to check. Draw a letter or open a font, then run Check."
private const val NO_CORPUS = "This build cannot read the measured faces on this device, so there is nothing to compare against."
private const val NO_CLASS =
    "No class is declared and too few letters are drawn for our heuristic to read one. Choose one to compare against."
private const val TAP_A_STYLE = "Tap a style to compare against a different box."
private const val CHOOSE_A_STYLE = "Choose a style above to compare."

/** How wide the page's text and plots grow on a wide screen, so a line stays readable. */
private const val PAGE_MAX_WIDTH_DP = 720

/** Why the right circle cannot run again just now. */
private const val RUNNING_NOW = "Running now. Cancel layer one on the page, or wait for it to finish."

/** The page itself, drawn from its [run] state with no coroutines of its own: what [CheckScreen] shows and what a screenshot test draws. */
@Composable
internal fun CheckFrame(
    texture: CanvasTexture,
    project: OpenProject?,
    subject: CheckSubject?,
    run: CheckRunState,
    page: CheckPageState,
    corpus: NodeEconomyCorpus?,
    onRunAgain: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = LocalSectionNav.current
    val glyphNav = LocalGlyphNav.current
    val font = project?.font
    if (page.view == CheckView.FONT_INFO && project != null && font != null) {
        val landing = page.fontInfoTarget?.let { FontInfoRules.landing(it, font.ufo.fontInfo) }
        FontInfoScreen(
            texture = texture,
            project = project,
            onBack = { page.view = CheckView.REPORT },
            modifier = modifier,
            focus = landing,
        )
        return
    }
    val economyView = page.view == CheckView.ECONOMY
    val ownStyle = if (project != null && font != null) projectStyle(project.manifest, font) else null
    val selectedKey = page.styleKey ?: subject?.inputs?.style?.key
    val usable = corpus != null && selectedKey != null && selectedKey in corpus.styleKeys
    val economy =
        remember(font, selectedKey, corpus) {
            if (font != null && corpus != null && usable) checkNodeEconomy(font.ufo, selectedKey.orEmpty(), corpus) else null
        }
    val listing = remember(economy, page.sort) { economy?.let { economyListing(it, page.sort) } }
    val faceCount = if (corpus != null && selectedKey != null) corpus.families(selectedKey).size else 0

    fun go(route: CheckRoute) {
        when (val destination = destinationOf(route)) {
            is CheckDestination.InPage -> {
                page.view = destination.view
            }

            is CheckDestination.FontInfo -> {
                page.fontInfoTarget = destination.target
                page.view = CheckView.FONT_INFO
            }

            is CheckDestination.Draw -> {
                glyphNav?.open?.invoke(destination.glyph) ?: nav?.goTo?.invoke(DeckSection.DRAW)
            }

            is CheckDestination.Section -> {
                destination.glyph?.let { glyphNav?.select?.invoke(it) }
                nav?.goTo?.invoke(destination.section)
            }
        }
    }
    val chrome =
        checkChrome(
            name = subject?.name,
            hasGlyphs = subject?.hasGlyphs == true,
            run = run,
            view = page.view,
            raw = page.raw,
            sort = page.sort,
            economy = if (usable) EconomyHeader(faceCount, ownStyle?.isHeuristic == true && selectedKey == ownStyle.key) else null,
        )
    SectionScaffold(
        texture = texture,
        section = DeckSection.CHECK,
        title = chrome.title,
        subtitle = chrome.subtitle,
        modifier = modifier,
        toggles = chrome.toggles,
        onToggle = { index ->
            when (index) {
                0 -> page.view = CheckView.REPORT
                1 -> page.view = CheckView.ECONOMY
                2 -> page.raw = false
                3 -> page.raw = true
            }
        },
        onBack = { nav?.goTo?.invoke(DeckSection.HOME) },
        right = chrome.right,
        onRight = onRunAgain,
        rightDimmed = chrome.rightDimmed,
        rightLabel = "Run again",
        rightExplanation = RUNNING_NOW,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
            Box(modifier = Modifier.widthIn(max = PAGE_MAX_WIDTH_DP.dp).fillMaxSize()) {
                when {
                    project == null -> {
                        NoProjectState(
                            texture = texture,
                            what = if (economyView) "your glyphs against real faces" else "its check report",
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
                    }

                    subject == null -> {
                        PageMessage(texture, "This project has no master font, so there is nothing to check yet.")
                    }

                    !subject.hasGlyphs -> {
                        PageMessage(texture, NO_GLYPHS)
                    }

                    economyView -> {
                        if (corpus == null) {
                            PageMessage(texture, NO_CORPUS)
                        } else {
                            val chips =
                                styleChips(
                                    keys = corpus.styleKeys,
                                    faces = { corpus.families(it).size },
                                    label = { genreNameOf(it) },
                                    projectKey = subject.inputs.style?.key,
                                    selectedKey = selectedKey,
                                )
                            val open = page.openRow.resolve(listing?.rows.orEmpty().map { it.glyph })
                            EconomyPage(
                                texture = texture,
                                model =
                                    EconomyPageModel(
                                        font = checkNotNull(font),
                                        corpus = corpus,
                                        chips = chips,
                                        selectedKey = selectedKey.takeIf { usable },
                                        classNote =
                                            economyClassNote(
                                                ownStyle?.key,
                                                ownStyle?.provenance,
                                                selectedKey,
                                                ownStyle?.name,
                                                hasBoxes = usable,
                                            ),
                                        entries = listing?.entries.orEmpty(),
                                        coverage = listing?.coverage(),
                                        sort = page.sort,
                                        open = open,
                                        faceCount = faceCount,
                                    ),
                                onStyle = { key -> page.styleKey = key },
                                onSort = { sort -> page.sort = sort },
                                onOpen = { glyph -> page.openRow = page.openRow.tapped(glyph, open) },
                            )
                        }
                    }

                    run.layerTwo is LayerTwoState.Failed -> {
                        LayerTwoStopped(texture, run.layerTwo.cause, onRunAgain)
                    }

                    run.report == null -> {
                        PageMessage(texture, "Checking the outlines…")
                    }

                    else -> {
                        ReportPage(
                            texture = texture,
                            report = checkNotNull(run.report),
                            run = run,
                            raw = page.raw,
                            showOutput = page.showOutput,
                            showPasses = page.showPasses,
                            actions =
                                ReportActions(
                                    onRoute = ::go,
                                    onCancel = onCancel,
                                    onRunAgain = onRunAgain,
                                    onToggleOutput = { page.showOutput = !page.showOutput },
                                    onTogglePasses = { page.showPasses = !page.showPasses },
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** One sentence in the page body where there is nothing else to draw. */
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

/**
 * The line under the Economy page's chips that says which class the glyphs are judged against and where it came from, that the
 * person is comparing against another class than their own, or that the class has no faces in the corpus ([hasBoxes] false) to judge by.
 */
internal fun economyClassNote(
    ownKey: String?,
    ownProvenance: String?,
    selectedKey: String?,
    ownName: String?,
    hasBoxes: Boolean = true,
): String? =
    when {
        selectedKey == null -> NO_CLASS

        !hasBoxes -> "${genreNameOf(
            selectedKey,
        )} has no measured faces in the corpus, so there is no box to judge your glyphs against. $CHOOSE_A_STYLE"

        ownKey == null -> "Comparing against ${genreNameOf(selectedKey).lowercase()}, which you chose here."

        selectedKey == ownKey -> "Judged against ${ownName.orEmpty().lowercase()}: $ownProvenance. $TAP_A_STYLE"

        else -> "Comparing against ${genreNameOf(selectedKey).lowercase()} instead of your own class, ${ownName.orEmpty().lowercase()}."
    }

/** Check could not finish the layer-two report: what stopped it, in the words of the error, and a way to try again. */
@Composable
private fun LayerTwoStopped(
    texture: CanvasTexture,
    cause: String,
    onRunAgain: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(
            text = "${Severity.FAIL.shape} ${CheckText.layerTwoStopped(cause)}",
            style = sentenceStyle(texture.fg.toColor(), size = 13.0, weight = FontWeight.SemiBold),
        )
        BasicText(text = CheckText.LAYER_TWO_STOPPED_ADVICE, style = sentenceStyle(texture.muted.toColor(), size = 12.0))
        ActionLink("Try again", texture, onClick = onRunAgain)
    }
}

/** A style class as a person names it (`Geometric sans`), from the brief's own list of genres; the key itself when it is not one. */
internal fun genreNameOf(key: String): String = genreByKey(key)?.name ?: key
