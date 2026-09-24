package dev.aarso.typewright.campaign

import kotlin.test.Test
import kotlin.test.assertEquals

class CampaignModuleTest {
    @Test
    fun placeholderNamesItsModule() {
        assertEquals("campaign", CampaignModule.NAME)
    }
}
