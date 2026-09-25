// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import kotlinx.browser.localStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalStorageAppConfigStoreTest {
    private val store = LocalStorageAppConfigStore()

    @AfterTest
    fun cleanUp() {
        localStorage.removeItem("typewright.recent-projects")
    }

    @Test
    fun missingFileReadsNull() =
        runTest {
            assertNull(store.read("recent-projects.json"))
        }

    @Test
    fun writeThenReadRoundTripsAsPlainText() =
        runTest {
            val json = "{\n  \"format\": \"typewright-recent-projects\"\n}\n"
            store.writeAtomic("recent-projects.json", json.encodeToByteArray())

            assertEquals(json, store.read("recent-projects.json")!!.decodeToString())
            // docs/PROJECT_MODEL.md §5.4's exact key: readable in devtools, not base64.
            assertEquals(json, localStorage.getItem("typewright.recent-projects"))
        }
}
