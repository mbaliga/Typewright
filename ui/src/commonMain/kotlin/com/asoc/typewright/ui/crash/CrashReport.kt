// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.crash

/**
 * The text of a crash report: what stopped, where, and on which build. It names the app version, the
 * platform and the thread and carries the exception's stack trace, and nothing from the person's
 * project (no glyph, no path, no font name). It is kept on the device and shown on the next launch for
 * the person to copy or share; nothing is sent anywhere (law 3).
 */
fun buildCrashReport(
    error: Throwable,
    threadName: String,
    appVersion: String,
    platform: String,
    whenText: String,
): String =
    buildString {
        appendLine("Typewright stopped unexpectedly.")
        appendLine("Version: $appVersion")
        appendLine("Platform: $platform")
        appendLine("Thread: $threadName")
        appendLine("When: $whenText")
        appendLine()
        append(error.stackTraceToString())
    }
