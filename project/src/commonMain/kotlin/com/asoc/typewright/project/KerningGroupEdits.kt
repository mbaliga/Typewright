// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.font.ufo.UfoKerning

/** What a group edit comes to: the [command] to run (one undo step, whatever it is made of) and a plain [note] on anything the person should know it did. */
sealed interface GroupEdit {
    data class Run(
        val command: FontCommand,
        val note: String? = null,
    ) : GroupEdit

    /** The edit would change nothing. */
    data class Unchanged(
        val reason: String,
    ) : GroupEdit

    /** The edit is not allowed, for [reason] in plain words. */
    data class Refused(
        val reason: String,
    ) : GroupEdit
}

/**
 * Edits to a font's kerning groups, each as one command so it is one undo step, under the UFO 3
 * rules: a kerning group's name carries its side's prefix, and a glyph is in at most one first-side
 * group and one second-side group. Renaming and deleting a group also rewrite the kerning entries
 * that name it, so no pair is left pointing at a group that is gone. Pure: each function reads the
 * font's [UfoKerning] and returns the [GroupEdit] to run.
 */
object KerningGroupEdits {
    /** Longest group name part accepted, so a pasted paragraph is not taken for a name. */
    const val MAX_NAME_LENGTH = 64

    /** [raw] as a group name part: trimmed, or null where it is empty, too long or holds a control character. */
    fun cleanName(raw: String): String? {
        val name = raw.trim()
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) return null
        if (name.any { it.code < SPACE || it.code == DELETE }) return null
        return name
    }

    /** A new, empty (or [members]-filled) kerning group on [side] called [name]. */
    fun create(
        master: String,
        kerning: UfoKerning,
        side: KernSide,
        name: String,
        members: List<String> = emptyList(),
    ): GroupEdit {
        val clean =
            cleanName(name)
                ?: return GroupEdit.Refused("Give the group a name: one line, no control characters, up to $MAX_NAME_LENGTH long.")
        val full = KerningGroups.nameOn(side, clean)
        if (full in kerning.groups) return GroupEdit.Refused("There is already a group called $clean on this side.")
        val distinct = members.distinct()
        val moved = distinct.mapNotNull { glyph -> otherGroupOf(kerning, side, glyph, except = full)?.let { glyph to it } }
        val commands = mutableListOf<FontCommand>()
        for ((glyph, from) in moved) commands += SetGroup(master, from, kerning.groups.getValue(from) - glyph)
        commands += SetGroup(master, full, distinct)
        return GroupEdit.Run(
            command = Batch("Add group", commands),
            note = movedNote(moved),
        )
    }

    /** Renames the group [group] to [newName] (without its prefix; the side stays), and the kerning entries that name it. */
    fun rename(
        master: String,
        kerning: UfoKerning,
        group: String,
        newName: String,
    ): GroupEdit {
        val members = kerning.groups[group] ?: return GroupEdit.Refused("There is no group called ${KerningGroups.displayName(group)}.")
        val clean =
            cleanName(newName)
                ?: return GroupEdit.Refused("Give the group a name: one line, no control characters, up to $MAX_NAME_LENGTH long.")
        val side = KerningGroups.sideOf(group)
        val full = if (side != null) KerningGroups.nameOn(side, clean) else clean
        if (full == group) return GroupEdit.Unchanged("That is the name already.")
        if (full in kerning.groups) return GroupEdit.Refused("There is already a group called $clean on this side.")
        val commands = mutableListOf<FontCommand>()
        commands += SetGroup(master, group, null)
        commands += SetGroup(master, full, members)
        commands += renameEntries(master, kerning, group, full)
        return GroupEdit.Run(Batch("Rename group", commands))
    }

    /**
     * Adds [glyph] to [group]. On a kerning side a glyph belongs to one group, so a glyph already in
     * another group of that side moves, and the note says from where.
     */
    fun addMember(
        master: String,
        kerning: UfoKerning,
        group: String,
        glyph: String,
    ): GroupEdit {
        val members = kerning.groups[group] ?: return GroupEdit.Refused("There is no group called ${KerningGroups.displayName(group)}.")
        val name = glyph.trim()
        if (name.isEmpty()) return GroupEdit.Refused("Name a glyph to add.")
        if (name in members) return GroupEdit.Unchanged("$name is in ${KerningGroups.displayName(group)} already.")
        val side = KerningGroups.sideOf(group)
        val from = side?.let { otherGroupOf(kerning, it, name, except = group) }
        val commands = mutableListOf<FontCommand>()
        if (from != null) commands += SetGroup(master, from, kerning.groups.getValue(from) - name)
        commands += SetGroup(master, group, members + name)
        return GroupEdit.Run(Batch("Add to group", commands), note = movedNote(listOfNotNull(from?.let { name to it })))
    }

    /** Takes [glyph] out of [group]. */
    fun removeMember(
        master: String,
        kerning: UfoKerning,
        group: String,
        glyph: String,
    ): GroupEdit {
        val members = kerning.groups[group] ?: return GroupEdit.Refused("There is no group called ${KerningGroups.displayName(group)}.")
        if (glyph !in members) return GroupEdit.Unchanged("$glyph is not in ${KerningGroups.displayName(group)}.")
        return GroupEdit.Run(Batch("Remove from group", listOf(SetGroup(master, group, members - glyph))))
    }

    /** Deletes [group] and every kerning entry that names it; the note says how many entries went. */
    fun delete(
        master: String,
        kerning: UfoKerning,
        group: String,
    ): GroupEdit {
        if (group !in kerning.groups) return GroupEdit.Refused("There is no group called ${KerningGroups.displayName(group)}.")
        val commands = mutableListOf<FontCommand>(SetGroup(master, group, null))
        var entries = 0
        for ((first, seconds) in kerning.kerning) {
            for (second in seconds.keys) {
                if (first == group || second == group) {
                    commands += SetKerning(master, first, second, null)
                    entries++
                }
            }
        }
        val note = if (entries == 0) null else "Also removed $entries kerning ${if (entries == 1) "pair" else "pairs"} that named it."
        return GroupEdit.Run(Batch("Delete group", commands), note)
    }

    /** How many kerning entries name [group] on either side. */
    fun entriesNaming(
        kerning: UfoKerning,
        group: String,
    ): Int = kerning.kerning.entries.sumOf { (first, seconds) -> if (first == group) seconds.size else seconds.keys.count { it == group } }

    private fun renameEntries(
        master: String,
        kerning: UfoKerning,
        from: String,
        to: String,
    ): List<FontCommand> {
        val commands = mutableListOf<FontCommand>()
        for ((first, seconds) in kerning.kerning) {
            for ((second, value) in seconds) {
                if (first != from && second != from) continue
                val newFirst = if (first == from) to else first
                val newSecond = if (second == from) to else second
                commands += SetKerning(master, first, second, null)
                commands += SetKerning(master, newFirst, newSecond, value)
            }
        }
        return commands
    }

    /** The group on [side] other than [except] that holds [glyph], or null. */
    private fun otherGroupOf(
        kerning: UfoKerning,
        side: KernSide,
        glyph: String,
        except: String,
    ): String? =
        kerning.groups.entries
            .firstOrNull { (name, members) -> name != except && KerningGroups.sideOf(name) == side && glyph in members }
            ?.key

    private fun movedNote(moved: List<Pair<String, String>>): String? =
        if (moved.isEmpty()) {
            null
        } else {
            moved.joinToString(
                " ",
            ) { (glyph, from) -> "$glyph left ${KerningGroups.displayName(from)}: a glyph is in one group on each side." }
        }

    private const val SPACE = 0x20
    private const val DELETE = 0x7F
}
