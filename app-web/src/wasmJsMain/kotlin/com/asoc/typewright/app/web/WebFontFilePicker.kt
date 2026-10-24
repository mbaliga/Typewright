// SPDX-License-Identifier: FSL-1.1-ALv2

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.asoc.typewright.app.web

import com.asoc.typewright.project.ProjectLocation
import com.asoc.typewright.ui.imports.FontFilePicker
import com.asoc.typewright.ui.imports.ImportDestination
import com.asoc.typewright.ui.imports.PickResult
import com.asoc.typewright.ui.imports.pickResultFromBrowserAnswer
import com.asoc.typewright.ui.project.IN_MEMORY_WORKSPACE_ROOT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise

internal const val NO_UFO_IN_BROWSER: String =
    "Opening a UFO folder isn't offered in the browser: a web page can be given a file at a time, " +
        "but not a folder's contents to read as a UFO and keep in place. Open the font file itself, or open the UFO in the desktop app."

/**
 * A hidden `<input type=file>`, clicked, answering the chosen file as `"<name>\u0000<base64 bytes>"`,
 * or null when the person cancels (the `cancel` event; a browser that does not send it leaves the
 * promise waiting, and the import's Close ends the wait). The file is read with a `FileReader` and
 * crosses as base64, the bridge this codebase already uses for binary data on wasm.
 */
internal fun browserFilePicker(accept: String): Promise<JsString?> =
    js(
        """
        new Promise(function (resolve, reject) {
            var input = document.createElement('input');
            input.type = 'file';
            input.accept = accept;
            input.style.display = 'none';
            var finished = false;
            function done(value) {
                if (finished) return;
                finished = true;
                if (input.parentNode) input.parentNode.removeChild(input);
                resolve(value);
            }
            input.addEventListener('change', function () {
                var file = input.files && input.files[0];
                if (!file) { done(null); return; }
                var reader = new FileReader();
                reader.onload = function () {
                    var url = String(reader.result);
                    done(file.name + '\u0000' + url.substring(url.indexOf(',') + 1));
                };
                reader.onerror = function () {
                    finished = true;
                    if (input.parentNode) input.parentNode.removeChild(input);
                    reject(reader.error);
                };
                reader.readAsDataURL(file);
            });
            input.addEventListener('cancel', function () { done(null); });
            document.body.appendChild(input);
            input.click();
        })
        """,
    )

/**
 * "Open a font…" in the browser: an `<input type=file>` for a `.ttf` or `.otf` file. The browser
 * hands the page only the file the person chose (law 3), and the page keeps it in memory.
 *
 * **Compile-verified only.** This container has no browser to run it in; `pickResultFromBrowserAnswer`,
 * which does everything after the file has been read, is tested on the JVM. The owner is asked to try
 * it in Chrome and in Firefox, including cancelling the dialog.
 */
class WebFontFilePicker : FontFilePicker {
    override val canPickUfoFolder: Boolean get() = false
    override val ufoFolderUnavailableReason: String get() = NO_UFO_IN_BROWSER

    override suspend fun pickUfoFolder(): PickResult = PickResult.Failed(NO_UFO_IN_BROWSER)

    override suspend fun pickFont(): PickResult {
        val answer: JsString? =
            try {
                browserFilePicker(".ttf,.otf,font/ttf,font/otf").await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return PickResult.Failed("The browser could not read that file.")
            }
        return pickResultFromBrowserAnswer(answer?.toString())
    }
}

/** Where a project made from an imported font goes in the browser: into the tab's memory, and the audit says it is gone with the tab. */
class WebImportDestination : ImportDestination {
    override val persists: Boolean get() = false

    override suspend fun parentFolder(): ProjectLocation = IN_MEMORY_WORKSPACE_ROOT

    override fun describe(
        parent: ProjectLocation?,
        folderName: String,
    ): String =
        "Nothing is written to disk. The browser keeps this project in the tab only, so it is gone when the tab closes " +
            "unless you download it first (Download project in the command palette)."
}
