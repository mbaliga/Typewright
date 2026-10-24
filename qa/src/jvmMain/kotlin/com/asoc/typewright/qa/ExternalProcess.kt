// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException

/**
 * What a child process did: its [exitCode] (null when it did not exit by itself), everything it printed
 * ([output], standard output and error together), and [problem], a plain phrase when it could not be
 * started or was stopped for running too long.
 */
internal class Captured(
    val exitCode: Int?,
    val output: String,
    val problem: String? = null,
) {
    val succeeded: Boolean get() = problem == null && exitCode == 0
}

/** Runs a command to completion; the seam that lets discovery be tested without a real Python. */
internal fun interface CommandRunner {
    fun run(
        command: List<String>,
        timeoutMillis: Long,
    ): Captured
}

/**
 * [CommandRunner] over real processes. A thread that is interrupted while the process runs (the caller was
 * cancelled) stops the process and every process it started, then rethrows as a [CancellationException]:
 * nothing is left running behind a cancelled check.
 */
internal object RealCommandRunner : CommandRunner {
    override fun run(
        command: List<String>,
        timeoutMillis: Long,
    ): Captured {
        val process =
            try {
                ProcessBuilder(command).redirectErrorStream(true).also { it.environment()["PYTHONUNBUFFERED"] = "1" }.start()
            } catch (e: java.io.IOException) {
                return Captured(null, "", "could not be started (${e.message ?: "unknown reason"})")
            }
        process.outputStream.close()
        val output = StringBuffer()
        val reader =
            thread(isDaemon = true, name = "typewright-layer-one-output") {
                process.inputStream.bufferedReader().forEachLine { output.append(it).append('\n') }
            }
        try {
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.killTree()
                reader.join(READER_JOIN_MILLIS)
                return Captured(null, output.toString(), "did not finish within ${describe(timeoutMillis)} and was stopped")
            }
        } catch (e: InterruptedException) {
            process.killTree()
            throw CancellationException("The check was cancelled.").also { it.initCause(e) }
        }
        reader.join(READER_JOIN_MILLIS)
        return Captured(process.exitValue(), output.toString())
    }

    private fun describe(millis: Long): String {
        if (millis >= MILLIS_PER_MINUTE) return "${millis / MILLIS_PER_MINUTE} minutes"
        val seconds = (millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
        return if (seconds == 1L) "1 second" else "$seconds seconds"
    }

    private const val READER_JOIN_MILLIS = 5_000L
    private const val MILLIS_PER_SECOND = 1_000L
    private const val MILLIS_PER_MINUTE = 60_000L
}

/** Stops this process and every process it started. */
internal fun Process.killTree() {
    val children = toHandle().descendants().toList()
    children.forEach { it.destroyForcibly() }
    destroyForcibly()
    runCatching { waitFor(KILL_WAIT_SECONDS, TimeUnit.SECONDS) }
}

private const val KILL_WAIT_SECONDS = 5L
