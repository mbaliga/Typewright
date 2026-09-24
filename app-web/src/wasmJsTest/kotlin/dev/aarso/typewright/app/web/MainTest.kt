package dev.aarso.typewright.app.web

import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {
    @Test
    fun mountsIntoTheElementIndexHtmlDeclares() {
        assertEquals("typewright", ROOT_ELEMENT_ID)
    }
}
