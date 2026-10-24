// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.ui.check.SegmentChip
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.glass.InspectorEdit
import com.asoc.typewright.ui.glass.InspectorField
import com.asoc.typewright.ui.glass.InspectorRow
import com.asoc.typewright.ui.project.OpenProject
import com.asoc.typewright.ui.project.ProjectFont
import com.asoc.typewright.ui.project.ProjectText
import com.asoc.typewright.ui.project.nameTableLines
import com.asoc.typewright.ui.project.nameTablePreview
import com.asoc.typewright.ui.project.threeDecimals
import com.asoc.typewright.ui.project.unitsText
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor

/**
 * Font info (S06): what `fontinfo.plist` of the open project says, and where it is changed. Each value is a mono label above a
 * value; a tap opens it to type (Enter keeps it, Esc puts the old one back) and every change is one undo step through the
 * session ([FontInfoEditor]). The family, style and version; the vertical metrics as numbers that take `=` and an expression,
 * with the project's own letters set at the line spacing those metrics give; who made it and under what licence (a name the
 * font does not state is shown as not set, with a shape, and left empty until the person types it; the Open Font License and
 * Apache write their standard lines); and a preview of the names a compiler would write. [focus] opens the page with that value
 * already open for typing, which is where a Check finding's action lands.
 */
@Composable
fun FontInfoScreen(
    texture: CanvasTexture,
    project: OpenProject,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    focus: FontInfoField? = null,
) {
    val master = project.master
    val font = project.font
    SectionScaffold(
        texture = texture,
        section = DeckSection.HOME,
        title = "Font info",
        subtitle = "fontinfo.plist · tap a value to change it",
        modifier = modifier,
        onBack = onBack,
    ) {
        if (master == null || font == null) {
            BasicText(
                text = "This project has no master font, so there is no font info to show.",
                style = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, color = texture.fg.toColor()),
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            return@SectionScaffold
        }
        val editor = remember(project.session, master.id) { FontInfoEditor(project.session, master.id) }
        FontInfoPage(texture, editor, master.ufo.fontInfo, font, focus)
    }
}

/** What the person last did on the page that deserves a sentence, and the part of the page it belongs under. */
private class Notice(
    val section: FontInfoSection,
    val text: String,
)

