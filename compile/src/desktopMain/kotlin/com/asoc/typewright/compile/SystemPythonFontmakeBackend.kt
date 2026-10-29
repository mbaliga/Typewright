// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The desktop backend: fontmake and fontTools run by the computer's own Python 3 (`TYPEWRIGHT_PYTHON`
 * first, then `python3` on PATH; [PythonLocator]).
 *
 * [availability] says in words which interpreter it found or why there is none, and names every
 * missing package with the pip command that installs it; it installs nothing. A relative
 * `TYPEWRIGHT_PYTHON` is made absolute when it is found, so the probe and the build mean the same file.
 * [compile] writes the project's UFO to a fresh temporary directory (with quadratic contours rewritten
 * as cubic in that copy only, [prepareUfoForFontmake]), runs fontmake on it, streams its output line by
 * line to `onLog`, and returns the TTF as a [FontBinary]. The copy and the font fontmake writes have
 * plain ASCII names ([withAsciiFileNames]) and the binary gets the project's own name back, so a family
 * with an accent or a glyph in Kana builds on a Java whose file-name encoding is ASCII. Overlaps are removed with skia-pathops when it
 * imports and are otherwise left alone, and the log says so. ttfautohint runs only when it is on
 * PATH; otherwise the log says "not hinted: ttfautohint not found" and the build goes on. Then the
 * Google Fonts fix-ups ([GoogleFontsFixes]) make the binary what that checker asks of every font
 * (embedding allowed, `USE_TYPO_METRICS`, and for an unhinted font `gasp` and the smart-dropout
 * `prep`) wherever the project states nothing of its own, and the log lists what they changed and what
 * they left alone because the project states it. Cancelling
 * the calling coroutine stops fontmake and every process it started and deletes the temporary
 * directory; the project is never written to. A build that runs past [timeoutMillis] is stopped.
 */
