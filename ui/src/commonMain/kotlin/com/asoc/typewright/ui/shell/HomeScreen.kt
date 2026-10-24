// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.project.imports.scriptCoverage
import com.asoc.typewright.qa.corpus.NodeEconomyVerdict
import com.asoc.typewright.ui.brief.LocalOpenBrief
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.DeckToggle
import com.asoc.typewright.ui.glass.projectStatusText
import com.asoc.typewright.ui.project.GlyphCell
import com.asoc.typewright.ui.project.GlyphFilter
import com.asoc.typewright.ui.project.NoProjectState
import com.asoc.typewright.ui.project.OpenProject
import com.asoc.typewright.ui.project.ProjectDataPacks
import com.asoc.typewright.ui.project.ProjectEconomy
import com.asoc.typewright.ui.project.ProjectFont
import com.asoc.typewright.ui.project.ProjectGlyphInBox
import com.asoc.typewright.ui.project.ProjectStyle
import com.asoc.typewright.ui.project.ProjectText
import com.asoc.typewright.ui.project.filterCells
import com.asoc.typewright.ui.project.filterCounts
import com.asoc.typewright.ui.project.fontVersionText
import com.asoc.typewright.ui.project.glyphBoxSentence
import com.asoc.typewright.ui.project.glyphCells
import com.asoc.typewright.ui.project.groupThousands
import com.asoc.typewright.ui.project.heaviestGlyphs
import com.asoc.typewright.ui.project.projectCounts
import com.asoc.typewright.ui.project.projectStyle
import com.asoc.typewright.ui.project.rememberOpenProject
import com.asoc.typewright.ui.project.rememberProjectEconomy
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor
import com.asoc.typewright.ui.workbook.WorkbookCampaignSnapshot
import com.asoc.typewright.ui.workbook.loadWorkbookCampaignSnapshot
import kotlinx.coroutines.delay

/**
 * Home (S04) from what the open project really holds, never a stand-in: its name, style, version and
 * save state; the hero word and a pangram set in its own outlines with its own kerning; what it
 * counts and how its letters fare against its style class; the workbook's next task; the glyphs
 * with the most points; and where the pipeline stands. The deck's two views are Specimen and Glyphs
 * (every glyph, filterable, a tap opens it in Draw). The ⋯ menu adds Font info. With no project open
 * it says so and offers Projects.
 */
@Composable
fun HomeScreen(
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
    onOpenDraw: () -> Unit = {},
    onOpenWorkbook: () -> Unit = {},
    /** The view the screen opens on; the person changes it with the deck's toggles and the ⋯ menu. */
    initialView: HomeView = HomeView.SPECIMEN,
) {
    var view by remember { mutableStateOf(initialView) }
    val glyphsView = view == HomeView.GLYPHS
    val openBrief = LocalOpenBrief.current
    val openProjects = LocalOpenProjects.current
    val glyphNav = LocalGlyphNav.current
    val project = rememberOpenProject()
    val font = project?.font
    val economy = rememberProjectEconomy(project)
    if (view == HomeView.FONT_INFO && font != null) {
        FontInfoScreen(texture = texture, font = font, onBack = { view = HomeView.SPECIMEN }, modifier = modifier)
        return
    }
    val manifest = project?.manifest
    val style = economy?.style ?: if (manifest != null && font != null) projectStyle(manifest, font) else null
    val onOpenGlyph: (String) -> Unit = { name -> glyphNav?.open?.invoke(name) ?: onOpenDraw() }
    SectionScaffold(
        texture = texture,
        section = DeckSection.HOME,
        title =
            when {
                manifest == null -> "Typewright"
                glyphsView -> "${manifest.name} · glyphs"
                else -> manifest.name
            },
        subtitle =
            when {
                font == null -> projectStatusText()
                glyphsView -> glyphsSubtitle(manifest?.scripts.orEmpty(), economy, style)
                else -> specimenSubtitle(font, projectStatusText())
            },
        modifier = modifier,
        toggles =
            if (font ==
                null
            ) {
                emptyList()
            } else {
                listOf(DeckToggle("Specimen", on = !glyphsView), DeckToggle("Glyphs", on = glyphsView))
            },
        onToggle = { view = if (it == 1) HomeView.GLYPHS else HomeView.SPECIMEN },
        onBack = { openProjects?.invoke() },
        menuItems =
            listOfNotNull(
                font?.let { PageMenuItem("Font info", "fontinfo.plist · names · metrics") { view = HomeView.FONT_INFO } },
                openBrief?.let { open -> PageMenuItem("Brief", "Find the visual language") { open(null, DeckSection.HOME) } },
                LocalOpenSettings.current?.let { open -> PageMenuItem("Settings", "Theme, text size, haptics", open) },
            ),
    ) {
        when {
            project == null -> {
                NoProjectState(texture = texture, what = "your font", modifier = Modifier.padding(horizontal = 18.dp))
            }

            font == null -> {
                BasicText(
                    text = "This project has no master font, so there is nothing to show yet.",
                    style = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp, color = texture.fg.toColor()),
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }

            glyphsView -> {
                GlyphsView(texture, project, font, economy, style, onOpenGlyph)
            }

            else -> {
                SpecimenView(texture, project, font, economy, style, onOpenGlyph, onOpenWorkbook)
            }
        }
    }
}

