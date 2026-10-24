// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.ui.deck.DeckGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * What the Groups, .fea and Control strings views say about what the person just did: every note and every refusal is text a
 * person can read while the view is open, found in the semantics tree the way a screen reader reads it, and not behind the
 * deck. A refused edit keeps what was typed and the row it was typed in.
 */
class SpaceSubViewNoticeTest {
    private val kerning =
        UfoKerning(
            groups = SpaceTestProjects.GROUPS,
            kerning =
                mapOf(
                    "public.kern1.round" to mapOf("public.kern2.straight" to -24.0),
                    "public.kern1.straight" to mapOf("public.kern2.straight" to -10.0),
                    "n" to mapOf("o" to -12.0),
                ),
        )
    private val ufo get() = SpaceTestProjects.hyleDeco(kerning)

    private fun SpacePageRig.kerningNow() =
        session.state.value.font
            .master(master)!!
            .ufo.kerningInfo

    /** The text is on screen, and clear of the deck at the foot of the page. */
    private fun SpacePageRig.assertShown(
        fragment: String,
        why: String,
    ) {
        val shown = visibleText(fragment) ?: fail("$why: '$fragment' is not on the screen. The page shows: ${texts().map { it.text }}")
        assertTrue(
            shown.bottom <= heightDp - DeckGeometry.HEIGHT + DECK_SLACK,
            "$why: '${shown.text}' is behind the deck (bottom ${shown.bottom})",
        )
    }

    // ---- Groups ---------------------------------------------------------------------------------------

    @Test
    fun aGlyphMovedOutOfItsOldGroupIsSaidSoWhereTheGroupsAre() =
        withSpacePage(ufo) {
            change { open(SpacePanel.GROUPS) }
            tapStartingWith("round", index = 0) // the first side's round group
            typeInto(0, "n")
            tapExactly("Add")
            assertEquals(listOf("o", "O", "n"), kerningNow().groups["public.kern1.round"], "n joined round")
            assertEquals(listOf("H"), kerningNow().groups["public.kern1.straight"], "and left straight")
            assertShown("n left straight", "the move")
            assertEquals("", fieldValues()[0], "the name that was added is put away once it went in")
        }

    @Test
    fun aRefusedRenameStaysOpenWithWhatWasTypedAndSaysWhy() =
        withSpacePage(ufo) {
            change { open(SpacePanel.GROUPS) }
            tapStartingWith("straight", index = 0)
            typeInto(1, "round")
            tapExactly("Rename")
            assertShown("There is already a group called round on this side.", "the rename")
            assertEquals("round", fieldValues()[1], "the row is still open, and the name is still what was typed")
            assertEquals(SpaceTestProjects.GROUPS.keys, kerningNow().groups.keys, "nothing was renamed")
            assertEquals(0, session.history.value.undoLabels.size)
        }

    @Test
    fun deletingAGroupSaysWhatElseWentWithIt() =
        withSpacePage(ufo) {
            change { open(SpacePanel.GROUPS) }
            tapStartingWith("straight", index = 1) // the second side's straight group, which two pairs name
            tapStartingWith("Delete this group and the 2 kerning pairs")
            assertShown("Also removed 2 kerning pairs that named it.", "the delete")
            assertNull(kerningNow().groups["public.kern2.straight"])
            assertEquals(mapOf("n" to mapOf("o" to -12.0)), kerningNow().kerning)
            assertEquals(1, fieldValues().size, "the row closed, leaving only the new-group field")
        }

    @Test
    fun aGlyphNameThatIsNotOneIsRefusedWithTheReasonAndStaysTyped() =
        withSpacePage(ufo) {
            change { open(SpacePanel.GROUPS) }
            tapStartingWith("round", index = 0)
            val before = kerningNow().groups
            for ((typed, reason) in listOf(
                "zzz" to "This font has no glyph called zzz.",
                "n o" to "one word with no space in it",
                "n\u0007" to "cannot hold a control character",
            )) {
                typeInto(0, typed)
                tapExactly("Add")
                assertShown(reason, "adding '${typed.replace("\u0007", "<BEL>")}'")
                assertEquals(typed, fieldValues()[0], "what was typed stays, so it can be corrected")
                assertEquals(before, kerningNow().groups, "nothing was written")
            }
            assertEquals(0, session.history.value.undoLabels.size)
        }

    @Test
    fun aNewGroupMadeWithTheSelectedLetterNamesTheGroupItLeft() =
        withSpacePage(ufo) {
            change { open(SpacePanel.GROUPS) }
            typeInto(0, "x")
            tapStartingWith("Make it with n")
            assertEquals(listOf("n"), kerningNow().groups["public.kern1.x"])
            assertShown("n left straight", "the new group")
        }

