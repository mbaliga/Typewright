// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.project.ProjectPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [ControlStrings]: the Workbook's two, the person's own, and what may be added. */
class ControlStringsTest {
    @Test
    fun theListIsTheWorkbooksThenTheirOwnWithoutRepeats() {
        val preferences = ProjectPreferences(spaceControlStrings = listOf("HOHOOHH", "nnonnonon", "HOHOOHH", "oOoOo"))
        val all = ControlStrings.all(preferences)
        assertEquals(listOf("nnonnonon", "HHOHOHOH", "HOHOOHH", "oOoOo"), all.map { it.text })
        assertEquals(listOf(false, false, true, true), all.map { it.own })
        assertEquals(2, ControlStrings.all(ProjectPreferences()).size)
    }

    @Test
    fun cleanTrimsAndSingleSpacesAndRefusesWhatIsNotAString() {
        assertEquals("nn on", ControlStrings.clean("  nn    on "))
        assertNull(ControlStrings.clean("   "))
        assertNull(ControlStrings.clean("no\nno"))
        assertNull(ControlStrings.clean("n\u0007o"))
        assertNull(ControlStrings.clean("n".repeat(ControlStrings.MAX_LENGTH + 1)))
        assertEquals("n".repeat(ControlStrings.MAX_LENGTH), ControlStrings.clean("n".repeat(ControlStrings.MAX_LENGTH)))
    }

    @Test
    fun addingKeepsTheNewStringInThePreferencesAndRefusesRepeats() {
        val added = assertIs<ControlStrings.Add.Added>(ControlStrings.add(ProjectPreferences(), " HOHO "))
        assertEquals("HOHO", added.text)
        assertEquals(listOf("HOHO"), added.preferences.spaceControlStrings)
        assertIs<ControlStrings.Add.Refused>(ControlStrings.add(added.preferences, "HOHO"))
        assertIs<ControlStrings.Add.Refused>(ControlStrings.add(ProjectPreferences(), "nnonnonon"))
        assertIs<ControlStrings.Add.Refused>(ControlStrings.add(ProjectPreferences(), ""))
    }

    @Test
    fun removingTakesOutTheirOwnAndNeverTheWorkbooks() {
        val preferences = ProjectPreferences(spaceControlStrings = listOf("HOHO", "oOo"))
        assertEquals(listOf("oOo"), ControlStrings.remove(preferences, "HOHO").spaceControlStrings)
        assertEquals(
            listOf("nnonnonon", "HHOHOHOH", "HOHO", "oOo"),
            ControlStrings.all(ControlStrings.remove(preferences, "nnonnonon")).map { it.text },
        )
    }
}
