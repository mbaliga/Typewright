// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.asoc.typewright.compile.FeaChecker
import com.asoc.typewright.compile.platformFeaChecker
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.project.KernResolution
import com.asoc.typewright.project.KernScope
import com.asoc.typewright.project.KernSide
import com.asoc.typewright.project.KernSource
import com.asoc.typewright.project.KerningGroups
import com.asoc.typewright.project.KerningTable
import com.asoc.typewright.project.Master
import com.asoc.typewright.ui.deck.ControlDeck
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.SystemBackHandler
import com.asoc.typewright.ui.glass.InspectorEdit
import com.asoc.typewright.ui.glass.InspectorField
import com.asoc.typewright.ui.glass.InspectorRow
import com.asoc.typewright.ui.project.GlyphPathCache
import com.asoc.typewright.ui.project.NoProjectState
import com.asoc.typewright.ui.project.OpenProject
import com.asoc.typewright.ui.project.ProjectFont
import com.asoc.typewright.ui.project.ProjectText
import com.asoc.typewright.ui.project.rememberOpenProject
import com.asoc.typewright.ui.sheet.SheetLayout
import com.asoc.typewright.ui.shell.ConfirmBlock
import com.asoc.typewright.ui.shell.LocalGlyphNav
import com.asoc.typewright.ui.shell.LocalSectionNav
import com.asoc.typewright.ui.shell.PageMenu
import com.asoc.typewright.ui.shell.PageMenuItem
import com.asoc.typewright.ui.shell.ScreenHeader
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.GridToken
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.max

/** The Before the page compares with: the font's master as it stood when the project was opened, or when the person last made a new Before. */
@Stable
internal class SpaceBaseline(
    initial: Master?,
) {
    var master: Master? by mutableStateOf(initial)

    /** When the Before was made, as the page says it ("this project was opened"). */
    var since: String by mutableStateOf("this project was opened")
}

/**
 * The Space page, drawn over the sheet while the router is on Space: the control string in the open
 * project's own outlines with the numbers that matter under it, the inspector, and its own control deck
 * (Spacing · Kerning ‖ Before · After, and the ⋯ menu). It edits the project through a [SpaceEditor], so
 * every change is an undo step and a locked glyph is spaced the way Draw spaces it. Nothing here is a
 * stand-in: with no project open it says so, and a letter the font has not drawn is left out and named.
 *
 * It composes whether or not [active], so the Before it compares with is the font as it stood when the
 * project was opened; it draws and takes input only while [active]. [onOpenPalette] opens the command
 * palette the sheet owns (the ⋯ menu's first item), and [paletteOpen] says it is open, so the page gives
 * back the keyboard and the system back to it.
 *
 * The keyboard: ← and → change the selected number by 1 (Shift: 10), and Tab steps along the letters, whenever
 * a letter (in Kerning, a pair) is selected and After is showing. With Ctrl or ⌘ held, or when the page has nothing for
 * them to change (no project, no letter drawn, a sub-view open), the arrows are not the page's: they move between
 * sections as they do everywhere else. Unsaved `.fea` text is never lost by leaving: the page asks first wherever it can
 * (the left circle, Esc, the seats, the section arc, the Map, the ⋯ menu), and where it cannot (the command palette, a
 * swipe to another room) the text is kept in [SpaceRoomState] and shown again, marked as not saved, when Space is opened.
 */
@Composable
fun SpaceRoom(
    active: Boolean,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
    onOpenPalette: () -> Unit = {},
    paletteOpen: Boolean = false,
    /** Told about the page's state whenever it is made anew (a different project), so a host or a test can read and drive it. */
    onState: (SpaceRoomState) -> Unit = {},
    /** Reads `.fea` text with the language's own parser where the platform has one; the platform's by default. */
    feaChecker: FeaChecker = remember { platformFeaChecker() },
) {
    val project = rememberOpenProject()
    val session = project?.session
    val state = remember(session) { SpaceRoomState() }
    LaunchedEffect(state) { onState(state) }
    val baseline = remember(session) { SpaceBaseline(project?.master) }
    if (baseline.master == null && project?.master != null) baseline.master = project.master
    if (!active) return
    SpacePage(project, state, baseline, texture, modifier, onOpenPalette, paletteOpen, feaChecker)
}

