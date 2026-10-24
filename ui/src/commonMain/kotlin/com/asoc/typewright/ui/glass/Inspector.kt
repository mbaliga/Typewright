// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.asoc.typewright.ui.tokens.Argb
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.SpacingTokens
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor

/**
 * One label/value pair in the [InspectorRow] (UI_SPEC §3 "Inspector"). [emphasize] renders the
 * value as "an ink block" (the edited or snapped value); [tint] overrides the value's colour for
 * "Cyan value for the snap target; amber for probe readings" -- pass `null` for neither. A non-null
 * [key] makes the value editable: tap it to type, drag sideways on it to scrub.
 */
data class InspectorField(
    val label: String,
    val value: String,
    val emphasize: Boolean = false,
    val tint: Argb? = null,
    val key: String? = null,
)

/**
 * How the row's editable values change the drawing. [onCommit] gets what was typed (a number, or `=`
 * and an expression) and answers null if it went in, else the plain-words reason it did not.
 * Scrubbing is one undo step: [onScrubStart], any number of [onScrub] with the total movement in
 * units so far, then [onScrubEnd]. [slow] says the puck is held, which makes a scrub a quarter as fast.
 */
class InspectorEdit(
    val onCommit: (key: String, text: String) -> String?,
    val onScrubStart: (key: String) -> Unit,
    val onScrub: (key: String, units: Int) -> Unit,
    val onScrubEnd: (commit: Boolean) -> Unit,
    val slow: () -> Boolean = { false },
)

/** Drag distance that moves a scrubbed value by one unit (INTERACTION_V1 §4.3), in dp. */
const val SCRUB_DP_PER_UNIT: Double = 8.0

/**
 * The inspector row (UI_SPEC §3 "Inspector", glass, bottom): "A single row of label/value pairs:
 * label mono 9.5 sp uppercase muted; value 15 sp semibold tabular. The edited or snapped value is
 * an ink block ... Never more than six pairs; wrap is a failure of content, not layout."
 *
 * With [edit], a field that has a key is a 44 dp target: a tap opens a field to type in (Enter
 * commits, Esc puts the old value back, Tab commits and moves to the next editable field) and a
 * sideways drag scrubs the value, one unit per [SCRUB_DP_PER_UNIT] dp. The drag reads its callbacks
 * and its field afresh on every move, so a row that changed under a drag cannot send a stale key.
 */
@Composable
fun InspectorRow(
    fields: List<InspectorField>,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
    edit: InspectorEdit? = null,
    bottomInsetDp: Double = SpacingTokens.INSPECTOR_FROM_BOTTOM_DP,
    /** Called with true while a value is being typed, so the page can stop treating letters as keys. */
    onEditingChanged: (Boolean) -> Unit = {},
    /** Changes to put away an open field without committing it (the deck's Close circle), as Esc does. */
    cancelEditing: Int = 0,
    /** The side gutters of the row: the page's 18 dp by default, less in a column that has its own padding. */
    horizontalPaddingDp: Double = SpacingTokens.GUTTER_DP,
    /** A fixed gap between values, for a column; null spreads the values across the whole width, as the row does. */
    gapDp: Double? = null,
) {
    require(fields.size <= 6) { "UI_SPEC §3 Inspector: \"Never more than six pairs\", got ${fields.size}" }
    var editingKey by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val editable = fields.mapNotNull { it.key }
    val latestEditingChanged by rememberUpdatedState(onEditingChanged)
    // A field that vanished under an open editor (the selection changed) is no longer being edited.
    val activeKey = editingKey?.takeIf { it in editable }
    val isEditing = activeKey != null
    LaunchedEffect(isEditing) { latestEditingChanged(isEditing) }
    LaunchedEffect(cancelEditing) {
        editingKey = null
        error = null
    }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPaddingDp.dp, vertical = 0.dp)
                .padding(bottom = bottomInsetDp.dp),
    ) {
        error?.let { message ->
            BasicText(
                text = message,
                style = Typography.mono(sizeSp = 10.0).copy(color = texture.muted.toColor()),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (gapDp == null) Arrangement.SpaceBetween else Arrangement.spacedBy(gapDp.dp),
        ) {
            for (field in fields) {
                InspectorCell(
                    field = field,
                    texture = texture,
                    edit = edit,
                    editing = field.key != null && activeKey == field.key,
                    onStartEditing = {
                        error = null
                        editingKey = field.key
                    },
                    onFinish = { text, moveOn ->
                        val key = field.key
                        if (text != null && key != null && edit != null) error = edit.onCommit(key, text) else error = null
                        editingKey =
                            if (moveOn && error == null) {
                                editable.getOrNull(editable.indexOf(key) + 1)
                            } else if (error != null) {
                                key
                            } else {
                                null
                            }
                    },
                )
            }
        }
    }
}

