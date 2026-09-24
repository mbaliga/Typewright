package dev.aarso.typewright.campaign

import dev.aarso.typewright.learn.scenes.yaml.YamlParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkbookYamlParserTest {
    @Test
    fun parsesATaskWithASceneDemonstrationAndAnImplementedGate() {
        val yaml =
            """
            - index: 4
              title: "Control characters"
              scaffold: true
              why: >
                n and o decide everything after them.

                A second paragraph, after a blank line.
              demonstration:
                kind: scene
                sceneId: "craft.c1-baked-composites"
              task: >
                Draw n, o, H and O.
              controlString: "nnonnonon"
              gate:
                summary: "node economy against the geometric box"
                qaFunction: "dev.aarso.typewright.qa.checkNodeEconomy"
                glyphs: [n, o, H, O]
              reflection: >
                What did you choose, and why?
            """.trimIndent()

        val tasks = parseWorkbookTasks(yaml)
        val task = tasks.single()

        assertEquals(4, task.index)
        assertEquals("Control characters", task.title)
        assertTrue(task.scaffold)
        assertEquals("n and o decide everything after them.\nA second paragraph, after a blank line.", task.why)
        assertEquals(Demonstration.SceneDemonstration("craft.c1-baked-composites"), task.demonstration)
        assertEquals("Draw n, o, H and O.", task.task)
        assertEquals("nnonnonon", task.controlString)
        assertEquals("What did you choose, and why?", task.reflection)

        val gate = task.gate
        assertEquals("node economy against the geometric box", gate.summary)
        assertEquals("dev.aarso.typewright.qa.checkNodeEconomy", gate.qaFunction)
        assertEquals(listOf("n", "o", "H", "O"), gate.glyphs)
        assertNull(gate.notImplementedReason)
        assertTrue(gate.isImplemented)
    }

    @Test
    fun parsesATaskWithAStatsDemonstrationAndANotImplementedGate() {
        val yaml =
            """
            - index: 7
              title: "Spacing"
              scaffold: true
              why: >
                Space n and o first.
              demonstration:
                kind: stats
                label: "Hyle Deco 'n'"
                stats:
                  - "LSB 46 -- RSB 51"
                  - "second stat"
              task: >
                Read nnonnonon.
              gate:
                summary: "no automated check yet"
                notImplementedReason: >
                  Spacing sanity has no implemented qa function yet.
              reflection: >
                What did you change?
            """.trimIndent()

        val task = parseWorkbookTasks(yaml).single()

        assertNull(task.controlString)
        val demo = task.demonstration as Demonstration.StatDemonstration
        assertEquals("Hyle Deco 'n'", demo.label)
        assertEquals(listOf("LSB 46 -- RSB 51", "second stat"), demo.stats)

        val gate = task.gate
        assertEquals(null, gate.qaFunction)
        assertEquals(emptyList(), gate.glyphs)
        assertEquals("Spacing sanity has no implemented qa function yet.", gate.notImplementedReason)
        assertFalse(gate.isImplemented)
    }

    @Test
    fun parsesMultipleTasksInDocumentOrder() {
        val yaml =
            """
            - index: 1
              title: "One"
              why: >
                Why one.
              demonstration:
                kind: stats
                label: "L"
                stats:
                  - "s"
              task: >
                Task one.
              gate:
                summary: "g"
                notImplementedReason: >
                  r
              reflection: >
                Reflect one.
            - index: 2
              title: "Two"
              why: >
                Why two.
              demonstration:
                kind: stats
                label: "L"
                stats:
                  - "s"
              task: >
                Task two.
              gate:
                summary: "g"
                notImplementedReason: >
                  r
              reflection: >
                Reflect two.
            """.trimIndent()

        val tasks = parseWorkbookTasks(yaml)
        assertEquals(listOf(1, 2), tasks.map { it.index })
        assertEquals(listOf("One", "Two"), tasks.map { it.title })
    }

    @Test
    fun rejectsAnUnknownDemonstrationKind() {
        val yaml =
            """
            - index: 1
              title: "One"
              why: >
                Why.
              demonstration:
                kind: video
              task: >
                Task.
              gate:
                summary: "g"
                notImplementedReason: >
                  r
              reflection: >
                Reflect.
            """.trimIndent()

        assertFailsWith<YamlParseException> { parseWorkbookTasks(yaml) }
    }

    @Test
    fun rejectsATopLevelDocumentThatIsNotASequence() {
        val yaml = "index: 1"
        assertFailsWith<YamlParseException> { parseWorkbookTasks(yaml) }
    }

    @Test
    fun rejectsAGateWithBothQaFunctionAndNotImplementedReason() {
        val yaml =
            """
            - index: 1
              title: "One"
              why: >
                Why.
              demonstration:
                kind: stats
                label: "L"
                stats:
                  - "s"
              task: >
                Task.
              gate:
                summary: "g"
                qaFunction: "dev.aarso.typewright.qa.checkAnchorsPresent"
                notImplementedReason: >
                  r
              reflection: >
                Reflect.
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> { parseWorkbookTasks(yaml) }
    }

    @Test
    fun rejectsAnIndexOutsideOneToTwelve() {
        val yaml =
            """
            - index: 13
              title: "One"
              why: >
                Why.
              demonstration:
                kind: stats
                label: "L"
                stats:
                  - "s"
              task: >
                Task.
              gate:
                summary: "g"
                notImplementedReason: >
                  r
              reflection: >
                Reflect.
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> { parseWorkbookTasks(yaml) }
    }
}