@Composable
private fun SpacePage(
    project: OpenProject?,
    state: SpaceRoomState,
    baseline: SpaceBaseline,
    texture: CanvasTexture,
    modifier: Modifier,
    onOpenPalette: () -> Unit,
    paletteOpen: Boolean,
    feaChecker: FeaChecker,
) {
    val nav = LocalSectionNav.current
    val focus = remember { FocusRequester() }
    val leave: () -> Unit = { nav?.goTo?.invoke(DeckSection.HOME) }
    // Unsaved .fea text kept from before the page was left is said to have been kept.
    LaunchedEffect(state) { state.reopened() }
    val master = project?.master
    val font = project?.font
    val session = project?.session
    val editor = remember(session, master?.id) { if (session != null && master != null) SpaceEditor(session, master.id) else null }
    DisposableEffect(editor) { onDispose { editor?.endScrub(commit = false) } }

    SystemBackHandler(enabled = !paletteOpen) { state.onLeft(leave) }
    // The sheet takes the keyboard back whenever its palette opens or closes; one frame later it is the page's again.
    LaunchedEffect(paletteOpen, state.editingField) {
        if (!paletteOpen && !state.editingField) {
            withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(texture.canvas.toColor())
                // Takes every press the page does not use, so nothing under it (the puck) answers a touch it cannot see.
                .pointerInput(Unit) { detectTapGestures { } }
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { event -> pageKey(event, state, editor, session, leave) },
    ) {
        val layout = SheetLayout.of(Vec2(maxWidth.value.toDouble(), maxHeight.value.toDouble()))
        if (project == null || master == null || font == null || editor == null) {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(title = "Space", subtitle = "Sidebearings and kerning", texture = texture)
                NoProjectState(texture = texture, what = "the control string", modifier = Modifier.padding(horizontal = PAGE_GUTTER_DP.dp))
            }
        } else {
            SpaceContent(project, master, font, editor, state, baseline, texture, layout, feaChecker)
        }

        ControlDeck(
            texture = texture,
            modifier = Modifier.align(Alignment.BottomCenter),
            section = DeckSection.SPACE,
            left = state.left(),
            right = DeckRight.MENU,
            toggles = if (editor == null) emptyList() else state.toggles(),
            sectionStatus = nav?.statusOf ?: { null },
            onLeft = { state.onLeft(leave) },
            onRight = { state.menuOpen = !state.menuOpen },
            onToggle = state::onToggle,
            // A section on the arc and the Map leave the page, so unsaved .fea text is asked about first.
            onSection = nav?.let { router -> { section: DeckSection -> state.guardExit { router.goTo(section) } } },
            onMap = nav?.openMap?.let { open -> { state.guardExit(open) } },
        )
        if (state.menuOpen && project != null && master != null) {
            PageMenu(
                texture = texture,
                items = menuItems(project, master, state, baseline, onOpenPalette),
                onDismiss = { state.menuOpen = false },
            )
        }
        if (state.confirmingDiscard) {
            ConfirmBlock(
                texture = texture,
                message = "Throw away the changes to features.fea that you have not saved?",
                confirmLabel = "Throw away",
                cancelLabel = "Keep editing",
                onConfirm = state::confirmDiscard,
                onCancel = state::cancelDiscard,
            )
        }
    }
}

