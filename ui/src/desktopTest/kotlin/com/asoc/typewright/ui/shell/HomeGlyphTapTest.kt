// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.shell

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.SampleProject
import com.asoc.typewright.ui.tokens.CanvasTextures
import kotlin.test.Test
import kotlin.test.assertEquals

/** A tap on a glyph in the Glyphs grid asks the router to open it in Draw, with that glyph's own name. */
class HomeGlyphTapTest {
    private val workspace = SampleProject.workspace()
    private val font = checkNotNull(SampleProject.openProject(workspace).font)

    /** Taps (x, y) in pixels on Home's Glyphs view at density 2 and answers the names the router was asked to open. */
    private fun tap(
        x: Float,
        y: Float,
    ): List<String> {
        val opened = mutableListOf<String>()
        val nav = GlyphNav(current = null, open = { opened += it }, select = {})
        ImageComposeScene(width = 780, height = 1688, density = Density(2f)) {
            CompositionLocalProvider(LocalProjectWorkspace provides workspace, LocalGlyphNav provides nav) {
                HomeScreen(texture = CanvasTextures.PAPER, initialView = HomeView.GLYPHS)
            }
        }.use { scene ->
            scene.render(0)
            val at = Offset(x, y)
            val primary = PointerButtons(isPrimaryPressed = true)
            scene.sendPointerEvent(PointerEventType.Move, at)
            scene.render(16_000_000)
            scene.sendPointerEvent(PointerEventType.Press, at, buttons = primary, button = PointerButton.Primary)
            scene.render(32_000_000)
            scene.sendPointerEvent(PointerEventType.Release, at, button = PointerButton.Primary)
            scene.render(48_000_000)
        }
        return opened
    }

    @Test
    fun tappingAGlyphOpensThatGlyphInDraw() {
        // The first row of the grid: .notdef, a space, A, B, C, D (see home-glyphs.png). Column 3 is A.
        val a = checkNotNull(font.glyphFor('A')).name
        assertEquals(listOf(a), tap(x = 330f, y = 310f))
        // Column 1 is the first glyph of the font.
        assertEquals(listOf(font.glyphs.first().name), tap(x = 92f, y = 310f))
    }

    @Test
    fun tappingBetweenGlyphsOpensNothing() {
        assertEquals(emptyList(), tap(x = 20f, y = 1000f))
    }
}
