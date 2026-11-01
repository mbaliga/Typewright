// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.asoc.typewright.engine.trace.capture.CellStatus
import com.asoc.typewright.project.capture.CaptureKind
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.DeckToggle
import com.asoc.typewright.ui.project.NoProjectState
import com.asoc.typewright.ui.project.rememberOpenProject
import com.asoc.typewright.ui.shell.ConfirmBlock
import com.asoc.typewright.ui.shell.LocalSectionNav
import com.asoc.typewright.ui.shell.PageMenu
import com.asoc.typewright.ui.shell.PageMenuItem
import com.asoc.typewright.ui.shell.SectionScaffold
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.toColor
import kotlinx.coroutines.launch
import kotlin.math.atan2

/** The views of Capture: its deck's two toggles. */
enum class CaptureView { SHEETS, CELLS }

/** What the person has chosen on the page so far, held where a tap and a screenshot test can both set it. */
@Stable
class CapturePageState(
    view: CaptureView = CaptureView.SHEETS,
    sheet: String? = null,
    relabelCell: Int? = null,
    menuOpen: Boolean = false,
) {
    var view by mutableStateOf(view)

    /** The sheet the Cells view shows, by record id; null means the first that needs the person, else the first. */
    var sheet by mutableStateOf(sheet)

    /** The cell whose relabel list is open. */
    var relabelCell by mutableStateOf(relabelCell)

    /** The plus menu. */
    var menuOpen by mutableStateOf(menuOpen)

    /** A sheet the person swiped to remove, waiting for the confirmation. */
    var removing by mutableStateOf<CaptureItem?>(null)
}

/** What Capture needs done, handed to [CaptureFrame] so a test can drive the page without a library. */
class CaptureActions(
    val pick: (CaptureSource) -> Unit = {},
    val remove: (CaptureItem) -> Unit = {},
    val relabel: (recordId: String, cell: Int, glyph: String) -> Unit = { _, _, _ -> },
    val dismissFailure: () -> Unit = {},
    val openTemplates: () -> Unit = {},
    val back: () -> Unit = {},
)

/**
 * Capture (S08) for the open project, with Templates (S07) as a state under it. Pictures come from the platform's pickers and
 * the camera and are read by the capture engine; what is read is kept in the project (`captures/`), so closing and opening the
 * project keeps it. With no project open it says so. [startOnTemplates] opens Templates first, which is where New project's
 * "draw on paper" leads.
 */
@Composable
fun CaptureScreen(
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
    startOnTemplates: Boolean = false,
    initialView: CaptureView = CaptureView.SHEETS,
) {
    val project = rememberOpenProject()
    val decoder = LocalImageDecoder.current
    val picker = LocalCaptureFilePicker.current
    val library = project?.let { CaptureLibraries.forSession(it.session, decoder) }
    val page = remember { CapturePageState(initialView) }
    var templates by remember { mutableStateOf(startOnTemplates) }
    var camera by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sectionNav = LocalSectionNav.current

    if (library != null) {
        val dropped = PlatformCaptureSupport.dropped
        LaunchedEffect(library) { dropped.collect { files -> library.add(files, CaptureSource.FILE) } }
    }
    if (templates) {
        TemplatesScreen(texture = texture, onBack = { templates = false }, modifier = modifier)
        return
    }
    if (camera && library != null) {
        CameraScreen(
            onClose = { camera = false },
            onPhoto = { bytes, manual ->
                camera = false
                scope.launch { library.add(listOf(PickedFile("camera.jpg", bytes)), CaptureSource.CAMERA, lenient = manual) }
            },
            modifier = modifier,
        )
        return
    }
    val items =
        library
            ?.items
            ?.collectAsState()
            ?.value
            .orEmpty()
    val failure = library?.failure?.collectAsState()?.value
    val note = library?.note?.collectAsState()?.value
    val busy = (library?.busyState?.collectAsState()?.value ?: 0) > 0

    fun pick(source: CaptureSource) {
        page.menuOpen = false
        if (library == null) return
        if (source == CaptureSource.CAMERA) {
            camera = true
            return
        }
        scope.launch {
            when (val outcome = picker.pick(source)) {
                is FilePickOutcome.Picked -> library.add(outcome.files, source)
                is FilePickOutcome.Failed -> library.reportFailure(outcome.message, source)
                FilePickOutcome.Cancelled -> Unit
            }
        }
    }
    CaptureFrame(
        texture = texture,
        projectName = project?.manifest?.name,
        hasProject = project != null,
        items = items,
        failure = failure,
        note = note,
        busy = busy,
        sources = picker.sources,
        acceptsDrops = PlatformCaptureSupport.acceptsDrops,
        page = page,
        actions =
            CaptureActions(
                pick = ::pick,
                remove = { library?.remove(it) },
                relabel = { id, cell, glyph -> library?.relabel(id, cell, glyph) },
                dismissFailure = { library?.dismissFailure() },
                openTemplates = { templates = true },
                back = { sectionNav?.goTo?.invoke(DeckSection.HOME) },
            ),
        modifier = modifier,
    )
}

