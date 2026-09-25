// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android

import android.app.Application
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.storage.AndroidAppConfigStore
import com.asoc.typewright.project.storage.AndroidStorageProvider
import com.asoc.typewright.ui.project.createProjectWorkspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Holds the app's one [ProjectWorkspace] for the life of the process, outside any Activity's
 * Compose composition, so an Activity recreate (a configuration change) keeps the same open
 * session, its undo history included (docs/PROJECT_MODEL.md §6 keeps history only for a live
 * process, not across a real reopen). Process death still loses it, exactly as the design
 * intends; [ProjectWorkspace.reopenLast] then rebuilds it from disk.
 */
class TypewrightApplication : Application() {
    val workspace: ProjectWorkspace by lazy {
        createProjectWorkspace(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            storage = AndroidStorageProvider(this),
            config = AndroidAppConfigStore(filesDir),
        )
    }
}
