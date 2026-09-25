// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.campaign

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The real, checked-in `workbook-latin.yaml` ([WorkbookLatinContent]), parsed for real and
 * checked against handoff M5's own numbered list -- exactly the shape a UI layer (out of this
 * task's own scope) would need, and the same shape [WorkbookGatesTest]/`HyleDecoTask4GateTest`
 * feed to [WorkbookGates].
 *
 * Task 1's and task 9's [Demonstration.SceneDemonstration] `sceneId`s are cross-checked against
 * `learn:scenes`' own real, loadable scenes in `WorkbookSceneDemonstrationsJvmTest` (`jvmTest`),
 * not here: `learn:scenes`' `readSceneResourceText` wasmJs actual is Node-`fs`-based, resolved
 * from *its own* compiled module's `import.meta.url` -- calling it from a different module's own
 * compiled wasmJs test bundle (this module's) throws for an environment/bundling reason that has
 * nothing to do with the content under test, exactly the situation `ui`'s own
 * `LineagesQuizItemsTest` already documents and works around the identical way
 * (`docs/OPEN_QUESTIONS.md` item 47).
 */
class WorkbookLatinContentTest {
    private val tasks = WorkbookLatinContent.load()

    @Test
    fun parsesExactlyTwelveTasksNumberedOneToTwelveInOrder() {
        assertEquals(12, tasks.size)
        assertEquals((1..12).toList(), tasks.map { it.index })
    }

    @Test
    fun everyTaskCarriesTheWholeWorkbooksHonestScaffoldMarker() {
        // "M5. Campaign, the workbook [SCAFFOLD, awaiting the Domestika lessons]" -- every task
        // says so, per WorkbookTask's own KDoc, not dropped silently.
        assertTrue(tasks.all { it.scaffold }, "every task should carry scaffold=true until the Domestika lessons land")
    }

    @Test
    fun titlesMatchHandoffM5sOwnNumberedList() {
        val expected =
            listOf(
                "Choose your reference",
                "Read it",
                "Set metrics",
                "Control characters",
                "Derive the family",
                "The hard letters",
                "Spacing",
                "Kerning",
                "Diacritics and anchors",
                "Extend a script (optional)",
                "Test",
                "Ship",
            )
        assertEquals(expected, tasks.map { it.title })
    }

    @Test
    fun everyWhyTaskAndReflectionIsRealNonBlankProse() {
        for (task in tasks) {
            assertTrue(task.why.length > 40, "task ${task.index}: why looks too short to be real prose")
            assertTrue(task.task.length > 10, "task ${task.index}: task looks too short to be real instructions")
            assertTrue(task.reflection.length > 10, "task ${task.index}: reflection looks too short to be a real prompt")
        }
    }

    @Test
    fun task4sDemonstrationMatchesTheExplorersOwnMockupShape() {
        val task4 = tasks.single { it.index == 4 }
        val demo = task4.demonstration as Demonstration.StatDemonstration
        assertEquals("noHO", demo.label)
        assertTrue(demo.stats.any { it.contains("stem 44") })
        assertTrue(demo.stats.any { it.contains("overshoot 0") })
        assertEquals("nnonnonon", task4.controlString)
    }

    @Test
    fun task1AndTask9UseASceneDemonstrationWithTheirExpectedSceneId() {
        // Whether these ids are real, loadable learn:scenes scenes is checked for real in
        // WorkbookSceneDemonstrationsJvmTest (see this class's own KDoc for why jvm-only).
        val task1 = tasks.single { it.index == 1 }
        assertEquals(Demonstration.SceneDemonstration("lineages.transitional"), task1.demonstration)

        val task9 = tasks.single { it.index == 9 }
        assertEquals(Demonstration.SceneDemonstration("craft.c1-baked-composites"), task9.demonstration)
    }

    @Test
    fun exactlyTheFiveTasksWithNoQaFunctionAreHonestlyNotImplemented() {
        val notImplementedIndices = tasks.filterNot { it.gate.isImplemented }.map { it.index }.sorted()
        assertEquals(listOf(1, 2, 7, 8, 10), notImplementedIndices)
        for (task in tasks.filterNot { it.gate.isImplemented }) {
            assertTrue(task.gate.notImplementedReason!!.isNotBlank())
        }
    }

    @Test
    fun theSevenImplementedGatesNameARealQaFunction() {
        val implemented = tasks.filter { it.gate.isImplemented }
        assertEquals(listOf(3, 4, 5, 6, 9, 11, 12), implemented.map { it.index })
        for (task in implemented) {
            assertTrue(
                task.gate.qaFunction!!.startsWith("dev.aarso.typewright.qa"),
                "task ${task.index}'s qaFunction should name a real qa package function",
            )
        }
    }

    @Test
    fun task4sGateNamesTheControlCharactersInOrder() {
        val task4 = tasks.single { it.index == 4 }
        assertEquals(listOf("n", "o", "H", "O"), task4.gate.glyphs)
        assertEquals(WorkbookGates.TASK4_DEFAULT_GLYPHS, task4.gate.glyphs)
    }

    @Test
    fun task5And6sGateGlyphListsMatchWorkbookGatesOwnDefaults() {
        val task5 = tasks.single { it.index == 5 }
        val task6 = tasks.single { it.index == 6 }
        assertEquals(WorkbookGates.TASK5_DEFAULT_GLYPHS, task5.gate.glyphs)
        assertEquals(WorkbookGates.TASK6_DEFAULT_GLYPHS, task6.gate.glyphs)
    }
}
