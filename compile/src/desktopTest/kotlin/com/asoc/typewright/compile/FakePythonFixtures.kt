// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.core.font.ufo.UfoLib
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.font.ufo.writeUfoProject
import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.core.geometry.ContourPoint
import com.asoc.typewright.core.geometry.CurveFormat
import com.asoc.typewright.core.geometry.Glyph
import com.asoc.typewright.core.geometry.Point
import com.asoc.typewright.project.InMemoryProjectFiles
import java.io.File
import java.nio.file.Files

/**
 * A stand-in for the Python interpreter: a shell script that answers the probe the way a real Python with
 * fontmake does, and, when it is run as `-m fontmake`, records what it was given in [directory] and then
 * does whatever [behaviour] says. The backend starts it as a real process, so streaming, cancelling and
 * timing out are the real thing; only fontmake itself is faked (the integration tests run the real one).
 */
internal class FakePython(
    behaviour: String,
    pathops: Boolean = true,
    /**
     * What the stand-in does when it is run as `-c <the Google Fonts fix-up script> <font>`: shell lines, with the font as `$2`'s next
     * argument (`FONT`), after it has recorded its arguments in `fixes-args.txt`. Null means it answers every `-c` like the probe.
     */
    fixes: String? = null,
) {
    val directory: File = Files.createTempDirectory("typewright-fake-python-").toFile()
    val script: File = File(directory, "fake-python")
    val argsFile: File = File(directory, "args.txt")
    val fixesArgsFile: File = File(directory, "fixes-args.txt")

    init {
        val pathopsLine = if (pathops) "ok 1.0" else "missing ModuleNotFoundError"
        val dollar = "$"
        val fixesLines =
            if (fixes == null) {
                emptyList()
            } else {
                listOf(
                    "  case \"${dollar}2\" in",
                    "    *typewright-google-fonts-fixes*)",
                    "      FONT=\"${dollar}3\"",
                    "      echo \"${dollar}1 SCRIPT ${dollar}FONT\" > \"${dollar}DIR/fixes-args.txt\"",
                ) + fixes.trimIndent().lines().map { "      $it" } + listOf("      ;;", "  esac")
            }
        val head =
            listOf(
                "#!/bin/sh",
                "DIR=\"$dollar(cd \"$dollar(dirname \"${dollar}0\")\" && pwd)\"",
                "if [ \"${dollar}1\" = \"-c\" ]; then",
            ) + fixesLines +
                listOf(
                    "  echo \"fontmake ok 9.9.9\"",
                    "  echo \"ufo2ft ok 9.9.9\"",
                    "  echo \"fontTools ok 9.9.9\"",
                    "  echo \"pathops $pathopsLine\"",
                    "  echo \"python ok 3.11.0\"",
                    "  exit 0",
                    "fi",
                    "echo \"$dollar@\" > \"${dollar}DIR/args.txt\"",
                    "UFO=\"\"",
                    "OUTFILE=\"\"",
                    "while [ $dollar# -gt 0 ]; do",
                    "  if [ \"${dollar}1\" = \"-u\" ]; then UFO=\"${dollar}2\"; fi",
                    "  if [ \"${dollar}1\" = \"--output-path\" ]; then OUTFILE=\"${dollar}2\"; fi",
                    "  shift",
                    "done",
                )
        script.writeText(head.joinToString("\n") + "\n" + behaviour.trimIndent() + "\n")
        script.setExecutable(true)
    }

    /** The command line fontmake was given, or null when it was never run. */
    fun arguments(): List<String>? =
        argsFile
            .takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.split(' ')

    /** The build's temporary directory, found from the `-u` argument (`<work>/source/source.ufo`). */
    fun workDirectory(): File = File(arguments()!!.let { it[it.indexOf("-u") + 1] }).parentFile.parentFile

    fun cleanUp() {
        directory.deleteRecursively()
    }

    fun backend(
        timeoutMillis: Long = 60_000L,
        path: String = "",
    ): SystemPythonFontmakeBackend =
        SystemPythonFontmakeBackend(
            env = { name -> mapOf(PYTHON_ENV_VARIABLE to script.absolutePath, "PATH" to path)[name] },
            runner = RealProcessRunner,
            timeoutMillis = timeoutMillis,
        )

    companion object {
        /** Writes a TTF-named file and exits cleanly; [pause] seconds between the first and second line it prints. */
        fun succeedsAfter(pause: Int): String =
            """
            echo "INFO:fontmake.font_project:Building TTF for Fixture-Regular"
            ls "${'$'}UFO/glyphs" > "${'$'}DIR/glyph-files.txt"
            cp "${'$'}UFO/glyphs/O_.glif" "${'$'}DIR/O.glif" 2>/dev/null
            sleep $pause
            echo "WARNING:ufo2ft:a gentle warning"
            printf 'FAKE-TTF-BYTES' > "${'$'}OUTFILE"
            exit 0
            """.trimIndent()

        /** Records which glyph files the copy holds, the layer's `contents.plist` and `fontinfo.plist`, then writes a font. */
        val LISTS_THE_COPY =
            """
            ls "${'$'}UFO/glyphs" > "${'$'}DIR/glyph-files.txt"
            cp "${'$'}UFO/glyphs/contents.plist" "${'$'}DIR/contents.plist"
            cp "${'$'}UFO/fontinfo.plist" "${'$'}DIR/fontinfo.plist"
            printf 'FAKE-TTF-BYTES' > "${'$'}OUTFILE"
            exit 0
            """.trimIndent()

        val FAILS =
            """
            echo "INFO:fontmake.font_project:Building TTF for Fixture-Regular"
            echo "Traceback (most recent call last):"
            echo '  File "x.py", line 1, in <module>'
            echo "ufo2ft.errors.InvalidFontData: boom in glyph A"
            echo "fontmake: Error: In 'x.ufo': Compiling UFO failed: boom in glyph A"
            exit 2
            """.trimIndent()

        val WRITES_NOTHING =
            """
            echo "INFO:fontmake.font_project:Building TTF for Fixture-Regular"
            exit 0
            """.trimIndent()

        /** Starts a child that outlives it unless the whole tree is stopped, writes both process ids, then waits. */
        val HANGS =
            """
            sleep 120 &
            echo ${'$'}! > "${'$'}DIR/child.pid"
            echo ${'$'}${'$'} > "${'$'}DIR/self.pid"
            echo "INFO:fontmake.font_project:Building TTF for Fixture-Regular"
            wait
            """.trimIndent()
    }
}

