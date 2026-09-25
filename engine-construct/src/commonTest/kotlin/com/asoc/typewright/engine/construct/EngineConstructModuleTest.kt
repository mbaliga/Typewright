// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.construct

import kotlin.test.Test
import kotlin.test.assertEquals

class EngineConstructModuleTest {
    @Test
    fun placeholderNamesItsModule() {
        assertEquals("engine-construct", EngineConstructModule.NAME)
    }
}
