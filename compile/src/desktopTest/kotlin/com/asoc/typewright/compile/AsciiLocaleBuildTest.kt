// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.parsePlistDict
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The build in a Java whose file-name encoding is ASCII, which is what a Linux desktop launched with `LANG=C` runs.
 * [AsciiLocaleBuildMain] is started as a real child JVM with `LC_ALL=C`, builds a family called "Hylé Deco" with an
 * accented glyph and a Kana glyph against a stand-in interpreter, and the files that interpreter was given are read back here.
 * In such a Java a non-ASCII file name is written as question marks, so without ASCII names the two glyphs would share a file.
 */
class AsciiLocaleBuildTest {
    private val fakes = mutableListOf<FakePython>()

    @AfterTest
    fun cleanUp() {
        fakes.forEach { it.cleanUp() }
    }

    private fun child(python: FakePython): List<String> {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val command =
            listOf(java, "-cp", System.getProperty("java.class.path"), AsciiLocaleBuildMain::class.java.name, python.script.absolutePath)
        val builder = ProcessBuilder(command).redirectErrorStream(true)
        builder.environment().apply {
            keys.removeAll { it.startsWith("LC_") || it == "LANG" || it == "LANGUAGE" || it == "JAVA_TOOL_OPTIONS" }
            put("LC_ALL", "C")
            put("LANG", "C")
        }
        val process = builder.start()
        process.outputStream.close()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            fail("the child JVM did not finish: $output")
        }
        return output.lines().filter { it.isNotBlank() }
    }

    @Test
    fun aJavaThatCannotWriteNonAsciiFileNamesStillBuildsAnAccentedKanaProject() {
        val python = FakePython(FakePython.LISTS_THE_COPY).also { fakes += it }
        val said = child(python)

        // The child must really have been unable to write an accented file name, or this test proves nothing.
        assertContains(said, "can-write-accent=false", "the child JVM ran with a file-name encoding that can hold an accent: $said")
        assertContains(said, "result=success", said.toString())
        assertContains(said, "binary-name-ok=true", "the binary keeps the project's own name: $said")

        val files = File(python.directory, "glyph-files.txt").readLines().filter { it.endsWith(".glif") }
        assertEquals(2, files.size, "two glyphs need two glyph files, not one shared '?.glif': $files")
        val contents = parsePlistDict(File(python.directory, "contents.plist").readText(Charsets.UTF_8))
        assertEquals(
            files.toSet(),
            contents.entries.map { (it.second as PlistValue.PString).value }.toSet(),
            "contents.plist points at the files that are there",
        )
        val args = python.arguments().orEmpty()
        assertTrue(
            args.isNotEmpty() &&
                args.all { arg ->
                    arg.all { it.code in 0x20..0x7E }
                },
            "fontmake was given a name with a '?' in it: $args",
        )
    }
}

/** Run by [AsciiLocaleBuildTest] in a JVM started with `LC_ALL=C`: builds [accentedProject] with the interpreter in `args[0]`. */
object AsciiLocaleBuildMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val encoding = System.getProperty("sun.jnu.encoding") ?: "unknown"
        val canWrite = runCatching { Charset.forName(encoding).newEncoder().canEncode("é") }.getOrDefault(false)
        println("jnu=$encoding")
        println("can-write-accent=$canWrite")
        val backend =
            SystemPythonFontmakeBackend(
                env = { name -> if (name == PYTHON_ENV_VARIABLE) args[0] else null },
                runner = RealProcessRunner,
                timeoutMillis = 60_000L,
            )
        val result = runBlocking { backend.compile(CompileRequest(accentedProject(), CompileSource.Ufo(ACCENTED_UFO))) }
        when (result) {
            is CompileResult.Success -> {
                println("result=success")
                println("binary-name-ok=${result.binaries.single().fileName == "HyléDeco-Regular.ttf"}")
            }

            is CompileResult.Failure -> {
                println("result=failure ${result.reason}")
            }

            is CompileResult.NotImplemented -> {
                println("result=not-implemented")
            }
        }
    }
}