class SystemPythonFontmakeBackend internal constructor(
    private val env: (String) -> String?,
    private val runner: ProcessRunner,
    private val timeoutMillis: Long,
) : CompileBackend {
    constructor() : this(System::getenv, RealProcessRunner, DEFAULT_TIMEOUT_MILLIS)

    override val name: String = "fontmake (system Python)"

    override val location: CompileLocation =
        CompileLocation.OnThisComputer(runtime = "fontmake and fontTools in this computer's Python 3")

    override suspend fun availability(): BackendAvailability =
        withContext(Dispatchers.IO) {
            when (
                val found =
                    runCatching {
                        PythonLocator(
                            env,
                            runner,
                        ).resolve()
                    }.getOrElse { FontmakeEnvironment.NotReady(unexpected(it)) }
            ) {
                is FontmakeEnvironment.Ready -> BackendAvailability.Available
                is FontmakeEnvironment.NotReady -> BackendAvailability.Unavailable(found.reason)
            }
        }

    override suspend fun compile(
        request: CompileRequest,
        onLog: (CompileLogLine) -> Unit,
    ): CompileResult {
        val log = BuildLog(onLog)
        return try {
            // runInterruptible: cancelling the caller interrupts the thread that is waiting for fontmake.
            runInterruptible(Dispatchers.IO) { build(request, log) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(unexpected(e))
            CompileResult.Failure(unexpected(e), log.lines())
        }
    }

    private fun build(
        request: CompileRequest,
        log: BuildLog,
    ): CompileResult {
        val unsupported = request.options.formats - OutputFormat.TTF
        if (unsupported.isNotEmpty()) {
            return failure(
                log,
                "Only TTF builds are available in this version; ${unsupported.joinToString { it.name }} cannot be built yet.",
            )
        }
        val source = request.source
        if (source !is CompileSource.Ufo) {
            return failure(log, "Building from a designspace is not available in this version; a project is built from its one UFO.")
        }
        val ufoPath = source.path.trim('/')
        val ufoFiles = readUfo(request.project, ufoPath)
        if (ufoFiles.isEmpty()) return failure(log, "The project has no UFO at \"$ufoPath\", so there is nothing to build.")

        val python =
            when (val found = PythonLocator(env, runner).resolve()) {
                is FontmakeEnvironment.NotReady -> return failure(log, found.reason)
                is FontmakeEnvironment.Ready -> found.python
            }
        log.info(
            "Using Python ${python.pythonVersion ?: "3"} (${python.python}) with fontmake ${python.fontmakeVersion ?: "(version unknown)"}.",
        )

        val prepared =
            try {
                prepareUfoForFontmake(ufoFiles)
            } catch (e: Exception) {
                return failure(log, "Typewright could not read this project's UFO to prepare it for fontmake: ${e.message ?: e}")
            }
        if (prepared.elevatedGlyphs.isNotEmpty()) {
            log.info(
                "Rewrote ${prepared.elevatedGlyphs.size} glyphs from quadratic to cubic curves in the copy fontmake reads " +
                    "(same curves, coordinates rounded to whole units). Your project is unchanged.",
            )
        }

        val work = Files.createTempDirectory("typewright-build-").toFile()
        try {
            if (!canNameFile(work.path)) {
                return failure(
                    log,
                    "This computer's file-name encoding cannot hold the temporary folder ${work.path}, " +
                        "so fontmake could not be given it. " +
                        "Set TMPDIR (or java.io.tmpdir) to a folder whose name is plain ASCII, then build again.",
                )
            }
            // The copy fontmake reads, and the font it writes, have plain ASCII names whatever the project is called, so a family
            // such as "Hylé Deco" or a Kana glyph's file builds on a Java whose file-name encoding is ASCII. The project's own
            // name is put back on the binary afterwards.
            val ufoDir = File(work, "source/$BUILD_UFO_NAME")
            writeFiles(ufoDir, withAsciiFileNames(prepared.files))
            val outFile = File(work, "out/$BUILD_FONT_NAME").also { it.parentFile.mkdirs() }

            val command = fontmakeCommand(python, ufoDir, outFile, request.options, log)
            log.info("Running fontmake…")
            val exit = runStreaming(command, work, log)
            if (exit.code == 0 && !exit.timedOut && outFile.isFile && request.options.googleFontsFixes) {
                applyGoogleFontsFixes(python, outFile, GoogleFontsFixes.statedBy(prepared.files), log)
            }
            val binaries = collectBinaries(outFile, projectBinaryName(ufoPath))
            return when {
                exit.timedOut -> failure(log, "fontmake did not finish within ${describeDuration(timeoutMillis)} and was stopped.")
                exit.code != 0 -> CompileResult.Failure(explainFailure(log.lineTexts(), exit.code), log.lines())
                binaries.isEmpty() -> failure(log, "fontmake finished but wrote no font file, so there is nothing to use.")
                else -> CompileResult.Success(binaries, log.lines())
            }
        } finally {
            work.deleteRecursively()
        }
    }

    private fun fontmakeCommand(
        python: FontmakePython,
        ufoDir: File,
        outFile: File,
        options: CompileOptions,
        log: BuildLog,
    ): List<String> {
        val command =
            mutableListOf(python.python, "-m", "fontmake", "-u", ufoDir.absolutePath, "-o", "ttf", "--output-path", outFile.absolutePath)
        when {
            !options.removeOverlaps -> {
                command += "--keep-overlaps"
                log.info("Overlaps are kept, as the options ask.")
            }

            python.pathops -> {
                command += listOf("--overlaps-backend", "pathops")
            }

            else -> {
                command += "--keep-overlaps"
                log.warn("Overlaps were not removed: skia-pathops is not installed. Install it with: pip install skia-pathops")
            }
        }
        if (options.autohint) {
            val binDir = python.python.takeIf { '/' in it }?.let { File(it).absoluteFile.parentFile }
            if (findExecutable("ttfautohint", env("PATH"), binDir) != null) {
                command += "--autohint"
            } else {
                log.info("not hinted: ttfautohint not found")
            }
        }
        return command
    }

    /**
     * Makes [font] what Google Fonts' checker asks of every font, in the ways that belong to the build ([GoogleFontsFixes]), and says in the
     * log what it changed and what it left alone because the project states it ([stated]). A font that cannot be fixed is still the font
     * fontmake wrote: the log warns, and the build goes on, so the check on the binary reports what is wrong instead of the build failing
     * here. An interpreter that does not run the script at all (a stand-in) is silent.
     */
    private fun applyGoogleFontsFixes(
        python: FontmakePython,
        font: File,
        stated: List<String>,
        log: BuildLog,
    ) {
        val outcome =
            runner.run(listOf(python.python, "-c", GoogleFontsFixes.SCRIPT, font.absolutePath) + stated, FIXES_TIMEOUT_MILLIS)
        val report = if (outcome.succeeded) GoogleFontsFixes.parseReport(outcome.output) else null
        val changes = report?.changes
        when {
            changes != null && changes.isEmpty() -> {
                log.info("Google Fonts fix-ups: nothing to change.")
                report.kept.forEach { log.warn("Google Fonts fix-up not applied: $it.") }
            }

            changes != null -> {
                changes.forEach { log.info("Google Fonts fix-up: $it") }
                report.kept.forEach { log.warn("Google Fonts fix-up not applied: $it.") }
            }

            outcome.succeeded -> {
                Unit
            }

            else -> {
                val why =
                    outcome.problem ?: outcome.output
                        .trim()
                        .lineSequence()
                        .lastOrNull { it.isNotBlank() } ?: "it printed nothing"
                log.warn("The Google Fonts fix-ups could not be applied ($why). The font is as fontmake wrote it.")
            }
        }
    }

    /** Runs [command], handing each output line to [log]; stops the whole process tree on cancellation or timeout. */
    private fun runStreaming(
        command: List<String>,
        workDir: File,
        log: BuildLog,
    ): StreamedExit {
        val builder = ProcessBuilder(command).directory(workDir).redirectErrorStream(true)
        builder.environment()["PYTHONUNBUFFERED"] = "1"
        builder.environment()["PYTHONIOENCODING"] = "utf-8"
        val process = builder.start()
        process.outputStream.close()
        val reader =
            thread(isDaemon = true, name = "typewright-fontmake-output") { process.inputStream.bufferedReader().forEachLine(log::fromTool) }
        try {
            val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.killTree()
                reader.join(READER_JOIN_MILLIS)
                return StreamedExit(null, timedOut = true)
            }
            reader.join(READER_JOIN_MILLIS)
            return StreamedExit(process.exitValue(), timedOut = false)
        } catch (e: Throwable) {
            // Cancelled (or failed) while fontmake was running: nothing it started may outlive the build.
            process.killTree()
            throw e
        }
    }

    private class StreamedExit(
        val code: Int?,
        val timedOut: Boolean,
    )

    private fun failure(
        log: BuildLog,
        reason: String,
    ): CompileResult.Failure {
        log.error(reason)
        return CompileResult.Failure(reason, log.lines())
    }

    private fun describeDuration(millis: Long): String {
        if (millis >= MILLIS_PER_MINUTE) return "${millis / MILLIS_PER_MINUTE} minutes"
        val seconds = (millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
        return if (seconds == 1L) "1 second" else "$seconds seconds"
    }

    private fun unexpected(e: Throwable): String = "The build could not run: ${e.message ?: e.toString()}"

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 20L * 60L * 1000L
        const val FIXES_TIMEOUT_MILLIS = 2L * 60L * 1000L
        const val MILLIS_PER_MINUTE = 60_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val READER_JOIN_MILLIS = 5_000L
    }
}

