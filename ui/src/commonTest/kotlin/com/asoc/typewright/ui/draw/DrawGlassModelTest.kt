// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.ui.puck.PuckEdge
import com.asoc.typewright.ui.puck.Tool
import com.asoc.typewright.ui.sheet.SheetLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the header, the inspector, the rich menu, the shortcut sheet and the sheet's frame say, as plain data. */
class DrawGlassModelTest {
    private fun on(
        x: Int,
        y: Int,
        smooth: Boolean = false,
    ) = ContourPoint(Point(x, y), onCurve = true, smooth = smooth)

    private fun off(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = false)

    /** Two straight sides and a rounded one: three nodes, the middle one smooth, with handles of length 100 and 60. */
    private val glyph =
        Glyph(
            "o",
            500,
            listOf(
                Contour(
                    listOf(
                        on(0, 0),
                        off(0, 0),
                        off(300, 0),
                        on(300, 0, smooth = true),
                        off(400, 0),
                        off(500, 60),
                        on(500, 200),
                        off(500, 200),
                        off(0, 0),
                    ),
                    CurveFormat.CUBIC,
                ),
            ),
        )
    private val lines = GlyphLines.of(glyph)

    private fun labelled(fields: List<FieldSpec>) = fields.associate { it.label to it.value }

    // ---- the header while a radial is open -------------------------------------------------------

    @Test
    fun theHeaderNamesTheGlyphAndWhatTheRadialIsAbout() {
        assertEquals("o · node 300, 0", DrawSubject.headerLine(glyph, DrawSelection(setOf(PointRef(0, 3)))))
        assertEquals("o · handle 400, 0", DrawSubject.headerLine(glyph, DrawSelection(setOf(PointRef(0, 4)))))
        assertEquals("o · 2 points", DrawSubject.headerLine(glyph, DrawSelection(setOf(PointRef(0, 0), PointRef(0, 3)))))
        assertEquals("o · segment", DrawSubject.headerLine(glyph, DrawSelection.EMPTY.onlySegment(SegmentKey(0, 1), 0.5)))
        assertEquals(DrawSubject.glyphLine(glyph), DrawSubject.headerLine(glyph, DrawSelection.EMPTY))
    }

    @Test
    fun theGlyphLineCountsTheWayTheFileDoesSoStraightSidesAreNotOffCurvePoints() {
        // Three nodes; only the middle side is a curve, so two off-curve points, not six.
        assertEquals("o · 3 on · 2 off", DrawSubject.glyphLine(glyph))
    }

    // ---- the inspector ----------------------------------------------------------------------------

    @Test
    fun withNothingSelectedTheRowShowsTheGlyphsAdvanceSidebearingsAndCounts() {
        val fields = DrawInspectorModel.fields(glyph, DrawSelection.EMPTY, lines)
        assertEquals(listOf("adv", "lsb", "rsb", "on", "off"), fields.map { it.label })
        assertEquals("500", labelled(fields)["adv"])
        assertEquals(setOf(FieldKeys.ADVANCE, FieldKeys.LSB, FieldKeys.RSB), fields.mapNotNull { it.key }.toSet())
    }

    @Test
    fun aGlyphWithNoInkHasNoSidebearingsToEdit() {
        val empty = Glyph("space", 250, emptyList())
        val fields = DrawInspectorModel.fields(empty, DrawSelection.EMPTY, GlyphLines.of(empty))
        assertEquals("—", labelled(fields)["lsb"])
        assertNull(fields.first { it.label == "lsb" }.key, "there is no left sidebearing to type into")
    }

    @Test
    fun aNodeShowsItsPositionAndWhetherTheFileCallsItSmooth() {
        val smooth = DrawInspectorModel.fields(glyph, DrawSelection(setOf(PointRef(0, 3))), lines)
        assertEquals("300", labelled(smooth)["x"])
        assertEquals("Smooth", labelled(smooth)["node"])
        val corner = DrawInspectorModel.fields(glyph, DrawSelection(setOf(PointRef(0, 0))), lines)
        assertEquals("Corner", labelled(corner)["node"])
        assertTrue(smooth.size <= 6, "never more than six pairs")
    }

    @Test
    fun aHandleShowsItsLengthAndAngleFromItsNode() {
        val fields = DrawInspectorModel.fields(glyph, DrawSelection(setOf(PointRef(0, 4))), lines)
        assertEquals("100 · 0°", labelled(fields)["handle"])
        val retracted = DrawInspectorModel.fields(glyph, DrawSelection(setOf(PointRef(0, 1))), lines)
        assertEquals("line", labelled(retracted)["handle"], "a handle on its own node is a straight side")
    }