@Composable
private fun InspectorCell(
    field: InspectorField,
    texture: CanvasTexture,
    edit: InspectorEdit?,
    editing: Boolean,
    onStartEditing: () -> Unit,
    onFinish: (text: String?, moveOn: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val latestEdit by rememberUpdatedState(edit)
    val key = field.key
    val interactive = key != null && edit != null
    val cellModifier =
        if (interactive) {
            Modifier
                .defaultMinSize(minHeight = SpacingTokens.MIN_TOUCH_TARGET_DP.dp, minWidth = 44.dp)
                .pointerInput(key) { detectTapGestures(onTap = { onStartEditing() }) }
                .pointerInput(key) {
                    var travelled = 0.0
                    detectHorizontalDragGestures(
                        onDragStart = {
                            travelled = 0.0
                            latestEdit?.onScrubStart?.invoke(key!!)
                        },
                        onDragEnd = { latestEdit?.onScrubEnd?.invoke(true) },
                        onDragCancel = { latestEdit?.onScrubEnd?.invoke(false) },
                    ) { change, dragAmount ->
                        change.consume()
                        val current = latestEdit ?: return@detectHorizontalDragGestures
                        val factor = if (current.slow()) SLOW_SCRUB else 1.0
                        travelled += dragAmount / density.density * factor
                        current.onScrub(key!!, (travelled / SCRUB_DP_PER_UNIT).toInt())
                    }
                }
        } else {
            Modifier
        }
    Column(modifier = cellModifier) {
        BasicText(text = field.label.uppercase(), style = Typography.inspectorLabel.copy(color = texture.muted.toColor()))
        val valueColor = field.tint?.toColor() ?: texture.fg.toColor()
        if (editing) {
            ValueEditor(initial = field.value, texture = texture, onFinish = onFinish)
        } else if (field.emphasize) {
            Box(modifier = Modifier.background(texture.ink.toColor()).padding(horizontal = 4.dp)) {
                BasicText(text = field.value, style = Typography.inspectorValue.copy(color = texture.canvas.toColor()))
            }
        } else {
            BasicText(text = field.value, style = Typography.inspectorValue.copy(color = valueColor))
        }
    }
}

@Composable
private fun ValueEditor(
    initial: String,
    texture: CanvasTexture,
    onFinish: (text: String?, moveOn: Boolean) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(modifier = Modifier.background(texture.ink.toColor()).padding(horizontal = 4.dp)) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            textStyle = Typography.inspectorValue.copy(color = texture.canvas.toColor()),
            cursorBrush = SolidColor(texture.canvas.toColor()),
            modifier =
                Modifier
                    .focusRequester(focus)
                    .defaultMinSize(minWidth = 52.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                onFinish(value.text, false)
                                true
                            }

                            Key.Escape -> {
                                onFinish(null, false)
                                true
                            }

                            Key.Tab -> {
                                onFinish(value.text, true)
                                true
                            }

                            // D6: up and down step a whole number by one, Shift by ten; Enter then commits it, Esc puts the old value back.
                            Key.DirectionUp, Key.DirectionDown -> {
                                val next =
                                    NumericStep.stepped(
                                        value.text,
                                        if (event.key ==
                                            Key.DirectionUp
                                        ) {
                                            1
                                        } else {
                                            -1
                                        },
                                        event.isShiftPressed,
                                    )
                                if (next != null) value = TextFieldValue(next, TextRange(0, next.length))
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    },
        )
    }
}

private const val SLOW_SCRUB = 0.25