@Composable
private fun FontInfoPage(
    texture: CanvasTexture,
    editor: FontInfoEditor,
    info: UfoFontInfo,
    font: ProjectFont,
    focus: FontInfoField?,
) {
    var editing by remember(focus) { mutableStateOf(focus) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    val requesters = remember { FontInfoField.entries.associateWith { BringIntoViewRequester() } }
    LaunchedEffect(focus) { focus?.let { requesters.getValue(it).bringIntoView() } }

    /** Applies [outcome] to the page: the sentence it earns, and whether the field closes. Returns the reason it stayed open, if it did. */
    fun settle(
        field: FontInfoField,
        outcome: FontInfoOutcome,
    ): String? {
        if (outcome is FontInfoOutcome.Refused) return outcome.message
        notice = (outcome as? FontInfoOutcome.Done)?.note?.let { Notice(field.section, it) }
        editing = null
        return null
    }

    fun commit(
        field: FontInfoField,
        typed: String,
    ): String? = settle(field, editor.commit(field, typed))

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                for (field in listOf(FontInfoField.FAMILY, FontInfoField.STYLE)) {
                    InfoField(
                        texture = texture,
                        field = field,
                        value = FontInfoRules.textIn(info, field),
                        editing = editing == field,
                        onOpen = { editing = field },
                        onCancel = { editing = null },
                        onCommit = { commit(field, it) },
                        modifier = Modifier.weight(1f).bringIntoViewRequester(requesters.getValue(field)),
                        unset = "◐ Not set",
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                InfoField(
                    texture = texture,
                    field = FontInfoField.VERSION,
                    value = FontInfoRules.textIn(info, FontInfoField.VERSION),
                    editing = editing == FontInfoField.VERSION,
                    onOpen = { editing = FontInfoField.VERSION },
                    onCancel = { editing = null },
                    onCommit = { commit(FontInfoField.VERSION, it) },
                    modifier = Modifier.weight(1f).bringIntoViewRequester(requesters.getValue(FontInfoField.VERSION)),
                    unset = "—",
                )
                ReadOnlyField(texture, "Units per em", font.unitsPerEm.toString(), Modifier.weight(1f))
            }
            Muted(texture, "Units per em is fixed when a project begins.")
            notice?.takeIf { it.section == FontInfoSection.IDENTITY }?.let { Muted(texture, it.text) }
        }
        Metrics(
            texture,
            editor,
            info,
            font,
            editing,
            requesters,
            notice?.takeIf { it.section == FontInfoSection.METRICS }?.text,
        ) { field, typed ->
            commit(field, typed)
        }
        PeopleAndLicence(
            texture = texture,
            info = info,
            editing = editing,
            requesters = requesters,
            notice = notice?.takeIf { it.section == FontInfoSection.PEOPLE_AND_LICENCE }?.text,
            onOpen = { editing = it },
            onCancel = { editing = null },
            onCommit = ::commit,
            onChoose = { choice ->
                when (val outcome = editor.chooseLicence(choice)) {
                    is FontInfoOutcome.Done -> {
                        notice = Notice(FontInfoSection.PEOPLE_AND_LICENCE, "The ${choice.label} lines are written to name IDs 13 and 14.")
                        editing = null
                    }

                    is FontInfoOutcome.Unchanged -> {
                        // The person's own words: open the text to type.
                        notice = Notice(FontInfoSection.PEOPLE_AND_LICENCE, outcome.reason)
                        editing =
                            if (choice == LicenceChoice.PROPRIETARY || choice == LicenceChoice.OTHER) FontInfoField.LICENCE_TEXT else null
                    }

                    is FontInfoOutcome.Refused -> {
                        notice = Notice(FontInfoSection.PEOPLE_AND_LICENCE, outcome.message)
                    }
                }
            },
        )
        NameTable(texture, info)
    }
}

