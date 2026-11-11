// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.draw

import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.CutPlace
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.PenPath
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.core.geometry.brokenSmoothNodes
import com.asoc.typewright.core.geometry.polygonContour
import com.asoc.typewright.engine.construct.BooleanOp
import com.asoc.typewright.engine.construct.LineJoin
import com.asoc.typewright.project.AutosavePolicy
import com.asoc.typewright.project.GlyphRef
import com.asoc.typewright.project.OpenResult
import com.asoc.typewright.project.PointRef
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.SessionEnvironment
import com.asoc.typewright.ui.puck.PrimitiveDragPlan
import com.asoc.typewright.ui.puck.PrimitiveKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A seeded session of 200 random operations through every edit the Draw editor makes (moves, nudges, deletes, smooth toggles,
 * added nodes and extremes, tidy, reverse, align, split, tangent, Hobby, shapes, the pen, locks, clear, booleans, strokes, anchors
 * and the font's metric lines) over a few glyphs. Undoing
 * all of it must give back the very files the project started with, redoing all of it the final state, and saving a second session
 * and opening it again the same state. A refused edit is allowed, and leaves the history alone.
 */
class DrawFuzzTest {
    private fun start(): Array<Glyph> =
        arrayOf(
            Glyph(
                "I",
                400,
                listOf(polygonContour(listOf(Point(50, 0), Point(350, 0), Point(350, 400), Point(50, 400)), CurveFormat.CUBIC)),
            ),
            Glyph(
                "h",
                400,
                listOf(
                    polygonContour(
                        listOf(Point(0, 0), Point(60, 80), Point(150, 110), Point(240, 80), Point(300, 0), Point(150, -60)),
                        CurveFormat.CUBIC,
                    ),
                ),
            ),
            Glyph("a", 500, emptyList()),
        )

    private fun ProjectSession.glyph(name: String): Glyph = state.value.font.glyph(GlyphRef("regular", name))!!

    private fun files(session: ProjectSession): Map<String, List<Byte>> =
        runBlocking {
            session.flush()
            session.store
                .list()
                .sorted()
                .associateWith { session.store.read(it)!!.toList() }
        }

    /** One random operation on [name]; returns after making it or being refused. */
    private fun operate(
        random: Random,
        session: ProjectSession,
        name: String,
        newOnly: Boolean = false,
    ) {
        val editor = DrawTestProjects.editor(session, name)
        val glyph = session.glyph(name)
        val nodes = glyph.contours.flatMapIndexed { c, contour -> contour.points.indices.map { PointRef(c, it) } }
        val pick = if (nodes.isEmpty()) DrawSelection.EMPTY else DrawSelection(setOf(nodes[random.nextInt(nodes.size)]))
        val several =
            if (nodes.isEmpty()) {
                DrawSelection.EMPTY
            } else {
                DrawSelection(nodes.shuffled(random).take(random.nextInt(1, 5)).toSet())
            }
        val metrics = DrawTestProjects.small().let { drawMetricsFor(it.fontInfo) }
        when (if (newOnly) random.nextInt(18, 25) else random.nextInt(25)) {
            0 -> {
                editor.moveBy(several, random.nextInt(-20, 21), random.nextInt(-20, 21), merge = false)
            }

            1 -> {
                editor.deleteSelection(pick)
            }

            2 -> {
                editor.toggleSmooth(pick)
            }

            3 -> {
                editor.breakHandles(several)
            }

            4 -> {
                editor.addExtremes(DrawSelection.EMPTY)
            }

            5 -> {
                editor.tidy(DrawSelection.EMPTY)
            }

            6 -> {
                editor.reverseContours(DrawSelection.EMPTY)
            }

            7 -> {
                editor.alignToMetrics(several, metrics)
            }

            8 -> {
                editor.hobby(several)
            }

            9 -> {
                editor.tangent(several)
            }

            10 -> {
                editor.perform(editor.planTangentToMetric(several, metrics))
            }

            11 -> {
                val c = random.nextInt(glyph.contours.size.coerceAtLeast(1))
                if (glyph.contours.isNotEmpty()) {
                    val a = CutPlace.OnSegment(random.nextInt(glyph.contours[c].points.size / 3), random.nextDouble(0.1, 0.9))
                    val b = CutPlace.AtNode(3 * random.nextInt(glyph.contours[c].points.size / 3))
                    editor.cutContour(c, a, b)
                }
            }

            12 -> {
                val kind = PrimitiveKind.ON_SHEET[random.nextInt(PrimitiveKind.ON_SHEET.size)]
                val x = random.nextInt(0, 200).toDouble()
                val y = random.nextInt(0, 200).toDouble()
                PrimitiveDragPlan.plan(kind, Vec2(x, y), Vec2(x + random.nextInt(5, 200), y + random.nextInt(5, 200)))?.let {
                    editor.perform(editor.planPrimitive(it, CurveFormat.CUBIC))
                }
            }

            13 -> {
                val path =
                    PenPath()
                        .plus(
                            Point(random.nextInt(300), 0),
                        ).plus(Point(random.nextInt(300), 300))
                        .plus(Point(-random.nextInt(1, 300), 150))
                editor.perform(editor.planPen(path, null, CurveFormat.CUBIC))
            }

            14 -> {
                editor.setAdvance(random.nextInt(100, 900))
            }

            15 -> {
                if (glyph.contours.isNotEmpty() && random.nextInt(4) == 0) editor.clearGlyph() else editor.addNode(SegmentKey(0, 0), 0.5)
            }

            16 -> {
                if (editor.isLocked) {
                    editor.unlock()
                } else if (random.nextInt(3) == 0) {
                    editor.approve()
                }
            }

            17 -> {
                editor.correctDirection(DrawSelection.EMPTY)
            }

            18, 19 -> {
                val op = BooleanOp.entries[random.nextInt(BooleanOp.entries.size)]
                val picked =
                    glyph.contours.indices
                        .shuffled(random)
                        .take(random.nextInt(2, 4))
                val selection =
                    DrawSelection(
                        picked
                            .flatMap { c ->
                                glyph.contours[c]
                                    .points.indices
                                    .map { PointRef(c, it) }
                            }.toSet(),
                    )
                editor.boolean(op, selection, order = picked)
            }

            20 -> {
                val line = List(random.nextInt(2, 5)) { Point(random.nextInt(-100, 500), random.nextInt(-100, 500)) }
                val join = if (random.nextBoolean()) LineJoin.MITER else LineJoin.ROUND
                val plan = editor.planStroke(line, DrawSelection.EMPTY, random.nextInt(4, 90).toDouble(), join, CurveFormat.CUBIC)
                editor.perform(plan)
            }

            21 -> {
                val c = random.nextInt(glyph.contours.size.coerceAtLeast(1))
                val plan =
                    editor.planStroke(
                        emptyList(),
                        DrawSelection(setOf(PointRef(c, 0))),
                        random.nextInt(4, 60).toDouble(),
                        LineJoin.MITER,
                        CurveFormat.CUBIC,
                    )
                editor.perform(plan)
            }

            22, 23 -> {
                val names = glyph.anchors.map { it.name }
                val there = if (names.isEmpty()) null else names[random.nextInt(names.size)]
                val place = Point(random.nextInt(-50, 450), random.nextInt(-50, 450))
                when {
                    there == null || random.nextInt(3) == 0 -> editor.addAnchor("a${random.nextInt(6)}", place)
                    random.nextInt(3) == 0 -> editor.renameAnchor(there, "r${random.nextInt(6)}")
                    random.nextInt(3) == 0 -> editor.deleteAnchor(there)
                    else -> editor.moveAnchor(there, place)
                }
            }

            else -> {
                val kind = listOf(MetricKind.ASCENDER, MetricKind.CAP_HEIGHT, MetricKind.X_HEIGHT, MetricKind.DESCENDER)[random.nextInt(4)]
                val value =
                    when (kind) {
                        MetricKind.ASCENDER -> random.nextInt(600, 900)
                        MetricKind.CAP_HEIGHT -> random.nextInt(500, 800)
                        MetricKind.X_HEIGHT -> random.nextInt(300, 600)
                        else -> -random.nextInt(100, 400)
                    }
                editor.setMetric(kind, value)
            }
        }
    }

    private fun run(
        seed: Int,
        session: ProjectSession,
        newOnly: Boolean = false,
    ) {
        val random = Random(seed)
        repeat(200) { operate(random, session, listOf("I", "h", "a")[random.nextInt(3)], newOnly) }
    }

    @Test
    fun twoHundredRandomEditsUndoToTheStartingFilesAndRedoToTheFinalState() {
        val session = DrawTestProjects.session(DrawTestProjects.small(*start()), coalesceWindowMillis = 0)
        val initial = files(session)
        val glyphsBefore = start().map { it.name to it }
        run(seed = 20260411, session = session)
        val steps = session.history.value.undoLabels.size
        assertTrue(steps > 40, "the session really edited: $steps undo steps")
        val final = files(session)
        val finalGlyphs = glyphsBefore.map { session.glyph(it.first) }
        for (g in finalGlyphs) assertTrue(g.contours.all { it.brokenSmoothNodes().isEmpty() }, "${g.name}: a smooth flag lies")
        while (session.undo()) Unit
        assertEquals(initial, files(session), "undoing every step gives back byte-identical files")
        while (session.redo()) Unit
        assertEquals(final, files(session), "redoing every step gives back the final files")
        assertEquals(finalGlyphs, glyphsBefore.map { session.glyph(it.first) })
    }

    @Test
    fun booleansStrokesAnchorsAndMetricsAloneAlsoUndoToByteIdenticalFilesAndRedoToTheFinalOnes() {
        val session = DrawTestProjects.session(DrawTestProjects.small(*start()), coalesceWindowMillis = 0)
        val initial = files(session)
        run(seed = 20261104, session = session, newOnly = true)
        val steps = session.history.value.undoLabels
        assertTrue(steps.size > 40, "the session really edited: ${steps.size} undo steps")
        for (word in listOf("anchor", "Set", "Stroke")) {
            assertTrue(steps.any { it.contains(word) }, "no step made by $word among ${steps.take(12)}")
        }
        assertTrue(steps.any { it in listOf("Union", "Subtract", "Intersect", "Exclude") }, "no boolean among ${steps.take(12)}")
        val final = files(session)
        assertTrue(final != initial)
        while (session.undo()) Unit
        assertEquals(initial, files(session), "undoing every step gives back byte-identical files")
        while (session.redo()) Unit
        assertEquals(final, files(session), "redoing every step gives back the final files")
    }

    @Test
    fun aSecondSessionSavedAndReopenedGivesTheSameState() {
        val session = DrawTestProjects.session(DrawTestProjects.small(*start()), coalesceWindowMillis = 0)
        run(seed = 7, session = session)
        val names = listOf("I", "h", "a")
        val expected = names.map { session.glyph(it) }
        val expectedInfo =
            session.state.value.font
                .master("regular")!!
                .ufo.fontInfo
        runBlocking { session.flush() }
        val store = session.store
        runBlocking { session.close() }
        val env =
            SessionEnvironment(
                scope = CoroutineScope(Dispatchers.Unconfined),
                io = Dispatchers.Unconfined,
                autosave = AutosavePolicy(enabled = false),
            )
        val reopened = (runBlocking { ProjectSession.open(store, env) } as OpenResult.Opened).session
        assertEquals(expected, names.map { reopened.glyph(it) })
        assertEquals(
            expectedInfo,
            reopened.state.value.font
                .master("regular")!!
                .ufo.fontInfo,
        )
    }
}
