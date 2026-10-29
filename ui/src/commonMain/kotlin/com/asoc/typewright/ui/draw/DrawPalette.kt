// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.ui.glass.PaletteEntry

/**
 * The Draw room's rows in the command palette: every command of the registry as it stands for the
 * current selection (so a row's description is its measured consequence, or the reason it cannot run,
 * and a row that cannot run is dimmed), then the glyph steppers. Running a row closes the palette,
 * because the edit it made is on the page behind. The same registry drives the radial, the rich menu
 * and the shortcut sheet, so the four always agree.
 */
object DrawPalette {
    fun entries(room: DrawRoom): List<PaletteEntry> {
        val controller = room.controller
        val commands =
            controller
                ?.let { c ->
                    DrawCommands.build(DrawCommandContext(c.editor, c.selection, c.metrics(), c)).map { command ->
                        PaletteEntry(
                            id = "draw-" + command.id,
                            label = command.label,
                            description = command.reason ?: command.consequence ?: command.scope.title.lowercase(),
                            shortcut = command.keys,
                            run = {
                                if (command.enabled) {
                                    c.run(command)
                                    command.label
                                } else {
                                    "${command.label}: ${command.reason}"
                                }
                            },
                            closesPalette = command.enabled,
                            dimmed = !command.enabled,
                        )
                    }
                }.orEmpty()
        val steppers =
            if (room.directory.glyphs.size > 1) {
                listOf(
                    stepper(room, "Previous glyph", "PgUp", -1),
                    stepper(room, "Next glyph", "PgDn", 1),
                )
            } else {
                emptyList()
            }
        return commands + steppers
    }

    private fun stepper(
        room: DrawRoom,
        label: String,
        keys: String,
        delta: Int,
    ): PaletteEntry =
        PaletteEntry(
            id = "draw-step-$delta",
            label = label,
            description = "in the font's order",
            shortcut = keys,
            run = {
                room.stepGlyph(delta)
                label
            },
            closesPalette = true,
        )
}
