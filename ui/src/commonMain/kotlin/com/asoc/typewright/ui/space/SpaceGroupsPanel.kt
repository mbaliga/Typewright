// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.project.GroupEdit
import com.asoc.typewright.project.KernSide
import com.asoc.typewright.project.KerningGroupEdits
import com.asoc.typewright.project.KerningGroups
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.Typography
import com.asoc.typewright.ui.tokens.toColor

/**
 * The kerning groups of the open font, and the edits to them (UFO `public.kern1.*` and `public.kern2.*`):
 * each side's groups with their members, a group opened to add or take out letters, rename it or delete it,
 * and a new group. A letter is in one group on each side, so adding one that is already in another group of
 * that side moves it, and the page says so. Every change is one undo step. Groups that are not kerning
 * groups are listed and left as they are.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpaceGroupsPanel(
    kerning: UfoKerning,
    master: String,
    selectedGlyph: String?,
    texture: CanvasTexture,
    editable: Boolean,
    onEdit: (GroupEdit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf<String?>(null) }
    var addText by remember { mutableStateOf("") }
    var renameText by remember { mutableStateOf("") }
    var newSide by remember { mutableStateOf(KernSide.FIRST) }
    var newName by remember { mutableStateOf("") }
    val fg = texture.fg.toColor()
    val muted = texture.muted.toColor()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SpaceSentence(
            text =
                "Letters that kern alike share a group. A letter is in one group on each side of a pair: " +
                    "the first side is the letter before the gap, the second the letter after it.",
            texture = texture,
            muted = true,
        )
        for (side in KernSide.entries) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                SpaceLabel(if (side == KernSide.FIRST) "First side · public.kern1" else "Second side · public.kern2", texture)
                val groups = kerning.groups.filterKeys { KerningGroups.sideOf(it) == side }
                if (groups.isEmpty()) {
                    SpaceSentence(
                        "No groups on this side yet.",
                        texture,
                        muted = true,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                for ((name, members) in groups) {
                    val isOpen = open == name
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 44.dp)
                                    .clickable(role = Role.Button) {
                                        open = if (isOpen) null else name
                                        addText = ""
                                        renameText = KerningGroups.displayName(name)
                                    },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicText(
                                text = KerningGroups.displayName(name),
                                style = Typography.mono(sizeSp = 11.0, weight = FontWeight.SemiBold).copy(color = fg),
                                modifier = Modifier.width(96.dp),
                            )
                            BasicText(
                                text = if (members.isEmpty()) "empty" else members.joinToString(" "),
                                style = Typography.mono(sizeSp = 10.5).copy(color = muted, letterSpacing = 0.04.em),
                                modifier = Modifier.weight(1f),
                            )
                            BasicText(text = if (isOpen) "–" else "+", style = Typography.mono(sizeSp = 12.0).copy(color = fg))
                        }
                        if (isOpen) {
                            Column(
                                modifier = Modifier.padding(start = 12.dp, bottom = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    for (member in members) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .defaultMinSize(minHeight = 36.dp)
                                                    .clickable(enabled = editable, role = Role.Button) {
                                                        onEdit(KerningGroupEdits.removeMember(master, kerning, name, member))
                                                    }.background(texture.ink.toColor())
                                                    .padding(horizontal = 9.dp, vertical = 7.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            BasicText(
                                                text = "$member ✕",
                                                style = Typography.mono(sizeSp = 10.5).copy(color = texture.canvas.toColor()),
                                            )
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    SpaceLineField(
                                        value = addText,
                                        onValueChange = { addText = it },
                                        placeholder = "Add a glyph by name",
                                        texture = texture,
                                        modifier = Modifier.weight(1f),
                                        onSubmit = {
                                            if (editable) onEdit(KerningGroupEdits.addMember(master, kerning, name, addText))
                                            addText = ""
                                        },
                                    )
                                    SpaceAction("Add", texture, enabled = editable && addText.isNotBlank()) {
                                        onEdit(KerningGroupEdits.addMember(master, kerning, name, addText))
                                        addText = ""
                                    }
                                }
                                if (selectedGlyph != null && selectedGlyph !in members) {
                                    SpaceAction("Add $selectedGlyph, the letter selected on the page", texture, enabled = editable) {
                                        onEdit(KerningGroupEdits.addMember(master, kerning, name, selectedGlyph))
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    SpaceLineField(
                                        value = renameText,
                                        onValueChange = { renameText = it },
                                        placeholder = "Name",
                                        texture = texture,
                                        modifier = Modifier.weight(1f),
                                        onSubmit = {
                                            if (editable) {
                                                onEdit(KerningGroupEdits.rename(master, kerning, name, renameText))
                                                open = null
                                            }
                                        },
                                    )
                                    SpaceAction("Rename", texture, enabled = editable && renameText.isNotBlank()) {
                                        onEdit(KerningGroupEdits.rename(master, kerning, name, renameText))
                                        open = null
                                    }
                                }
                                val named = KerningGroupEdits.entriesNaming(kerning, name)
                                SpaceAction(
                                    text =
                                        if (named ==
                                            0
                                        ) {
                                            "Delete this group"
                                        } else {
                                            "Delete this group and the $named kerning ${if (named == 1) "pair" else "pairs"} that name it"
                                        },
                                    texture = texture,
                                    enabled = editable,
                                ) {
                                    onEdit(KerningGroupEdits.delete(master, kerning, name))
                                    open = null
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SpaceLabel("New group", texture)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SpaceChip("First side", on = newSide == KernSide.FIRST, texture = texture) { newSide = KernSide.FIRST }
                SpaceChip("Second side", on = newSide == KernSide.SECOND, texture = texture) { newSide = KernSide.SECOND }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SpaceLineField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = "Name, for example O or round",
                    texture = texture,
                    modifier = Modifier.weight(1f),
                    onSubmit = {
                        if (editable) {
                            onEdit(KerningGroupEdits.create(master, kerning, newSide, newName, listOfNotNull(selectedGlyph)))
                            newName = ""
                        }
                    },
                )
                SpaceAction(
                    text = if (selectedGlyph != null) "Make it with $selectedGlyph" else "Make it",
                    texture = texture,
                    enabled = editable && newName.isNotBlank(),
                ) {
                    onEdit(KerningGroupEdits.create(master, kerning, newSide, newName, listOfNotNull(selectedGlyph)))
                    newName = ""
                }
            }
        }

        val others = kerning.groups.filterKeys { KerningGroups.sideOf(it) == null }
        if (others.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SpaceLabel("Other groups · kept as they are", texture)
                for ((name, members) in others) {
                    BasicText(
                        text = "$name   ${members.joinToString(" ")}",
                        style = Typography.mono(sizeSp = 10.5).copy(color = muted, letterSpacing = 0.04.em),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}
