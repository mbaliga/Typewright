// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android

import com.asoc.typewright.ui.settings.APP_VERSION
import com.asoc.typewright.ui.settings.DistributionChannel
import com.asoc.typewright.ui.settings.supportRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * The build flag (`-Ptypewright.channel`) reaches About: the Play build has no support row, the
 * GitHub-release build has one. Which of the two this run is depends on the flag it was built with.
 */
class BuildChannelFlagTest {
    private val channel = DistributionChannel.fromId(BuildConfig.DISTRIBUTION_CHANNEL)

    @Test
    fun theFlagNamesARealChannel() {
        assertNotEquals(DistributionChannel.UNSPECIFIED, channel, "BuildConfig.DISTRIBUTION_CHANNEL = ${BuildConfig.DISTRIBUTION_CHANNEL}")
    }

    @Test
    fun theSupportRowFollowsTheChannel() {
        val row = supportRow(channel, supportUrl = "https://example.invalid/support")
        if (channel == DistributionChannel.PLAY) assertNull(row) else assertEquals("https://example.invalid/support", row?.url)
    }

    @Test
    fun theAndroidVersionNameIsTheOneVersionFileAndAbout() {
        assertEquals(APP_VERSION, BuildConfig.VERSION_NAME)
    }
}
