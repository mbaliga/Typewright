package dev.aarso.typewright.app.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {
    @Test
    fun windowIsTitledTypewright() {
        assertEquals("Typewright", APP_TITLE)
    }
}