/** The body of the page for an open project: the header, the string or the open sub-view, and the dock of notice and inspector. */
@Composable
private fun SpaceContent(
    project: OpenProject,
    master: Master,
    font: ProjectFont,
    editor: SpaceEditor,
    state: SpaceRoomState,
    baseline: SpaceBaseline,
    texture: CanvasTexture,
    layout: SheetLayout,
    feaChecker: FeaChecker,
) {
    val glyphNav = LocalGlyphNav.current
    val strings = ControlStrings.all(project.manifest.preferences)
    val activeText = state.activeString?.takeIf { text -> strings.any { it.text == text } } ?: strings.first().text
    val after = remember(font, activeText) { SpaceString.set(font, activeText) }
    val beforeFont = remember(baseline.master) { baseline.master?.let { ProjectFont(it) } }
    val before = remember(beforeFont, activeText) { beforeFont?.let { SpaceString.set(it, activeText) } }
    // The two pictures put side by side by where each letter stands in the string and its glyph, so a string whose drawn letters
    // changed since the Before is still compared letter by letter, and the letters it cannot compare are named.
    val blend = remember(after, before) { SpaceBlend.of(after, before) }
    val glyphNames = remember(font) { font.ufo.glyphs.map { it.name } }
    val progress by animateFloatAsState(if (state.showAfter) 1f else 0f, tween(SHIFT_MS))
    val paths = remember { GlyphPathCache() }
    val table = font.kerningTable

    val selected = state.selected.coerceIn(0, max(after.cells.lastIndex, 0))
    val junction = if (state.mode == SpaceMode.KERNING) after.junctionAt(selected) else null
    val pair = junction?.let { after.cells[it].glyph.name to after.cells[it + 1].glyph.name }
    val cell = after.cells.getOrNull(junction ?: selected)
    val glyphName = cell?.glyph?.name

    // The router's glyph in hand and this page's selection follow each other: a change of the glyph in hand moves the selection to it.
    LaunchedEffect(glyphNav?.current) {
        val wanted = glyphNav?.current ?: return@LaunchedEffect
        if (after.cells
                .getOrNull(state.selected)
                ?.glyph
                ?.name != wanted
        ) {
            after.indexOf(wanted)?.let { state.selected = it }
        }
    }

    // Lambdas, not local functions: they are remembered against what they capture, so a new string gets a new one.
    val say: (SpaceOutcome) -> Unit = { outcome -> state.notice = outcome.toNotice() }
    val select: (Int) -> Unit = { index ->
        state.selected = index
        state.notice = null
        after.cells.getOrNull(index)?.let { glyphNav?.select?.invoke(it.glyph.name) }
    }

    SideEffect { state.target = SpaceTarget(glyphName, pair, junction ?: selected, after.cells.size, select) }

    val edit =
        remember(editor, state, pair, glyphName) {
            InspectorEdit(
                onCommit = { key, text ->
                    val field = SpaceInspectorModel.fieldOf(key) ?: return@InspectorEdit "That field cannot be edited."
                    when (val value = editor.evaluate(text, glyphName)) {
                        is SpaceValue.Bad -> {
                            value.message
                        }

                        is SpaceValue.Units -> {
                            state.field = field
                            say(editor.apply(field, value.forField(field), glyphName, pair, state.scope))
                            null
                        }
                    }
                },
                onScrubStart = { key ->
                    SpaceInspectorModel.fieldOf(key)?.let { field ->
                        state.field = field
                        editor.beginScrub(field, glyphName, pair, state.scope)?.let(say)
                    }
                },
                onScrub = { _, units -> editor.scrubTo(units) },
                onScrubEnd = { commit -> editor.endScrub(commit) },
            )
        }

    val panel = state.panel
    val letters =
        after.cells
            .map { it.char }
            .distinct()
            .take(TITLE_LETTERS)
            .joinToString(" ")
    val dockHeightDp = layout.inspectorBottomDp + SheetLayout.INSPECTOR_ROW_DP + DOCK_NOTICE_DP
    // Where a sub-view's room ends: above the dock, which on the string lies over the string's foot instead.
    val viewportFootDp = if (panel == null) 0.0 else layout.inspectorBottomDp + SheetLayout.INSPECTOR_ROW_DP + PANEL_NOTICE_DP

    Box(Modifier.fillMaxSize()) {
        if (panel == null) SpaceGrid(texture)
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Space · " + (if (panel == null) letters else panel.title),
                subtitle = if (panel == null) state.mode.subtitle else panel.subtitle,
                texture = texture,
            )
            // A sub-view scrolls in the room above the dock, so its notice never lies over what is being edited; the string
            // keeps the board's layout, with the dock over its foot.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = viewportFootDp.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier =
                        Modifier
                            .widthIn(max = CONTENT_MAX_DP.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = PAGE_GUTTER_DP.dp,
                                end = PAGE_GUTTER_DP.dp,
                                bottom = (if (panel == null) dockHeightDp else 0.0).dp + SCROLL_END_DP.dp,
                            ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (panel) {
                        SpacePanel.GROUPS -> {
                            SpaceGroupsPanel(
                                kerning = font.ufo.kerningInfo,
                                master = master.id,
                                glyphNames = remember(glyphNames) { glyphNames.toSet() },
                                selectedGlyph = glyphName,
                                texture = texture,
                                editable = state.editable,
                                onEdit = { edit -> editor.applyGroupEdit(edit).also(say) },
                            )
                        }

                        SpacePanel.FEATURES -> {
                            SpaceFeaturesPanel(
                                saved = font.ufo.kerningInfo.features,
                                state = state,
                                texture = texture,
                                editable = state.editable,
                                glyphNames = glyphNames,
                                checker = feaChecker,
                                onSave = { text -> editor.setFeatures(text).also(say) },
                            )
                        }

                        SpacePanel.STRINGS -> {
                            SpaceStringsPanel(
                                strings = strings,
                                active = activeText,
                                texture = texture,
                                editable = state.editable,
                                onChoose = {
                                    state.activeString = it
                                    state.selected = 0
                                },
                                onAdd = { raw ->
                                    when (val added = ControlStrings.add(editor.preferences(), raw)) {
                                        is ControlStrings.Add.Refused -> {
                                            added.reason
                                        }

                                        is ControlStrings.Add.Added -> {
                                            say(editor.setPreferences(added.preferences))
                                            state.activeString = added.text
                                            state.selected = 0
                                            null
                                        }
                                    }
                                },
                                onRemove = { text ->
                                    say(editor.setPreferences(ControlStrings.remove(editor.preferences(), text)))
                                    if (state.activeString == text) state.activeString = null
                                },
                            )
                        }

                        null -> {
                            StringView(
                                font = font,
                                proofFont = if (state.showAfter) font else beforeFont ?: font,
                                after = after,
                                before = before,
                                blend = blend,
                                progress = progress,
                                paths = paths,
                                activeText = activeText,
                                strings = strings,
                                selected = junction ?: selected,
                                pair = pair,
                                editor = editor,
                                state = state,
                                baseline = baseline,
                                texture = texture,
                                wide = layout.wide,
                                onSelect = select,
                                say = say,
                            )
                        }
                    }
                }
            }
        }
        // The dock: the notice, always at the same place above the deck - on the string and in every sub-view, so that no refusal
        // happens out of sight - and, on the string, the inspector row under it.
        Column(modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = (if (panel == null) DOCK_MAX_DP else CONTENT_MAX_DP).dp)) {
            val notice = state.notice ?: if (panel == null) lockNote(editor, glyphName, state) else readOnlyNote(state)
            if (notice != null) {
                SpaceNoticeLine(
                    notice = notice,
                    texture = texture,
                    modifier = Modifier.padding(horizontal = PAGE_GUTTER_DP.dp),
                    onUnlock = { locked -> say(editor.unlock(locked)) },
                )
            }
            if (panel == null) {
                // Under Before the row says what the picture says: the pair as it stood, not as it is.
                val shownTable = if (state.showAfter) table else beforeFont?.kerningTable ?: table
                val fields: List<InspectorField> =
                    if (state.mode == SpaceMode.SPACING) {
                        SpaceInspectorModel.spacing(cell, table, state.field, state.editable)
                    } else {
                        SpaceInspectorModel.kerning(pair?.first, pair?.second, shownTable, state.editable)
                    }
                InspectorRow(
                    fields = fields,
                    texture = texture,
                    edit = edit,
                    bottomInsetDp = layout.inspectorBottomDp,
                    onEditingChanged = { state.editingField = it },
                    cancelEditing = state.cancelEditTick,
                    horizontalPaddingDp = PAGE_GUTTER_DP.toDouble(),
                )
            } else {
                // Where the inspector row is on the string: the notice keeps its place and the deck stays clear of it.
                Spacer(Modifier.height((layout.inspectorBottomDp + SheetLayout.INSPECTOR_ROW_DP).dp))
            }
        }
    }
}

