// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.asoc.typewright.app.android.widget.WidgetLaunch
import com.asoc.typewright.app.android.widget.WidgetPublisher
import com.asoc.typewright.project.OpenResult
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.StoreLookup
import com.asoc.typewright.project.storage.AndroidAppConfigStore
import com.asoc.typewright.project.storage.AndroidStorageProvider
import com.asoc.typewright.ui.project.createProjectWorkspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the app's one [ProjectWorkspace] for the life of the process, outside any Activity's
 * Compose composition, so an Activity recreate (a configuration change) keeps the same open
 * session, its undo history included (docs/PROJECT_MODEL.md §6 keeps history only for a live
 * process, not across a real reopen). Process death still loses it, exactly as the design
 * intends; [ProjectWorkspace.reopenLast] then rebuilds it from disk.
 *
 * It also publishes the open project to the home-screen widgets (docs/SCREENS_V1.md §9) and
 * opens a recent project when one of their rows is tapped.
 */
class TypewrightApplication : Application() {
    /** The process's coroutine scope: the workspace's sessions, the widget publisher and widget redraws. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val storage by lazy { AndroidStorageProvider(this) }

    val workspace: ProjectWorkspace by lazy {
        createProjectWorkspace(
            scope = appScope,
            storage = storage,
            config = AndroidAppConfigStore(filesDir),
        )
    }

    private val widgetOpeningFlow = MutableStateFlow(false)

    /** True while a widget tap is opening a recent project; `MainActivity` holds its UI back until then. */
    val widgetOpening: StateFlow<Boolean> = widgetOpeningFlow.asStateFlow()

    /** Publishes the open project to the home-screen widgets; a newly placed widget asks it to publish again. */
    val widgetPublisher: WidgetPublisher by lazy { WidgetPublisher(this, workspace, appScope) }

    override fun onCreate() {
        super.onCreate()
        widgetPublisher.start()
    }

    /**
     * Opens the recent project whose [WidgetLaunch.recentKey] is [key], as S01's row does. The open
     * project is closed only once the tapped one has opened, so a project that can't be reached or
     * read leaves the app as it was, undo history included, and says why in one line.
     */
    fun openRecentFromWidget(key: String) {
        if (widgetOpeningFlow.value) return
        widgetOpeningFlow.value = true
        appScope.launch {
            try {
                openRecent(key)?.let(::say)
            } finally {
                widgetOpeningFlow.value = false
            }
        }
    }

    /** Null when [key]'s project is open now; otherwise why it couldn't be opened. */
    private suspend fun openRecent(key: String): String? {
        val entry =
            workspace.loadRecentProjects().firstOrNull { WidgetLaunch.recentKey(it.location) == key }
                ?: return getString(R.string.widget_open_not_in_recents)
        if (workspace.current.value
                ?.store
                ?.location == entry.location
        ) {
            return null
        }
        when (storage.storeFor(entry.location)) {
            is StoreLookup.Available -> Unit
            StoreLookup.Missing -> return getString(R.string.widget_open_missing, entry.name)
            StoreLookup.PermissionNeeded -> return getString(R.string.widget_open_no_access, entry.name)
        }
        // ProjectWorkspace.open leaves the session it replaces for its caller to close.
        val previous = workspace.current.value
        return when (val result = workspace.open(entry.location)) {
            is OpenResult.Opened, is OpenResult.NewerFormat -> {
                previous?.close()
                null
            }

            is OpenResult.NotAProject -> {
                getString(R.string.widget_open_not_a_project, entry.name)
            }

            is OpenResult.Damaged -> {
                getString(R.string.widget_open_failed, entry.name, result.message)
            }
        }
    }

    private fun say(message: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }
}
