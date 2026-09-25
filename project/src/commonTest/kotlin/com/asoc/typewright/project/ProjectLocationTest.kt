// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectLocationTest {
    private val json = Json

    @Test
    fun encodesWithTheKindDiscriminatorAndSnakeCaseKeys() {
        val cases =
            listOf(
                ProjectLocation.FileSystem("/home/m/Fonts/Hyle Deco") to """{"kind":"file","path":"/home/m/Fonts/Hyle Deco"}""",
                ProjectLocation.SafTree(
                    "content://com.android.externalstorage.documents/tree/primary%3ADocuments",
                    "primary:Documents/Hyle Deco",
                ) to
                    """{"kind":"saf","tree_uri":"content://com.android.externalstorage.documents/tree/primary%3ADocuments",""" +
                    """"document_id":"primary:Documents/Hyle Deco"}""",
                ProjectLocation.BrowserHandle("h-1") to """{"kind":"browser-handle","key":"h-1"}""",
                ProjectLocation.InMemory("Untitled") to """{"kind":"memory","name":"Untitled"}""",
            )
        for ((location, expected) in cases) {
            assertEquals(expected, json.encodeToString(ProjectLocation.serializer(), location))
            assertEquals(location, json.decodeFromString(ProjectLocation.serializer(), expected))
        }
    }

    @Test
    fun recoveryReportKnowsWhenItIsEmpty() {
        assertEquals(true, RecoveryReport.NONE.isEmpty)
        assertEquals(false, RecoveryReport(listOf("a"), emptyList()).isEmpty)
    }
}
