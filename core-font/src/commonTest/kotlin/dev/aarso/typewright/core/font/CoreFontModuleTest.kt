// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.core.font

import kotlin.test.Test
import kotlin.test.assertEquals

class CoreFontModuleTest {
    @Test
    fun placeholderNamesItsModule() {
        assertEquals("core-font", CoreFontModule.NAME)
    }
}