/** The string, its numbers, and what sits around them: the left-out letters, the Before line, the pair's level and the proof. */
@Composable
private fun StringView(
    font: ProjectFont,
    proofFont: ProjectFont,
    after: SpaceString,
    before: SpaceString?,
    blend: SpaceBlend,
    progress: Float,
    paths: GlyphPathCache,
    activeText: String,
    strings: List<ControlString>,
    selected: Int,
    pair: Pair<String, String>?,
    editor: SpaceEditor,
    state: SpaceRoomState,
    baseline: SpaceBaseline,
    texture: CanvasTexture,
    wide: Boolean,
    onSelect: (Int) -> Unit,
    say: (SpaceOutcome) -> Unit,
) {
    if (after.cells.isEmpty()) {
        val missing = after.leftOutText()
        SpaceSentence(
            text =
                "None of the letters of $activeText are drawn yet" + (missing?.let { " ($it)" } ?: "") +
                    ". Draw some of them, or choose another string from ⋯ · Control strings.",
            texture = texture,
            bold = true,
        )
        return
    }
    SpaceRow(
        after = after,
        before = before,
        blend = blend,
        progress = progress,
        font = font,
        mode = state.mode,
        selected = selected,
        editable = state.editable,
        texture = texture,
        paths = paths,
        maxEmDp = if (wide) WIDE_EM_DP else SpaceRowLayout.MAX_EM_DP,
        // On a phone the string sits in the middle, as the board draws it; on a wide screen it lines up with the text under it.
        centred = !wide,
        onSelect = onSelect,
        onScrubStart = { target ->
            // The letter or pair the press took hold of, read from the string and not from what the page last published,
            // so a drag that starts on the same frame as the selection edits the right one.
            val first = after.cells.getOrNull(target.index)
            val second = after.cells.getOrNull(target.index + 1)
            val pair =
                if (target.field == SpaceField.KERN && first != null &&
                    second != null
                ) {
                    first.glyph.name to second.glyph.name
                } else {
                    null
                }
            val outcome = editor.beginScrub(target.field, first?.glyph?.name, pair, state.scope)
            if (outcome == null) state.field = target.field else say(outcome)
            outcome == null
        },
        onScrub = { units -> editor.scrubTo(units) },
        onScrubEnd = { commit -> editor.endScrub(commit) },
        modifier = Modifier.padding(top = 8.dp),
    )
    after.leftOutText()?.let { SpaceLabel("Not drawn yet, so left out: $it", texture) }

    if (state.mode == SpaceMode.KERNING) KerningNotes(font, pair, blend, editor, state, baseline, texture, say)

    SpaceLabel("Proof", texture, modifier = Modifier.padding(top = 8.dp))
    val others = strings.map { it.text }.filter { it != activeText }
    ProjectText(proofFont, activeText, sizeSp = PROOF_BIG_SP, color = texture.ink.toColor(), shrinkToFit = true)
    if (others.isNotEmpty()) {
        ProjectText(proofFont, others.joinToString(" "), sizeSp = PROOF_SMALL_SP, color = texture.ink.toColor(), wrap = true)
    }
}