/** The page itself, drawn from its inputs with no coroutines of its own: what [CaptureScreen] shows and what a screenshot test draws. */
@Composable
fun CaptureFrame(
    texture: CanvasTexture,
    projectName: String?,
    hasProject: Boolean,
    items: List<CaptureItem>,
    failure: AddFailure?,
    note: String?,
    busy: Boolean,
    sources: List<CaptureSource>,
    acceptsDrops: Boolean,
    page: CapturePageState,
    actions: CaptureActions,
    modifier: Modifier = Modifier,
) {
    val sheets = items.filterIsInstance<CaptureItem.Sheet>()
    val ready = sheets.filter { it.state is SheetState.Ready }
    val chosen =
        page.sheet?.let { id -> ready.firstOrNull { it.record.id == id } }
            ?: ready.firstOrNull { (it.state as SheetState.Ready).needYou > 0 }
            ?: ready.firstOrNull()
    val cells = page.view == CaptureView.CELLS && chosen != null
    val readyState = chosen?.state as? SheetState.Ready
    val script =
        readyState?.sheet?.identity?.script ?: sheets.firstNotNullOfOrNull { (it.state as? SheetState.Ready)?.sheet?.identity?.script }
            ?: PrintScript.LATIN
    val total = CaptureLibrary.sheetsIn(script)
    val have = sheets.mapNotNull { it.record.sheet }.toSet().size
    val title = if (cells) "Sheet ${readyState!!.sheet.identity.index + 1} · cells" else "Capture"
    val subtitle =
        when {
            cells -> readyState!!.summary.replace(" · ", " · ")
            !hasProject -> "no project open"
            else -> "${projectName.orEmpty()} · $have of $total sheets"
        }
    val toggles =
        listOf(
            DeckToggle("Sheets", on = page.view == CaptureView.SHEETS),
            DeckToggle(
                "Cells",
                on = page.view == CaptureView.CELLS,
                enabled = ready.isNotEmpty(),
                why = "Capture a sheet first, then its cells are here.",
            ),
        )
    SectionScaffold(
        texture = texture,
        section = DeckSection.CAPTURE,
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        toggles = toggles,
        onToggle = { page.view = if (it == 0) CaptureView.SHEETS else CaptureView.CELLS },
        onBack = actions.back,
        right = DeckRight.PLUS,
        rightLabel = "Add a sheet",
        onRight = {
            if (sources.isEmpty()) {
                page.menuOpen = false
            } else {
                page.menuOpen = !page.menuOpen
            }
        },
        rightDimmed = !hasProject || sources.isEmpty(),
        rightExplanation =
            if (!hasProject) {
                "Open or start a project first."
            } else {
                "This build has no way to add a picture yet, so Capture cannot take one."
            },
    ) {
        val horizontal = 14.dp
        if (!hasProject) {
            Column(Modifier.padding(horizontal = horizontal)) {
                NoProjectState(texture, "your captured sheets")
            }
        } else if (cells && readyState != null) {
            CellsView(texture, chosen!!, readyState, page, actions, horizontal)
        } else {
            SheetsView(texture, items, failure, note, busy, total, have, acceptsDrops, page, actions, horizontal)
        }
        if (page.menuOpen) {
            PageMenu(
                texture = texture,
                items =
                    sources.map { source ->
                        PageMenuItem(source.label, source.note) { actions.pick(source) }
                    },
                onDismiss = { page.menuOpen = false },
            )
        }
        page.removing?.let { item ->
            ConfirmBlock(
                texture = texture,
                message =
                    when (item) {
                        is CaptureItem.Sheet -> "Remove ${rowTitle(item)}? Its picture and cells are deleted from the project."
                        is CaptureItem.SvgBatch -> "Remove these ${item.cells.size} SVG drawings from the project?"
                    },
                confirmLabel = "Remove",
                cancelLabel = "Keep",
                onConfirm = {
                    page.removing = null
                    actions.remove(item)
                },
                onCancel = { page.removing = null },
            )
        }
    }
}

