// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.ui.brief.LocalOpenBrief
import com.asoc.typewright.ui.deck.ControlDeck
import com.asoc.typewright.ui.deck.DeckLeft
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.DeckToggle
import com.asoc.typewright.ui.glass.projectStatusText
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor
import com.asoc.typewright.ui.workbook.WorkbookCampaignSnapshot
import com.asoc.typewright.ui.workbook.loadWorkbookCampaignSnapshot

/**
 * A station that is not built yet, shown inside the real deck (law 4): what it will do, and that it
 * is not built. Never hidden, never faked.
 */
@Composable
fun StubSection(
    texture: CanvasTexture,
    section: DeckSection,
    modifier: Modifier = Modifier,
) {
    val nav = LocalSectionNav.current
    SectionScaffold(
        texture = texture,
        section = section,
        title = section.label,
        subtitle = "Not built yet",
        modifier = modifier,
        onBack = { nav?.goTo?.invoke(DeckSection.HOME) },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(
                text = STUB_TEXT.getValue(section),
                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp, color = texture.fg.toColor()),
            )
            BasicText(
                text = "NOT BUILT YET · NOTHING HERE RUNS",
                style = Typography.mono(sizeSp = 9.5).copy(color = texture.muted.toColor()),
            )
        }
    }
}

private val STUB_TEXT: Map<DeckSection, String> =
    mapOf(
        DeckSection.CAPTURE to
            "Photograph your printed template sheets. Typewright will find the four corner marks, " +
            "straighten each sheet and cut it into one cell per letter.",
        DeckSection.TRACE to
            "Turn a cell's ink into outlines you can edit, with a few points where a type designer " +
            "would put them, and show every stage so you can see what it did.",
        DeckSection.CHECK to
            "Measure your letters against what Google Fonts accepts and against real fonts of your style, " +
            "as a report and as boxes with your own letters inside them.",
        DeckSection.SHIP to
            "Build the font and write the repository folder Google Fonts expects, and stop with a reason if anything fails.",
    )

/**
 * Home (S04) from what the open project really holds, never a fixture: its name and save state, its
 * scripts, how many glyphs it has, the workbook's next task, and where the pipeline stands. The two
 * views are the deck's: Specimen and Glyphs. With no project open it says so.
 */
@Composable
fun HomeScreen(
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
    onOpenDraw: () -> Unit = {},
    onOpenWorkbook: () -> Unit = {},
) {
    var glyphsView by remember { mutableStateOf(false) }
    val openBrief = LocalOpenBrief.current
    val session =
        LocalProjectWorkspace.current
            ?.current
            ?.collectAsState()
            ?.value
    val projectState = session?.state?.collectAsState()?.value
    val master = projectState?.font?.masters?.let { masters -> masters.firstOrNull { it.isDefault } ?: masters.firstOrNull() }
    val glyphs = master?.ufo?.glyphs.orEmpty()
    val manifest = projectState?.meta?.manifest
    SectionScaffold(
        texture = texture,
        section = DeckSection.HOME,
        title = manifest?.name ?: "Typewright",
        subtitle =
            if (master != null) {
                "${master.styleName} · ${master.ufo.fontInfo.unitsPerEm ?: 1000} · ${projectStatusText()}"
            } else {
                projectStatusText()
            },
        modifier = modifier,
        toggles = listOf(DeckToggle("Specimen", on = !glyphsView), DeckToggle("Glyphs", on = glyphsView)),
        onToggle = { glyphsView = it == 1 },
        left = DeckLeft.NONE,
        menuItems =
            listOfNotNull(
                openBrief?.let { open -> PageMenuItem("Brief", "Find the visual language") { open(null, DeckSection.HOME) } },
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (manifest == null) {
                NoProject(texture)
            } else if (glyphsView) {
                GlyphGrid(texture = texture, names = glyphs.map { it.name }, onOpen = onOpenDraw)
            } else {
                Specimen(texture = texture, scripts = manifest.scripts, glyphCount = glyphs.size, onOpenWorkbook = onOpenWorkbook)
            }
        }
    }
}

@Composable
private fun NoProject(texture: CanvasTexture) {
    BasicText(
        text = "No project is open. Open the ⋯ menu, then Command…, then New project… to make one.",
        style = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp, color = texture.fg.toColor()),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Specimen(
    texture: CanvasTexture,
    scripts: List<String>,
    glyphCount: Int,
    onOpenWorkbook: () -> Unit,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (script in scripts) BasicText(text = "○ ${scriptName(script)}", style = TextStyle(fontSize = 12.5.sp, color = fg))
    }
    BasicText(
        text = if (glyphCount == 0) "No letters yet. Draw one, or capture a sheet." else "$glyphCount glyphs",
        style = Typography.mono(sizeSp = 9.5).copy(color = if (glyphCount == 0) muted else fg),
    )
    val snapshot = remember { loadWorkbookCampaignSnapshot() }
    if (snapshot is WorkbookCampaignSnapshot.Loaded) {
        val task = snapshot.tasks.firstOrNull { it.index == snapshot.currentTaskIndex }
        if (task != null) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenWorkbook),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicText(text = "○", style = TextStyle(fontSize = 13.sp, color = fg))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(
                        text = "Task ${task.index} · ${task.title}",
                        style = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg),
                    )
                    BasicText(text = task.why, style = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp, color = muted))
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(text = "PIPELINE", style = Typography.mono(sizeSp = 9.0).copy(color = muted))
        BasicText(
            text = "Capture · not built    Trace · not built    Check · not built",
            style = Typography.mono(sizeSp = 9.5).copy(color = muted),
        )
    }
}

private fun scriptName(code: String): String =
    when (code) {
        "Latn" -> "Latin"
        "Hira" -> "Hiragana"
        "Kana" -> "Katakana"
        "Deva" -> "Devanagari"
        "Arab" -> "Naskh"
        else -> code
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlyphGrid(
    texture: CanvasTexture,
    names: List<String>,
    onOpen: () -> Unit,
) {
    if (names.isEmpty()) {
        BasicText(text = "No glyphs yet.", style = TextStyle(fontSize = 13.sp, color = texture.fg.toColor()))
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (name in names) {
            BasicText(
                text = name,
                style = Typography.mono(sizeSp = 10.0).copy(color = texture.fg.toColor()),
                modifier = Modifier.clickable(onClick = onOpen).padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * The Map (S24): every section as an ink block, a tap flies there. The deck shows only the left
 * Close, which leaves without going anywhere.
 */
@Composable
fun MapScreen(
    texture: CanvasTexture,
    current: DeckSection,
    onGo: (DeckSection) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(texture.canvas.toColor())) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Map", subtitle = "Tap a section to go there", texture = texture)
            Column(modifier = Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (row in DeckSection.entries.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (section in row) {
                            val on = section == current
                            BasicText(
                                text = section.label.uppercase(),
                                style =
                                    Typography.mono(sizeSp = 12.0).copy(
                                        color = if (on) texture.canvas.toColor() else texture.ink.toColor(),
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .background(if (on) texture.ink.toColor() else texture.ink.toColor().copy(alpha = 0.12f))
                                        .clickable { onGo(section) }
                                        .padding(horizontal = 10.dp, vertical = 28.dp),
                            )
                        }
                    }
                }
            }
        }
        ControlDeck(
            texture = texture,
            modifier = Modifier.align(Alignment.BottomCenter),
            section = current,
            left = DeckLeft.CLOSE,
            right = DeckRight.NONE,
            dimSections = true,
            onLeft = onClose,
        )
    }
}
