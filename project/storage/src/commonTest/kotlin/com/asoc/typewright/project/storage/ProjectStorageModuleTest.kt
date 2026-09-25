// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectStorageModuleTest {
    @Test
    fun namesItsModule() {
        assertEquals("project-storage", ProjectStorageModule.NAME)
    }
}
