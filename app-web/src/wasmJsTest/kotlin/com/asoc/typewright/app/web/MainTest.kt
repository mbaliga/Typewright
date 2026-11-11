// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.web

import com.asoc.typewright.ui.settings.WEB_PREVIEW_NOTICE
import kotlinx.browser.document
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MainTest {
    @Test
    fun mountsIntoTheElementIndexHtmlDeclares() {
        assertEquals("typewright", ROOT_ELEMENT_ID)
    }

    @Test
    fun thePreviewBannerSaysWhatTheWebBuildCannotDoAndSitsAboveTheApp() {
        val root = document.createElement("div")
        root.setAttribute("id", ROOT_ELEMENT_ID)
        document.body?.appendChild(root)
        showPreviewBanner()
        val banner = assertNotNull(document.getElementById(PREVIEW_BANNER_ID))
        assertEquals(WEB_PREVIEW_NOTICE, banner.textContent)
        assertEquals(root, banner.nextElementSibling)
    }
}