/** In Kerning: whether there are pairs yet, what Before means, where a typed value is written, and removing a pair. */
@Composable
private fun KerningNotes(
    font: ProjectFont,
    pair: Pair<String, String>?,
    blend: SpaceBlend,
    editor: SpaceEditor,
    state: SpaceRoomState,
    baseline: SpaceBaseline,
    texture: CanvasTexture,
    say: (SpaceOutcome) -> Unit,
) {
    val table = font.kerningTable
    if (font.ufo.kerningInfo.kerning
            .isEmpty()
    ) {
        SpaceSentence("no pairs yet. Tap between two letters, then type a value or drag it.", texture, muted = true)
    }
    val diff = baseline.master?.let { SpaceDiff.of(it.ufo, font.ufo) }
    if (!state.showAfter) {
        SpaceSentence(
            text = "Before: the spacing and kerning as they stood when ${baseline.since}. Switch to After to change anything.",
            texture = texture,
            muted = true,
        )
        // The Before is the string as it was. A letter that was not drawn then is not in it, and a letter that is not drawn now is
        // not in After: the page says which, instead of showing one picture under both names.
        blend.sentence()?.let { SpaceSentence(it, texture, bold = true) }
        if (diff == null || diff.isEmpty) {
            SpaceSentence("Nothing has changed since then, so Before looks the same as After.", texture, muted = true)
        }
    } else {
        SpaceSentence(diff?.summary()?.let { "Changed since Before: $it." } ?: "Nothing has changed since Before.", texture, muted = true)
    }
    val first = pair?.first
    val second = pair?.second
    if (first != null && second != null && state.showAfter) {
        val grouped = table.groupOf(first, KernSide.FIRST) != null || table.groupOf(second, KernSide.SECOND) != null
        val resolved = table.resolve(first, second)
        // An exception is edited where it is; the choice between the groups and this pair arises only where the value comes from the groups or is not set.
        val choice = grouped && resolved.source != KernSource.PAIR
        if (choice) SpaceLabel("A value you type or drag goes to", texture)
        val removable = resolved.key != null && editor.canRemoveKern(first, second, state.scope)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (choice) {
                SpaceChip("Groups", on = state.scope == KernScope.GROUPS, texture = texture) { state.scope = KernScope.GROUPS }
                SpaceChip(
                    "This pair only",
                    on = state.scope == KernScope.PAIR_ONLY,
                    texture = texture,
                ) { state.scope = KernScope.PAIR_ONLY }
            }
            if (removable) SpaceAction("Remove", texture) { say(editor.removeKern(first, second, state.scope)) }
        }
        SpaceSentence(kernSourceSentence(table, grouped, resolved, state.scope, removable), texture, muted = true)
    }
    if (font.ufo.kerningInfo.features
            ?.let(FEA_OWN_KERN::containsMatchIn) == true
    ) {
        SpaceSentence("features.fea writes its own kern feature, so the build leaves out these pairs. See ⋯ · .fea.", texture, bold = true)
    }
}

