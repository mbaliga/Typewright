// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildChannelTest {
    @Test
    fun thePlayBuildShowsNoSupportRowWhateverTheSupportPageIs() {
        assertNull(supportRow(DistributionChannel.PLAY))
        assertNull(supportRow(DistributionChannel.PLAY, supportUrl = "https://example.invalid/support"))
    }

    @Test
    fun aBuildThatHasNotSaidWhichItIsShowsNoSupportRow() {
        assertEquals(DistributionChannel.UNSPECIFIED, BuildChannel.current)
        assertNull(supportRow(BuildChannel.current, supportUrl = "https://example.invalid/support"))
    }

    @Test
    fun desktopWebAndGitHubBuildsShowTheSupportLinkOnceThereIsAPage() {
        for (channel in listOf(DistributionChannel.DESKTOP, DistributionChannel.WEB, DistributionChannel.GITHUB_RELEASE)) {
            val row = assertNotNull(supportRow(channel, supportUrl = "https://example.invalid/support"), channel.id)
            assertEquals("https://example.invalid/support", row.url)
        }
    }

    @Test
    fun withoutASupportPageTheRowSaysSoInsteadOfLinkingNowhere() {
        val row = assertNotNull(supportRow(DistributionChannel.DESKTOP, supportUrl = ""))
        assertNull(row.url)
        assertTrue("not open yet" in row.text)
        assertEquals("", SUPPORT_URL, "the owner sets the address when the merchant-of-record page exists")
    }

    @Test
    fun theBuildFlagMapsToAChannelAndATypoHidesTheLink() {
        assertEquals(DistributionChannel.PLAY, DistributionChannel.fromId("play"))
        assertEquals(DistributionChannel.GITHUB_RELEASE, DistributionChannel.fromId("github"))
        assertEquals(DistributionChannel.UNSPECIFIED, DistributionChannel.fromId("plya"))
        assertEquals(DistributionChannel.UNSPECIFIED, DistributionChannel.fromId(null))
        assertEquals(
            DistributionChannel.entries.size,
            DistributionChannel.entries
                .map { it.id }
                .toSet()
                .size,
        )
    }

    @Test
    fun onlyTheWebBuildIsAPreview() {
        assertEquals(listOf(DistributionChannel.WEB), DistributionChannel.entries.filter { it.isPreview })
        assertTrue("cannot compile" in WEB_PREVIEW_NOTICE)
    }

    @Test
    fun theVersionLineNamesTheVersionTheBuildNumberAndTheChannel() {
        assertEquals("Typewright $APP_VERSION (build $APP_VERSION_CODE)", aboutVersionLine(DistributionChannel.UNSPECIFIED))
        assertEquals("Typewright $APP_VERSION (build $APP_VERSION_CODE) · Google Play", aboutVersionLine(DistributionChannel.PLAY))
    }
}
