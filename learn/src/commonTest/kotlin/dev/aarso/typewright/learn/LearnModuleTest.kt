package dev.aarso.typewright.learn

import kotlin.test.Test
import kotlin.test.assertEquals

class LearnModuleTest {
    @Test
    fun placeholderNamesItsModule() {
        assertEquals("learn", LearnModule.NAME)
    }
}
