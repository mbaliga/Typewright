// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.campaign.WorkbookLatinContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ControlStrings.WORKBOOK] against the Workbook's own lesson text. The lessons are a JVM resource, so this
 * runs on the desktop only; the browser build has no resource loader for them.
 */
class ControlStringsWorkbookTest {
    @Test
    fun theWorkbookStringsAreTheOnesTheWorkbooksSpacingTasksName() {
        val tasks = WorkbookLatinContent.load()
        val spacing = tasks.single { it.index == 7 }
        for (string in ControlStrings.WORKBOOK) {
            assertTrue(string in spacing.task, "$string is named by the Workbook's Spacing task")
        }
        assertEquals("nnonnonon", tasks.single { it.index == 4 }.controlString)
        assertEquals(listOf("nnonnonon", "HHOHOHOH"), ControlStrings.WORKBOOK)
    }
}
