// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.check

import com.asoc.typewright.compile.platformCompileBackend
import com.asoc.typewright.qa.platformLayerOneChecker
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The Check page's controller against the real compiler and the real Fontbakery, on the sample font, when `TYPEWRIGHT_PYTHON`
 * names a Python that has them (`pip install -r tools/requirements-golden-path.txt`). Without that variable these tests do
 * nothing and say so: the golden path and `:compile:pythonIntegrationTest` / `:qa:pythonIntegrationTest` are what fail for a
 * missing Python; this is the controller's own check that the two real tools work through its code path.
 */
class CheckControllerRealToolsTest {
    private val python: String? = System.getenv("TYPEWRIGHT_PYTHON")?.takeIf { it.isNotBlank() }

    private fun controller() = CheckController(CheckTestProject.scope(), platformCompileBackend(), platformLayerOneChecker(), CheckMemory())

    private fun tempBuildDirectories(): Set<String> =
        File(System.getProperty("java.io.tmpdir")).list { _, name -> name.startsWith("typewright-build-") }?.toSet().orEmpty()

    @Test
    fun theSampleIsCompiledAndCheckedForRealAndLayerOneIsNotASkip() {
        if (python == null) return println("not run: TYPEWRIGHT_PYTHON is not set")
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
            assertTrue(layerOne.report.results.none { it.id == "layer-one/unavailable" })
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
        if (python == null) return println("not run: TYPEWRIGHT_PYTHON is not set")
        runBlocking {
            val before = tempBuildDirectories()
            val c = controller()
            c.open(CheckTestProject.subject)
            c.await(seconds = 120) { it.layerOne is LayerOneState.Running }
            c.cancel()
            assertEquals(LayerOneState.Cancelled, c.state.value.layerOne)
            // Starting again waits for the cancelled run to have cleaned up; then nothing of it is left on disk.
            kotlinx.coroutines.delay(1500)
            assertEquals(
                emptySet<String>(),
                tempBuildDirectories() - before,
                "a cancelled build must leave its temporary directory removed",
            )
        }
    }
}
