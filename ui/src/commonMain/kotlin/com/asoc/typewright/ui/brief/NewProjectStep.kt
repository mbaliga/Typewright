// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.brief

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asoc.typewright.brief.GENRES
import com.asoc.typewright.brief.Genre
import com.asoc.typewright.brief.GenreGroup
import com.asoc.typewright.brief.genreByKey
import com.asoc.typewright.project.ContrastModel
import com.asoc.typewright.project.MetaChange
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.Use
import com.asoc.typewright.scripts.WritingScript
import com.asoc.typewright.scripts.arabic.ArabicGlyphInventory
import com.asoc.typewright.scripts.devanagari.DevanagariGlyphInventory
import com.asoc.typewright.scripts.kana.HIRAGANA_GLYPH_INVENTORY
import com.asoc.typewright.scripts.kana.KATAKANA_GLYPH_INVENTORY
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.toColor

/**
 * The new-project flow's second step, laid out as the V1 screens canvas lays out its `NewProject`
 * board: the step strip, Name, Scripts, Intent, Closest class (each with its corpus count) with the
 * line under it, and One line about it. These are the Brief's Style and Use doors asked the short
 * way: the class chips write [BriefScreenUiState.chooseGenre], Text and Display write the main use,
 * and the ethos stays the one record. "Not sure" leaves for the other doors.
 */
@Composable
internal fun NewProjectStep(
    state: BriefScreenUiState,
    draft: NewProjectDraft,
    texture: CanvasTexture,
) {
    val ink = texture.ink.toColor()
    val muted = texture.muted.toColor()
    val implied = state.implied
    val model = state.intentModel ?: implied.model
    val classKeys = closestClassKeys(model = model, use = implied.use)
    Column(verticalArrangement = Arrangement.spacedBy(STEP_GAP.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BasicText(
                text = "1 Source · ${draft.source}".uppercase(),
                style = monoStyle(ink, sizeSp = 10.0),
                modifier = Modifier.alpha(DIMMED_STEP),
            )
            BasicText(
                text = "2 Brief".uppercase(),
                style = monoStyle(texture.canvas.toColor(), sizeSp = 10.0),
                modifier = Modifier.background(ink).padding(horizontal = 6.dp, vertical = 2.dp),
            )
            BasicText(text = "3 Metrics".uppercase(), style = monoStyle(ink, sizeSp = 10.0), modifier = Modifier.alpha(DIMMED_STEP))
        }

        Field(label = "Name", texture = texture) {
            UnderlineField(
                value = draft.name,
                onValueChange = { draft.name = it },
                onBlur = draft::commitName,
                texture = texture,
                sizeSp = 20.0,
                weight = FontWeight.Medium,
            )
        }

        Field(label = "Scripts", texture = texture) {
            Chips {
                for (choice in SCRIPT_CHOICES) {
                    Chip(label = choice.chipLabel, on = draft.has(choice), texture = texture) { draft.toggle(choice) }
                }
            }
        }

        Field(label = "Intent", texture = texture) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Chip(
                        label = "Serif",
                        on = model == ContrastModel.SERIF,
                        texture = texture,
                    ) { state.chooseIntentModel(ContrastModel.SERIF) }
                    Chip(
                        label = "Sans",
                        on = model == ContrastModel.SANS,
                        texture = texture,
                    ) { state.chooseIntentModel(ContrastModel.SANS) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Chip(label = "Text", on = implied.use == Use.TEXT, texture = texture) {
                        state.chooseIntentUse(if (implied.use == Use.TEXT) null else Use.TEXT)
                    }
                    Chip(label = "Display", on = implied.use == Use.DISPLAY, texture = texture) {
                        state.chooseIntentUse(if (implied.use == Use.DISPLAY) null else Use.DISPLAY)
                    }
                }
            }
        }

        Field(label = "Closest class · what you will be measured against", texture = texture) {
            Chips {
                for (genre in classKeys.mapNotNull(::genreByKey)) {
                    val count =
                        state.atlas
                            ?.pack
                            ?.classes
                            ?.get(genre.key)
                            ?.n
                    Chip(
                        label = if (count == null) genre.name else "${genre.name} · n $count",
                        on = state.ethos.genre == genre.key,
                        texture = texture,
                    ) { state.chooseGenre(if (state.ethos.genre == genre.key) null else genre.key) }
                }
                Chip(label = "Not sure", on = false, texture = texture, onClick = state::notSure)
            }
            val chosen = genreByKey(state.ethos.genre)?.takeIf { it.corpus }
            BasicText(
                text =
                    classExplanation(
                        chosen?.name,
                        chosen?.let {
                            state.atlas
                                ?.pack
                                ?.classes
                                ?.get(it.key)
                                ?.n
                        },
                    ),
                style = bodyStyle(muted),
                modifier = Modifier.padding(top = 2.dp).fillMaxWidth(),
            )
        }

        Field(label = "One line about it · optional", texture = texture) {
            UnderlineField(
                value = draft.note,
                onValueChange = { draft.note = it },
                onBlur = draft::commitNote,
                texture = texture,
                sizeSp = 14.0,
                placeholder = "A monolinear deco for headlines",
            )
        }
        if (!state.saved) BasicText(text = state.saveNote.uppercase(), style = monoStyle(muted))
    }
}