/**
 * Where the selected pair's value comes from and where a value typed now would go, in the terms of the scope that is chosen: a
 * pair that is not kerned says where a value would be written (the groups, or this pair alone) and not the other, and a value
 * that comes from the groups says plainly that, with This pair only chosen, there is no exception of its own to remove.
 */
internal fun kernSourceSentence(
    table: KerningTable,
    grouped: Boolean,
    resolved: KernResolution,
    scope: KernScope,
    removable: Boolean,
): String {
    val key = resolved.key
    if (key == null) {
        return when {
            !grouped -> "Not kerned yet."
            scope == KernScope.GROUPS -> "Not kerned. A value typed here goes to the groups, so every letter in them follows."
            else -> "Not kerned. A value typed here is kept for this pair only; the groups stay as they are."
        }
    }
    val comes = "The value comes from ${table.describe(key)}."
    return if (scope == KernScope.PAIR_ONLY && resolved.source != KernSource.PAIR && !removable) {
        "$comes With This pair only chosen it has no exception of its own to remove; choose Groups to remove the group's value."
    } else {
        comes
    }
}

/** A faint grid behind the string, densest at its middle and gone by its edges: the design space's grid, only where there is a letter under study. */
@Composable
private fun SpaceGrid(texture: CanvasTexture) {
    val grid = GridToken.colorFor(texture).toColor()
    val ink = texture.ink.toColor()
    Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val step = GridToken.SIZE_DP.dp.toPx()
        val width = size.width
        val top = GRID_TOP_DP.dp.toPx()
        val height = GRID_HEIGHT_DP.dp.toPx()
        var x = 0f
        while (x <= width) {
            drawLine(grid, Offset(x, top), Offset(x, top + height), strokeWidth = 1.dp.toPx())
            x += step
        }
        var y = top
        while (y <= top + height) {
            drawLine(grid, Offset(0f, y), Offset(width, y), strokeWidth = 1.dp.toPx())
            y += step
        }
        drawRect(
            brush =
                Brush.radialGradient(
                    0f to ink,
                    GRID_SOLID to ink,
                    1f to ink.copy(alpha = 0f),
                    center = Offset(width / 2f, top + height / 2f),
                    radius = max(width, height) * GRID_REACH,
                ),
            blendMode = BlendMode.DstIn,
        )
    }
}

