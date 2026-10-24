// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Which tool layer one uses, and what it says on a device that cannot run either. */
class DesktopLayerOneCheckerTest {
    /** A checker that answers as told and records that it was asked to check. */
    private class Stand(
        private val tool: String,
        private val available: LayerOneAvailability,
        private val report: LayerOneReport,
    ) : LayerOneChecker {
        var checked = 0
        override val name: String = tool

        override suspend fun availability(): LayerOneAvailability = available

        override suspend fun runtime(): LayerOneRuntime? =
            if (available is LayerOneAvailability.Available) LayerOneRuntime(tool, "1.0", "on this computer") else null

        override suspend fun check(input: LayerOneInput): LayerOneReport {
            checked++
            return report
        }
    }

    private val pass = LayerOneReport(listOf(CheckOutcome("a", CheckStatus.PASS, "ok")))
    private val fail = LayerOneReport(listOf(CheckOutcome("b", CheckStatus.FAIL, "no")))
    private val input = LayerOneInput("Font-Regular.ttf", byteArrayOf(1))

    @Test
    fun fontspectorIsUsedWhenItsBinaryIsThereAndFontbakeryIsNotAsked() {
        val spector = Stand("Fontspector", LayerOneAvailability.Available, pass)
        val bakery = Stand("Fontbakery", LayerOneAvailability.Available, fail)
        val checker = DesktopLayerOneChecker(spector, bakery)

        val report = runSuspend { checker.check(input) }

        assertEquals(1, spector.checked)
        assertEquals(0, bakery.checked)
        assertEquals(CheckStatus.PASS, report.results.single().status)
        assertEquals("Fontspector", report.tool?.tool)
        assertEquals("Fontspector", runSuspend { checker.runtime() }?.tool)
    }

    @Test
    fun fontbakeryIsUsedWhenThereIsNoFontspector() {
        val spector = Stand("Fontspector", LayerOneAvailability.Unavailable("Unavailable: fontspector not found on PATH."), pass)
        val bakery = Stand("Fontbakery", LayerOneAvailability.Available, fail)
        val checker = DesktopLayerOneChecker(spector, bakery)

        assertEquals(LayerOneAvailability.Available, runSuspend { checker.availability() })
        val report = runSuspend { checker.check(input) }

        assertEquals(0, spector.checked)
        assertEquals(1, bakery.checked)
        assertEquals(CheckStatus.FAIL, report.results.single().status)
        assertEquals("Fontbakery", runSuspend { checker.runtime() }?.tool)
    }

    @Test
    fun withNeitherToolAvailabilitySaysWhyInOneSentenceBothAreNamed() {
        val spector = Stand("Fontspector", LayerOneAvailability.Unavailable("Unavailable: fontspector not found on PATH."), pass)
        val bakery =
            Stand(
                "Fontbakery",
                LayerOneAvailability.Unavailable("Fontbakery is not installed. Install it with: pip install \"fontbakery[googlefonts]\""),
                fail,
            )
        val availability = runSuspend { DesktopLayerOneChecker(spector, bakery).availability() }
        assertIs<LayerOneAvailability.Unavailable>(availability)
        assertTrue("neither Fontspector nor Fontbakery is available" in availability.reason, availability.reason)
        assertTrue("fontspector not found on PATH" in availability.reason, availability.reason)
        assertTrue("pip install \"fontbakery[googlefonts]\"" in availability.reason, availability.reason)
    }

    @Test
    fun theDeviceCheckerSaysLayerOneNeedsTheDesktopAppAndChecksNothing() {
        val checker = NeedsDesktopLayerOneChecker()
        val availability = runSuspend { checker.availability() }
        assertIs<LayerOneAvailability.Unavailable>(availability)
        assertTrue(availability.reason.startsWith("Layer one needs the desktop app"), availability.reason)
        val report = runSuspend { checker.check(input) }
        assertEquals(CheckStatus.SKIP, report.results.single().status)
        assertEquals(availability.reason, report.results.single().message)
    }

    @Test
    fun androidIsRecognisedFromItsJavaPropertiesAndALinuxDesktopIsNot() {
        assertTrue(isAndroidRuntime { if (it == "java.vm.name") "Dalvik" else null })
        assertTrue(isAndroidRuntime { if (it == "java.vendor") "The Android Project" else null })
        assertTrue(isAndroidRuntime { if (it == "java.vm.vendor") "The Android Project" else null })
        assertFalse(isAndroidRuntime { mapOf("java.vm.name" to "OpenJDK 64-Bit Server VM", "java.vendor" to "Eclipse Adoptium")[it] })
        assertFalse(isAndroidRuntime { null })
    }

    @Test
    fun thePlatformCheckerOnThisComputerIsTheDesktopOneAndNotTheDeviceOne() {
        val checker = assertIs<BackgroundLayerOneChecker>(platformLayerOneChecker(), "wrapped, so no caller waits on a tool")
        assertIs<DesktopLayerOneChecker>(checker.delegate)
    }
}
