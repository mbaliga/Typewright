// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlin.time.Instant

/** [ProjectSession.saveStatus]: what autosave is doing, for the header's "saved/saving/…" status (docs/PROJECT_MODEL.md §13). */
sealed interface SaveStatus {
    /** Nothing to save; the store matches the state. */
    data object Clean : SaveStatus

    /** A change is waiting to be saved, debounced since [since]. */
    data class Pending(
        val since: Instant,
    ) : SaveStatus

    /** A save is in progress. */
    data object Saving : SaveStatus

    /** The last save completed at [at]. */
    data class Saved(
        val at: Instant,
    ) : SaveStatus

    /** The last save of [path] failed with [message]; the state stays dirty. */
    data class Failed(
        val path: String,
        val message: String,
    ) : SaveStatus

    /** One or more of [paths] changed on disk since this session last wrote them (§8.7); see [ProjectSession.resolveConflict]. */
    data class Conflict(
        val paths: List<String>,
    ) : SaveStatus

    /** The store is [com.asoc.typewright.project.Durability.TAB_ONLY]; [unexportedChanges] says whether a download would differ from the last one. */
    data class TabOnly(
        val unexportedChanges: Boolean,
    ) : SaveStatus

    /** The session cannot write: a newer format, or a store opened read-only. */
    data class ReadOnly(
        val reason: String,
    ) : SaveStatus
}
