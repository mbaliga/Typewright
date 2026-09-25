// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import com.asoc.typewright.project.AppConfigStore
import kotlinx.browser.localStorage

/**
 * The web's [AppConfigStore]: `recent-projects.json` lives at `localStorage["typewright.recent-
 * projects"]` (docs/PROJECT_MODEL.md §5.4), plain UTF-8 text (not base64 -- it already is JSON, so
 * it stays readable in devtools). [configKey] drops a trailing `.json`, matching that exact key
 * for the one config file the app writes today; any other [name] gets the same `typewright.`
 * prefix. `kotlinx-browser` gives a typed `Storage` for `localStorage` itself, so this needs no
 * hand-bound interop.
 */
class LocalStorageAppConfigStore : AppConfigStore {
    override suspend fun read(name: String): ByteArray? = localStorage.getItem(configKey(name))?.encodeToByteArray()

    override suspend fun writeAtomic(
        name: String,
        bytes: ByteArray,
    ) {
        // A plain localStorage.setItem is already atomic from a reader's point of view: no
        // reader ever observes a partial value.
        localStorage.setItem(configKey(name), bytes.decodeToString())
    }

    private fun configKey(name: String): String = "typewright.${name.removeSuffix(".json")}"
}
