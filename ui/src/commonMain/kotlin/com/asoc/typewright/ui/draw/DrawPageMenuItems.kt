// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

/**
 * The Draw page's ⋯ menu (S20): Command… first, then the ways to move between glyphs, then the commands
 * a person reaches for most from the same registry as the radial and the palette, Layers, and the
 * destructive row last, marked ●. A command that cannot run is still listed, with its reason in place of
 * its keys, and running it says the reason instead of doing nothing. Only commands that exist are listed:
 * the board's Undo history and Guides panel are not built. The Layers row is there only when [build] is
 * given somewhere to show the layers panel: the desktop frame's tool rail already has its own layers
 * section, and a row that did nothing there would be a lie.
 */
object DrawPageMenuItems {
    private val ORDER = listOf("undo", "redo", "lock", "add-extremes", "tidy", "reverse", "correct-direction", "fit")

    fun build(
        room: DrawRoom,
        layersShown: Boolean,
        onCommand: () -> Unit,
        onGoTo: () -> Unit,
        onLayers: (() -> Unit)?,
    ): List<PageMenuEntry> {
        val items = mutableListOf(PageMenuEntry("Command…", "Ctrl or ⌘ K", run = onCommand))
        val layers = onLayers?.let { PageMenuEntry(if (layersShown) "Hide layers" else "Layers", run = it) }
        val controller = room.controller
        if (controller == null) {
            layers?.let { items += it }
            return items
        }
        if (room.directory.glyphs.size > 1) {
            items += PageMenuEntry("Go to glyph…", "Ctrl G", run = onGoTo)
            items += PageMenuEntry("Previous glyph", "PgUp") { room.stepGlyph(-1) }
            items += PageMenuEntry("Next glyph", "PgDn") { room.stepGlyph(1) }
        }
        val commands =
            DrawCommands
                .build(
                    DrawCommandContext(controller.editor, controller.selection, controller.metrics(), controller),
                ).associateBy {
                    it.id
                }
        for (id in ORDER) {
            val command = commands[id] ?: continue
            items += PageMenuEntry(command.label, command.keys, command.reason, runnerFor(controller, command))
        }
        layers?.let { items += it }
        commands["clear"]?.let { command ->
            items += PageMenuEntry("● ${command.label}", reason = command.reason, run = runnerFor(controller, command))
        }
        return items
    }

    private fun runnerFor(
        controller: DrawController,
        command: DrawCommand,
    ): () -> Unit =
        {
            if (command.enabled) controller.run(command) else controller.say("${command.label}: ${command.reason}")
        }
}