private fun rowTitle(item: CaptureItem.Sheet): String =
    when (val state = item.state) {
        is SheetState.Ready -> state.title
        else -> item.record.name
    }

// ---- Sheets ---------------------------------------------------------------------------------------

@Composable
private fun SheetsView(
    texture: CanvasTexture,
    items: List<CaptureItem>,
    failure: AddFailure?,
    note: String?,
    busy: Boolean,
    total: Int,
    have: Int,
    acceptsDrops: Boolean,
    page: CapturePageState,
    actions: CaptureActions,
    horizontal: androidx.compose.ui.unit.Dp,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState(),
                ).padding(horizontal = horizontal)
                .padding(bottom = DECK_CLEARANCE),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        failure?.let { FailureCard(texture, it, actions) }
        if (busy) BasicText("READING THE PICTURE…", style = mono(muted, 9.5))
        note?.let { BasicText(it, style = sentence(muted)) }
        for (item in items) {
            when (item) {
                is CaptureItem.Sheet -> SheetRow(texture, item, page)
                is CaptureItem.SvgBatch -> SvgRow(texture, item, page)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = if (items.isEmpty()) 0.dp else 6.dp)) {
            val left = total - have
            val headline =
                when {
                    items.isEmpty() -> "No sheets yet"
                    left > 0 -> "$left sheets to go"
                    else -> "All $total sheets are in"
                }
            BasicText(headline, style = sentence(fg, 14.0, FontWeight.SemiBold))
            BasicText(
                text =
                    (if (items.isEmpty()) "Print the sheets and draw on them, then " else "") +
                        (if (items.isEmpty()) "photograph" else "Photograph") +
                        " them, pick them from the gallery, or " +
                        (if (acceptsDrops) "drop scans and SVG drawings in" else "choose scans and SVG drawings") +
                        ". Nothing is traced until you open Trace.",
                style = sentence(muted),
            )
            LinkText("Print the sheets", texture, actions.openTemplates, Modifier.padding(top = 4.dp))
        }
    }
}

private val DECK_CLEARANCE = 190.dp

@Composable
private fun FailureCard(
    texture: CanvasTexture,
    failure: AddFailure,
    actions: CaptureActions,
) {
    val onInk = texture.canvas.toColor()
    Column(
        modifier = Modifier.fillMaxWidth().background(texture.ink.toColor()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText("● NOT READ · ${failure.name.uppercase()}", style = mono(onInk, 9.5))
        BasicText(failure.sentence, style = sentence(onInk, 13.0))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            failure.source?.let { source ->
                BasicText(
                    text = if (source == CaptureSource.CAMERA) "Retake" else "Choose again",
                    style = sentence(onInk, 12.5, FontWeight.SemiBold),
                    modifier = Modifier.clickable { actions.pick(source) }.padding(vertical = 8.dp),
                )
            }
            BasicText(
                text = "Dismiss",
                style = sentence(onInk.copy(alpha = 0.72f), 12.5),
                modifier = Modifier.clickable { actions.dismissFailure() }.padding(vertical = 8.dp),
            )
        }
    }
}

