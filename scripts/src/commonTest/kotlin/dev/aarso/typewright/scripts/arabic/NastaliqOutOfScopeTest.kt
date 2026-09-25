// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.scripts.arabic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NastaliqOutOfScopeTest {
    @Test
    fun realTemplateCountInZipIsFortyNine() {
        assertEquals(49, NastaliqOutOfScope.REAL_TEMPLATE_COUNT_IN_ZIP)
    }

    @Test
    fun templateFolderIsSvgNastaliq() {
        assertEquals("svg/Nastaliq", NastaliqOutOfScope.TEMPLATE_FOLDER)
    }

    @Test
    fun disclosureNamesNastaliqAndOutOfScope() {
        assertTrue("Nastaliq" in NastaliqOutOfScope.disclosure)
        assertTrue("out of scope" in NastaliqOutOfScope.disclosure)
    }

    @Test
    fun disclosureGivesTheConcreteOpenTypeReasonNotJustTheDecision() {
        assertTrue("OpenType" in NastaliqOutOfScope.disclosure)
        assertTrue("sloping baseline" in NastaliqOutOfScope.disclosure)
    }

    @Test
    fun disclosureIsNotBlank() {
        assertTrue(NastaliqOutOfScope.disclosure.isNotBlank())
    }
}
