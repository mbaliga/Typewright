// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.ship

import com.asoc.typewright.compile.CompileLocation
import com.asoc.typewright.project.TreeReplacement
import com.asoc.typewright.qa.ship.ShipGate
import com.asoc.typewright.qa.ship.ShipMode

/** The sentences the Ship pages print, kept apart from the screens so what a person is told can be tested without drawing anything. */
object ShipText {
    /** The page's one line about GitHub, which V1 does not push to. */
    const val GITHUB_NOTE: String = "GitHub push is not in V1. You push the folder yourself."

    /** The title of a page for [family]: `Ship · Hyle Deco`. */
    fun title(family: String): String = "Ship · $family"

    /** The title while building. */
    fun buildingTitle(family: String): String = "Building $family"

    /** The mono line under the page's title: what the folder is and how it leaves the app here. */
    fun subtitle(
        mode: ShipMode,
        exit: ShipExit,
    ): String {
        val what = if (mode == ShipMode.GOOGLE_FONTS) "A Google Fonts repository" else "A repository under your own licence"
        return when (exit) {
            ShipExit.FOLDER -> "$what, written to a folder"
            ShipExit.ZIP -> "$what, exported as a zip"
            ShipExit.DOCUMENT_TREE -> "$what, written to a folder on this device"
            ShipExit.NOWHERE -> "$what, with nowhere to write it yet"
        }
    }

    /** The building page's line: where the work happens, and that nothing leaves. */
    fun buildingSubtitle(
        location: CompileLocation?,
        exit: ShipExit,
    ): String =
        when (location) {
            is CompileLocation.OnThisComputer -> "On this computer · nothing leaves it"
            is CompileLocation.OnThisDevice -> "On this device · nothing leaves it"
            is CompileLocation.HostedEndpoint -> "On a hosted build endpoint · your project is sent there"
            null -> if (exit == ShipExit.ZIP) "In this browser · nothing is sent" else "On this device · nothing leaves it"
        }

    /** What the right circle says on Ready: what a tap does here. */
    fun actionLabel(
        exit: ShipExit,
        compiles: Boolean,
    ): String =
        when {
            exit == ShipExit.ZIP -> "Export a zip"
            !compiles -> "Write the folder without a font"
            else -> "Build and write the repository"
        }

    /** The line under the target row when nothing is chosen; on the web the zip is fixed, so it names it ([folderName] is the family's folder name). */
    fun notChosen(
        exit: ShipExit,
        folderName: String? = null,
    ): String =
        when (exit) {
            ShipExit.FOLDER -> "No folder is chosen yet"
            ShipExit.ZIP -> "A zip your browser downloads" + (folderName?.let { ": $it.zip" } ?: "")
            ShipExit.DOCUMENT_TREE -> "No folder is chosen yet"
            ShipExit.NOWHERE -> "This build cannot write a folder"
        }

    /** The one line the web page says about why it exports a zip. */
    const val WEB_WHY: String =
        "Builds need the desktop or Android app for now. This exports your UFO and the rest of the folder as a zip; the font is not in it."

    /** The one line a phone says about why the folder has no font. */
    const val PHONE_WHY: String = "Building the font on a phone is not available in this version, so the folder is written without it."

    /** The line a page that cannot compile says about it, or null where the font is compiled here. */
    fun withoutFontWhy(
        exit: ShipExit,
        compiles: Boolean,
    ): String? =
        when {
            compiles -> null
            exit == ShipExit.ZIP -> WEB_WHY
            exit == ShipExit.DOCUMENT_TREE -> PHONE_WHY
            else -> null
        }

    /** The link under the target row. */
    fun chooseLink(
        exit: ShipExit,
        chosen: Boolean,
    ): String? =
        when (exit) {
            ShipExit.FOLDER, ShipExit.DOCUMENT_TREE -> if (chosen) "Choose another folder" else "Choose a folder"
            ShipExit.ZIP, ShipExit.NOWHERE -> null
        }

    /** The ink block under the checklist while the build is stopped. */
    fun blockedBlock(gate: ShipGate): String = gate.blockedSentence()