    @Test
    fun severalPointsAreCountedByKind() {
        val fields =
            DrawInspectorModel.fields(glyph, DrawSelection(setOf(PointRef(0, 0), PointRef(0, 3), PointRef(0, 4))), lines)
        assertEquals("2 nodes · 1 handle", labelled(fields)["selected"])
    }

    @Test
    fun aSnappedValueAndADraggedValueAreFlagged() {
        val fields = DrawInspectorModel.fields(glyph, DrawSelection(setOf(PointRef(0, 3))), lines, snapX = true, dragging = true)
        assertTrue(fields.first { it.label == "x" }.snapped)
        assertTrue(fields.first { it.label == "x" }.emphasize, "the value being dragged is the ink block")
        assertTrue(!fields.first { it.label == "y" }.snapped)
    }

    @Test
    fun theDesktopColumnGroupsTheNodeItsHandlesAndTheGlyph() {
        val groups = DrawInspectorModel.groups(glyph, DrawSelection(setOf(PointRef(0, 3))), lines)
        assertEquals(listOf("Node", "Handles", "Glyph"), groups.map { it.title })
        val handles = groups[1].fields
        assertEquals(listOf("handle out", "in"), handles.map { it.label })
        assertEquals("100 · 0°", handles[0].value)
        assertEquals(listOf("adv", "lsb", "rsb", "on", "off"), groups.last().fields.map { it.label })
        assertEquals(listOf("Glyph"), DrawInspectorModel.groups(glyph, DrawSelection.EMPTY, lines).map { it.title })
    }

    @Test
    fun aNodeWithNoHandlesOfItsOwnHasNoHandlesGroup() {
        val square =
            Glyph(
                "I",
                300,
                listOf(Contour(listOf(on(0, 0), on(100, 0), on(100, 100)), CurveFormat.QUADRATIC)),
            )
        val groups = DrawInspectorModel.groups(square, DrawSelection(setOf(PointRef(0, 1))), GlyphLines.of(square))
        assertEquals(listOf("Node", "Glyph"), groups.map { it.title })
    }

    // ---- the rich menu ----------------------------------------------------------------------------

    private fun command(
        id: String,
        scope: DrawScope,
        reason: String? = null,
        destructive: Boolean = false,
        label: String = id,
    ) = DrawCommand(id, label, scope, consequence = null, reason = reason, keys = null, gesture = null, destructive = destructive, run = {})

    private val commands =
        listOf(
            command("toggle-smooth", DrawScope.NODE, label = "Make corner"),
            command("add-node", DrawScope.SEGMENT, reason = "select a segment first"),
            command("reverse", DrawScope.CONTOUR),
            command("lock", DrawScope.GLYPH, label = "Lock as approved"),
            command("undo", DrawScope.EDIT, reason = "nothing to undo"),
            command("clear", DrawScope.GLYPH, destructive = true, label = "Clear glyph"),
        )

    @Test
    fun theMenuListsOnlyTheScopesThatApplyAndTheDestructiveRowsLast() {
        val sections = RichMenuModel.sections(commands, recent = emptyList(), query = "", hasPoints = true, hasSegment = false)
        assertEquals(listOf("This node", "This contour", "This glyph", "Edit", ""), sections.map { it.title })
        assertEquals(listOf("clear"), sections.last().commands.map { it.id })
        assertTrue(sections.none { it.title == "This segment" }, "no segment is selected")
    }

    @Test
    fun withNothingSelectedTheNodeAndSegmentSectionsAreLeftOut() {
        val sections = RichMenuModel.sections(commands, emptyList(), "", hasPoints = false, hasSegment = false)
        assertTrue(sections.none { it.title == "This node" || it.title == "This segment" })
    }

    @Test
    fun aDisabledRowStaysInItsSectionWithItsReason() {
        val sections = RichMenuModel.sections(commands, emptyList(), "", hasPoints = false, hasSegment = true)
        val segment = sections.first { it.title == "This segment" }
        assertEquals("select a segment first", segment.commands.single().reason)
    }

    @Test
    fun recentHoldsTheLastUsedCommandsThatCanStillRunAndNeverADisabledOrDestructiveOne() {
        val sections =
            RichMenuModel.sections(
                commands,
                recent = listOf("undo", "reverse", "clear", "gone"),
                query = "",
                hasPoints = true,
                hasSegment = false,
            )
        assertEquals("Recent", sections.first().title)
        assertEquals(listOf("reverse"), sections.first().commands.map { it.id })
    }

