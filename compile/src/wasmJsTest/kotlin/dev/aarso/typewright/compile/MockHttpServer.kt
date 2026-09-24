package dev.aarso.typewright.compile

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise

/**
 * A real local HTTP server for [HostedEndpointBackendRealHttpTest], started with Node's own
 * built-in `http` module (`process.getBuiltinModule('http')`, the same idiom
 * `CorpusResources.wasmJs.kt` uses for `fs`) so the test proves [HostedEndpointBackend] against
 * an actual socket rather than a Kotlin fake. Every request it receives gets the one fixed
 * `responseStatus`/`responseBodyJson` the test configured at start time; [lastMockRequestJson]
 * reads back what the server actually received, so a test can also assert the real request that
 * reached it, not only the response [HostedEndpointBackend] decoded from it.
 *
 * `:compile:wasmJsNodeTest` is what runs this, per this module's own `wasmJs { nodejs() }`
 * (`build.gradle.kts`); Node has had a global, browser-compatible `fetch()` since Node 18, so the
 * exact `fetch()` call [HostedEndpointTransport.kt] makes under a real browser is what this
 * exercises here too, just over a loopback socket instead of the public internet.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun startMockHttpServerAsync(
    responseStatus: Int,
    responseBodyJson: String,
): Promise<JsString> =
    js(
        """
        (function () {
            var http = process.getBuiltinModule('http');
            globalThis.__typewrightMockRequests = [];
            var server = http.createServer(function (req, res) {
                var chunks = [];
                req.on('data', function (chunk) { chunks.push(chunk); });
                req.on('end', function () {
                    var bodyText = Buffer.concat(chunks).toString('utf8');
                    globalThis.__typewrightMockRequests.push({
                        method: req.method,
                        url: req.url,
                        contentType: req.headers['content-type'] || '',
                        bodyText: bodyText
                    });
                    res.writeHead(responseStatus, { 'Content-Type': 'application/json' });
                    res.end(responseBodyJson);
                });
            });
            globalThis.__typewrightMockServer = server;
            // net.Server.listen() binds asynchronously (Node queues the actual bind()/listen()
            // syscalls); server.address() can still be null right after calling listen(), so this
            // waits for the real 'listening' event before handing back the real assigned port --
            // the bug this file's first version had, confirmed by wasmJsNodeTest failing with a
            // JsException from `.address().port` on a null address.
            return new Promise(function (resolve) {
                server.on('listening', function () {
                    resolve(String(server.address().port));
                });
                server.listen(0, '127.0.0.1');
            });
        })()
        """,
    )

@OptIn(ExperimentalWasmJsInterop::class)
private suspend fun Promise<JsString>.await(): String =
    suspendCoroutine { continuation ->
        then({ value: JsString ->
            continuation.resume(value.toString())
            null
        })
    }

/** Starts the mock server and suspends until it is really listening, returning the real port it bound. */
@OptIn(ExperimentalWasmJsInterop::class)
internal suspend fun startMockHttpServer(
    responseStatus: Int,
    responseBodyJson: String,
): Int = startMockHttpServerAsync(responseStatus, responseBodyJson).await().toInt()

/** The most recent request the mock server received, as JSON `{method,url,contentType,bodyText}`, or `null` if it has received none yet. */
@OptIn(ExperimentalWasmJsInterop::class)
internal fun lastMockRequestJson(): JsString? =
    js(
        """
        (function () {
            var list = globalThis.__typewrightMockRequests;
            if (!list || list.length === 0) { return null; }
            return JSON.stringify(list[list.length - 1]);
        })()
        """,
    )

/** Closes the server [startMockHttpServer] opened. */
@OptIn(ExperimentalWasmJsInterop::class)
internal fun stopMockHttpServer() {
    js(
        """
        (function () {
            if (globalThis.__typewrightMockServer) {
                globalThis.__typewrightMockServer.close();
                globalThis.__typewrightMockServer = null;
            }
        })()
        """,
    )
}