/** The views of Home: the deck's two toggles, and Font info, which the ⋯ menu opens over them. */
enum class HomeView {
    SPECIMEN,
    GLYPHS,
    FONT_INFO,
}

/** The header line under the project's name on the Specimen view: style, version when it has one, then the save state. */
internal fun specimenSubtitle(
    font: ProjectFont,
    saveState: String,
): String = listOfNotNull(font.styleName, fontVersionText(font.ufo.fontInfo), saveState).joinToString(" · ")

/** The header line on the Glyphs view: the first script, then what the letters are judged against, and where that class came from. */
internal fun glyphsSubtitle(
    scripts: List<String>,
    economy: ProjectEconomy?,
    style: ProjectStyle?,
): String {
    val script = scriptName(scripts.firstOrNull() ?: "Latn")
    val against =
        when {
            economy != null -> "against ${economy.classFaces} ${economy.style.name}"
            style != null -> "${style.name}, with no measured faces here"
            else -> "no style class to judge against"
        }
    val reading = if (style?.isHeuristic == true) " · class read by our heuristic" else ""
    return "$script · $against$reading"
}

/** Why the letters were not judged, in one sentence; used under the counts when [ProjectEconomy] is null. */
internal fun notJudgedReason(
    corpusAvailable: Boolean,
    style: ProjectStyle?,
): String =
    when {
        !corpusAvailable -> "this build cannot read the measured fonts on this device"
        style == null -> "no style class is declared and too few letters are drawn for our heuristic to read one; declare one in the Brief"
        else -> "the measured fonts have no ${style.name} class"
    }

internal fun scriptName(code: String): String =
    when (code) {
        "Latn" -> "Latin"
        "Hira" -> "Hiragana"
        "Kana", "Hrkt" -> "Kana"
        "Deva" -> "Devanagari"
        "Arab" -> "Arabic"
        else -> code
    }

private const val HERO_WORD = "Hamburgefonstiv"
private const val PANGRAM = "Sphinx of black quartz, judge my vow."

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpecimenView(
    texture: CanvasTexture,
    project: OpenProject,
    font: ProjectFont,
    economy: ProjectEconomy?,
    style: ProjectStyle?,
    onOpenGlyph: (String) -> Unit,
    onOpenWorkbook: () -> Unit,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val locks = project.state.font.locks
    val cells = remember(font, economy, locks) { glyphCells(font, economy, locks) }
    val counts = remember(font) { projectCounts(font) }
    val coverage = remember(font) { scriptCoverage(font.glyphs.flatMapTo(HashSet()) { it.unicodes }) }
    val word = remember(font) { font.showWord(HERO_WORD) }
    val snapshot = remember(project.state) { loadWorkbookCampaignSnapshot(project) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val listed = project.manifest.scripts
            val codes = listed + coverage.map { it.code }.filter { it !in listed }
            for (code in codes) {
                val have = coverage.firstOrNull { it.code == code }
                val text = if (have != null) "${have.name} · ${have.characters}" else "○ ${scriptName(code)} · none yet"
                BasicText(text = text, style = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.5.sp, color = fg))
            }
        }
        if (word != null) {
            ProjectText(font = font, text = word, sizeSp = 54f, color = texture.ink.toColor(), shrinkToFit = true)
            if (font.canSet(PANGRAM.filter { it.isLetter() })) {
                ProjectText(font = font, text = PANGRAM, sizeSp = 18f, color = texture.ink.toColor(), wrap = true, lineHeight = 1.25f)
            }
        } else {
            BasicText(
                text = "No letters drawn yet. Draw one, or capture a sheet.",
                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp, color = fg),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Count(texture, "", groupThousands(counts.glyphs), "glyphs")
                Count(texture, "", groupThousands(counts.points), "points")
                Count(texture, "", groupThousands(counts.curves), "curves")
                if (economy != null) {
                    Count(texture, "● ", economy.outliers.toString(), "outliers")
                    Count(texture, "◐ ", economy.above.toString(), "above")
                    Count(texture, "✓ ", economy.inRange.toString(), "in range")
                }
            }
            BasicText(
                text =
                    if (economy != null) {
                        "Of ${economy.judged} letters judged against ${economy.classFaces} ${economy.style.name} faces. " +
                            "Class ${economy.style.provenance}. The fence's 0.25 · Q3 minimum is our heuristic."
                    } else {
                        "Not judged: ${notJudgedReason(ProjectDataPacks.corpus != null, style)}."
                    },
                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.5.sp, lineHeight = 16.sp, color = muted),
            )
        }
        if (snapshot is WorkbookCampaignSnapshot.Loaded) {
            snapshot.tasks.firstOrNull { it.index == snapshot.currentTaskIndex }?.let { task ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable(onClick = onOpenWorkbook),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        BasicText(
                            text = "Task ${task.index} · ${task.title}",
                            style =
                                TextStyle(
                                    fontFamily = FontFamily.Default,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = texture.canvas.toColor(),
                                ),
                            modifier = Modifier.background(texture.ink.toColor()).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                        BasicText(
                            text = "gate: ${task.gate.summary}",
                            style = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.5.sp, lineHeight = 16.sp, color = muted),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicText(text = "›", style = TextStyle(fontSize = 18.sp, color = fg))
                }
            }
        }
        val heaviest = heaviestGlyphs(cells)
        if (heaviest.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                Label(texture, "Most points first")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (cell in heaviest) {
                        Column(
                            modifier = Modifier.clickable { onOpenGlyph(cell.glyph.name) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            ProjectGlyphInBox(font, cell.glyph, 34f, texture.ink.toColor(), Modifier.width(52.dp).height(52.dp))
                            CountBadge(texture, cell)
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            Label(texture, "Pipeline")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (step in listOf("Capture · not built", "Trace · ${traceStatusWords(economy)}", "Check · not built")) {
                    BasicText(text = step, style = Typography.mono(sizeSp = 9.5).copy(color = muted, letterSpacing = 0.sp))
                }
            }
        }
    }
}

