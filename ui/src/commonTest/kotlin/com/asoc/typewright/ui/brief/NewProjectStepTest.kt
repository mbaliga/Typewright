// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.brief

import com.asoc.typewright.project.ContrastModel
import com.asoc.typewright.project.Use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewProjectStepTest {
    @Test
    fun withNoIntentEveryCorpusClassIsOffered() {
        assertEquals(10, closestClassKeys(model = null, use = null).size)
    }

    @Test
    fun sansWithDisplayOffersTheSansClassesAndTheDisplayOnes() {
        val keys = closestClassKeys(ContrastModel.SANS, Use.DISPLAY)
        assertTrue("sans-geometric" in keys && "sans-grotesque" in keys && "sans-humanist" in keys)
        assertTrue("display-artdeco" in keys)
        assertFalse("serif-didone" in keys)
    }

    @Test
    fun textLeavesTheDisplayClassesOut() {
        val keys = closestClassKeys(model = null, use = Use.TEXT)
        assertFalse("display-artdeco" in keys || "blackletter" in keys)
        assertTrue("slab" in keys)
    }

    @Test
    fun sansAloneOffersOnlySans() {
        assertTrue(closestClassKeys(ContrastModel.SANS, use = null).all { it.startsWith("sans-") })
    }

    @Test
    fun theLineUnderTheChipsSaysHowWideTheBoxesAreFromTheCount() {
        assertTrue(classExplanation("Art deco", 8).startsWith("Only 8 art deco families on Google Fonts qualify, so its boxes are wide."))
        assertTrue(classExplanation("Geometric sans", 30).startsWith("30 geometric sans families on Google Fonts set its boxes."))
        assertTrue(classExplanation("Art deco", 8).endsWith("show its evidence, and ask you to confirm."))
    }

    @Test
    fun withNoClassOrNoCountTheLineOnlySaysWhatTheLettersWillDo() {
        val inference = "Typewright will also infer a class from your letters, show its evidence, and ask you to confirm."
        assertEquals(inference, classExplanation(null, null))
        assertEquals(inference, classExplanation("Art deco", null))
    }

    @Test
    fun theScriptChipsCarryTheCountsTheAppReallyHolds() {
        val labels = SCRIPT_CHOICES.map { it.chipLabel(inProject = false) }
        assertTrue(labels.first().endsWith("Latin"))
        assertTrue(labels.drop(1).all { it.endsWith(" preview") && Regex("\\d+").containsMatchIn(it) }, labels.toString())
    }

    @Test
    fun withNoProjectTheDraftKeepsWhatIsTypedAndWritesNothing() {
        val draft = NewProjectDraft(session = null)
        draft.name = "Hyle Deco Redraw"
        draft.commit()
        assertEquals("Hyle Deco Redraw", draft.name)
        assertEquals("blank", draft.source)
        assertTrue(draft.has(SCRIPT_CHOICES.first()), "Latin is always on")
        draft.toggle(SCRIPT_CHOICES.first())
        assertTrue(draft.has(SCRIPT_CHOICES.first()), "and cannot be turned off")
    }
}
