// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {
    @Test
    fun windowIsTitledTypewright() {
        assertEquals("Typewright", APP_TITLE)
    }
}