@Composable
private fun Metrics(
    texture: CanvasTexture,
    editor: FontInfoEditor,
    info: UfoFontInfo,
    font: ProjectFont,
    editing: FontInfoField?,
    requesters: Map<FontInfoField, BringIntoViewRequester>,
    notice: String?,
    commit: (FontInfoField, String) -> String?,
) {
    val ascender = FontInfoRules.ascenderOf(info)
    val descender = FontInfoRules.descenderOf(info)
    val lineGap = info.openTypeHheaLineGap ?: 0
    val unset = "—"
    val fields =
        listOf(
            InspectorField("Ascender", ascender?.let(::unitsText) ?: unset, key = FontInfoField.ASCENDER.name),
            InspectorField(
                "Cap",
                info.capHeight?.let(::unitsText) ?: unset,
                emphasize = info.capHeight != null,
                key = FontInfoField.CAP_HEIGHT.name,
            ),
            InspectorField("x-height", info.xHeight?.let(::unitsText) ?: unset, key = FontInfoField.X_HEIGHT.name),
            InspectorField("Descender", descender?.let(::unitsText) ?: unset, key = FontInfoField.DESCENDER.name),
            InspectorField("Line gap", unitsText(lineGap), key = FontInfoField.LINE_GAP.name),
        )
    val edit =
        InspectorEdit(
            onCommit = { key, typed -> commit(FontInfoField.valueOf(key), typed) },
            onScrubStart = {},
            onScrub = { _, _ -> },
            onScrubEnd = {},
        )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(texture, "Vertical metrics")
        val landing = editing?.takeIf { it.section == FontInfoSection.METRICS }
        Column(modifier = Modifier.bringIntoViewRequester(requesters.getValue(landing ?: FontInfoField.ASCENDER))) {
            InspectorRow(
                fields = fields,
                texture = texture,
                edit = edit,
                bottomInsetDp = 0.0,
                horizontalPaddingDp = 0.0,
                initialEditingKey = landing?.name,
                // Opening a value elsewhere on the page puts this row's open number away.
                cancelEditing = (editing?.takeIf { it.section != FontInfoSection.METRICS }?.ordinal ?: -1) + 1,
            )
        }
        if (ascender != null && descender != null) {
            val total = ascender - descender + lineGap
            val em = total.toDouble() / font.unitsPerEm
            val word = font.showWord(PREVIEW_WORD)
            if (word != null) {
                ProjectText(
                    font = font,
                    text = word + "\n" + (font.showWord(PREVIEW_SECOND_LINE) ?: word),
                    sizeSp = 24f,
                    color = texture.ink.toColor(),
                    wrap = true,
                    lineHeight = em.toFloat(),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            val gap = if (lineGap != 0) " + $lineGap line gap" else ""
            Muted(texture, "Line spacing preview at ${threeDecimals(em)} em (${unitsText(ascender)} + ${-descender}$gap).")
        } else {
            Muted(
                texture,
                "The font states no ascender or descender, so there is no line spacing to preview. Tap Ascender or Descender to set one.",
            )
        }
        Muted(texture, "A number, or = and a name: =xheight, =capheight/2, =n.rsb+4.")
        notice?.let { Muted(texture, it) }
    }
}

@Composable
private fun PeopleAndLicence(
    texture: CanvasTexture,
    info: UfoFontInfo,
    editing: FontInfoField?,
    requesters: Map<FontInfoField, BringIntoViewRequester>,
    notice: String?,
    onOpen: (FontInfoField) -> Unit,
    onCancel: () -> Unit,
    onCommit: (FontInfoField, String) -> String?,
    onChoose: (LicenceChoice) -> Unit,
) {
    @Composable
    fun field(
        field: FontInfoField,
        size: Float,
        unset: String,
    ) {
        InfoField(
            texture = texture,
            field = field,
            value = FontInfoRules.textIn(info, field),
            editing = editing == field,
            onOpen = { onOpen(field) },
            onCancel = onCancel,
            onCommit = { onCommit(field, it) },
            modifier = Modifier.bringIntoViewRequester(requesters.getValue(field)),
            valueSize = size,
            unset = unset,
            small = true,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(texture, "People and licence")
        field(FontInfoField.DESIGNER, 14f, "◐ Not set · add one")
        field(FontInfoField.DESIGNER_URL, 13f, "—")
        field(FontInfoField.COPYRIGHT, 13f, "◐ Not set · add one")
        LicenceChoices(texture, FontInfoRules.licenceOf(info), onChoose)
        field(FontInfoField.LICENCE_TEXT, 14f, "◐ Not set · choose one above or write your own")
        field(FontInfoField.LICENCE_URL, 13f, "—")
        field(FontInfoField.VENDOR_ID, 14f, "—")
        notice?.let { Muted(texture, it) }
        Muted(
            texture,
            "Typewright writes only what you type here, or the standard lines of the licence you choose. It never fills in a name for you.",
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LicenceChoices(
    texture: CanvasTexture,
    current: LicenceChoice?,
    onChoose: (LicenceChoice) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Label(texture, "Licence kind", small = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (choice in LicenceChoice.entries) {
                SegmentChip(choice.label, selected = choice == current, texture = texture, onClick = { onChoose(choice) })
            }
        }
    }
}

@Composable
private fun NameTable(
    texture: CanvasTexture,
    info: UfoFontInfo,
) {
    val rows = nameTablePreview(info)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(texture, "Name table")
        if (rows.isEmpty()) {
            Muted(texture, "The font has no family name, so there are no names to write.")
        } else {
            for (line in nameTableLines(rows)) {
                BasicText(
                    text = line,
                    style = Typography.mono(sizeSp = 11.0).copy(color = texture.fg.toColor(), lineHeight = 18.7.sp, letterSpacing = 0.sp),
                )
            }
            Muted(texture, "What the keys above say. This is a preview of the names a compiler would write, not a build.")
        }
    }
}

@Composable
private fun Muted(
    texture: CanvasTexture,
    text: String,
) {
    BasicText(text = text, style = TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, color = texture.muted.toColor()))
}

@Composable
private fun Label(
    texture: CanvasTexture,
    text: String,
    small: Boolean = false,
) {
    BasicText(
        text = text.uppercase(),
        style = Typography.mono(sizeSp = if (small) 8.5 else 9.5).copy(color = texture.muted.toColor(), letterSpacing = 0.14.em),
    )
}

/** A mono label above a value that cannot be changed here. */
@Composable
private fun ReadOnlyField(
    texture: CanvasTexture,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Label(texture, label)
        BasicText(text = value, style = valueStyle(texture, 15f))
    }
}

private fun valueStyle(
    texture: CanvasTexture,
    size: Float,
): TextStyle =
    TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = size.sp,
        lineHeight = (size * VALUE_LINE_HEIGHT).sp,
        fontWeight = if (size >= SEMIBOLD_FROM) FontWeight.SemiBold else FontWeight.Normal,
        color = texture.fg.toColor(),
    )

