// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.project

import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.project.imports.ImportOutcome
import com.asoc.typewright.project.imports.ImportReview
import com.asoc.typewright.project.imports.ProjectImporter
import com.asoc.typewright.project.imports.reviewFontFile
import com.asoc.typewright.ui.imports.BundledSampleFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/**
 * Tests only: a workspace whose open project is the repository's sample font, imported through the
 * same importer a person's font goes through. Screens read "your font" from the open project, so a
 * test that wants a font on screen opens a project like this instead of reaching for a font file.
 */
object SampleProject {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** A fresh in-memory workspace with the sample open; [declaredStyleClass] is the class the brief declares (null for none). */
    fun workspace(declaredStyleClass: String? = BundledSampleFont.DECLARED_STYLE_CLASS): ProjectWorkspace {
        val workspace = createProjectWorkspace(scope)
        val ready = BundledSampleFont.review() as ImportReview.Ready
        val outcome =
            runBlocking {
                ProjectImporter(
                    workspace,
                ).create(ready.candidate, IN_MEMORY_WORKSPACE_ROOT, "Sample", declaredStyleClass = declaredStyleClass)
            }
        check(outcome is ImportOutcome.Imported) { "the sample did not import: $outcome" }
        return workspace
    }

    /** A fresh in-memory workspace with the font file [bytes] (named [fileName]) imported and open, whatever font it is. */
    fun workspaceFor(
        fileName: String,
        bytes: ByteArray,
        declaredStyleClass: String? = null,
    ): ProjectWorkspace {
        val workspace = createProjectWorkspace(scope)
        val ready = reviewFontFile(fileName, bytes) as ImportReview.Ready
        val outcome =
            runBlocking {
                ProjectImporter(
                    workspace,
                ).create(
                    ready.candidate,
                    IN_MEMORY_WORKSPACE_ROOT,
                    fileName.substringBeforeLast('.'),
                    declaredStyleClass = declaredStyleClass,
                )
            }
        check(outcome is ImportOutcome.Imported) { "$fileName did not import: $outcome" }
        return workspace
    }

    /** The open project of [workspace] as a screen reads it. */
    fun openProject(workspace: ProjectWorkspace): OpenProject {
        val session = checkNotNull(workspace.current.value) { "no project is open" }
        return OpenProject(session, session.state.value)
    }
}