    @Test
    fun theNoteGoesWhenTheViewIsLeft() =
        withSpacePage(ufo) {
            change { open(SpacePanel.GROUPS) }
            tapStartingWith("round", index = 0)
            typeInto(0, "n")
            tapExactly("Add")
            assertShown("n left straight", "the move")
            change { closePanel() }
            assertNull(state.notice, "a note about the groups is not carried back to the string")
            assertNull(visibleText("n left straight"))
        }

    @Test
    fun groupsThatShareAGlyphAreFlaggedWithTheGroupTheBuildIgnores() {
        val clashing =
            UfoKerning(
                groups = mapOf("public.kern1.a" to listOf("n"), "public.kern1.b" to listOf("n", "H"), "public.kern2.a" to listOf("o")),
            )
        withSpacePage(SpaceTestProjects.hyleDeco(clashing)) {
            change { open(SpacePanel.GROUPS) }
            assertShown("◐ warn · b shares n with a, so the build ignores the whole of b.", "the group the build ignores")
            assertNull(visibleText("warn · a shares"), "the group that is kept is not accused")
        }
        withSpacePage(ufo) {
            change { open(SpacePanel.GROUPS) }
            assertNull(visibleText("warn ·"), "a font that follows the rule is told nothing")
        }
    }

    // ---- Control strings --------------------------------------------------------------------------------

    @Test
    fun aControlStringThatIsAlreadyThereIsRefusedInTheView() =
        withSpacePage(ufo) {
            change { open(SpacePanel.STRINGS) }
            typeInto(0, "nnonnonon")
            tapExactly("Add")
            assertShown("is in the list already", "the duplicate string")
            assertEquals("nnonnonon", fieldValues()[0])
        }

    @Test
    fun takingAwayOneOfYourOwnStringsIsConfirmedOnScreen() =
        withSpacePage(ufo) {
            change { open(SpacePanel.STRINGS) }
            typeInto(0, "HOHOOHH")
            tapExactly("Add")
            assertEquals(listOf("HOHOOHH"), session.state.value.meta.manifest.preferences.spaceControlStrings)
            tapExactly("Remove")
            assertEquals(emptyList(), session.state.value.meta.manifest.preferences.spaceControlStrings)
        }

    // ---- .fea -------------------------------------------------------------------------------------------

    @Test
    fun aSaveTheProjectRefusesKeepsEveryCharacterTypedAndSaysWhy() {
        val ufo = ufo
        withSpacePage(ufo, workspace = readOnlyWorkspaceWith(ufo)) {
            change { open(SpacePanel.FEATURES) }
            val typed = "feature calt {\n    sub n o by H;\n} calt;\n"
            typeInto(0, typed)
            assertEquals(typed, state.featuresDraft)
            tapExactly("Save")
            assertShown("open elsewhere", "the refused save")
            assertEquals(typed, state.featuresDraft, "the draft was not thrown away with the refusal")
            assertEquals(typed, fieldValues()[0], "and the editor still shows it")
            assertTrue(state.featuresDirty)
            assertNull(
                session.state.value.font
                    .master(master)!!
                    .ufo.kerningInfo.features,
                "the project holds nothing new",
            )
        }
    }

    @Test
    fun aSaveTheProjectTakesPutsTheDraftAwayAndTheProjectHoldsIt() =
        withSpacePage(ufo) {
            change { open(SpacePanel.FEATURES) }
            val typed = "feature kern {\n    pos n o -12;\n} kern;\n"
            typeInto(0, typed)
            assertTrue(state.featuresDirty)
            tapExactly("Save")
            assertEquals(
                typed,
                session.state.value.font
                    .master(master)!!
                    .ufo.kerningInfo.features,
            )
            assertNull(state.featuresDraft)
            assertFalse(state.featuresDirty)
            assertEquals(1, session.history.value.undoLabels.size)
        }

    // ---- Before ----------------------------------------------------------------------------------------

    @Test
    fun aSubViewOpenedWhileBeforeIsShownSaysItCannotBeChangedAndWhy() =
        withSpacePage(ufo) {
            change { showMode(SpaceMode.KERNING) }
            change { showAfter(false) }
            for (panel in SpacePanel.entries) {
                change { open(panel) }
                assertShown("Before is a look back, so nothing here can be changed", "the ${panel.name} view under Before")
            }
            // And it is the page's own words that go: with After chosen the view is editable and says nothing of the kind.
            change { showAfter(true) }
            assertNull(visibleText("Before is a look back, so nothing here can be changed"))
        }

    private companion object {
        const val DECK_SLACK = 4.0
    }
}