private val SpaceMode.subtitle: String
    get() =
        when (this) {
            SpaceMode.SPACING -> "Sidebearings in font units · advance lines shown"
            SpaceMode.KERNING -> "Pair values in font units · tap between two letters"
        }

private val SpacePanel.title: String
    get() =
        when (this) {
            SpacePanel.GROUPS -> "Groups"
            SpacePanel.FEATURES -> ".fea"
            SpacePanel.STRINGS -> "Control strings"
        }

private val SpacePanel.subtitle: String
    get() =
        when (this) {
            SpacePanel.GROUPS -> "Kerning groups · each change is one undo step"
            SpacePanel.FEATURES -> "features.fea · saved only when you say so"
            SpacePanel.STRINGS -> "Set in your own letters, with the project's kerning"
        }

/** The notice an outcome leaves on the page: its note, why nothing changed, why it was refused, or the lock with its Unlock. */
internal fun SpaceOutcome.toNotice(): SpaceNotice? =
    when (this) {
        is SpaceOutcome.Done -> note?.let { SpaceNotice(it) }
        is SpaceOutcome.Unchanged -> SpaceNotice(reason)
        is SpaceOutcome.Refused -> SpaceNotice(message)
        is SpaceOutcome.Locked -> SpaceNotice(message, unlockGlyph = glyph)
    }

/** The line that says a selected glyph is approved and what spacing does to it, or null. */
private fun lockNote(
    editor: SpaceEditor,
    glyph: String?,
    state: SpaceRoomState,
): SpaceNotice? =
    if (glyph != null && state.mode == SpaceMode.SPACING && editor.isLocked(glyph)) {
        SpaceNotice("$glyph is approved. Spacing slides or widens it without redrawing it.")
    } else {
        null
    }

/** The line that says a sub-view cannot be changed because the string is shown as it stood at Before, or null when it can. */
private fun readOnlyNote(state: SpaceRoomState): SpaceNotice? =
    if (state.editable) null else SpaceNotice("Before is a look back, so nothing here can be changed. Choose After to change anything.")

private fun menuItems(
    project: OpenProject,
    master: Master,
    state: SpaceRoomState,
    baseline: SpaceBaseline,
    onOpenPalette: () -> Unit,
): List<PageMenuItem> {
    val session = project.session
    val history = session.history.value
    val kerning = master.ufo.kerningInfo
    val groupCount = kerning.groups.keys.count { KerningGroups.sideOf(it) != null }
    val features = kerning.features
    return listOf(
        PageMenuItem("Command…", "Ctrl or ⌘ K") { onOpenPalette() },
        PageMenuItem("Undo", history.undoLabels.lastOrNull() ?: "Nothing to undo") { session.undo() },
        PageMenuItem("Redo", history.redoLabels.lastOrNull() ?: "Nothing to redo") { session.redo() },
        // Opening a sub-view goes through the same guard as every other way out of the .fea editor.
        PageMenuItem("Groups", "$groupCount kerning ${if (groupCount == 1) "group" else "groups"}") {
            state.requestPanel(SpacePanel.GROUPS)
        },
        PageMenuItem(".fea", features?.let { "features.fea · ${it.count { c -> c == '\n' } + 1} lines" } ?: "no features.fea yet") {
            state.requestPanel(SpacePanel.FEATURES)
        },
        PageMenuItem(
            "Control strings",
            "In use: ${state.activeString ?: ControlStrings.WORKBOOK.first()}",
        ) { state.requestPanel(SpacePanel.STRINGS) },
        PageMenuItem("Make this the Before", "Compare from now on") {
            baseline.master = project.master
            baseline.since = "you made this the Before"
            state.showAfter(true)
            state.notice = SpaceNotice("Before is now the spacing and kerning as they stand.")
        },
    )
}

