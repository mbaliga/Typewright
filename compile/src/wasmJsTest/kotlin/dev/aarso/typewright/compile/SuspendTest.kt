package dev.aarso.typewright.compile

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString

/**
 * A `@Test fun` on wasmJs cannot itself be `suspend` (the Kotlin 2.4.20 compiler refuses it:
 * "'suspend' functions annotated with '@kotlin.test.Test' are unsupported"). Returning a
 * [Promise] instead is `kotlin.test`'s own supported way to write an async JS/Wasm test -- the
 * runner awaits it before deciding pass/fail. This turns a suspend test body into that [Promise]:
 * on success it resolves with `"ok"`; on failure or an uncaught exception it re-throws inside the
 * `resolve` callback (rather than calling `reject`) so the *original* [Throwable]'s message
 * reaches the test report -- `commonTest`'s own [runSuspend] (`RunSuspend.kt`) is this file's
 * synchronous sibling, for suspend blocks proven not to really suspend.
 */
@OptIn(ExperimentalWasmJsInterop::class)
internal fun runSuspendTest(block: suspend () -> Unit): Promise<JsString> =
    Promise { resolve, reject ->
        block.startCoroutine(
            Continuation(EmptyCoroutineContext) { result ->
                result.fold(
                    onSuccess = { resolve("ok".toJsString()) },
                    onFailure = { error -> reject(error.toJsFailure()) },
                )
            },
        )
    }

@OptIn(ExperimentalWasmJsInterop::class)
private fun Throwable.toJsFailure(): JsAny = (message ?: toString()).toJsString()