/**
 * A row that can be swiped left to remove: it follows the finger, and past [SWIPE_REMOVE_DP] it asks to be removed, which the
 * page confirms. The same removal is offered to a screen reader as a custom action, and a tap opens the row.
 */
@Composable
private fun SwipeRow(
    texture: CanvasTexture,
    key: Any,
    removeLabel: String,
    onRemove: () -> Unit,
    onOpen: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var drag by remember { mutableStateOf(0f) }
    val threshold = with(density) { SWIPE_REMOVE_DP.dp.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(removeLabel) {
                            onRemove()
                            true
                        },
                    )
            },
    ) {
        BasicText(
            "REMOVE",
            style = mono(texture.muted.toColor(), 9.5),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
        )
        Row(
            modifier =
                Modifier
                    .offset {
                        androidx.compose.ui.unit
                            .IntOffset(drag.toInt().coerceIn(-(threshold * 1.4f).toInt(), 0), 0)
                    }.background(texture.canvas.toColor())
                    .fillMaxWidth()
                    .pointerInput(key) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val pulled = drag <= -threshold
                                drag = 0f
                                if (pulled) onRemove()
                            },
                            onDragCancel = { drag = 0f },
                        ) { _, dx -> drag = (drag + dx).coerceAtMost(0f) }
                    }.let { if (onOpen != null) it.clickable(onClick = onOpen) else it },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

@Composable
private fun SheetRow(
    texture: CanvasTexture,
    item: CaptureItem.Sheet,
    page: CapturePageState,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    val ready = item.state as? SheetState.Ready
    val title = rowTitle(item)
    SwipeRow(
        texture,
        item.key,
        "Remove $title",
        onRemove = { page.removing = item },
        onOpen =
            if (ready == null) {
                null
            } else {
                {
                    page.sheet = item.record.id
                    page.view = CaptureView.CELLS
                }
            },
    ) {
        SheetThumb(texture, ready)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
            BasicText(title, style = sentence(fg, 15.0, FontWeight.SemiBold))
            when (val state = item.state) {
                is SheetState.Ready -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.needYou > 0) {
                            InkTag("${state.needYou} need you", texture)
                            BasicText(" · ${state.read} READ", style = mono(fg, 9.5))
                        } else {
                            BasicText("✓ ${state.read} READ", style = mono(fg, 9.5))
                        }
                    }
                }

                SheetState.Reading -> {
                    BasicText("READING…", style = mono(fg, 9.5))
                }

                is SheetState.Unreadable -> {
                    BasicText("● NOT READ", style = mono(fg, 9.5))
                    BasicText(state.sentence, style = sentence(muted, 12.0), modifier = Modifier.width(220.dp))
                }
            }
            BasicText(item.sourceLine.uppercase(), style = mono(muted, 9.0))
        }
    }
}

private const val SWIPE_REMOVE_DP = 72

@Composable
private fun SheetThumb(
    texture: CanvasTexture,
    ready: SheetState.Ready?,
) {
    val desk = texture.ink.toColor().copy(alpha = 0.12f)
    val paper = texture.canvas.toColor()
    val ink = texture.ink.toColor()
    val tilt = ready?.let { tiltDegrees(it) } ?: 0.0
    Box(Modifier.size(width = 76.dp, height = 96.dp).background(desk), contentAlignment = Alignment.Center) {
        val thumb = ready?.thumbnail
        Box(
            Modifier.size(width = 56.dp, height = 78.dp).rotate(tilt.toFloat()).background(paper),
        ) {
            if (thumb != null) InkPicture(thumb, ink.copy(alpha = 0.8f), Modifier.fillMaxSize())
        }
    }
}

/** How far the sheet is turned in its photograph, in degrees, from where its top edge runs; the board tilts the thumbnail by it. */
internal fun tiltDegrees(ready: SheetState.Ready): Double {
    val h = ready.sheet.pageToPhoto
    val a = h.apply(0.0, 0.0)
    val b = h.apply(ready.sheet.layout.pageWidthMm, 0.0)
    val degrees = atan2(b.y - a.y, b.x - a.x) * 180.0 / kotlin.math.PI
    return degrees.coerceIn(-MAX_TILT, MAX_TILT)
}

