// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.asoc.typewright.ui.tokens.CanvasTexture

/**
 * The control strings Space can set: the Workbook's two, then the person's own (kept in the project), with
 * the one in use shown as an ink block. A tap sets the string; the person's own can be taken away; a field
 * adds one. Each is set with whichever of its letters the font has drawn, and the page names the rest.
 */
@Composable
internal fun SpaceStringsPanel(
    strings: List<ControlString>,
    active: String,
    texture: CanvasTexture,
    editable: Boolean,
    onChoose: (String) -> Unit,
    onAdd: (String) -> String?,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var typed by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SpaceSentence(
            text =
                "A control string sets one letter among others you have already spaced, so you judge it by how the line reads. " +
                    "nnonnonon and HHOHOHOH are the Workbook's own, from its Spacing task.",
            texture = texture,
            muted = true,
        )
        Column(modifier = Modifier.padding(top = 10.dp)) {
            for (string in strings) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                            .clickable(role = Role.Button) { onChoose(string.text) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SpaceChip(string.text, on = string.text == active, texture = texture, modifier = Modifier.weight(1f), caps = false) {
                        onChoose(string.text)
                    }
                    SpaceLabel(if (string.own) "yours" else "Workbook", texture)
                    if (string.own) SpaceAction("Remove", texture, enabled = editable) { onRemove(string.text) }
                }
            }
        }
        SpaceLabel("Add your own", texture, modifier = Modifier.padding(top = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SpaceLineField(
                value = typed,
                onValueChange = {
                    typed = it
                    problem = null
                },
                placeholder = "For example HOHOOHH",
                texture = texture,
                modifier = Modifier.weight(1f),
                onSubmit = {
                    problem = onAdd(typed)
                    if (problem == null) typed = ""
                },
            )
            SpaceAction("Add", texture, enabled = editable && typed.isNotBlank()) {
                problem = onAdd(typed)
                if (problem == null) typed = ""
            }
        }
        problem?.let { SpaceSentence(it, texture, bold = true) }
    }
}
