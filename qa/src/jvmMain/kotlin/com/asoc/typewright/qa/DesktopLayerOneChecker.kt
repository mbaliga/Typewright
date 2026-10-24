// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa

/**
 * Layer one on a computer: Fontspector when its binary is on PATH, otherwise Fontbakery from the Python
 * on this computer, the same googlefonts profile either way and the same [CheckOutcome]s out. When
 * neither can run it still answers: [availability] says why in one sentence that names the pip command,
 * and [check] returns the one skipped outcome the interface promises (a skip is never a pass).
 */
internal class DesktopLayerOneChecker(
    private val fontspector: LayerOneChecker = FontspectorCliChecker(),
    private val fontbakery: LayerOneChecker = FontbakeryCliChecker(),
) : LayerOneChecker {
    override val name: String = "Layer one (Fontspector, else Fontbakery)"

    override suspend fun availability(): LayerOneAvailability {
        val spector = fontspector.availability()
        if (spector is LayerOneAvailability.Available) return spector
        val bakery = fontbakery.availability()
        if (bakery is LayerOneAvailability.Available) return bakery
        val why =
            listOfNotNull(
                (spector as? LayerOneAvailability.Unavailable)?.reason?.removePrefix("Unavailable: "),
                (bakery as? LayerOneAvailability.Unavailable)?.reason,
            ).joinToString(" ")
        return LayerOneAvailability.Unavailable("Layer one did not run: neither Fontspector nor Fontbakery is available. $why")
    }

    override suspend fun runtime(): LayerOneRuntime? {
        if (fontspector.availability() is LayerOneAvailability.Available) return fontspector.runtime()
        return fontbakery.runtime()
    }

    override suspend fun check(input: LayerOneInput): LayerOneReport {
        val chosen = if (fontspector.availability() is LayerOneAvailability.Available) fontspector else fontbakery
        val report = chosen.check(input)
        return if (report.tool == null) report.copy(tool = chosen.runtime()) else report
    }
}

/**
 * The checker for a phone or tablet, where there is no Python and no Fontspector to run: it says that layer
 * one needs the desktop app, and checks nothing. Layer two, Typewright's own checks, runs everywhere.
 */
internal class NeedsDesktopLayerOneChecker : LayerOneChecker {
    override val name: String = "Layer one (desktop app only)"

    override suspend fun availability(): LayerOneAvailability = LayerOneAvailability.Unavailable(REASON)

    override suspend fun check(input: LayerOneInput): LayerOneReport = unavailableReport(REASON)

    companion object {
        const val REASON =
            "Layer one needs the desktop app: Fontbakery and Fontspector run on a computer, not on this device. " +
                "Layer two, Typewright's own checks, runs here."
    }
}

/**
 * Whether this JVM is Android's runtime. A phone's Java properties name it (`java.vm.name` is "Dalvik",
 * `java.vendor` "The Android Project"), which is read without any Android class, so this module stays free
 * of the SDK. Only a device can show that it answers true there; the lookup is injected so the rule can be tested.
 */
internal fun isAndroidRuntime(property: (String) -> String? = System::getProperty): Boolean =
    property("java.vm.name")?.equals("Dalvik", ignoreCase = true) == true ||
        property("java.vendor")?.contains("Android", ignoreCase = true) == true ||
        property("java.vm.vendor")?.contains("Android", ignoreCase = true) == true
