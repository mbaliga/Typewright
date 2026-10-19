// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.saveStatusWord

/**
 * The line under a page title that says which project is open and whether it is saved:
 * `"<name> · saved/saving/in this tab only"` (docs/PROJECT_MODEL.md §13), read from
 * [LocalProjectWorkspace], or [NO_PROJECT_STATUS] when none is open.
 */
@Composable
fun projectStatusText(): String {
    val session =
        LocalProjectWorkspace.current
            ?.current
            ?.collectAsState()
            ?.value ?: return NO_PROJECT_STATUS
    val name =
        session.state
            .collectAsState()
            .value.meta.manifest.name
    val status = session.saveStatus.collectAsState().value
    return "$name · ${saveStatusWord(status)}"
}

/** The status line with no project open. */
const val NO_PROJECT_STATUS = "No project open"
