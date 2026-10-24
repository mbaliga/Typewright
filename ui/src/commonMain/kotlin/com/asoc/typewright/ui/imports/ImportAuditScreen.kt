// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.project.Master
import com.asoc.typewright.project.imports.AuditSource
import com.asoc.typewright.project.imports.ImportAudit
import com.asoc.typewright.project.imports.ImportCandidate
import com.asoc.typewright.project.imports.ImportReport
import com.asoc.typewright.project.imports.ImportReview
import com.asoc.typewright.project.imports.ImportVerdict
import com.asoc.typewright.ui.deck.ControlDeck
import com.asoc.typewright.ui.deck.DeckKeyCommand
import com.asoc.typewright.ui.deck.DeckLeft
import com.asoc.typewright.ui.deck.DeckRight
import com.asoc.typewright.ui.deck.DeckSeats
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.SystemBackHandler
import com.asoc.typewright.ui.deck.deckKeys
import com.asoc.typewright.ui.project.ProjectFont
import com.asoc.typewright.ui.project.ProjectText
import com.asoc.typewright.ui.shell.SectionToast
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor
import kotlinx.coroutines.delay

/**
 * The import licence audit (S03) and the states around it, drawn over whatever section the import
 * started from. The section stays underneath, faded to a fifth, and the deck is the real one with
 * the section arc dimmed, Close on the left (leaving discards the import) and the ✓ Import circle on
 * the right. Nothing here decides anything: [controller] holds the state and the rules.
 *
 * The host is meant to sit last in the app's root layout, so it covers every screen, and the
 * command palette's "Open a font…" can start an import from any of them. With no import under way
 * it draws nothing.
 */
@Composable
fun FontImportHost(
    controller: FontImportController,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    DisposableEffect(controller) { onDispose { controller.cancel() } }
    when (val s = state) {
        ImportState.Idle, is ImportState.Picking -> Unit
        else -> ImportOverlay(s, controller, texture, modifier)
    }
}

@Composable
private fun ImportOverlay(
    state: ImportState,
    controller: FontImportController,
    texture: CanvasTexture,
    modifier: Modifier,
) {
    val from = state.originSection() ?: DeckSection.HOME
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(TOAST_MILLIS)
            toast = null
        }
    }
    SystemBackHandler { controller.close() }
    val auditing = (state as? ImportState.Auditing) ?: (state as? ImportState.Creating)?.auditing
    val busy = state is ImportState.Reading || state is ImportState.Creating
    val layout = DeckSeats.importAudit(canImport = auditing?.canImport == true && !busy, whyNot = auditing?.whyNot)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .deckKeys { command ->
                    when (command) {
                        DeckKeyCommand.Leave -> controller.close()
                        else -> Unit
                    }
                    true
                }.focusRequester(focus)
                .focusable(),
    ) {
        // The section underneath, at a fifth of its strength (the board draws the page at 18%); a tap on it does nothing.
        Box(
            Modifier
                .fillMaxSize()
                .background(texture.canvas.toColor().copy(alpha = SCRIM_ALPHA))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxBlock = (maxHeight - BLOCK_TOP - DECK_CLEARANCE).coerceAtLeast(MIN_BLOCK_HEIGHT)
            Box(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 18.dp, end = 18.dp, top = BLOCK_TOP)
                    .fillMaxWidth()
                    .heightIn(max = maxBlock)
                    .background(texture.ink.toColor()),
            ) {
                val onInk = texture.canvas.toColor()
                // What the font says scrolls; the verdict, the new-name field and any error stay pinned under it, so
                // the answer to "may I?" is on screen however long the facts are.
                Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp)) {
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (state) {
                            is ImportState.Reading -> ReadingBlock(state.fileName, onInk)
                            is ImportState.Message -> MessageBlock(onInk)
                            else -> if (auditing != null) AuditFacts(auditing, onInk)
                        }
                    }
                    Column(modifier = Modifier.padding(top = 14.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (state) {
                            is ImportState.Message -> VerdictRow(shape = "●", title = "Can't open that", words = state.text, onInk = onInk)
                            is ImportState.Reading -> Unit
                            else -> if (auditing != null) AuditFooter(auditing, busy, controller, onInk)
                        }
                    }
                }
            }
        }
        toast?.let { SectionToast(text = it, texture = texture, modifier = Modifier.align(Alignment.BottomCenter)) }
        ControlDeck(
            texture = texture,
            modifier = Modifier.align(Alignment.BottomCenter),
            section = from,
            left = if (busy) DeckLeft.NONE else layout.left,
            right = if (state is ImportState.Message || state is ImportState.Reading) DeckRight.NONE else layout.right,
            rightDimmed = !(auditing?.canImport == true) || busy,
            rightLabel = DeckSeats.IMPORT_LABEL,
            dimSections = true,
            onLeft = controller::close,
            onRight = {
                if (auditing != null && auditing.canImport &&
                    !busy
                ) {
                    controller.confirm()
                } else {
                    toast = layout.actionWhyNot ?: "Not ready to import yet."
                }
            },
            onSection = { toast = "Finish or close the import first" },
        )
    }
}

