// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.crash

import kotlin.test.Test
import kotlin.test.assertTrue

class CrashReportTest {
    @Test
    fun theReportNamesTheBuildTheThreadAndTheCauseAndNothingElse() {
        val report =
            buildCrashReport(IllegalStateException("boom in the picker"), "main", "0.0.1", "Android 14", "2026-11-03 10:00")
        assertTrue("Version: 0.0.1" in report)
        assertTrue("Platform: Android 14" in report)
        assertTrue("Thread: main" in report)
        assertTrue("IllegalStateException" in report && "boom in the picker" in report, "the cause and its message are kept")
    }

    @Test
    fun aCauseChainIsKept() {
        val report = buildCrashReport(RuntimeException("outer", IllegalArgumentException("inner cause")), "t", "v", "p", "w")
        assertTrue("inner cause" in report, "the root cause appears, not only the wrapper")
    }
}