/** The build's log: every line is kept for the result and handed to the caller's callback as it happens. */
private class BuildLog(
    private val sink: (CompileLogLine) -> Unit,
) {
    private val entries = mutableListOf<CompileLogLine>()

    fun lines(): List<CompileLogLine> = synchronized(entries) { entries.toList() }

    fun lineTexts(): List<String> = lines().map { it.message }

    fun info(message: String) = add(CompileLogLine.Level.INFO, message)

    fun warn(message: String) = add(CompileLogLine.Level.WARNING, message)

    fun error(message: String) = add(CompileLogLine.Level.ERROR, message)

    /** One line of fontmake's own output; Python's logging prefixes (`WARNING:`, `ERROR:`) set the level. */
    fun fromTool(line: String) {
        if (line.isBlank()) return
        val level =
            when {
                line.startsWith("WARNING:") -> CompileLogLine.Level.WARNING

                line.startsWith("ERROR:") || line.startsWith("CRITICAL:") || line.startsWith("Traceback") ||
                    line.startsWith("fontmake: Error") -> CompileLogLine.Level.ERROR

                else -> CompileLogLine.Level.INFO
            }
        add(level, line)
    }

    private fun add(
        level: CompileLogLine.Level,
        message: String,
    ) {
        val line = CompileLogLine(level, message)
        synchronized(entries) { entries += line }
        sink(line)
    }
}

/** The files of the UFO at [ufoPath] in [project], by path relative to the UFO. */
private fun readUfo(
    project: ProjectDirectory,
    ufoPath: String,
): Map<String, ByteArray> {
    val prefix = "$ufoPath/"
    return project
        .listFiles()
        .filter { it.startsWith(prefix) }
        .associate { it.removePrefix(prefix) to project.readBytes(it) }
}

/** Writes [files] under [root], refusing any path that would land outside it. */
private fun writeFiles(
    root: File,
    files: Map<String, ByteArray>,
) {
    root.mkdirs()
    val base = root.canonicalFile
    for ((path, bytes) in files) {
        val target = File(base, path).canonicalFile
        require(target.path.startsWith(base.path + File.separator)) { "\"$path\" is outside the UFO" }
        target.parentFile.mkdirs()
        target.writeBytes(bytes)
    }
}

/** The font fontmake wrote to [outFile] as a binary called [name] (the project's own name for it), or none when it wrote nothing. */
private fun collectBinaries(
    outFile: File,
    name: String,
): List<FontBinary> = if (outFile.isFile) listOf(FontBinary(name, OutputFormat.TTF, outFile.readBytes())) else emptyList()

/** What the project calls the font fontmake builds: the name of its UFO folder with `.ttf` in place of `.ufo`. */
private fun projectBinaryName(ufoPath: String): String = ufoPath.substringAfterLast('/').removeSuffix(".ufo").ifEmpty { "font" } + ".ttf"

/** Whether this Java's file-name encoding can write [path], which it cannot when it is ASCII and the path is not. */
private fun canNameFile(path: String): Boolean {
    val encoding = System.getProperty("sun.jnu.encoding") ?: return isPlainAscii(path)
    return runCatching { Charset.forName(encoding).newEncoder().canEncode(path) }.getOrDefault(isPlainAscii(path))
}

/** The names the build's own files get, whatever the project is called. */
private const val BUILD_UFO_NAME = "source.ufo"
private const val BUILD_FONT_NAME = "font.ttf"

actual fun platformCompileBackend(): CompileBackend = SystemPythonFontmakeBackend()