private fun ImportState.originSection(): DeckSection? =
    when (this) {
        is ImportState.Picking -> from
        is ImportState.Reading -> from
        is ImportState.Auditing -> from
        is ImportState.Creating -> auditing.from
        is ImportState.Message -> from
        ImportState.Idle -> null
    }

@Composable
private fun ReadingBlock(
    fileName: String,
    onInk: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(text = "OPENING", style = Typography.mono(sizeSp = 9.5).copy(color = onInk.copy(alpha = LABEL_ALPHA)))
        BasicText(text = fileName, style = titleStyle(onInk))
    }
    BasicText(text = "Reading the font and checking its licence…", style = valueStyle(onInk))
}

@Composable
private fun MessageBlock(onInk: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(text = "OPEN A FONT", style = Typography.mono(sizeSp = 9.5).copy(color = onInk.copy(alpha = LABEL_ALPHA)))
        BasicText(text = "Nothing was opened", style = titleStyle(onInk))
    }
}

/** What the audit shows of the font: its file name, its own name set in itself, and every fact the licence and import rules read. */
@Composable
private fun AuditFacts(
    s: ImportState.Auditing,
    onInk: Color,
) {
    val review = s.review
    val label = "BEFORE IMPORTING · LICENCE CHECK" + if (s.sample) " · SAMPLE" else ""
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(text = label, style = Typography.mono(sizeSp = 9.5).copy(color = onInk.copy(alpha = LABEL_ALPHA)))
        BasicText(text = review.fileName, style = titleStyle(onInk))
    }
    when (review) {
        is ImportReview.Unreadable -> {
            Unit
        }

        is ImportReview.Refused -> {
            AuditRows(review.audit, report = null, notes = emptyList(), where = "", onInk = onInk)
        }

        is ImportReview.Ready -> {
            val candidate = review.candidate
            Specimen(candidate.ufo, onInk)
            AuditRows(candidate.audit, candidate.report, candidate.report.notes, s.where, onInk)
        }
    }
}

/** The part of the audit that never scrolls away: the verdict as shape, title and words, the new-name field a reserved name needs, and what went wrong last. */
@Composable
private fun AuditFooter(
    s: ImportState.Auditing,
    busy: Boolean,
    controller: FontImportController,
    onInk: Color,
) {
    when (val review = s.review) {
        is ImportReview.Unreadable -> {
            VerdictRow(shape = "●", title = "Can't open this font", words = review.message, onInk = onInk)
        }

        is ImportReview.Refused -> {
            VerdictRow(review.audit.verdict, review.audit.verdictWords, onInk)
        }

        is ImportReview.Ready -> {
            VerdictRow(review.candidate.audit.verdict, review.candidate.audit.verdictWords, onInk)
            if (s.needsName) NewNameField(s, controller, onInk)
        }
    }
    s.error?.let { error -> BasicText(text = error, style = valueStyle(onInk).copy(fontWeight = FontWeight.SemiBold)) }
    if (busy) BasicText(text = "Making the project…", style = Typography.mono(sizeSp = 10.0).copy(color = onInk.copy(alpha = LABEL_ALPHA)))
}

/** The font's own name set in the font itself, as the board's "Hamburg" at 44. A font that cannot set it shows its first letters instead. */
@Composable
private fun Specimen(
    ufo: UfoProject,
    onInk: Color,
) {
    val font = remember(ufo) { ProjectFont(Master("regular", "", ufo.fontInfo.styleName ?: "Regular", true, ufo)) }
    val word = remember(font) { font.showWord("Hamburg", fallbackLength = 7) } ?: return
    ProjectText(font = font, text = word, sizeSp = 44f, color = onInk)
}

@Composable
private fun AuditRows(
    audit: ImportAudit,
    report: ImportReport?,
    notes: List<String>,
    where: String,
    onInk: Color,
) {
    val source = if (audit.source == AuditSource.FONT_FILE) "name table" else "fontinfo.plist"
    val ids = audit.source == AuditSource.FONT_FILE
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Field("Licence · ${if (ids) "name ID 13" else source}", licenceLine(audit), onInk)
        Field("Copyright · ${if (ids) "name ID 0" else source}", audit.copyright ?: "◐ Not set.", onInk)
        Field("Reserved Font Name", reservedLine(audit), onInk)
        Field("Designer · ${if (ids) "name ID 9" else source}", audit.designer ?: "◐ Not set.", onInk)
        if (report != null) {
            Field("Contents", contentsLine(report), onInk)
            Field("Metrics · ${if (ids) "head, hhea, OS/2" else source}", metricsLine(report), onInk)
            Field("Outlines", report.outlines.words, onInk)
            for (note in notes) Field("Not carried over", note, onInk, labelOnlyFirst = false)
        }
        if (where.isNotBlank()) Field("Where it goes", where, onInk)
    }
}