    /** `00:08`: whole seconds as minutes and seconds. */
    fun elapsed(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        return "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** `0.4 s`, `6.2 s`, `1 min 12 s`: how long a step took. */
    fun duration(millis: Long): String {
        if (millis < 0) return ""
        if (millis < MILLIS_PER_MINUTE) {
            val tenths = (millis + TENTH_ROUND) / MILLIS_PER_TENTH
            return "${tenths / TENTHS_PER_SECOND}.${tenths % TENTHS_PER_SECOND} s"
        }
        val seconds = millis / MILLIS_PER_SECOND
        return "${seconds / SECONDS_PER_MINUTE} min ${seconds % SECONDS_PER_MINUTE} s"
    }

    /** The heading of a build that stopped: `Ship stopped while compiling the project with fontmake`. */
    fun failureHeading(step: ShipStep): String = "Ship stopped while ${step.activity}"

    /** What a cancelled build says: it was stopped before anything was written. */
    const val CANCELLED: String = "Cancelled. Your project and the folder are as they were."

    /** What the page says when Close came while the folder was being written: the writer finishes a move it has begun. */
    const val CLOSED_DURING_WRITE: String =
        "You closed the build while the folder was being written. The folder was already written, so it holds the whole repository; " +
            "your project was not changed."

    /** The deck's label and the page's line while a Close waits for the run to stop. */
    const val CANCELLING: String = "Cancelling…"

    /** Before a build: what choosing a place that already holds a copy will replace. */
    fun replacingWarning(replacements: List<TreeReplacement>): String? =
        replacements.takeIf { it.isNotEmpty() }?.let { list ->
            "This folder already holds ${replacedWords(list)}. Building replaces " +
                (
                    if (list.any {
                            it.directory
                        }
                    ) {
                        "the whole sources folder (anything in it that this project does not hold is removed) and "
                    } else {
                        ""
                    }
                ) +
                "those files with the new ones. Everything else in the folder is left alone."
        }

    /** After a build: what the write replaced, one line per folder or file. */
    fun replacedLine(item: TreeReplacement): String =
        if (item.directory) {
            "${item.path}/ replaced whole" +
                if (item.removed >
                    0
                ) {
                    "; ${item.removed} ${if (item.removed == 1) "file" else "files"} in it that the project does not hold were removed"
                } else {
                    ""
                }
        } else {
            "${item.path} replaced"
        }

    private fun replacedWords(list: List<TreeReplacement>): String =
        list.joinToString(", ") { if (it.directory) "${it.path}/" else it.path }

    /** The note under a fresh build: what cancelling does. */
    const val CANCEL_NOTE: String = "Cancel leaves the project untouched"

    /** The heading of the page that follows a write. */
    fun written(
        target: String,
        withFont: Boolean,
    ): String = if (withFont) "Written to $target" else "Written to $target, without a font"

    /** `19 files · 3.4 MB`. */
    fun size(
        files: Int,
        bytes: Long,
    ): String {
        val amount =
            when {
                bytes >= BYTES_PER_MEGABYTE -> "${oneDecimal(bytes.toDouble() / BYTES_PER_MEGABYTE)} MB"
                bytes >= BYTES_PER_KILOBYTE -> "${(bytes + BYTES_PER_KILOBYTE / 2) / BYTES_PER_KILOBYTE} KB"
                else -> "$bytes bytes"
            }
        return "$files ${if (files == 1) "file" else "files"} · $amount"
    }

    /** [path] with the home folder written as `~`, the way the board writes a target. */
    fun shortPath(
        path: String,
        home: String?,
    ): String {
        val h = home?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return path
        return if (path == h) {
            "~"
        } else if (path.startsWith("$h/")) {
            "~" + path.removePrefix(h)
        } else {
            path
        }
    }

    private fun oneDecimal(value: Double): String {
        val tenths = (value * TENTHS_PER_SECOND + HALF).toLong()
        return "${tenths / TENTHS_PER_SECOND}.${tenths % TENTHS_PER_SECOND}"
    }

    private const val MILLIS_PER_SECOND = 1000L
    private const val MILLIS_PER_TENTH = 100L
    private const val TENTH_ROUND = 50L
    private const val TENTHS_PER_SECOND = 10L
    private const val SECONDS_PER_MINUTE = 60L
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val BYTES_PER_KILOBYTE = 1024L
    private const val BYTES_PER_MEGABYTE = 1024L * 1024L
    private const val HALF = 0.5
}

/** How a repository leaves the app on this platform. */
enum class ShipExit {
    /** A folder the person picks, on a computer. */
    FOLDER,

    /** A zip the browser downloads. */
    ZIP,

    /** A folder in a document tree the person grants, on a phone. */
    DOCUMENT_TREE,

    /** Nothing is wired to write anywhere. */
    NOWHERE,
}
