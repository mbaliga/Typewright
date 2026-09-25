// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.storage.AndroidFolderPicker
import com.asoc.typewright.ui.TypewrightApp

/** Hosts the shared UI. On-device behaviour is owner-verified only (CLAUDE.md law 4). */
class MainActivity : ComponentActivity() {
    /** [TypewrightApplication]'s process-scoped session; survives this Activity's own recreate. */
    val workspace: ProjectWorkspace get() = (application as TypewrightApplication).workspace

    private lateinit var folderPicker: AndroidFolderPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        folderPicker = AndroidFolderPicker(activityResultRegistry, contentResolver)
        setContent { TypewrightApp(workspace = workspace) }
    }

    /** S02's folder picker (docs/PROJECT_MODEL.md §5, §13): a SAF tree, or null if the user backs out. */
    suspend fun pickProjectParentFolder(): ProjectLocation.SafTree? = folderPicker.pick()
}