private fun licenceLine(audit: ImportAudit): String {
    val excerpt =
        audit.licenceText?.replace('\n', ' ')?.let {
            if (it.length >
                EXCERPT_LENGTH
            ) {
                it.take(EXCERPT_LENGTH).trimEnd() + "…"
            } else {
                it
            }
        }
    return when {
        audit.licence == com.asoc.typewright.project.imports.LicenceKind.OFL ||
            audit.licence == com.asoc.typewright.project.imports.LicenceKind.APACHE -> {
            audit.licenceName
        }

        excerpt != null -> {
            "${audit.licenceName}: “$excerpt”"
        }

        else -> {
            audit.licenceName
        }
    }
}

private fun reservedLine(audit: ImportAudit): String =
    if (audit.reservedFontNames.isEmpty()) "None declared" else audit.reservedFontNames.joinToString(" and ") { "“$it”" }

private fun contentsLine(report: ImportReport): String {
    val scripts = report.scripts.joinToString(" · ") { "${it.name} ${it.characters}" }
    val characters = "${report.mappedCharacters} characters mapped" + if (scripts.isNotEmpty()) " ($scripts)" else ""
    return "${report.glyphCount} glyphs · $characters"
}

private fun metricsLine(report: ImportReport): String {
    val m = report.metrics
    val parts =
        listOfNotNull(
            m.unitsPerEm?.let { "$it units per em" },
            m.ascender?.let { "ascender $it" },
            m.descender?.let { "descender ${signed(it)}" },
            m.xHeight?.let { "x-height $it" },
            m.capHeight?.let { "cap height $it" },
        )
    return parts.joinToString(" · ").ifEmpty { "None stated" }
}

private fun signed(value: Int): String = if (value < 0) "−${-value}" else value.toString()

@Composable
private fun Field(
    label: String,
    value: String,
    onInk: Color,
    labelOnlyFirst: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(text = label.uppercase(), style = Typography.mono(sizeSp = 9.0).copy(color = onInk.copy(alpha = LABEL_ALPHA)))
        BasicText(text = value, style = valueStyle(onInk))
    }
}

@Composable
private fun VerdictRow(
    verdict: ImportVerdict,
    words: String,
    onInk: Color,
) = VerdictRow(verdict.shape, verdict.title, words, onInk)

@Composable
private fun VerdictRow(
    shape: String,
    title: String,
    words: String,
    onInk: Color,
) {
    Row(
        modifier = Modifier.padding(top = 4.dp).semantics { contentDescription = "$title. $words" },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BasicText(text = shape, style = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 18.sp, color = onInk))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(
                text = title.uppercase(),
                style = Typography.mono(sizeSp = 10.0, weight = FontWeight.SemiBold).copy(color = onInk),
            )
            BasicText(
                text = words,
                style =
                    TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = onInk.copy(alpha = VERDICT_ALPHA),
                    ),
            )
        }
    }
}

/** The new family name a font that reserves one must be imported under: an underline field, as the canvas draws inputs. */
@Composable
private fun NewNameField(
    s: ImportState.Auditing,
    controller: FontImportController,
    onInk: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText(text = "NEW FAMILY NAME", style = Typography.mono(sizeSp = 9.0).copy(color = onInk.copy(alpha = LABEL_ALPHA)))
        Box {
            if (s.newName.isEmpty()) {
                BasicText(text = "A name of your own", style = valueStyle(onInk).copy(color = onInk.copy(alpha = PLACEHOLDER_ALPHA)))
            }
            BasicTextField(
                value = s.newName,
                onValueChange = controller::setNewName,
                singleLine = true,
                textStyle = valueStyle(onInk),
                cursorBrush = SolidColor(onInk),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(onInk.copy(alpha = UNDERLINE_ALPHA)))
        s.nameProblem?.let { BasicText(text = it, style = valueStyle(onInk).copy(fontSize = 12.sp)) }
    }
}

private fun titleStyle(onInk: Color) =
    TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = onInk)

private fun valueStyle(onInk: Color) =
    TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.2.sp, color = onInk, letterSpacing = 0.em)

private const val SCRIM_ALPHA = 0.82f
private const val LABEL_ALPHA = 0.6f
private const val VERDICT_ALPHA = 0.85f
private const val PLACEHOLDER_ALPHA = 0.45f
private const val UNDERLINE_ALPHA = 0.35f
private const val EXCERPT_LENGTH = 140
private const val TOAST_MILLIS = 2600L
private val BLOCK_TOP = 94.dp
private val DECK_CLEARANCE = 186.dp
private val MIN_BLOCK_HEIGHT = 240.dp
