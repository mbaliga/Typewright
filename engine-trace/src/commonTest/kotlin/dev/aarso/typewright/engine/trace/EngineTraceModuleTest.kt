package dev.aarso.typewright.engine.trace

import kotlin.test.Test
import kotlin.test.assertEquals

class EngineTraceModuleTest {
    @Test
    fun placeholderNamesItsModule() {
        assertEquals("engine-trace", EngineTraceModule.NAME)
    }
}