/**
 * What a key does on the page, or false to leave it for the sheet (its arrow keys switch rooms).
 *
 * The rule for ← and →: they are the page's - they change the selected number, by 1 or with Shift by 10 - only while there is a
 * number for them to change: a project is open, a letter (in Kerning, a pair) is selected, and no sub-view is up. In
 * Before they are still the page's, and say it is a look back. With Ctrl or ⌘ held, and wherever the page has no number to
 * change, they are left for the sheet, which moves between rooms and sections with them as on every other screen.
 */
private fun pageKey(
    event: KeyEvent,
    state: SpaceRoomState,
    editor: SpaceEditor?,
    session: com.asoc.typewright.project.ProjectSession?,
    leave: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val command = event.isCtrlPressed || event.isMetaPressed
    if (event.key == Key.Escape) {
        state.onLeft(leave)
        return true
    }
    if (command && (event.key == Key.Z || event.key == Key.Y) && session != null) {
        if (event.key == Key.Y || event.isShiftPressed) session.redo() else session.undo()
        return true
    }
    if (command || state.editingField) return false
    return when (event.key) {
        // The arrows are the page's while there is a number for them to change, so they never move the sheet's camera unseen behind
        // it; where there is none they are left for the sheet, so the keyboard still moves between sections.
        Key.DirectionLeft, Key.DirectionRight -> {
            val target = state.target
            val aimed =
                editor != null && state.panel == null && target != null &&
                    (if (state.mode == SpaceMode.KERNING) target.pair != null else target.glyph != null)
            if (!aimed || target == null) return false
            if (!state.editable) {
                state.notice = SpaceNotice("Before is a look back. Switch to After to change anything.")
            } else {
                val step = if (event.isShiftPressed) BIG_STEP else 1
                val delta = if (event.key == Key.DirectionRight) step else -step
                state.notice = editor?.nudge(state.field, delta, target.glyph, target.pair, state.scope)?.toNotice()
            }
            true
        }

        // Tab and Shift Tab step through the letters (the pairs, in Kerning), as in Draw.
        Key.Tab -> {
            val target = state.target
            if (editor == null || state.panel != null || target == null || target.cells == 0) return false
            target.select((target.index + if (event.isShiftPressed) -1 else 1).coerceIn(0, target.cells - 1))
            true
        }

        Key.DirectionUp, Key.DirectionDown -> {
            if (editor == null || state.panel != null || state.mode != SpaceMode.SPACING) return false
            val at = SPACING_FIELDS.indexOf(state.field).coerceAtLeast(0)
            state.field = SPACING_FIELDS[(at + if (event.key == Key.DirectionDown) 1 else SPACING_FIELDS.lastIndex) % SPACING_FIELDS.size]
            true
        }

        else -> {
            false
        }
    }
}

private val SPACING_FIELDS = listOf(SpaceField.LEFT, SpaceField.RIGHT, SpaceField.ADVANCE)
private val FEA_OWN_KERN = Regex("""(?m)^\s*feature\s+kern\b""")
private const val SHIFT_MS = 300
private const val TITLE_LETTERS = 8
private const val PAGE_GUTTER_DP = 22
private const val CONTENT_MAX_DP = 720
private const val DOCK_MAX_DP = 560
private const val DOCK_NOTICE_DP = 24.0

/** The room a sub-view leaves for its notice: two lines of it, so a long one does not climb over what is being edited. */
private const val PANEL_NOTICE_DP = 44.0
private const val SCROLL_END_DP = 12
private const val WIDE_EM_DP = 96.0
private const val PROOF_BIG_SP = 26f
private const val PROOF_SMALL_SP = 17f
private const val BIG_STEP = 10
private const val GRID_TOP_DP = 36
private const val GRID_HEIGHT_DP = 150
private const val GRID_SOLID = 0.3f
private const val GRID_REACH = 0.5f
