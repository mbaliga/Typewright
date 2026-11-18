// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The picker's back-ends are modal dialogs that wait for a person, so no test may reach one: CI
 * runs these under `xvfb-run`, where a display exists and nobody answers. The no-display case is
 * therefore stated by the test, not inherited from the host: [DesktopFolderPicker.pick] must give
 * up cleanly (null), never throw and never open a dialog. The chosen-folder path is owner-verified
 * only (CLAUDE.md law 4).
 */
class DesktopFolderPickerTest {
    @Test
    fun returnsNullRatherThanThrowingWhenNothingCanShowAPicker() =
        runTest {
            assertNull(DesktopFolderPicker(hasDisplay = { false }).pick())
            assertNull(DesktopFolderPicker(LinuxSandbox.Flatpak, hasDisplay = { false }).pick())
        }

    @Test
    fun aNamedX11OrWaylandDisplayCountsAsADisplay() {
        assertTrue(displayAvailable(mapOf("DISPLAY" to ":99"), awtHeadless = true))
        assertTrue(displayAvailable(mapOf("WAYLAND_DISPLAY" to "wayland-0"), awtHeadless = true))
    }

    @Test
    fun noDisplayVariableAndHeadlessAwtMeansNoDisplay() {
        assertFalse(displayAvailable(emptyMap(), awtHeadless = true))
        assertFalse(displayAvailable(mapOf("DISPLAY" to "", "WAYLAND_DISPLAY" to ""), awtHeadless = true))
    }

    @Test
    fun aDesktopWithoutDisplayVariablesStillCountsWhenAwtIsNotHeadless() {
        assertTrue(displayAvailable(emptyMap(), awtHeadless = false))
    }
}
