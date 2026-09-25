// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * `:golden-path:goldenPath` (PROMPTS_V1.md P10 step 6): reads `GoldenPathTest`'s JUnit XML from
 * [testResults], prints the pass count and one row per golden-path step (never failing on the
 * step results themselves -- CLAUDE.md's standing rule "every prompt ends by running the
 * golden-path test", not by making it red), and writes the same summary to [summaryFile] so CI can
 * append it to the job summary. Only fails the build if [testResults] holds no `GoldenPathTest`
 * results at all -- a broken harness, not a failing step, and worth stopping the build for.
 */
abstract class GoldenPathReportTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testResults: DirectoryProperty

    @get:InputFile
    abstract val passingSteps: RegularFileProperty

    @get:OutputFile
    abstract val summaryFile: RegularFileProperty

    @TaskAction
    fun report() {
        val results = parseGoldenPathResults(testResults.get().asFile)
        if (results.isEmpty()) {
            throw GradleException("no GoldenPathTest results")
        }
        // passingSteps is not read here (goldenPath prints every step's real result regardless of
        // the ratchet list); it is still a declared input so this task reruns when the ratchet
        // list changes the meaning of "which steps may not regress" for whoever reads the summary.
        val summary = buildGoldenPathSummary(results)
        val output = summaryFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(summary)
        logger.lifecycle(summary)
    }
}

/**
 * `:golden-path:goldenPathRatchet` (PROMPTS_V1.md P10 step 6): fails the build when
 * `golden-path/passing-steps.txt`'s own rule is broken -- see its header comment and
 * `golden-path/README.md` for the rule in full; this task is what actually enforces it:
 * - a listed id does not [StepStatus.PASS];
 * - an id [StepStatus.PASS]es but is not listed (so a regression on it would go unnoticed);
 * - the file names an id [GOLDEN_PATH_ALL_IDS] does not recognise;
 * - any of [GOLDEN_PATH_STEP_IDS] has no testcase at all in the results;
 * - any testcase is [StepStatus.SKIPPED] (a golden-path step is never `@Ignore`d -- CLAUDE.md's
 *   own design for this harness).
 */
abstract class GoldenPathRatchetTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testResults: DirectoryProperty

    @get:InputFile
    abstract val passingSteps: RegularFileProperty

    @TaskAction
    fun ratchet() {
        val results = parseGoldenPathResults(testResults.get().asFile)
        if (results.isEmpty()) {
            throw GradleException("no GoldenPathTest results")
        }
        val byId = results.associateBy { it.id }
        val listedIds = readPassingSteps(passingSteps.get().asFile)
        val problems = mutableListOf<String>()

        for (id in listedIds) {
            if (id !in GOLDEN_PATH_ALL_IDS) {
                problems += "golden-path/passing-steps.txt lists unknown id '$id'"
            }
        }

        for (id in GOLDEN_PATH_STEP_IDS) {
            if (byId[id] == null) {
                problems += "step $id has no testcase in the GoldenPathTest results"
            }
        }

        for (result in results) {
            if (result.status == StepStatus.SKIPPED) {
                problems += "step ${result.id} is SKIPPED: ${result.message}"
            }
        }

        for (id in listedIds) {
            if (id !in GOLDEN_PATH_ALL_IDS) continue // already reported above
            val result = byId[id]
            when {
                result == null -> {
                    problems += "golden-path/passing-steps.txt lists '$id', which has no testcase"
                }

                result.status != StepStatus.PASS -> {
                    problems += "golden-path/passing-steps.txt lists '$id', but it is ${result.status}: ${result.message}"
                }
            }
        }

        for (result in results) {
            if (result.status == StepStatus.PASS && result.id !in listedIds) {
                problems += "step ${result.id} passes but is not in golden-path/passing-steps.txt: add it in this PR, so it can't regress"
            }
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "golden-path ratchet failed (golden-path/passing-steps.txt):\n" +
                    problems.joinToString("\n") { "  - $it" },
            )
        }
        logger.lifecycle("golden-path ratchet OK: ${listedIds.size} step id(s) locked in golden-path/passing-steps.txt")
    }
}
