// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Sidebearings
import com.asoc.typewright.core.geometry.sidebearings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What Draw leans on in the project layer: the smooth flag as a real, undoable, persisted, lockable
 * edit; the gesture's `isOpen` as the one honest answer to "may I still update this?"; and one
 * definition of sidebearing shared by the command and every readout.
 */
class DrawSupportTest {
    private val ref = GlyphRef("regular", "A")

    private fun on(
        x: Int,
        y: Int,
        smooth: Boolean = false,
    ) = ContourPoint(Point(x, y), onCurve = true, smooth = smooth)

    private fun off(
        x: Int,
        y: Int,
    ) = ContourPoint(Point(x, y), onCurve = false)

    /** Two curved sides: a lens, 100 wide, whose two nodes are corners until told otherwise. */
    private fun lens(smooth: Boolean) =
        Contour(
            listOf(on(0, 0, smooth), off(0, 60), off(100, 60), on(100, 0, smooth), off(100, -60), off(0, -60)),
            CurveFormat.CUBIC,
        )

    private fun spec(glyph: Glyph) =
        NewProjectSpec(
            name = "Draw Fixture",
            brief = Fixtures.manifest().brief,
            scripts = listOf("Latn"),
            masters =
                listOf(
                    NewMaster(
                        id = "regular",
                        styleName = "Regular",
                        ufo = Fixtures.ufoProject(listOf("B")).let { it.copy(glyphs = it.glyphs + glyph) },
                    ),
                ),
        )

    private suspend fun newSession(
        env: SessionEnvironment,
        store: ProjectStore = InMemoryProjectStore("Draw Fixture"),
        glyph: Glyph = Glyph("A", 100, listOf(lens(smooth = false))),
    ): ProjectSession = (ProjectSession.create(store, spec(glyph), env) as CreateResult.Created).session

    private fun ProjectSession.glyphA(): Glyph = state.value.font.glyph(ref)!!

    @Test
    fun aSmoothFlagSetThroughTheSessionIsUndoableAndSurvivesASaveAndAReopen() =
        runTest {
            val store = InMemoryProjectStore("Draw Fixture")
            val env = Fixtures.env(backgroundScope)
            val session = newSession(env, store)

            val toggled =
                session.glyphA().contours.single().let { c ->
                    c.copy(
                        points =
                            c.points.mapIndexed { i, p ->
                                if (i ==
                                    0
                                ) {
                                    p.copy(smooth = true)
                                } else {
                                    p
                                }
                            },
                    )
                }
            assertIs<EditResult.Applied>(session.execute(ReplaceOutline(ref, listOf(toggled), "Make smooth")))
            assertTrue(
                session
                    .glyphA()
                    .contours
                    .single()
                    .points[0]
                    .smooth,
            )

            assertTrue(session.undo())
            assertFalse(
                session
                    .glyphA()
                    .contours
                    .single()
                    .points[0]
                    .smooth,
            )
            assertTrue(session.redo())
            session.flush()
            session.close()

            val reopened = (ProjectSession.open(store, Fixtures.env(backgroundScope)) as OpenResult.Opened).session
            val points =
                reopened
                    .glyphA()
                    .contours
                    .single()
                    .points
            assertTrue(points[0].smooth, "the flag is in the .glif and was read back")
            assertFalse(points[3].smooth)
            val glif = store.read("DrawFixture-Regular.ufo/glyphs/A_.glif")?.decodeToString().orEmpty()
            assertTrue(glif.contains("smooth=\"yes\""), glif)
        }

    @Test
    fun aLockedGlyphRefusesAChangeThatOnlyFlipsASmoothFlag() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            session.execute(Approve(ref, ApprovalOrigin.DRAW))
            val smoothed = lens(smooth = true)

            val result = session.execute(ReplaceOutline(ref, listOf(smoothed), "Make smooth"))

            assertEquals(EditResult.Refused(Refusal.Locked(ref)), result)
            assertFalse(
                session
                    .glyphA()
                    .contours
                    .single()
                    .points[0]
                    .smooth,
            )
        }

    @Test
    fun anUnlockedEditThatFlipsSmoothIsKeptInTheUnlockDiff() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            session.execute(Approve(ref, ApprovalOrigin.DRAW))
            session.execute(Unlock(ref))
            session.execute(ReplaceOutline(ref, listOf(lens(smooth = true)), "Make smooth"))
            session.execute(Approve(ref, ApprovalOrigin.DRAW))

            val episode =
                session.state.value.font.locks
                    .getValue(ref)
                    .episodes
                    .single()
            assertTrue(episode.frozenDiff.orEmpty().contains("smooth=\"yes\""), episode.frozenDiff)
        }

    @Test
    fun aGestureIsOpenUntilItIsCommittedOrCancelled() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))
            val committed = session.beginGesture("Move")
            assertTrue(committed.isOpen)
            committed.update(MovePoints(ref, setOf(PointRef(0, 0)), 5, 0))
            committed.commit()
            assertFalse(committed.isOpen)

            val cancelled = session.beginGesture("Move")
            assertTrue(cancelled.isOpen)
            cancelled.cancel()
            assertFalse(cancelled.isOpen)
        }

    @Test
    fun aGestureThatTheSessionEndedReportsItselfClosedAndRefusesAnUpdate() =
        runTest {
            val session = newSession(Fixtures.env(backgroundScope))

            val byExecute = session.beginGesture("Move")
            byExecute.update(MovePoints(ref, setOf(PointRef(0, 0)), 5, 0))
            session.execute(SetAdvance(ref, 120))
            assertFalse(byExecute.isOpen, "execute commits the open gesture first")
            assertFailsWith<IllegalStateException> { byExecute.update(MovePoints(ref, setOf(PointRef(0, 0)), 1, 0)) }

            val byUndo = session.beginGesture("Move")
            byUndo.update(MovePoints(ref, setOf(PointRef(0, 0)), 5, 0))
            session.undo()
            assertFalse(byUndo.isOpen, "undo during a drag cancels it")

            val first = session.beginGesture("Move")
            val second = session.beginGesture("Move")
            assertFalse(first.isOpen, "a new gesture commits the one before it")
            assertTrue(second.isOpen)
            second.cancel()
        }

    @Test
    fun theSidebearingsTheCommandSetsAreTheSidebearingsAGlyphReports() =
        runTest {
            // The right curve's control points sit at x = 400 although the curve stops at about 337: the old control-point
            // measure and the ink measure disagree here, and the readout must agree with the command.
            val bowl =
                Glyph(
                    "A",
                    500,
                    listOf(
                        Contour(
                            listOf(on(150, 0), off(-100, 0), off(-100, 400), on(150, 400), off(400, 400), off(400, 0)),
                            CurveFormat.CUBIC,
                        ),
                    ),
                )
            val session = newSession(Fixtures.env(backgroundScope), glyph = bowl)
            assertEquals(Sidebearings(-37, 162), session.glyphA().sidebearings())

            session.execute(SetSidebearings(ref, left = 47, right = 48))

            assertEquals(Sidebearings(47, 48), session.glyphA().sidebearings())
        }
}
