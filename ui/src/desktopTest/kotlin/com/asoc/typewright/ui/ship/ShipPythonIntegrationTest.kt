// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.ship

import com.asoc.typewright.compile.CompileLocation
import com.asoc.typewright.compile.platformCompileBackend
import com.asoc.typewright.project.FolderTreeWriter
import com.asoc.typewright.qa.platformLayerOneChecker
import com.asoc.typewright.qa.report.CheckReport
import com.asoc.typewright.qa.report.Severity
import com.asoc.typewright.qa.report.buildCheckReport
import com.asoc.typewright.qa.ship.GfCategory
import com.asoc.typewright.qa.ship.ShipCapability
import com.asoc.typewright.qa.ship.ShipItemId
import com.asoc.typewright.qa.ship.ShipMark
import com.asoc.typewright.qa.ship.ShipMode
import com.asoc.typewright.ui.check.RealTools
import com.asoc.typewright.ui.project.SampleProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

/**
 * Ship against the real compiler and the real Fontbakery, on the real Hyle Deco, in the Python that `TYPEWRIGHT_PYTHON` names. These tests
 * never skip: without that variable they fail and say which pip command installs what is missing. They are their own Gradle task,
 * `:ui:pythonIntegrationTest`.
 *
 * Hyle Deco is a traced font and Check fails it, so in the app Ship is blocked for it and this test could not write its folder. The report
 * Ship is given here is therefore its real layer-two report with the fails flipped to passes: a fixture, in a test, and the app never does
 * it. Everything after that is real: the project's own UFO, fontmake, Google Fonts' profile on the compiled binary, the folder writer, and
 * then Fontbakery's profile again on the folder as Google Fonts' own tree would hold it.
 */
class ShipPythonIntegrationTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * What the folder of the real Hyle Deco still fails in Google Fonts' profile for facts only its owner has: the check's id, and the line of
     * Ship's checklist that must say so (not done, with a link to the value), so that none of them is a surprise on the page.
     */
    private val ownersFacts: Map<String, ShipItemId> = mapOf("com.google.fonts/check/metadata/empty_designer" to ShipItemId.DESIGNER)

    @Test
    fun theRealHyleDecoFolderIsWrittenAndFontbakeryFindsNoFailThatIsOurs() {
        RealTools.python()
        val bytes = RealTools.repoFile("fonts/HyleDeco-Regular.ttf").readBytes()
        val workspace = SampleProject.workspaceFor("HyleDeco-Regular.ttf", bytes, declaredStyleClass = "sans-geometric")
        val project = SampleProject.openProject(workspace)
        val subject = checkNotNull(shipSubjectOf(project))
        val real = buildCheckReport(subject.check.inputs)
        assertTrue(real.counts.fail > 0, "Hyle Deco is a traced font and Check fails it, or this fixture is not needed")
        val report: CheckReport =
            real.copy(
                items =
                    real.items.map {
                        if (it.severity ==
                            Severity.FAIL
                        ) {
                            it.copy(severity = Severity.PASS)
                        } else {
                            it
                        }
                    },
            )

        val parent = Files.createTempDirectory("typewright-ship-real-").toFile()
        val target = File(parent, "hyle-deco-gf")
        try {
            val controller = ShipController(scope, platformCompileBackend(), platformLayerOneChecker(), ShipMemory())
            val request =
                ShipRequest(
                    subject,
                    ShipMode.GOOGLE_FONTS,
                    GfCategory.DISPLAY,
                    ShipTarget(target.path, FolderTreeWriter(target.toPath())),
                    report,
                    // The two facts the project cannot know, stated as a person would on the checklist's Details.
                    branch = "main",
                    dateAdded = "2026-10-24",
                )
            assertIs<ShipStart.Started>(runBlocking { controller.ship(request) })
            val finished = runBlocking { withTimeout(10.minutes) { controller.state.first { it !is ShipRunState.Building } } }
            val done =
                finished as? ShipRunState.Done
                    ?: fail("Ship did not finish: $finished ${(finished as? ShipRunState.Stopped)?.blockers?.joinToString { it.id }}")

            // The binary was checked by Google Fonts' profile before anything was written, and had no fail (which is why it was written).
            assertTrue(
                done.result.checkLine
                    .orEmpty()
                    .contains(" 0 fail"),
                "the check on the binary: ${done.result.checkLine}",
            )
            val ttf = File(target, "fonts/ttf/HyleDeco-Regular.ttf")
            assertTrue(ttf.isFile, "the folder holds no fonts/ttf/HyleDeco-Regular.ttf")

            // Fontbakery on the family folder, as Google Fonts' tree would hold it.
            val tree = Files.createTempDirectory("typewright-ship-ofl-").toFile()
            try {
                val family = File(tree, "hyledeco").apply { mkdirs() }
                val lines = File(target, "upstream.yaml").readLines()
                for (line in lines.dropWhile { it.trim() != "files:" }.drop(1).takeWhile { it.startsWith(" ") }) {
                    val (from, to) = line.trim().split(":", limit = 2).map { it.trim().trim('"', '\'') }
                    File(target, from).copyTo(File(family, to))
                }
                File(target, "upstream.yaml").copyTo(File(family, "upstream.yaml"))
                val results = fontbakery(RealTools.python(), File(family, "HyleDeco-Regular.ttf"))
                assertTrue(results.size > 50, "a real googlefonts run has hundreds of checks, not ${results.size}")
                val fails = results.filter { it.status == "FAIL" || it.status == "ERROR" || it.status == "FATAL" }

                // The honest end state: nothing fails except what depends on a fact only the owner has, and each of those is on the page.
                val checklist = ShipSheet(subject, ShipMode.GOOGLE_FONTS, report, GfCategory.DISPLAY, desktop).checklistAfter(done.result)
                val ours = fails.filter { it.id !in ownersFacts }
                assertEquals(
                    emptyList(),
                    ours.map { it.toString() },
                    "Fontbakery's FAILs on the real Hyle Deco folder that are not the owner's to fix",
                )
                for (failed in fails) {
                    val line = checklist.first { it.id == ownersFacts.getValue(failed.id) }
                    assertTrue(line.mark != ShipMark.DONE && line.action != null, "${failed.id} is not on the page as an unmet line: $line")
                }
                // Printed for the record: every FAIL, and every WARN, so a reader sees what the end state is.
                println("ship-real fails: ${fails.map { it.toString() }}")
                println("ship-real warns: ${results.filter { it.status == "WARN" }.map { it.toString() }}")
            } finally {
                tree.deleteRecursively()
            }
        } finally {
            scope.cancel()
            parent.deleteRecursively()
        }
    }

    private val desktop =
        ShipPlatform(
            ShipExit.FOLDER,
            ShipCapability(true, "Compiled on this computer", "fontmake runs here when you build · nothing is sent"),
            CompileLocation.OnThisComputer("fontmake"),
        )

    private class Result(
        val id: String,
        val status: String,
        val message: String,
    ) {
        override fun toString(): String = "$status $id: ${message.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()}"
    }

    /** `fontbakery check-googlefonts` on [font], as a person would run it, and every check's result in the report's order. */
    private fun fontbakery(
        python: String,
        font: File,
    ): List<Result> {
        val work = Files.createTempDirectory("typewright-ship-fb-").toFile()
        try {
            val json = File(work, "report.json")
            val process =
                ProcessBuilder(
                    File(python).absolutePath,
                    "-m",
                    "fontbakery",
                    "check-googlefonts",
                    "-q",
                    "-n",
                    "-C",
                    "--skip-network",
                    "--json",
                    json.path,
                    font.path,
                ).redirectErrorStream(true).start()
            val output = process.inputStream.readBytes().decodeToString()
            process.waitFor()
            assertTrue(json.isFile, "fontbakery wrote no report for ${font.path}: ${output.takeLast(600)}")
            val results = mutableListOf<Result>()
            val sections = (Json.parseToJsonElement(json.readText()) as JsonObject)["sections"] as? JsonArray ?: return emptyList()
            for (section in sections) {
                for (check in (section as JsonObject)["checks"] as? JsonArray ?: continue) {
                    val entry = check as JsonObject
                    val status = (entry["result"] as? JsonPrimitive)?.content ?: continue
                    val id =
                        (entry["key"] as? JsonArray)
                            .orEmpty()
                            .mapNotNull { (it as? JsonPrimitive)?.content }
                            .firstNotNullOfOrNull { Regex("<FontBakeryCheck:(.+)>").find(it)?.groupValues?.get(1) }
                            ?: (entry["description"] as? JsonPrimitive)?.content.orEmpty()
                    val message =
                        (entry["logs"] as? JsonArray).orEmpty().joinToString("\n") { log ->
                            when (val text = (log as? JsonObject)?.get("message")) {
                                is JsonObject -> (text["message"] as? JsonPrimitive)?.content.orEmpty()
                                is JsonPrimitive -> text.content
                                else -> ""
                            }
                        }
                    results += Result(id, status, message)
                }
            }
            return results
        } finally {
            work.deleteRecursively()
        }
    }
}
