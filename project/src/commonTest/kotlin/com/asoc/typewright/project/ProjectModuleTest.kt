// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectModuleTest {
    @Test
    fun namesItsModule() {
        assertEquals("project", ProjectModule.NAME)
    }
}
