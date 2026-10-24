// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.ship

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoFontInfo

/** Small, exact fixtures for the Ship tests: a font info that states everything a repository folder needs, and the inputs built from it. */
internal object ShipFixtures {
    const val COPYRIGHT = "Copyright 2026 The Hyle Deco Project Authors (https://github.com/example/hyle-deco)"
    const val OFL_TEXT =
        "This Font Software is licensed under the SIL Open Font License, Version 1.1. " +
            "This license is available with a FAQ at: https://openfontlicense.org"

    /** Everything stated: names, version, designer, copyright with its address, the OFL, a vendor ID and a weight class. */
    val completeInfo =
        UfoFontInfo(
            familyName = "Hyle Deco",
            styleName = "Regular",
            unitsPerEm = 1000,
            capHeight = 700,
            xHeight = 500,
            versionMajor = 1,
            versionMinor = 24,
            copyright = COPYRIGHT,
            openTypeNameDesigner = "A. Designer",
            openTypeNameLicense = OFL_TEXT,
            openTypeNameLicenseURL = "https://openfontlicense.org",
            postscriptFontName = "HyleDeco-Regular",
            openTypeOS2VendorID = "HYLE",
            other = listOf("openTypeOS2WeightClass" to PlistValue.PInteger(400)),
        )

    val font =
        ShipFont(
            glyphCount = 337,
            codepoints = (0x20..0x7E).toSet() + 0x100 + 0x101,
            scripts = listOf("Latn"),
            capHeight = 700,
            xHeight = 500,
        )

    val ufo: Map<String, ByteArray> =
        mapOf(
            "metainfo.plist" to "meta".encodeToByteArray(),
            "fontinfo.plist" to "info <>&".encodeToByteArray(),
            "glyphs/A_.glif" to byteArrayOf(0, 1, 2, 3, 127, -128),
            "glyphs/contents.plist" to "contents".encodeToByteArray(),
        )

    val binary: ByteArray = byteArrayOf(0, 1, 0, 0, 5, 6, 7, -1)

    fun inputs(
        info: UfoFontInfo = completeInfo,
        mode: ShipMode = ShipMode.GOOGLE_FONTS,
        binary: ByteArray? = this.binary,
        category: GfCategory? = GfCategory.DISPLAY,
        brief: ShipBrief = ShipBrief("Art deco display", listOf("Watch faces", "Posters")),
        layerOne: LayerOneSummary? = LayerOneSummary("Fontbakery 0.12.10", 0, 12, 147),
        font: ShipFont = this.font,
    ) = ShipInputs(
        mode = mode,
        facts = ShipFacts.of(info),
        font = font,
        brief = brief,
        ufoFolderName = "HyleDeco-Regular.ufo",
        ufo = ufo,
        binary = binary,
        category = category,
        layerOne = layerOne,
        today = "2026-10-24",
    )

    fun plan(
        info: UfoFontInfo = completeInfo,
        mode: ShipMode = ShipMode.GOOGLE_FONTS,
        binary: ByteArray? = this.binary,
        category: GfCategory? = GfCategory.DISPLAY,
    ) = buildShipPlan(inputs(info, mode, binary, category))

    fun ShipPlan.text(path: String): String = checkNotNull(files[path]) { "the plan has no $path; it has $paths" }.decodeToString()
}
