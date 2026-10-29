// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BackgroundLayerOneCheckerTest {
    private val report = LayerOneReport(listOf(CheckOutcome("a", CheckStatus.PASS, "ok")))

    /** A delegate that blocks its thread the way a child-process wait does, and says which thread it was on. */
    private class Blocking(
        private val report: LayerOneReport,
    ) : LayerOneChecker {
        override val name = "blocking"
        val entered = CompletableDeferred<Thread>()
        val release = CountDownLatch(1)
        val interrupted = CompletableDeferred<Boolean>()

        override suspend fun availability(): LayerOneAvailability {
            entered.complete(Thread.currentThread())
            return LayerOneAvailability.Available
        }

        override suspend fun check(input: LayerOneInput): LayerOneReport {
            entered.complete(Thread.currentThread())
            try {
                release.await(60, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                interrupted.complete(true)
                throw e
            }
            return report
        }
    }

    @Test
    fun aBlockingToolRunsOffTheCallersThreadSoTheCallerStaysFree() =
        runBlocking<Unit> {
            val delegate = Blocking(report)
            val caller = Thread.currentThread()
            // Everything here runs on this one thread. If the tool ran on it too, the latch below could never be released.
            val result = launch { BackgroundLayerOneChecker(delegate).check(LayerOneInput("F.ttf", byteArrayOf(1))) }
            val worker = withTimeout(10_000) { delegate.entered.await() }
            assertNotEquals(caller, worker, "the tool ran on a background thread")
            assertTrue(result.isActive)
            delegate.release.countDown()
            withTimeout(10_000) { result.join() }
            assertTrue(result.isCompleted && !result.isCancelled)
        }

    @Test
    fun cancellingTheCallerInterruptsTheToolsThread() =
        runBlocking<Unit> {
            val delegate = Blocking(report)
            val job = launch(Dispatchers.Default) { BackgroundLayerOneChecker(delegate).check(LayerOneInput("F.ttf", byteArrayOf(1))) }
            withTimeout(10_000) { delegate.entered.await() }
            job.cancelAndJoin()
            assertTrue(withTimeout(10_000) { delegate.interrupted.await() }, "the thread waiting on the tool was interrupted")
        }

    @Test
    fun theWrapperAnswersWhatTheDelegateAnswersAndKeepsItsName() =
        runBlocking<Unit> {
            val delegate = Blocking(report)
            val wrapped = BackgroundLayerOneChecker(delegate)
            assertEquals("blocking", wrapped.name)
            assertEquals(LayerOneAvailability.Available, wrapped.availability())
            delegate.release.countDown()
            assertEquals(report, wrapped.check(LayerOneInput("F.ttf", byteArrayOf(1))))
            assertEquals(null, wrapped.runtime())
        }
}
