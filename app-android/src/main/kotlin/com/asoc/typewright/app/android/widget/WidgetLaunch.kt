// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android.widget

import com.asoc.typewright.project.ProjectLocation
import java.security.MessageDigest

/** Where a widget tap lands in the app (SCREENS_V1 §9.2's "Tap" lines). */
enum class WidgetDestination {
    /** Typewright where you left it: whatever screen and overlay it was showing, or the sheet on a fresh start. */
    HOME,

    /** The Workbook (S19). */
    WORKBOOK,

    /** Learn (S18), which opens on Lineages. */
    LEARN,

    /** One recent project, named by [WidgetLaunch.recentKey], on the sheet with Learn and the Workbook closed. */
    PROJECT,
}

/** A validated widget tap: where to go and, for [WidgetDestination.PROJECT], which recent project. */
data class LaunchRequest(
    val destination: WidgetDestination,
    val projectKey: String? = null,
)

/**
 * The intent extras a widget tap carries into `MainActivity`, and their validation. The extras
 * never hold a folder or URI: a recent project travels as [recentKey], a one-way digest the
 * activity matches against the recent list it reads itself.
 */
object WidgetLaunch {
    /** The action every widget tap uses, so the activity can tell a widget tap from a launcher start. */
    const val ACTION_OPEN_FROM_WIDGET: String = "com.asoc.typewright.action.OPEN_FROM_WIDGET"
    const val EXTRA_DESTINATION: String = "com.asoc.typewright.extra.WIDGET_DESTINATION"
    const val EXTRA_PROJECT_KEY: String = "com.asoc.typewright.extra.WIDGET_PROJECT_KEY"

    private const val KEY_HEX_LENGTH = 32
    private val KEY_PATTERN = Regex("[0-9a-f]{$KEY_HEX_LENGTH}")

    /**
     * The request [action], [destination] and [projectKey] describe, or null when they don't form
     * one: another action, an unknown destination, or a project without a well-formed key. A start
     * [fromHistory] (Overview relaunching a task a widget began) replays an old tap, so it is none.
     */
    fun parse(
        action: String?,
        destination: String?,
        projectKey: String?,
        fromHistory: Boolean = false,
    ): LaunchRequest? {
        if (fromHistory || action != ACTION_OPEN_FROM_WIDGET) return null
        val target = WidgetDestination.entries.firstOrNull { it.name == destination } ?: return null
        if (target != WidgetDestination.PROJECT) return LaunchRequest(target)
        if (projectKey == null || !KEY_PATTERN.matches(projectKey)) return null
        return LaunchRequest(target, projectKey)
    }

    /** A stable, opaque key for [location]: the first 128 bits of a SHA-256 of its fields. */
    fun recentKey(location: ProjectLocation): String {
        val canonical =
            when (location) {
                is ProjectLocation.FileSystem -> listOf("file", location.path)
                is ProjectLocation.SafTree -> listOf("saf", location.treeUri, location.documentId)
                is ProjectLocation.BrowserHandle -> listOf("browser", location.key)
                is ProjectLocation.InMemory -> listOf("memory", location.name)
            }.joinToString(separator = "\u0000")
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.encodeToByteArray())
        return digest.joinToString(separator = "") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }.take(KEY_HEX_LENGTH)
    }
}