/** The room between blocks on the board. */
private const val STEP_GAP = 22

/** How far the steps that are not this one fade. */
private const val DIMMED_STEP = 0.45f

/** A mono label over its control, 6 apart (8 over a row of chips). */
@Composable
private fun Field(
    label: String,
    texture: CanvasTexture,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(text = label.uppercase(), style = monoStyle(texture.muted.toColor()))
        content()
    }
}

/** A text field that is only an underline (ink at 35%), with a muted [placeholder]; [onBlur] runs when it loses focus. */
@Composable
private fun UnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    onBlur: () -> Unit,
    texture: CanvasTexture,
    sizeSp: Double,
    weight: FontWeight = FontWeight.Normal,
    placeholder: String? = null,
) {
    val fg = texture.fg.toColor()
    var focused by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().underline(texture.ink.toColor()).padding(vertical = 4.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = bodyStyle(fg, sizeSp = sizeSp, weight = weight),
            singleLine = true,
            cursorBrush = SolidColor(fg),
            modifier =
                Modifier.fillMaxWidth().onFocusChanged {
                    if (focused && !it.isFocused) onBlur()
                    focused = it.isFocused
                },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        BasicText(text = placeholder, style = bodyStyle(texture.muted.toColor(), sizeSp = sizeSp))
                    }
                    inner()
                }
            },
        )
    }
}

/**
 * The classes "Closest class" offers: the ten that have Economy boxes, narrowed by the Serif/Sans
 * chip to that group, and by Display to include the display classes (Art deco, Blackletter) beside
 * it; Text leaves the display classes out. With no [model] chosen every class shows.
 */
internal fun closestClassKeys(
    model: ContrastModel?,
    use: Use?,
): List<String> {
    val group =
        when (model) {
            ContrastModel.SANS -> GenreGroup.SANS
            ContrastModel.SERIF -> GenreGroup.SERIF
            null -> null
        }
    return GENRES
        .filter { it.corpus }
        .filter { genre -> fitsIntent(genre, group, use) }
        .map { it.key }
}

private fun fitsIntent(
    genre: Genre,
    group: GenreGroup?,
    use: Use?,
): Boolean =
    when {
        genre.group == GenreGroup.DISPLAY -> use != Use.TEXT && (group == null || use == Use.DISPLAY)
        else -> group == null || genre.group == group
    }

/** Fewer measured families than this and the line under the chips says the class's boxes are wide: our heuristic, not a measurement. */
internal const val FEW_FAMILIES = 15

/**
 * The line under the class chips: for a chosen class, how many measured families its boxes rest on
 * ([count], from the style atlas) and so how wide they are, then what Typewright does with the
 * letters. With no class chosen, only the second part.
 */