    @Test
    fun aQueryFiltersEveryCommandByNameInOneSectionAndSaysNothingWhenNoneMatch() {
        val found = RichMenuModel.sections(commands, emptyList(), "LOCK", hasPoints = false, hasSegment = false)
        assertEquals(listOf("Matches"), found.map { it.title })
        assertEquals(listOf("lock"), found.single().commands.map { it.id })
        assertTrue(RichMenuModel.sections(commands, emptyList(), "zzz", hasPoints = true, hasSegment = true).isEmpty())
    }

    // ---- the shortcut sheet ----------------------------------------------------------------------------

    @Test
    fun theShortcutSheetListsEveryToolWithItsKeyAndOnlyCommandsThatHaveAChord() {
        val chorded = commands.map { if (it.id == "reverse") it.copy(keys = "Ctrl Alt R") else it }
        val groups = ShortcutSheetModel.groups(chorded)
        val tools = groups.first { it.title == "Tools" }.lines
        assertEquals(Tool.ORDERED.size, tools.size)
        assertEquals("V", tools.first { it.what == "Select" }.keys)
        val edit = groups.first { it.title == "Edit" }.lines
        assertEquals("Ctrl Alt R", edit.first { it.what == "reverse" }.keys)
        assertTrue(edit.none { it.what == "lock" }, "a command with no chord is not a shortcut")
    }

    @Test
    fun everyToolKeyTheSheetListsDoesWhatItSays() {
        for (line in ShortcutSheetModel.groups(emptyList()).first { it.title == "Tools" }.lines) {
            val key = DrawKey.valueOf(line.keys)
            val action = DrawKeyMap.actionFor(KeyChord(key, ctrl = false, shift = false, alt = false), hasSelection = false)
            assertNotNull(action, "${line.what} (${line.keys}) has no binding")
            assertEquals(DrawKeyAction.PickTool(Tool.entries.first { it.label == line.what }), action)
        }
    }

    // ---- the sheet's frame ----------------------------------------------------------------------------

    @Test
    fun aPhoneLeavesTheGlyphTheSpaceBetweenTheHeaderAndTheInspectorRowAboveTheDeck() {
        val layout = SheetLayout.of(Vec2(360.0, 780.0), statusBarDp = 24.0, puckEdge = null)
        assertTrue(!layout.wide)
        assertEquals(24.0 + SheetLayout.PHONE_HEADER_DP, layout.free.top)
        assertEquals(172.0 * 360.0 / 390.0, layout.deckVisibleDp, 1e-9, "the deck is drawn smaller on a narrow screen")
        assertEquals(
            layout.deckVisibleDp + SheetLayout.DECK_TO_INSPECTOR_DP,
            layout.inspectorBottomDp,
            1e-9,
            "the row is 12 dp above the deck",
        )
        assertTrue(layout.free.bottom > layout.inspectorBottomDp + SheetLayout.INSPECTOR_ROW_DP, "and the glyph stays clear of the row")
    }

    @Test
    fun thePuckTakesItsSideOfTheGlyphOnAPhoneAndNothingOnADesktop() {
        assertEquals(SheetLayout.PUCK_SIDE_INSET_DP, SheetLayout.of(Vec2(360.0, 780.0), puckEdge = PuckEdge.LEFT).free.left)
        assertEquals(0.0, SheetLayout.of(Vec2(360.0, 780.0), puckEdge = PuckEdge.LEFT).free.right)
        assertEquals(SheetLayout.PUCK_SIDE_INSET_DP, SheetLayout.of(Vec2(360.0, 780.0), puckEdge = PuckEdge.RIGHT).free.right)
        val desktop = SheetLayout.of(Vec2(1280.0, 800.0), puckEdge = PuckEdge.LEFT)
        assertTrue(desktop.wide)
        assertEquals(SheetLayout.TOOL_RAIL_DP, desktop.free.left, "the rail, not the puck, is on the left")
        assertEquals(SheetLayout.INSPECTOR_COLUMN_DP, desktop.free.right)
        assertEquals(SheetLayout.TOP_BAR_DP, desktop.free.top)
    }

    @Test
    fun theDesktopFrameStartsAtNineHundredDp() {
        assertTrue(!SheetLayout.of(Vec2(899.0, 800.0)).wide)
        assertTrue(SheetLayout.of(Vec2(900.0, 800.0)).wide)
    }
}
