package dev.aarso.typewright.compile

import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise

/**
 * The hosted build endpoint's one HTTP call (docs/HOSTED_BUILD_ENDPOINT.md), done entirely
 * inside JS: `fetch`, reading the body with `response.text()`, and catching whatever `fetch`
 * itself can throw (DNS failure, refused connection, CORS, ...). Real browser `fetch()` and
 * Node's own global `fetch()` (Node >= 18; this build already requires Node >= 22.3.0 elsewhere,
 * `CorpusResources.wasmJs.kt`) implement the same Fetch API, so this exact function is both what
 * a real browser runs in the shipped app and what `HostedEndpointBackendRealHttpTest`
 * (wasmJsTest) exercises under Node against a real local server.
 *
 * Only one value crosses the Kotlin/Wasm <-> JS boundary: the JSON text [HostedBuildProtocol]'s
 * [TransportOutcome] mirrors. That keeps this file to the one JS interop type this module
 * actually needs -- `kotlin.js.Promise`, already part of `kotlin-stdlib-wasm-js` -- rather than
 * typed bindings for `Request`/`Response`/`Headers`, so nothing new is added to the dependency
 * graph for this (`CorpusResources.wasmJs.kt` and `CampaignResources.wasmJs.kt` are the same
 * raw-`js()` idiom, for Node's `fs` instead of `fetch`).
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun fetchOutcomeJson(
    url: String,
    bodyJson: String,
): Promise<JsString> =
    js(
        """
        (function () {
            return fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: bodyJson
            }).then(function (response) {
                return response.text().then(function (text) {
                    return JSON.stringify({ ok: true, status: response.status, bodyText: text });
                });
            }).catch(function (error) {
                var message = (error && error.message) ? error.message : String(error);
                return JSON.stringify({ ok: false, error: message });
            });
        })()
        """,
    )

/**
 * Awaits [this], resolving with its value read back as a Kotlin [String]. Only the fulfilled
 * path is wired up: [fetchOutcomeJson]'s own `.catch()` means the promise this file ever awaits
 * always resolves, never rejects.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private suspend fun Promise<JsString>.awaitText(): String =
    suspendCoroutine { continuation ->
        then({ value: JsString ->
            continuation.resume(value.toString())
            null
        })
    }

/** POSTs [bodyJson] to `endpoint + path` and returns the transport outcome as raw JSON text (see [HostedBuildProtocol.interpretTransportOutcome]). */
@OptIn(ExperimentalWasmJsInterop::class)
internal suspend fun postJsonForOutcome(
    endpoint: String,
    path: String,
    bodyJson: String,
): String = fetchOutcomeJson(endpoint + path, bodyJson).awaitText()
