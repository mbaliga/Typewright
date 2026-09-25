// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.campaign

import kotlin.test.Test
import kotlin.test.assertEquals

class CampaignModuleTest {
    @Test
    fun namesItsModule() {
        assertEquals("campaign", CampaignModule.NAME)
    }
}
