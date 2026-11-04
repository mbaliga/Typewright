// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.asoc.typewright.app.android.widget.WidgetDestination
import com.asoc.typewright.app.android.widget.WidgetLaunch
import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.storage.AndroidFolderPicker
import com.asoc.typewright.ui.TypewrightApp
import com.asoc.typewright.ui.TypewrightAppNavState
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.toColor

/**
 * Hosts the shared UI, and lands a home-screen widget tap on the screen it names
 * (docs/SCREENS_V1.md §9.2). On-device behaviour is owner-verified only (CLAUDE.md law 4).
 */
class MainActivity : ComponentActivity() {
    private val app: TypewrightApplication get() = application as TypewrightApplication

    /** [TypewrightApplication]'s process-scoped session; survives this Activity's own recreate. */
    val workspace: ProjectWorkspace get() = app.workspace

    private val navState = TypewrightAppNavState()

    private lateinit var folderPicker: AndroidFolderPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        folderPicker = AndroidFolderPicker(activityResultRegistry, contentResolver)
        // A recreate keeps the intent that started it; only a fresh start acts on its widget tap.
        if (savedInstanceState == null) handleWidgetTap(intent)
        setContent {
            val opening by app.widgetOpening.collectAsState()
            if (opening) {
                // The app's UI waits while a tapped project opens, so it never reopens the last one alongside it.
                Box(Modifier.fillMaxSize().background(CanvasTextures.DEFAULT.canvas.toColor()))
            } else {
                TypewrightApp(navState = navState, workspace = workspace)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetTap(intent)
    }

    /** S02's folder picker (docs/PROJECT_MODEL.md §5, §13): a SAF tree, or null if the user backs out. */
    suspend fun pickProjectParentFolder(): ProjectLocation.SafTree? = folderPicker.pick()

    private fun handleWidgetTap(intent: Intent?) {
        val request =
            WidgetLaunch.parse(
                action = intent?.action,
                destination = intent?.getStringExtra(WidgetLaunch.EXTRA_DESTINATION),
                projectKey = intent?.getStringExtra(WidgetLaunch.EXTRA_PROJECT_KEY),
                fromHistory = intent != null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0,
            ) ?: return
        when (request.destination) {
            // Typewright where you left it: bringing the task forward is all it takes.
            WidgetDestination.HOME -> {
                return
            }

            WidgetDestination.WORKBOOK -> {
                navState.openWorkbook()
            }

            WidgetDestination.LEARN -> {
                navState.openLearn()
            }

            WidgetDestination.PROJECT -> {
                navState.closeLearn()
                navState.closeWorkbook()
                request.projectKey?.let(app::openRecentFromWidget)
            }
        }
    }
}