private fun on(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = true)

private fun off(
    x: Int,
    y: Int,
) = ContourPoint(Point(x, y), onCurve = false)

/** A project with one UFO, `Fixture-Regular.ufo`, whose O has real quadratic curves (as a font opened from a TTF has). */
internal fun fixtureProject(): InMemoryProjectFiles {
    val o =
        Glyph(
            name = "O",
            advanceWidth = 600,
            contours =
                listOf(
                    Contour(
                        listOf(
                            on(300, 700),
                            off(560, 700),
                            off(560, 350),
                            off(560, 0),
                            off(300, 0),
                            off(40, 0),
                            off(40, 350),
                            off(40, 700),
                        ),
                        CurveFormat.QUADRATIC,
                    ),
                ),
            unicodes = listOf(0x4F),
        )
    val ufo =
        UfoProject(
            UfoFontInfo(familyName = "Fixture", styleName = "Regular", unitsPerEm = 1000),
            listOf(o),
            lib = UfoLib(lineContourFormat = CurveFormat.QUADRATIC),
        )
    val files = writeUfoProject(ufo).mapKeys { "Fixture-Regular.ufo/${it.key}" }.mapValues { it.value.encodeToByteArray() }
    return InMemoryProjectFiles("Fixture", files + ("typewright.json" to "{}".encodeToByteArray()))
}

/** The UFO folder of [accentedProject], whose name is not ASCII. */
internal const val ACCENTED_UFO = "HyléDeco-Regular.ufo"

/** The family of [accentedProject], as the font's own name table must carry it. */
internal const val ACCENTED_FAMILY = "Hylé Deco"

/**
 * A project whose family is [ACCENTED_FAMILY] and whose glyphs are [glyphs] (name and code point); the default two, an e with an acute
 * accent and Kana a, are named by their letters, so their glyph files are not ASCII: what a font with an accent or a non-Latin script
 * looks like to a Java that cannot write such a file name. The UFO folder, [ACCENTED_UFO], is not ASCII either.
 */
internal fun accentedProject(glyphs: List<Pair<String, Int>> = listOf("\u00E9" to 0xE9, "\u3042" to 0x3042)): InMemoryProjectFiles {
    fun square(
        name: String,
        code: Int,
    ) = Glyph(
        name = name,
        advanceWidth = 600,
        contours = listOf(Contour(listOf(on(50, 0), on(50, 700), on(550, 700), on(550, 0)), CurveFormat.QUADRATIC)),
        unicodes = listOf(code),
    )
    val ufo =
        UfoProject(
            UfoFontInfo(familyName = ACCENTED_FAMILY, styleName = "Regular", unitsPerEm = 1000),
            glyphs.map { (name, code) -> square(name, code) },
        )
    val files = writeUfoProject(ufo).mapKeys { "$ACCENTED_UFO/${it.key}" }.mapValues { it.value.encodeToByteArray() }
    return InMemoryProjectFiles("Accented", files + ("typewright.json" to "{}".encodeToByteArray()))
}
