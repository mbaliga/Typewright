package dev.aarso.typewright.engine.construct

import kotlin.test.Test
import kotlin.test.assertEquals

class EngineConstructModuleTest {
    @Test
    fun placeholderNamesItsModule() {
        assertEquals("engine-construct", EngineConstructModule.NAME)
    }
}
