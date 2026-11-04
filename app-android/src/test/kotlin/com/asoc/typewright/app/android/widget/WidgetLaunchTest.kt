// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android.widget

import com.asoc.typewright.project.ProjectLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Widget taps: which extras the activity accepts, and the opaque key a recent project travels as. */
class WidgetLaunchTest {
    private val action = WidgetLaunch.ACTION_OPEN_FROM_WIDGET
    private val location =
        ProjectLocation.SafTree(
            "content://com.android.externalstorage.documents/tree/primary%3AFonts",
            "primary:Fonts/Mine",
        )

    @Test
    fun eachDestinationParses() {
        assertEquals(LaunchRequest(WidgetDestination.HOME), WidgetLaunch.parse(action, "HOME", null))
        assertEquals(LaunchRequest(WidgetDestination.WORKBOOK), WidgetLaunch.parse(action, "WORKBOOK", null))
        assertEquals(LaunchRequest(WidgetDestination.LEARN), WidgetLaunch.parse(action, "LEARN", "ignored"))
        val key = WidgetLaunch.recentKey(location)
        assertEquals(LaunchRequest(WidgetDestination.PROJECT, key), WidgetLaunch.parse(action, "PROJECT", key))
    }

    @Test
    fun aRelaunchFromOverviewDoesNotReplayTheTapThatStartedTheTask() {
        assertNull(WidgetLaunch.parse(action, "WORKBOOK", null, fromHistory = true))
        val key = WidgetLaunch.recentKey(location)
        assertNull(WidgetLaunch.parse(action, "PROJECT", key, fromHistory = true))
        assertEquals(LaunchRequest(WidgetDestination.LEARN), WidgetLaunch.parse(action, "LEARN", null, fromHistory = false))
    }

    @Test
    fun anythingElseIsNotAWidgetTap() {
        assertNull(WidgetLaunch.parse("android.intent.action.MAIN", "HOME", null))
        assertNull(WidgetLaunch.parse(null, "HOME", null))
        assertNull(WidgetLaunch.parse(action, null, null))
        assertNull(WidgetLaunch.parse(action, "home", null))
        assertNull(WidgetLaunch.parse(action, "SETTINGS", null))
        assertNull(WidgetLaunch.parse(action, "PROJECT", null))
        assertNull(WidgetLaunch.parse(action, "PROJECT", "../../etc"))
        assertNull(WidgetLaunch.parse(action, "PROJECT", "ABCDEF0123456789ABCDEF0123456789"))
        assertNull(WidgetLaunch.parse(action, "PROJECT", "0123456789abcdef"))
    }

    @Test
    fun aRecentKeyIsStableOpaqueAndDistinct() {
        val key = WidgetLaunch.recentKey(location)
        assertEquals(key, WidgetLaunch.recentKey(location.copy()))
        assertTrue(Regex("[0-9a-f]{32}").matches(key))
        assertFalse(key.contains("Fonts"))
        assertNotEquals(key, WidgetLaunch.recentKey(location.copy(documentId = "primary:Fonts/Other")))
        assertNotEquals(WidgetLaunch.recentKey(ProjectLocation.FileSystem("a")), WidgetLaunch.recentKey(ProjectLocation.InMemory("a")))
    }

    @Test
    fun fieldBoundariesCannotCollide() {
        val joined = WidgetLaunch.recentKey(ProjectLocation.SafTree("ab", "c"))
        val shifted = WidgetLaunch.recentKey(ProjectLocation.SafTree("a", "bc"))
        assertNotEquals(joined, shifted)
    }
}
