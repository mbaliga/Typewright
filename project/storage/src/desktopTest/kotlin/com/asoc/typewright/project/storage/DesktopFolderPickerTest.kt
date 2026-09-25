// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * This build's own sandbox has neither `zenity`/`kdialog` nor a display (CLAUDE.md law 4), so
 * every fallback genuinely fails here -- which is exactly the case this test holds constant:
 * [DesktopFolderPicker.pick] must give up cleanly (null), never throw. The chosen-folder path is
 * owner-verified only.
 */
class DesktopFolderPickerTest {
    @Test
    fun returnsNullRatherThanThrowingWhenNothingCanShowAPicker() =
        runTest {
            assertNull(DesktopFolderPicker().pick())
        }
}
