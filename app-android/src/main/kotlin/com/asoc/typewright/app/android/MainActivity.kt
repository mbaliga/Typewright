// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.storage.AndroidFolderPicker
import com.asoc.typewright.ui.TypewrightApp
import com.asoc.typewright.ui.deck.SystemBack
import com.asoc.typewright.ui.imports.PlatformImportSupport
import com.asoc.typewright.ui.ship.AndroidShipSupport

/** Hosts the shared UI. On-device behaviour is owner-verified only (CLAUDE.md law 4). */
class MainActivity : ComponentActivity() {
    /** [TypewrightApplication]'s process-scoped session; survives this Activity's own recreate. */
    val workspace: ProjectWorkspace get() = (application as TypewrightApplication).workspace

    private lateinit var folderPicker: AndroidFolderPicker
    private lateinit var fontPicker: AndroidFontFilePicker
    private val systemBack = SystemBack()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        folderPicker = AndroidFolderPicker(activityResultRegistry, contentResolver)
        // "Open a font…": the system document picker, and the folder picker for where the project goes.
        // Both are tied to this activity, so a recreated activity installs its own.
        fontPicker = AndroidFontFilePicker(activityResultRegistry, contentResolver)
        PlatformImportSupport.filePicker = fontPicker
        PlatformImportSupport.destination = AndroidImportDestination { pickProjectParentFolder() }
        // Ship writes its repository folder through the same document tree.
        val shipFolder = AndroidShipFolderChooser({ pickProjectParentFolder() }, (application as TypewrightApplication).storage)
        AndroidShipSupport.chooseFolder = shipFolder::choose
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // The screen on top gets back first; only when it declines does the system go on.
                    if (!systemBack.dispatch()) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            },
        )
        setContent { TypewrightApp(workspace = workspace, systemBack = systemBack) }
    }

    override fun onDestroy() {
        // A pick in flight belongs to this activity; end it rather than leave it waiting for a screen that is gone.
        fontPicker.release()
        AndroidShipSupport.chooseFolder = null
        super.onDestroy()
    }

    /** S02's folder picker (docs/PROJECT_MODEL.md §5, §13): a SAF tree, or null if the user backs out. */
    suspend fun pickProjectParentFolder(): ProjectLocation.SafTree? = folderPicker.pick()
}
