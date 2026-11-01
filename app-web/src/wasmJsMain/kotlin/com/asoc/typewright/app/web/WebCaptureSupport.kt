// SPDX-License-Identifier: FSL-1.1-ALv2

@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalEncodingApi::class)

package com.asoc.typewright.app.web

import com.asoc.typewright.ui.capture.CaptureFilePicker
import com.asoc.typewright.ui.capture.CaptureSource
import com.asoc.typewright.ui.capture.ExportOutcome
import com.asoc.typewright.ui.capture.FileExporter
import com.asoc.typewright.ui.capture.FilePickOutcome
import com.asoc.typewright.ui.capture.PlatformCaptureSupport
import com.asoc.typewright.ui.capture.picksFromBrowserAnswer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString

/** A hidden `<input type=file multiple>`, clicked; answers the chosen files as [picksFromBrowserAnswer] reads them, or null if the person cancels. */
private fun browserMultiFilePicker(accept: String): Promise<JsString?> =
    js(
        """
        new Promise(function (resolve, reject) {
            var input = document.createElement('input');
            input.type = 'file';
            input.multiple = true;
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
                var files = Array.prototype.slice.call(input.files || []);
                if (files.length === 0) { done(null); return; }
                var out = [];
                var remaining = files.length;
                files.forEach(function (file, i) {
                    var reader = new FileReader();
                    reader.onload = function () {
                        var url = String(reader.result);
                        out[i] = file.name + '\u0000' + url.substring(url.indexOf(',') + 1);
                        if (--remaining === 0) done(out.join('\u0001'));
                    };
                    reader.onerror = function () {
                        if (finished) return;
                        finished = true;
                        if (input.parentNode) input.parentNode.removeChild(input);
                        reject(reader.error);
                    };
                    reader.readAsDataURL(file);
                });
            });
            input.addEventListener('cancel', function () { done(null); });
            document.body.appendChild(input);
            input.click();
        })
        """,
    )

/**
 * Adds a picture or an SVG in the browser: an `<input type=file multiple>`. Only File is offered; files can also be dropped on the
 * page. The browser hands the page only what the person chose (law 3).
 *
 * **Compile-verified only.** This container has no browser to run it in; [picksFromBrowserAnswer], which does everything after the
 * files have been read, is tested. The owner is asked to try it in Chrome and in Firefox, including cancelling the dialog.
 */
class WebCaptureFilePicker : CaptureFilePicker {
    override val sources: List<CaptureSource> get() = listOf(CaptureSource.FILE)

    override suspend fun pick(source: CaptureSource): FilePickOutcome {
        val answer: JsString? =
            try {
                browserMultiFilePicker(".jpg,.jpeg,.png,.gif,.bmp,.svg,image/*").await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return FilePickOutcome.Failed("The browser could not read that file.")
            }
        return picksFromBrowserAnswer(answer?.toString())
    }
}

/** Saves a file in the browser as a download: a Blob made from the bytes in this tab and a link that is clicked once. Nothing is sent anywhere. */
class WebFileExporter : FileExporter {
    override val describe: String get() = "a download"

    override suspend fun export(
        suggestedName: String,
        mimeType: String,
        bytes: ByteArray,
    ): ExportOutcome {
        browserDownload(suggestedName.toJsString(), Base64.encode(bytes).toJsString(), mimeType.toJsString())
        return ExportOutcome.Saved("$suggestedName in your downloads")
    }
}

private fun browserDownload(
    fileName: JsString,
    base64: JsString,
    mime: JsString,
): Unit =
    js(
        """
        {
            var binary = atob(base64);
            var array = new Uint8Array(binary.length);
            for (var i = 0; i < binary.length; i++) array[i] = binary.charCodeAt(i);
            var url = URL.createObjectURL(new Blob([array], { type: mime }));
            var link = document.createElement('a');
            link.href = url;
            link.download = fileName;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            setTimeout(function () { URL.revokeObjectURL(url); }, 10000);
        }
        """,
    )

/** Starts listening for files dropped on the page; they wait in a queue the page's own loop empties. */
private fun listenForDrops(): Unit =
    js(
        """
        {
            if (!window.__typewrightDropsInstalled) {
                window.__typewrightDropsInstalled = true;
                window.__typewrightDrops = [];
                document.addEventListener('dragover', function (e) { e.preventDefault(); });
                document.addEventListener('drop', function (e) {
                    e.preventDefault();
                    var files = Array.prototype.slice.call((e.dataTransfer && e.dataTransfer.files) || []);
                    var keep = files.filter(function (f) { return /\.(jpe?g|png|gif|bmp|svg)${'$'}/i.test(f.name); });
                    keep.forEach(function (file) {
                        var reader = new FileReader();
                        reader.onload = function () {
                            var url = String(reader.result);
                            window.__typewrightDrops.push(file.name + '\u0000' + url.substring(url.indexOf(',') + 1));
                        };
                        reader.readAsDataURL(file);
                    });
                });
            }
        }
        """,
    )

/** The dropped files read since the last call, in the same form as [browserMultiFilePicker], or an empty string. */
private fun takeDrops(): JsString =
    js(
        """
        {
            var q = window.__typewrightDrops || [];
            window.__typewrightDrops = [];
            return q.join('\u0001');
        }
        """,
    )

/**
 * Installs the browser's Capture support: the file picker, the download, and a drop target on the whole page, whose files are
 * handed to Capture by a short loop (a callback from the page into Kotlin would need an interop type the build does not use
 * elsewhere). Compile-verified only: no browser runs here (CLAUDE.md law 4).
 */
fun installWebCaptureSupport(scope: CoroutineScope) {
    PlatformCaptureSupport.picker = WebCaptureFilePicker()
    PlatformCaptureSupport.exporter = WebFileExporter()
    PlatformCaptureSupport.acceptsDrops = true
    listenForDrops()
    scope.launch {
        while (true) {
            delay(DROP_POLL_MILLIS)
            val waiting = takeDrops().toString()
            if (waiting.isNotEmpty()) {
                (
                    picksFromBrowserAnswer(
                        waiting,
                    ) as? FilePickOutcome.Picked
                )?.let { PlatformCaptureSupport.deliverDrop(it.files) }
            }
        }
    }
}

private const val DROP_POLL_MILLIS = 400L