private fun traceStatusWords(economy: ProjectEconomy?): String =
    when {
        economy == null -> "not measured"
        economy.outliers > 0 -> "${economy.outliers} to refit"
        else -> "none to refit"
    }

@Composable
private fun Label(
    texture: CanvasTexture,
    text: String,
) {
    BasicText(text = text.uppercase(), style = Typography.mono(sizeSp = 9.0).copy(color = texture.muted.toColor()))
}

/** One figure of the counts line: [shape] (a word as well as a colour, never colour alone), the bold number, then what it counts. */
@Composable
private fun Count(
    texture: CanvasTexture,
    shape: String,
    number: String,
    label: String,
) {
    val text: AnnotatedString =
        buildAnnotatedString {
            append(shape)
            withStyle(SpanStyle(color = texture.fg.toColor(), fontWeight = FontWeight.SemiBold)) { append(number) }
            append(" ")
            append(label)
        }
    BasicText(text = text, style = Typography.mono(sizeSp = 11.0).copy(color = texture.muted.toColor(), letterSpacing = 0.sp))
}

/** A glyph's point count with the shape of its verdict: an outlier in an ink block, an above-the-box plain, in range quieter. */
@Composable
private fun CountBadge(
    texture: CanvasTexture,
    cell: GlyphCell,
    modifier: Modifier = Modifier,
) {
    val points = groupThousands(cell.count.onCurveEquivalent)
    val (text, outlier) =
        when {
            !cell.drawn -> "○" to false
            cell.verdict == NodeEconomyVerdict.OUTLIER -> "$points ●" to true
            cell.verdict == NodeEconomyVerdict.ABOVE -> "$points ◐" to false
            cell.verdict == NodeEconomyVerdict.IN_RANGE -> "$points ✓" to false
            else -> points to false
        }
    val base = if (outlier) texture.canvas.toColor() else texture.fg.toColor()
    val color =
        if (cell.verdict ==
            NodeEconomyVerdict.IN_RANGE
        ) {
            base.copy(alpha = 0.6f)
        } else if (cell.verdict == null) {
            texture.muted.toColor()
        } else {
            base
        }
    BasicText(
        text = text,
        style =
            Typography
                .mono(
                    sizeSp = 9.5,
                ).copy(color = color, fontWeight = if (outlier) FontWeight.SemiBold else FontWeight.Normal, letterSpacing = 0.sp),
        modifier = modifier.then(if (outlier) Modifier.background(texture.ink.toColor()).padding(horizontal = 3.dp) else Modifier),
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun GlyphsView(
    texture: CanvasTexture,
    project: OpenProject,
    font: ProjectFont,
    economy: ProjectEconomy?,
    style: ProjectStyle?,
    onOpenGlyph: (String) -> Unit,
) {
    val locks = project.state.font.locks
    val cells = remember(font, economy, locks) { glyphCells(font, economy, locks) }
    val counts = remember(cells) { filterCounts(cells) }
    var filter by remember { mutableStateOf(GlyphFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var boxNote by remember { mutableStateOf<String?>(null) }
    val shown = remember(cells, filter, query) { filterCells(cells, filter, query) }
    val verdictChips = setOf(GlyphFilter.OUTLIERS, GlyphFilter.ABOVE, GlyphFilter.IN_RANGE)
    LaunchedEffect(boxNote) {
        if (boxNote != null) {
            delay(BOX_NOTE_MILLIS)
            boxNote = null
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 200.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (chip in GlyphFilter.entries) {
                            if (chip in verdictChips && economy == null) continue
                            if (chip == GlyphFilter.NOT_DRAWN && counts[chip] == 0) continue
                            val on = chip == filter
                            BasicText(
                                text = "${chip.label} ${counts[chip] ?: 0}",
                                style =
                                    Typography.mono(sizeSp = 10.5).copy(
                                        color = if (on) texture.canvas.toColor() else texture.fg.toColor(),
                                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                        letterSpacing = 0.sp,
                                    ),
                                modifier =
                                    Modifier
                                        .background(if (on) texture.ink.toColor() else texture.ink.toColor().copy(alpha = 0.12f))
                                        .clickable { filter = chip }
                                        .padding(horizontal = 9.dp, vertical = 6.dp),
                            )
                        }
                    }
                    SearchField(texture, query) { query = it }
                }
            }
            items(shown, key = { it.glyph.name }) { cell ->
                GlyphGridCell(
                    texture = texture,
                    font = font,
                    cell = cell,
                    onOpen = { onOpenGlyph(cell.glyph.name) },
                    onBox = {
                        val result = economy?.result(cell.glyph.name)
                        boxNote = glyphBoxSentence(cell.glyph.name, result?.onCurve ?: cell.count.onCurveEquivalent, result, style)
                    },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                BasicText(
                    text =
                        if (shown.isEmpty()) {
                            "No glyph matches. Clear the search or choose All."
                        } else {
                            "Tap a glyph to draw it · long-press for its box"
                        },
                    style = Typography.mono(sizeSp = 9.5).copy(color = texture.muted.toColor(), letterSpacing = 0.sp),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        boxNote?.let { note -> GlyphBoxNote(texture, note, onDismiss = { boxNote = null }) }
    }
}

@Composable
private fun BoxScope.GlyphBoxNote(
    texture: CanvasTexture,
    text: String,
    onDismiss: () -> Unit,
) {
    BasicText(
        text = text,
        style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.5.sp, lineHeight = 17.sp, color = texture.canvas.toColor()),
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 18.dp, end = 18.dp, bottom = 184.dp)
                .background(texture.ink.toColor())
                .clickable(onClick = onDismiss)
                .padding(horizontal = 13.dp, vertical = 10.dp),
    )
}

@Composable
private fun SearchField(
    texture: CanvasTexture,
    query: String,
    onChange: (String) -> Unit,
) {
    val fg = texture.fg.toColor()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, color = fg),
            cursorBrush = SolidColor(fg),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        BasicText(
                            text = "Search a glyph name or character",
                            style = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, color = texture.muted.toColor()),
                        )
                    }
                    inner()
                }
            },
        )
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(texture.muted.toColor().copy(alpha = 0.5f)))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlyphGridCell(
    texture: CanvasTexture,
    font: ProjectFont,
    cell: GlyphCell,
    onOpen: () -> Unit,
    onBox: () -> Unit,
) {
    val verdictWord =
        when (cell.verdict) {
            NodeEconomyVerdict.OUTLIER -> ", outlier"
            NodeEconomyVerdict.ABOVE -> ", above the box"
            NodeEconomyVerdict.IN_RANGE -> ", in range"
            null -> ""
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f / CELL_HEIGHT_RATIO)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${cell.glyph.name}, ${cell.count.onCurveEquivalent} points$verdictWord"
                }.combinedClickable(onClick = onOpen, onLongClick = onBox)
                .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ProjectGlyphInBox(font, cell.glyph, 28f, texture.ink.toColor(), Modifier.fillMaxSize())
        }
        CountBadge(texture, cell)
    }
}

private const val GRID_COLUMNS = 6
private const val CELL_HEIGHT_RATIO = 1.16f
private const val BOX_NOTE_MILLIS = 7000L
