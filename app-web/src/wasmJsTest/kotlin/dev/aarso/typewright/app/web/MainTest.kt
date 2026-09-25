// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.app.web

import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {
    @Test
    fun mountsIntoTheElementIndexHtmlDeclares() {
        assertEquals("typewright", ROOT_ELEMENT_ID)
    }
}
