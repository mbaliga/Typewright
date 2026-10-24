// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import com.asoc.typewright.compile.CompileRequest
import com.asoc.typewright.compile.CompileResult
import com.asoc.typewright.compile.CompileSource
import com.asoc.typewright.compile.platformCompileBackend
import com.asoc.typewright.qa.FontbakeryCliChecker
import com.asoc.typewright.qa.LayerOneInput
import com.asoc.typewright.qa.platformLayerOneChecker
import com.asoc.typewright.ui.project.SampleProject
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The Check page against the real compiler and the real Fontbakery, in the Python that `TYPEWRIGHT_PYTHON` names. These tests never
 * skip: without that variable they fail and say which pip command installs what is missing, because a run that never happened proves
 * nothing. They are their own Gradle task, `:ui:pythonIntegrationTest`, so a machine without the tools (the Android and core CI jobs)
 * keeps a green `:ui:desktopTest`; CI's golden-path job, which installs them, runs this task.
 */
class CheckControllerPythonIntegrationTest {
    private fun controller() = CheckController(CheckTestProject.scope(), platformCompileBackend(), platformLayerOneChecker(), CheckMemory())

    private fun tempBuildDirectories(): Set<String> =
        File(System.getProperty("java.io.tmpdir")).list { _, name -> name.startsWith("typewright-build-") }?.toSet().orEmpty()

    /**
     * The compilers this JVM started that are still running: `python -m fontmake`, not the probes that merely mention fontmake
     * in the script they run to see whether it is installed.
     */
    private fun fontmakeProcesses(): List<ProcessHandle> =
        ProcessHandle
            .current()
            .descendants()
            .filter {
                it.isAlive &&
                    it
                        .info()
                        .arguments()
                        .orElse(emptyArray())
                        .toList()
                        .zipWithNext()
                        .any { pair -> pair == "-m" to "fontmake" }
            }.toList()

    @Test
    fun theSampleIsCompiledAndCheckedForRealAndLayerOneIsNotASkip() {
        RealTools.python()
        runBlocking {
            val c = controller()
            c.open(CheckTestProject.subject)
            val done =
                c.await(seconds = 300) {
                    it.layerOne is LayerOneState.Done || it.layerOne is LayerOneState.Failed ||
                        it.layerOne is LayerOneState.Unavailable
                }
            val layerOne = assertIs<LayerOneState.Done>(done.layerOne, "layer one ended as ${done.layerOne}")
            assertTrue(
                layerOne.report.results.size > 50,
                "a real googlefonts run has hundreds of checks, not ${layerOne.report.results.size}",
            )
            assertTrue(layerOne.report.results.none { it.isLayerOneSkip() })
            assertTrue(
                layerOne.report.tool?.tool in setOf("Fontbakery", "Fontspector"),
                "the report names its tool: ${layerOne.report.tool}",
            )
            // The page's runtime line names the real tool and version.
            assertTrue(CheckText.runtimeLine(done).startsWith("Layer one: ${layerOne.report.tool?.tool} "), CheckText.runtimeLine(done))
            // The traced font fails Google Fonts' own profile somewhere, and layer two fails it too.
            assertTrue(layerOne.report.counts.fail > 0, "Hyle Deco, as a traced font, should fail some layer-one check")
            assertTrue(checkNotNull(done.report).counts.fail > 0)
        }
    }

    @Test
    fun cancellingARealRunStopsItAndLeavesNoBuildDirectoryBehind() {
        RealTools.python()
        runBlocking {
            val before = tempBuildDirectories()
            val c = controller()
            c.open(CheckTestProject.subject)
            // Wait for the signal that matters: the compiler's own process is running. Cancelling earlier would prove nothing, because
            // nothing would yet have been written; no amount of waiting on the clock can say the same.
            withTimeout(180_000) { while (fontmakeProcesses().isEmpty()) delay(20) }
            c.cancel()
            assertEquals(LayerOneState.Cancelled, c.state.value.layerOne)
            // The cancelled run has stopped and cleaned up when its job has finished; that is the signal to look at the disk.
            withTimeout(60_000) { c.settled() }
            assertEquals(emptyList(), fontmakeProcesses().map { it.pid() }, "the cancelled build left fontmake running")
            assertEquals(
                emptySet<String>(),
                tempBuildDirectories() - before,
                "a cancelled build must leave its temporary directory removed",
            )
            assertEquals(LayerOneState.Cancelled, c.state.value.layerOne, "and the page still says it was cancelled")
        }
    }

