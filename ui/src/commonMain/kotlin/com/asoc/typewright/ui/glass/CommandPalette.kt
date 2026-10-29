// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.ui.brief.LocalOpenNewProjectBrief
import com.asoc.typewright.ui.draw.GlyphDirectory
import com.asoc.typewright.ui.imports.rememberFontImportPaletteEntries
import com.asoc.typewright.ui.project.InMemoryProjectFolderPicker
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.LocalZipTransferSupported
import com.asoc.typewright.ui.project.closeCurrentProject
import com.asoc.typewright.ui.project.createUntitledProject
import com.asoc.typewright.ui.project.downloadProjectZip
import com.asoc.typewright.ui.project.openPickedProject
import com.asoc.typewright.ui.project.saveProjectNow
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The command palette (`UI_SPEC.md` §4 and brief §5.2/§10 item 6: Ctrl/⌘ K, and the first item of every
 * ⋯ menu): the explorer's `.palette` (a 520 dp panel 70 dp from the top, an input row with a `›_`
 * prompt, icon-less rows of label, a small muted description and a right-aligned shortcut, the current
 * row a quiet ink tint, square corners as its brutalist pass resets them).
 *
 * What it lists comes from where it is opened. [extraCommands] are the open page's own commands (Draw
 * passes its registry, so each row is the real command with its measured consequence and its reason when
 * it cannot run); the project rows (new, open, save, close) follow when a workspace is provided. With a
 * [directory] and [onGoToGlyph] it also offers "Go to glyph…" and lists glyphs: first when the query is
 * one character or the person chose go-to, after the commands otherwise ([PaletteRanking]). Enter runs
 * the highlighted row, a glyph row goes to that glyph, and the highlight returns to the first row
 * whenever the query changes ([PaletteState]). The rows that need a feature this build does not have
 * (the gate, a comparison font) are shown with the explorer's copy and say so when run.
 */