private const val VALUE_LINE_HEIGHT = 1.4f
private const val SEMIBOLD_FROM = 15f

/**
 * A mono label above a value that opens to be typed over when tapped. Enter (or the keyboard's Done, or moving to another
 * value) keeps what was typed, Esc puts the old value back, and a value the project refuses stays open under the reason in
 * plain words. A value the font does not state is shown as [unset], with a shape, rather than filled in.
 */
@Composable
private fun InfoField(
    texture: CanvasTexture,
    field: FontInfoField,
    value: String?,
    editing: Boolean,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onCommit: (String) -> String?,
    modifier: Modifier = Modifier,
    valueSize: Float = 15f,
    unset: String = "—",
    small: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Label(texture, field.label, small)
        if (editing) {
            FieldEditor(texture, value.orEmpty(), valueSize, field, onCancel, onCommit)
        } else {
            BasicText(
                text = value ?: unset,
                style = valueStyle(texture, valueSize),
                modifier =
                    Modifier
                        .semantics { role = Role.Button }
                        .clickable(onClick = onOpen)
                        .padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun FieldEditor(
    texture: CanvasTexture,
    initial: String,
    valueSize: Float,
    field: FontInfoField,
    onCancel: () -> Unit,
    onCommit: (String) -> String?,
) {
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    var problem by remember { mutableStateOf<String?>(null) }
    var hadFocus by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun keep() {
        if (finished) return
        problem = onCommit(text.text)
        if (problem == null) finished = true
    }

    fun putBack() {
        if (finished) return
        finished = true
        onCancel()
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                problem = null
            },
            singleLine = true,
            textStyle = valueStyle(texture, valueSize),
            cursorBrush = SolidColor(texture.ink.toColor()),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keep() }),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(texture.canvas.toColor())
                    .border(1.dp, texture.ink.toColor())
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .focusRequester(focus)
                    // Moving to another value keeps this one, as Enter does; losing focus before it was ever gained is not that.
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            hadFocus = true
                        } else if (hadFocus) {
                            keep()
                        }
                    }.onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                keep()
                                true
                            }

                            Key.Escape -> {
                                putBack()
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    },
        )
        problem?.let {
            BasicText(text = "◐ $it", style = Typography.mono(sizeSp = 10.0).copy(color = texture.muted.toColor(), letterSpacing = 0.sp))
        }
    }
}

private const val PREVIEW_WORD = "Hamburgefonstiv"
private const val PREVIEW_SECOND_LINE = "quartz judge vow"