private const val MAX_TILT = 6.0

@Composable
private fun SvgRow(
    texture: CanvasTexture,
    item: CaptureItem.SvgBatch,
    page: CapturePageState,
) {
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()
    SwipeRow(texture, item.key, "Remove ${item.title}", onRemove = { page.removing = item }, onOpen = null) {
        Box(
            Modifier.size(width = 76.dp, height = 96.dp).background(texture.ink.toColor().copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("SVG", style = mono(fg, 11.0))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
            BasicText(item.title, style = sentence(fg, 15.0, FontWeight.SemiBold))
            BasicText(item.summary.uppercase(), style = mono(fg, 9.5))
            BasicText(item.sourceLine.uppercase(), style = mono(muted, 9.0))
        }
    }
}

// ---- Cells ----------------------------------------------------------------------------------------

@Composable
private fun CellsView(
    texture: CanvasTexture,
    item: CaptureItem.Sheet,
    ready: SheetState.Ready,
    page: CapturePageState,
    actions: CaptureActions,
    horizontal: androidx.compose.ui.unit.Dp,
) {
    val script = ready.sheet.identity.script
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(
                        rememberScrollState(),
                    ).padding(horizontal = horizontal)
                    .padding(bottom = DECK_CLEARANCE),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (row in ready.cells.chunked(CELL_COLUMNS)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (cell in row) {
                        CellTile(texture, script, cell, Modifier.weight(1f)) { page.relabelCell = cell.index }
                    }
                    repeat(CELL_COLUMNS - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        val open = page.relabelCell?.let { index -> ready.cells.firstOrNull { it.index == index } }
        if (open != null) {
            RelabelList(texture, script, open, item.record.id, actions, onDone = { page.relabelCell = null })
        }
    }
}

private const val CELL_COLUMNS = 4

@Composable
private fun CellTile(
    texture: CanvasTexture,
    script: PrintScript,
    cell: CellView,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val unsure = cell.needsYou
    val bg = if (unsure) texture.ink.toColor() else androidx.compose.ui.graphics.Color.Transparent
    val fg = if (unsure) texture.canvas.toColor() else texture.ink.toColor()
    Column(
        modifier =
            modifier
                .height(84.dp)
                .background(bg)
                .clickable(onClick = onClick)
                .semantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (cell.hasInk) {
            InkPicture(cell.outline, fg, Modifier.size(width = 56.dp, height = 44.dp))
        } else {
            BasicText("—", style = sentence(fg.copy(alpha = 0.5f), 20.0), modifier = Modifier.height(44.dp))
        }
        BasicText(
            cellLabel(script, cell).uppercase().let {
                if (unsure) cellLabel(script, cell) else it
            },
            style = mono(fg.copy(alpha = 0.78f), 9.5),
        )
    }
}

/** What a cell is called under its drawing: the character it should be, or "l or I ?" when the reader is unsure, or the name when it has no character. */
internal fun cellLabel(
    script: PrintScript,
    cell: CellView,
): String {
    val status = cell.status
    if (cell.needsYou && status is CellStatus.Uncertain) {
        val top = status.candidates.take(2).map { glyphMark(script, it.glyphName) }
        return top.joinToString(" or ") + " ?"
    }
    if (!cell.hasInk) return glyphMark(script, cell.glyphName) + " empty"
    return glyphMark(script, cell.glyphName)
}

/** The character a glyph name stands for in [script]'s templates, or the name when it has none or is not one of them. */
internal fun glyphMark(
    script: PrintScript,
    name: String,
): String = TemplatePack.printGlyph(script, name)?.character?.takeIf { it.isNotBlank() } ?: name

@Composable
private fun RelabelList(
    texture: CanvasTexture,
    script: PrintScript,
    cell: CellView,
    recordId: String,
    actions: CaptureActions,
    onDone: () -> Unit,
) {
    val ink = texture.ink.toColor()
    val paper = texture.canvas.toColor()
    val muted = texture.muted.toColor()
    val status = cell.status
    val names =
        buildList {
            if (status is CellStatus.Uncertain) status.candidates.forEach { add(it.glyphName) }
            if (cell.templateName !in this) add(cell.templateName)
        }.take(MAX_CANDIDATES)
    var typed by remember(cell.index) { mutableStateOf("") }
    Box(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone),
    ) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset(y = (-40).dp)
                    .width(240.dp)
                    .background(paper)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText(
                "CELL ${cell.index + 1} · ${if (status is CellStatus.Uncertain) "MOST LIKELY FIRST" else "WHAT IS IT?"}",
                style = mono(muted, 9.0),
            )
            names.forEachIndexed { i, name ->
                val first = i == 0 && status is CellStatus.Uncertain
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(if (first) Modifier.background(ink) else Modifier)
                            .clickable {
                                actions.relabel(recordId, cell.index, name)
                                onDone()
                            }.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    val color = if (first) paper else ink
                    BasicText(glyphMark(script, name), style = sentence(color, 20.0))
                    BasicText(
                        (TemplatePack.printGlyph(script, name)?.unicodeName?.lowercase() ?: name).uppercase(),
                        style = mono(color, 9.5),
                    )
                }
            }
            if (status is CellStatus.Uncertain) BasicText(status.rankedBy.sentence, style = mono(muted, 8.5, 0.04))
            BasicText(status.reasonLine(), style = sentence(muted, 11.5))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(
                    value = typed,
                    onValueChange = { typed = it.take(MAX_NAME) },
                    singleLine = true,
                    textStyle = mono(ink, 11.0),
                    cursorBrush = SolidColor(ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                if (typed.isNotBlank()) {
                                    actions.relabel(recordId, cell.index, typed.trim())
                                    onDone()
                                }
                            },
                        ),
                    modifier = Modifier.weight(1f).background(ink.copy(alpha = 0.08f)).padding(horizontal = 8.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        Box {
                            if (typed.isEmpty()) BasicText("OR TYPE ANY NAME", style = mono(muted, 9.0))
                            inner()
                        }
                    },
                )
            }
            if (cell.relabelled) {
                BasicText(
                    text = "Back to ${glyphMark(script, cell.templateName)}",
                    style = sentence(texture.ink.toColor(), 12.0, FontWeight.SemiBold),
                    modifier =
                        Modifier
                            .clickable {
                                actions.relabel(recordId, cell.index, cell.templateName)
                                onDone()
                            }.padding(vertical = 4.dp),
                )
            }
        }
    }
}

