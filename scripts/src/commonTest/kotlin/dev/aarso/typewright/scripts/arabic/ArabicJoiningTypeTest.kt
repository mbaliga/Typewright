package dev.aarso.typewright.scripts.arabic

import kotlin.test.Test
import kotlin.test.assertEquals

class ArabicJoiningTypeTest {
    @Test
    fun hasExactlyTheThreeRealJoiningTypesThisScriptsLettersUse() {
        assertEquals(
            listOf(ArabicJoiningType.DUAL_JOINING, ArabicJoiningType.RIGHT_JOINING, ArabicJoiningType.NON_JOINING),
            ArabicJoiningType.entries,
        )
    }
}
