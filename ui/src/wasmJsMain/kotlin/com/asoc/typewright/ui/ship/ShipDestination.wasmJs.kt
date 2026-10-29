// SPDX-License-Identifier: FSL-1.1-ALv2

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.asoc.typewright.ui.ship

import com.asoc.typewright.project.ZipTreeWriter
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.toJsString

/** The browser's destination: a zip the page offers for download. Nothing is sent anywhere; the file is made in this tab. */
actual fun platformShipDestination(): ShipDestination = WebShipDestination

private object WebShipDestination : ShipDestination {
    override val exit: ShipExit get() = ShipExit.ZIP

    override suspend fun choose(
        folderName: String,
        previous: String?,
    ): ShipChoice =
        ShipChoice.Chosen(
            ShipTarget("$folderName.zip", ZipTreeWriter("$folderName.zip", folderName, ::offerDownload), withoutFontOk = true),
        )
}

/**
 * Hands [bytes] to the browser as a download called [fileName]: a Blob made from the bytes in this tab and a link that is clicked once.
 * Owner-verified only: this container runs no browser that downloads (CLAUDE.md law 4); that the zip it offers is the right zip is tested.
 */
@OptIn(ExperimentalEncodingApi::class)
private suspend fun offerDownload(
    fileName: String,
    bytes: ByteArray,
) {
    browserDownload(fileName.toJsString(), Base64.encode(bytes).toJsString(), "application/zip".toJsString())
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