private fun CellStatus.reasonLine(): String =
    when (this) {
        is CellStatus.Uncertain -> reason.sentence
        CellStatus.Empty -> "Nothing is drawn in this box."
        CellStatus.Read -> "This box was read as the template says. Rename it if you drew a different glyph here."
    }

private const val MAX_CANDIDATES = 5
private const val MAX_NAME = 40

/** The shared libraries of open projects: one per session, kept for the life of the process so leaving Capture does not throw away what was read. */
object CaptureLibraries {
    private var current: Pair<com.asoc.typewright.project.ProjectSession, CaptureLibrary>? = null
    private var scope: kotlinx.coroutines.CoroutineScope? = null

    /** The library for [session], made (and started) on first ask and replaced when another project is opened. */
    fun forSession(
        session: com.asoc.typewright.project.ProjectSession,
        decoder: ImageDecoder,
    ): CaptureLibrary {
        current?.takeIf { it.first === session }?.let { return it.second }
        scope?.let { (it.coroutineContext[kotlinx.coroutines.Job])?.cancel() }
        val fresh = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        scope = fresh
        return CaptureLibrary(session, decoder, fresh).also {
            it.start()
            current = session to it
        }
    }
}

/** The texture the Camera draws on: black, as the board's black field. */
internal val CameraTexture: CanvasTexture get() = CanvasTextures.BLACK
