// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [RecentProjectsCodec]: `recent-projects.json`'s round trip, including a SAF location and null fields. Runs on the JVM and Wasm. */
class RecentProjectsCodecTest {
    @Test
    fun encodeThenDecodeRoundTripsAFullEntry() {
        val entry =
            RecentProject(
                location = ProjectLocation.SafTree("content://tree/primary%3ADocuments", "primary:Documents/Hyle Deco"),
                name = "Hyle Deco",
                lastOpened = Fixtures.EPOCH,
                lastEdited = Fixtures.EPOCH,
                resume = ResumePoint("draw", "regular", "T"),
                summary = ProjectSummary(338, 38, "sans-geometric"),
            )
        val file = RecentProjectsFile(listOf(entry), lastOpen = 0)
        val decoded = RecentProjectsCodec.decode(RecentProjectsCodec.encode(file))
        assertEquals(file, decoded)
    }

    @Test
    fun encodeThenDecodeRoundTripsAnEntryWithNoResumeOrSummaryYet() {
        val entry =
            RecentProject(
                ProjectLocation.FileSystem("/home/user/Hyle Deco"),
                "Hyle Deco",
                Fixtures.EPOCH,
                null,
                resume = null,
                summary = null,
            )
        val decoded = RecentProjectsCodec.decode(RecentProjectsCodec.encode(RecentProjectsFile(listOf(entry), 0)))
        assertNull(decoded.projects.single().resume)
        assertNull(decoded.projects.single().summary)
        assertNull(decoded.projects.single().lastEdited)
    }

    @Test
    fun emptyRecentsRoundTrips() {
        val file = RecentProjectsFile(emptyList(), null)
        assertEquals(file, RecentProjectsCodec.decode(RecentProjectsCodec.encode(file)))
    }

    @Test
    fun aBrowserHandleLocationRoundTrips() {
        val entry = RecentProject(ProjectLocation.BrowserHandle("handle-1"), "Web Project", Fixtures.EPOCH, null, null, null)
        val decoded = RecentProjectsCodec.decode(RecentProjectsCodec.encode(RecentProjectsFile(listOf(entry), 0)))
        assertEquals(ProjectLocation.BrowserHandle("handle-1"), decoded.projects.single().location)
    }
}