@Composable
fun CommandPalette(
    visible: Boolean,
    onDismiss: () -> Unit,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
    extraCommands: List<PaletteEntry> = emptyList(),
    directory: GlyphDirectory? = null,
    currentGlyph: String? = null,
    onGoToGlyph: ((String) -> Unit)? = null,
    startInGoTo: Boolean = false,
) {
    if (!visible) return

    var state by remember(startInGoTo) { mutableStateOf(PaletteState(goToMode = startInGoTo && directory != null)) }
    var lastResult by remember { mutableStateOf<String?>(null) }
    val workspace = LocalProjectWorkspace.current
    val zipTransferSupported = LocalZipTransferSupported.current
    val openNewProject = LocalOpenNewProjectBrief.current
    val scope = rememberCoroutineScope()
    val fontImportEntries = rememberFontImportPaletteEntries(onDismiss)
    val canGoTo = directory != null && onGoToGlyph != null && directory.glyphs.isNotEmpty()
    val entries =
        remember(workspace, zipTransferSupported, openNewProject, extraCommands, canGoTo, fontImportEntries) {
            extraCommands +
                listOfNotNull(if (canGoTo) goToGlyphEntry() else null) +
                projectPaletteEntries(workspace, zipTransferSupported, openNewProject, scope) { lastResult = it } +
                fontImportEntries +
                unwiredEntries()
        }
    val rows = remember(state, entries, directory) { PaletteRanking.arrange(state, entries, directory.takeIf { canGoTo }) }
    val highlighted = state.highlighted.coerceIn(0, (rows.size - 1).coerceAtLeast(0))

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(visible) { if (visible) focusRequester.requestFocus() }

    val panelBg = texture.canvas.toColor()
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val ink = texture.ink.toColor()

    fun runRow(index: Int) {
        when (val row = rows.getOrNull(index)) {
            null -> {
                Unit
            }

            is PaletteItem.GlyphRow -> {
                onGoToGlyph?.invoke(row.match.entry.name)
                onDismiss()
            }

            is PaletteItem.Command -> {
                val entry = row.entry
                if (entry.id == GO_TO_GLYPH_ID) {
                    state = state.enteringGoTo()
                } else {
                    val result = entry.run?.invoke() ?: "${entry.label}: not wired in this build"
                    if (entry.closesPalette) onDismiss() else lastResult = result
                }
            }
        }
    }

    Column(
        modifier =
            modifier
                .widthIn(max = PALETTE_WIDTH_DP.dp)
                .padding(top = PALETTE_TOP_DP.dp)
                .background(panelBg)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Escape -> {
                            onDismiss()
                            true
                        }

                        Key.DirectionDown -> {
                            state = state.moved(1, rows.size)
                            true
                        }

                        Key.DirectionUp -> {
                            state = state.moved(-1, rows.size)
                            true
                        }

                        Key.Enter, Key.NumPadEnter -> {
                            runRow(highlighted)
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
    ) {
        // The input row: the `›_` prompt, the query, and the esc hint, as the explorer's `.in` row.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(text = "›_", style = Typography.sentence.copy(color = muted))
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (state.query.isEmpty() && state.goToMode) {
                    BasicText(text = "Go to a glyph: a name, a character or U+0041", style = Typography.sentence.copy(color = muted))
                }
                BasicTextField(
                    value = state.query,
                    onValueChange = { state = state.withQuery(it) },
                    textStyle = Typography.sentence.copy(color = fg),
                    singleLine = true,
                    cursorBrush = SolidColor(fg),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
            BasicText(text = "esc", style = Typography.sentence.copy(color = muted))
        }

        Column(modifier = Modifier.heightIn(max = PALETTE_MAX_LIST_DP.dp).verticalScroll(rememberScrollState())) {
            for ((index, row) in rows.withIndex()) {
                val current = index == highlighted
                val text =
                    when (row) {
                        is PaletteItem.Command -> RowText(row.entry.label, row.entry.description, row.entry.shortcut, row.entry.dimmed)
                        is PaletteItem.GlyphRow -> glyphRowText(row, currentGlyph)
                    }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .let { if (current) it.background(ink.copy(alpha = PALETTE_CURRENT_ROW_TINT)) else it }
                            .clickable { runRow(index) }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val strength = if (text.dimmed) PALETTE_DIMMED_ALPHA else 1f
                    BasicText(
                        text = text.label,
                        style = Typography.sentence.copy(fontSize = PALETTE_ROW_SIZE_SP.sp, color = fg.copy(alpha = strength)),
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        text = "· ${text.description}",
                        style = Typography.sentence.copy(color = muted),
                        modifier = Modifier.weight(1f),
                    )
                    text.shortcut?.let { BasicText(text = it, style = Typography.mono(sizeSp = 11.0).copy(color = muted)) }
                }
            }
            if (rows.isEmpty()) {
                BasicText(
                    text = if (state.goToMode) "No glyph is called that." else "Nothing is called that.",
                    style = Typography.sentence.copy(color = muted),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        lastResult?.let { result ->
            BasicText(
                text = result,
                style = Typography.mono(sizeSp = 10.0).copy(color = muted),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

private data class RowText(
    val label: String,
    val description: String,
    val shortcut: String?,
    val dimmed: Boolean,
)

private fun glyphRowText(
    row: PaletteItem.GlyphRow,
    currentGlyph: String?,
): RowText {
    val entry = row.match.entry
    val description =
        listOfNotNull(
            entry.character?.takeIf { it != entry.name },
            entry.unicodeLabel,
            if (entry.name == currentGlyph) "open now" else null,
        ).joinToString(" · ").ifEmpty { "no unicode" }
    return RowText(label = entry.name, description = description, shortcut = null, dimmed = false)
}

/** Opens/closes [CommandPalette] on Ctrl+K (desktop/Linux/Windows) or ⌘+K (macOS) key-down, per UI_SPEC's own repeated "command palette on Ctrl/⌘ K" -- see [CommandPalette]'s own KDoc on why this lives in commonMain with no desktop-only gate. */
fun Modifier.commandPaletteShortcut(onToggle: () -> Unit): Modifier =
    this.onKeyEvent { event: KeyEvent ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        if (event.key == Key.K && (event.isCtrlPressed || event.isMetaPressed)) {
            onToggle()
            true
        } else {
            false
        }
    }

private const val PALETTE_WIDTH_DP = 520.0
private const val PALETTE_MAX_LIST_DP = 420.0
private const val PALETTE_DIMMED_ALPHA = 0.5f
private const val GO_TO_GLYPH_ID = "go-to-glyph"
private const val PALETTE_TOP_DP = 70.0
private const val PALETTE_ROW_SIZE_SP = 12.5
private const val PALETTE_CURRENT_ROW_TINT = 0.06f

/**
 * One command-palette row: what it shows, and what running it actually does ([run] null is a row shown
 * but not wired in this build, and says so when chosen). [closesPalette] says the effect is visible on
 * the page behind (an edit), so the palette gets out of its way; [dimmed] draws a row that cannot run
 * right now at half strength, with the reason as its description.
 */
data class PaletteEntry(
    val id: String,
    val label: String,
    val description: String,
    val shortcut: String? = null,
    val run: (() -> String)?,
    val closesPalette: Boolean = false,
    val dimmed: Boolean = false,
)

private fun goToGlyphEntry() =
    PaletteEntry(
        id = GO_TO_GLYPH_ID,
        label = "Go to glyph…",
        description = "by name, character or code point",
        shortcut = "Ctrl G",
        run = { "" },
    )

/** The explorer's two rows that need features this build does not have: shown with its copy, and they say so. */
private fun unwiredEntries(): List<PaletteEntry> =
    listOf(
        PaletteEntry(
            id = "gate",
            label = "Run the gate on this glyph",
            description = "node economy, extrema, tangents",
            shortcut = "⌘⏎",
            run = null,
        ),
        PaletteEntry(
            id = "compare",
            label = "Compare with…",
            description = "overlay another font at cap height",
            shortcut = null,
            run = null,
        ),
    )

/**
 * The P11 WP5 stopgap project entries (docs/PROJECT_MODEL.md §13's palette table): "New
 * project…"/"Open project…"/"Save now"/"Close project" always, and the two `.zip` rows only when
 * [zipTransferSupported] (web in-memory mode). Empty when [workspace] is null -- no
 * [LocalProjectWorkspace] was provided at all (Android's real entry waits on P13's deck, per that
 * table's own last row; every existing screenshot test, which never provides one either). Each
 * `run` launches the real, `suspend` [ProjectWorkspace] call on [scope] and reports through
 * [setResult] once it finishes, returning a "working…" line immediately so [CommandPalette]'s own
 * synchronous result line has something honest to show meanwhile. "New project…" also opens the
 * Brief on the new-project step once created ([openNewProject] null when nothing provided [LocalOpenNewProjectBrief]).
 */
private fun projectPaletteEntries(
    workspace: ProjectWorkspace?,
    zipTransferSupported: Boolean,
    openNewProject: (() -> Unit)?,
    scope: CoroutineScope,
    setResult: (String) -> Unit,
): List<PaletteEntry> {
    if (workspace == null) return emptyList()
    val picker = InMemoryProjectFolderPicker(workspace)
    val entries =
        mutableListOf(
            PaletteEntry(
                id = "project-new",
                label = "New project…",
                description = "choose a folder; creates Untitled/ inside",
                run = {
                    scope.launch { setResult(createUntitledProject(workspace, picker, onCreated = { openNewProject?.invoke() })) }
                    "New project…: working…"
                },
            ),
            PaletteEntry(
                id = "project-open",
                label = "Open project…",
                description = "choose an existing project's folder",
                run = {
                    scope.launch { setResult(openPickedProject(workspace, picker)) }
                    "Open project…: working…"
                },
            ),
            PaletteEntry(
                id = "project-save",
                label = "Save now",
                description = "flush the open project to disk",
                run = {
                    scope.launch { setResult(saveProjectNow(workspace)) }
                    "Save now: working…"
                },
            ),
            PaletteEntry(
                id = "project-close",
                label = "Close project",
                description = "flush and close the open project",
                run = {
                    scope.launch { setResult(closeCurrentProject(workspace)) }
                    "Close project: working…"
                },
            ),
        )
    if (zipTransferSupported) {
        entries +=
            PaletteEntry(
                id = "project-zip-download",
                label = "Download project (.zip)",
                description = "web in-memory mode only",
                run = {
                    scope.launch { setResult(downloadProjectZip(workspace)) }
                    "Download project (.zip): working…"
                },
            )
        entries +=
            PaletteEntry(
                id = "project-zip-open",
                label = "Open project (.zip)…",
                description = "web in-memory mode only",
                run = { "Open project (.zip)…: not available in this build (no file picker is wired yet)." },
            )
    }
    return entries
}
