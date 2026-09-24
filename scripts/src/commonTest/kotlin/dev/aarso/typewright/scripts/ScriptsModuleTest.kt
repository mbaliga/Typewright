package dev.aarso.typewright.scripts

import kotlin.test.Test
import kotlin.test.assertEquals

class ScriptsModuleTest {
    @Test
    fun placeholderNamesItsModule() {
        assertEquals("scripts", ScriptsModule.NAME)
    }
}