internal fun classExplanation(
    className: String?,
    count: Int?,
): String {
    val inference = "Typewright will also infer a class from your letters, show its evidence, and ask you to confirm."
    if (className == null || count == null) return inference
    val name = className.lowercase()
    val boxes =
        if (count < FEW_FAMILIES) {
            "Only $count $name families on Google Fonts qualify, so its boxes are wide."
        } else {
            "$count $name families on Google Fonts set its boxes."
        }
    return "$boxes $inference"
}

/** One Scripts chip: [label], the manifest script codes it stands for, and how many glyphs the app has for it (null where it has no inventory to count). */
internal data class ScriptChoice(
    val label: String,
    val codes: List<String>,
    val glyphs: Int?,
    val preview: Boolean,
)

/** Latin, which every project has, then the three previews, each with the glyph count the app really holds for it. */
internal val SCRIPT_CHOICES: List<ScriptChoice> =
    listOf(
        ScriptChoice("Latin", listOf(WritingScript.LATIN.isoCode), glyphs = null, preview = false),
        ScriptChoice(
            "Kana",
            listOf(WritingScript.HIRAGANA.isoCode, WritingScript.KATAKANA.isoCode),
            HIRAGANA_GLYPH_INVENTORY.glyphs.size + KATAKANA_GLYPH_INVENTORY.glyphs.size,
            preview = true,
        ),
        ScriptChoice("Devanagari", listOf(WritingScript.DEVANAGARI.isoCode), DevanagariGlyphInventory.build().glyphs.size, preview = true),
        ScriptChoice("Naskh", listOf(WritingScript.ARABIC_NASKH.isoCode), ArabicGlyphInventory.build().glyphs.size, preview = true),
    )

/** The chip's text: the script, its glyph count where there is one, and "preview" where it is one. */
internal val ScriptChoice.chipLabel: String
    get() = listOfNotNull(label, glyphs?.toString(), "preview".takeIf { preview }).joinToString(" · ")

/**
 * The board's fields that are not the Brief's own answers: the project's name, its scripts and a
 * line about it. Name and the line are held while they are typed and written when the field loses
 * focus or [commit] runs; scripts are written at once. With no [session] they stay in memory and
 * nothing is written. The name is the project's name in `typewright.json`: the folder keeps the name
 * it was made with.
 */
internal class NewProjectDraft(
    private val session: ProjectSession?,
) {
    private val manifest get() =
        session
            ?.state
            ?.value
            ?.meta
            ?.manifest

    var name: String by mutableStateOf(manifest?.name.orEmpty())
    var note: String by mutableStateOf(manifest?.brief?.note.orEmpty())
    private var scripts: List<String> by mutableStateOf(manifest?.scripts ?: listOf(WritingScript.LATIN.isoCode))

    /** Where the project started, in a word: paper, font or blank. */
    val source: String get() =
        manifest
            ?.brief
            ?.source
            ?.name
            ?.lowercase() ?: "blank"

    /** True when [choice]'s scripts are all in the project (Latin always is). */
    fun has(choice: ScriptChoice): Boolean = choice.codes.all { it in scripts } || choice.codes == listOf(WritingScript.LATIN.isoCode)

    /** Adds or removes [choice]'s scripts; Latin stays. */
    fun toggle(choice: ScriptChoice) {
        if (choice.codes == listOf(WritingScript.LATIN.isoCode)) return
        scripts = if (has(choice)) scripts - choice.codes.toSet() else scripts + choice.codes.filter { it !in scripts }
        session?.update(MetaChange.SetScripts(scripts))
    }

    /** Writes the name, if it is not empty and has changed. */
    fun commitName() {
        val live = session ?: return
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && trimmed != manifest?.name) live.update(MetaChange.Rename(trimmed))
    }

    /** Writes the line about the project into its brief, if it has changed. */
    fun commitNote() {
        val live = session ?: return
        val brief = manifest?.brief ?: return
        val trimmed = note.trim().ifEmpty { null }
        if (trimmed != brief.note) live.update(MetaChange.SetBrief(brief.copy(note = trimmed)))
    }

    /** Writes every field that is still being typed. */
    fun commit() {
        commitName()
        commitNote()
    }
}
