// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import kotlin.test.Test
import kotlin.test.assertEquals

class QaModuleTest {
    @Test
    fun placeholderNamesItsModule() {
        assertEquals("qa", QaModule.NAME)
    }
}