    @Test
    fun hyleDecoOpenedThroughTheImporterIsCompiledAndItsFailCountIsADirectFontbakeryRuns() {
        val python = RealTools.python()
        val bytes = RealTools.repoFile("fonts/HyleDeco-Regular.ttf").readBytes()
        // The font is opened the way a person's is, through the importer, and the project's own UFO is what is compiled.
        val workspace = SampleProject.workspaceFor("HyleDeco-Regular.ttf", bytes)
        val subject = checkNotNull(checkSubjectOf(SampleProject.openProject(workspace)))
        val compiled =
            runBlocking { platformCompileBackend().compile(CompileRequest(subject.snapshot(), CompileSource.Ufo(subject.masterPath))) }
        val success =
            assertIs<CompileResult.Success>(compiled, "compiling Hyle Deco failed: ${(compiled as? CompileResult.Failure)?.reason}")
        val binary = success.binaries.single()

        // What the app runs: whichever tool the platform picked must give a real report, never a skip.
        val platform = runBlocking { platformLayerOneChecker().check(LayerOneInput(binary.fileName, binary.bytes)) }
        assertTrue(!platform.isUnavailable, "layer one must not be a skip: ${platform.results.first().message}")

        // What a person would get by running the tool themselves, on the same file: the FAIL count is the same.
        val ours = runBlocking { FontbakeryCliChecker().check(LayerOneInput(binary.fileName, binary.bytes)) }
        assertTrue(!ours.isUnavailable && ours.results.size > 50, "Fontbakery gave no report: ${ours.results.first().message}")
        val direct = RealTools.directFontbakeryCounts(python, binary.fileName, binary.bytes)
        val expectedFails = (direct["FAIL"] ?: 0) + (direct["ERROR"] ?: 0) + (direct["FATAL"] ?: 0)
        assertEquals(expectedFails, ours.counts.fail, "FAIL count against a direct fontbakery run $direct")
        assertTrue(expectedFails > 0, "Hyle Deco is a traced font and must fail something; the direct run said $direct")
        // INFO is its own count and is never folded into the warnings.
        assertEquals(direct["INFO"] ?: 0, ours.counts.info, "INFO count against the direct run $direct")
        assertEquals(direct["WARN"] ?: 0, ours.counts.warn, "WARN count against the direct run $direct")
    }

    private fun com.asoc.typewright.qa.CheckOutcome.isLayerOneSkip(): Boolean = id == com.asoc.typewright.qa.LAYER_ONE_UNAVAILABLE_ID
}

/** What the real-tool tests need from the machine, checked once and named when it is not there. */
internal object RealTools {
    /** The Python the tests run, or a failure that names the variable and the pip command. */
    fun python(): String {
        val python = System.getenv("TYPEWRIGHT_PYTHON")
        if (python.isNullOrBlank()) {
            fail(
                "TYPEWRIGHT_PYTHON is not set. These tests run the real fontmake and Fontbakery, so point it at a Python that has them. " +
                    "Install them with: python3 -m venv .venv && .venv/bin/pip install -r tools/requirements-golden-path.txt " +
                    "and then set TYPEWRIGHT_PYTHON=.venv/bin/python (an absolute path is safest).",
            )
        }
        return python
    }

    /** A file of the repository, found through the root the build tells the tests about. */
    fun repoFile(path: String): File {
        val root = System.getProperty("typewright.repoRoot") ?: fail("system property typewright.repoRoot is not set by the build")
        return File(root, path).also { assertTrue(it.isFile, "$path is missing from the repository") }
    }

    /** `fontbakery check-googlefonts` run directly on [bytes], as a person would, and its own tally of each result. */
    fun directFontbakeryCounts(
        python: String,
        fileName: String,
        bytes: ByteArray,
    ): Map<String, Int> {
        val work = Files.createTempDirectory("typewright-direct-fontbakery-").toFile()
        try {
            val ttf = File(work, fileName.substringAfterLast('/')).apply { writeBytes(bytes) }
            val json = File(work, "direct.json")
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
                    ttf.path,
                ).redirectErrorStream(true).start()
            process.inputStream.readBytes()
            process.waitFor()
            assertTrue(json.isFile, "a direct fontbakery run wrote no report")
            return Json
                .parseToJsonElement(json.readText())
                .jsonObject
                .getValue("result")
                .jsonObject
                .mapValues {
                    it.value.jsonPrimitive.content
                        .toInt()
                }
        } finally {
            work.deleteRecursively()
        }
    }
}
