// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxSandboxTest {
    private val nothing: (String) -> Boolean = { false }

    @Test
    fun plainInstallIsNone() {
        assertEquals(LinuxSandbox.None, LinuxSandbox.detect(emptyMap(), nothing))
    }

    @Test
    fun flatpakIdOrInfoFileMeansFlatpak() {
        assertEquals(LinuxSandbox.Flatpak, LinuxSandbox.detect(mapOf("FLATPAK_ID" to "x.y"), nothing))
        assertEquals(LinuxSandbox.Flatpak, LinuxSandbox.detect(emptyMap()) { it == "/.flatpak-info" })
    }

    @Test
    fun snapNeedsBothVariables() {
        assertEquals(LinuxSandbox.Snap, LinuxSandbox.detect(mapOf("SNAP" to "/snap/x/1", "SNAP_NAME" to "x"), nothing))
        assertEquals(LinuxSandbox.None, LinuxSandbox.detect(mapOf("SNAP" to "/snap/x/1"), nothing))
    }

    @Test
    fun emptyFlatpakIdIsIgnored() {
        assertEquals(LinuxSandbox.None, LinuxSandbox.detect(mapOf("FLATPAK_ID" to ""), nothing))
    }

    @Test
    fun pickerOrderSkipsHostToolsInsideASandbox() {
        assertEquals(listOf(PickerBackend.Swing), pickerOrder(LinuxSandbox.Flatpak))
        assertEquals(listOf(PickerBackend.Swing), pickerOrder(LinuxSandbox.Snap))
        assertEquals(
            listOf(PickerBackend.Zenity, PickerBackend.Kdialog, PickerBackend.Swing),
            pickerOrder(LinuxSandbox.None),
        )
        assertTrue(LinuxSandbox.Flatpak.hidesHostTools)
        assertFalse(LinuxSandbox.None.hidesHostTools)
    }
}
