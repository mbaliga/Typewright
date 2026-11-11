// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android

import android.app.Application
import android.os.Build
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.storage.AndroidAppConfigStore
import com.asoc.typewright.project.storage.AndroidStorageProvider
import com.asoc.typewright.ui.crash.buildCrashReport
import com.asoc.typewright.ui.project.createProjectWorkspace
import com.asoc.typewright.ui.settings.APP_VERSION
import com.asoc.typewright.ui.settings.BuildChannel
import com.asoc.typewright.ui.settings.DistributionChannel
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
    override fun onCreate() {
        super.onCreate()
        // The store this build is for (app-android/build.gradle.kts, -Ptypewright.channel): the Play build shows no support link.
        BuildChannel.current = DistributionChannel.fromId(BuildConfig.DISTRIBUTION_CHANNEL)
        // Any crash leaves a report on this device for the next launch to show (nothing is sent anywhere).
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val report =
                    buildCrashReport(
                        error,
                        thread.name,
                        APP_VERSION,
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}",
                        java.util.Date().toString(),
                    )
                java.io.File(filesDir, CRASH_FILE).writeText(report)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The document-tree storage the workspace and Ship's folder both write through. */
    val storage: AndroidStorageProvider by lazy { AndroidStorageProvider(this) }

    val workspace: ProjectWorkspace by lazy {
        createProjectWorkspace(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            storage = storage,
            config = AndroidAppConfigStore(filesDir),
        )
    }
}

/** The file, in the app's private files, that holds the last crash's report until it has been shown. */
const val CRASH_FILE: String = "last-crash.txt"
