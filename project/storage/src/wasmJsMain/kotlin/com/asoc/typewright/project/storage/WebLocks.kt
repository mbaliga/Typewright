// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalWasmJsInterop::class)

package com.asoc.typewright.project.storage

import com.asoc.typewright.project.WriteLease
import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise

/**
 * The Web Locks API single-writer check (docs/PROJECT_MODEL.md §5's table):
 * `navigator.locks.request(name, {ifAvailable:true}, callback)` only runs `callback` while the
 * lock is free, and holds it for exactly as long as the promise `callback` returns stays
 * pending. [requestWebLockIfAvailable] turns that into an ordinary acquire/release pair by
 * handing back a still-pending inner promise's own resolver as `{release}`, resolved by
 * [WriteLease.release] -- the standard "manually released Web Lock" pattern; resolving an
 * already-resolved promise a second time is a no-op in JS, so the lease is safe to release twice,
 * as [WriteLease] requires.
 */
internal suspend fun webLockLease(name: JsString): WriteLease? {
    val handle = requestWebLockIfAvailable(name).await<JsAny?>() ?: return null
    return WriteLease { releaseWebLockHandle(handle) }
}

private fun requestWebLockIfAvailable(name: JsString): Promise<JsAny?> =
    js(
        """
        (function () {
            if (typeof navigator === 'undefined' || !navigator.locks) return Promise.resolve(null);
            return new Promise(function (resolveOuter) {
                navigator.locks.request(name, { ifAvailable: true }, function (lock) {
                    if (!lock) {
                        resolveOuter(null);
                        return undefined;
                    }
                    return new Promise(function (resolveInner) {
                        resolveOuter({ release: resolveInner });
                    });
                });
            });
        })()
        """,
    )

private fun releaseWebLockHandle(handle: JsAny) {
    js("handle.release();")
}
