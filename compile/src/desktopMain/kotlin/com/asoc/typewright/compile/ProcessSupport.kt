// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * What a short-lived child process did: its [exitCode] (null when it did not exit by itself), all of
 * its [output] (standard output and error together), and [problem], a plain sentence when it could
 * not be started or was stopped for running too long.
 */
internal class ProcessOutcome(
    val exitCode: Int?,
    val output: String,
    val problem: String? = null,
) {
    val succeeded: Boolean get() = problem == null && exitCode == 0
}

/** Runs a command to completion and answers what it printed; the seam that lets discovery be tested without Python. */
internal fun interface ProcessRunner {
    fun run(
        command: List<String>,
        timeoutMillis: Long,
    ): ProcessOutcome
}

/** [ProcessRunner] over real processes. A command that cannot start, or runs past its timeout, is reported in [ProcessOutcome.problem]. */
internal object RealProcessRunner : ProcessRunner {
    override fun run(
        command: List<String>,
        timeoutMillis: Long,
    ): ProcessOutcome {
        val process =
            try {
                ProcessBuilder(command).redirectErrorStream(true).also { it.environment()["PYTHONUNBUFFERED"] = "1" }.start()
            } catch (e: java.io.IOException) {
                return ProcessOutcome(null, "", "could not be started (${e.message ?: "unknown reason"})")
            }
        process.outputStream.close()
        val output = StringBuilder()
        val reader =
            thread(
                isDaemon = true,
                name = "typewright-process-output",
            ) { process.inputStream.bufferedReader().forEachLine { output.appendLine(it) } }
        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.killTree()
            reader.join(READER_JOIN_MILLIS)
            return ProcessOutcome(
                null,
                output.toString(),
                "did not finish within ${timeoutMillis / MILLIS_PER_SECOND} seconds and was stopped",
            )
        }
        reader.join(READER_JOIN_MILLIS)
        return ProcessOutcome(process.exitValue(), output.toString())
    }

    private const val READER_JOIN_MILLIS = 5_000L
    private const val MILLIS_PER_SECOND = 1_000L
}

/**
 * [interpreter] as a command that means the same file wherever a process is started from: a path with a directory part
 * (`.venv/bin/python`) is made absolute against the working directory, and a bare name (`python3`) is left for the PATH.
 * A build runs its interpreter from a temporary directory, where a relative path would name another file, so a path is
 * made absolute once, when the interpreter is found, and the probe and the build use the same one.
 */
internal fun absoluteInterpreter(interpreter: String): String {
    if ('/' !in interpreter && File.separatorChar !in interpreter) return interpreter
    return File(interpreter)
        .toPath()
        .toAbsolutePath()
        .normalize()
        .toString()
}

/** Stops this process and every process it started, which is what cancelling a build has to do. */
internal fun Process.killTree() {
    val handle = toHandle()
    val children = handle.descendants().toList()
    children.forEach { it.destroyForcibly() }
    destroyForcibly()
    runCatching { waitFor(KILL_WAIT_SECONDS, TimeUnit.SECONDS) }
}

private const val KILL_WAIT_SECONDS = 5L

/** The first executable called [name] in the directories of [path] (a `PATH` value), or in [alsoIn], or null. */
internal fun findExecutable(
    name: String,
    path: String?,
    alsoIn: File? = null,
): File? {
    val directories =
        path
            .orEmpty()
            .split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .map { File(it) } + listOfNotNull(alsoIn)
    return directories.map { File(it, name) }.firstOrNull { it.isFile && it.canExecute() }
}
