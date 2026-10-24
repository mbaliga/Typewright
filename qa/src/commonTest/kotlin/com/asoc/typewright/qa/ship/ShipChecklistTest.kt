// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.ship

import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.qa.report.ReportFixtures
import com.asoc.typewright.qa.report.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShipChecklistTest {
    private val compiles = ShipCapability(true, "Compiled on this computer", "fontmake runs here when you build · nothing is sent")
    private val cannot = ShipCapability(false, "Not compiled here", "The web preview does not compile; build in the desktop app")
    private val cleanGate = shipGate(ReportFixtures.report())
    private val failingGate = shipGate(ReportFixtures.report(info = ReportFixtures.cleanInfo.copy(copyright = null, familyName = null)))

    private fun situation(
        info: UfoFontInfo = ShipFixtures.completeInfo,
        mode: ShipMode = ShipMode.GOOGLE_FONTS,
        gate: ShipGate = cleanGate,
        names: Severity? = Severity.PASS,
        category: GfCategory? = GfCategory.DISPLAY,
        categoryClass: String? = "art deco display",
        descriptionBytes: Int = 320,
        compile: ShipCapability = compiles,
        built: ShipBuilt? = null,
    ) = ShipSituation(
        mode,
        ShipFacts.of(info),
        gate,
        "14 checks · 0 fail · 4 warn",
        names,
        category,
        categoryClass,
        descriptionBytes,
        compile,
        built,
    )

    private fun list(s: ShipSituation) = shipChecklist(s).associateBy { it.id }

    @Test
    fun aCompleteProjectIsAllDoneOrWaitingForTheBuild() {
        val items = shipChecklist(situation())
        assertEquals(
            listOf(
                ShipItemId.CHECK,
                ShipItemId.NAMES,
                ShipItemId.LICENCE,
                ShipItemId.DESIGNER,
                ShipItemId.REPOSITORY,
                ShipItemId.CATEGORY,
                ShipItemId.DESCRIPTION,
                ShipItemId.FILES,
                ShipItemId.COMPILED,
            ),
            items.map { it.id },
        )
        assertEquals(
            listOf(
                ShipMark.DONE,
                ShipMark.DONE,
                ShipMark.DONE,
                ShipMark.DONE,
                ShipMark.DONE,
                ShipMark.DONE,
                ShipMark.TODO,
                ShipMark.TODO,
                ShipMark.TODO,
            ),
            items.map { it.mark },
        )
        assertTrue(items.none { it.mark == ShipMark.WARN || it.mark == ShipMark.FAIL })
    }

    @Test
    fun theWordsAreTheBoardsWords() {
        val items = list(situation())
        assertEquals("Check has no fails", items.getValue(ShipItemId.CHECK).title)
        assertEquals("14 checks · 0 fail · 4 warn", items.getValue(ShipItemId.CHECK).detail)
        assertEquals("Names are unique", items.getValue(ShipItemId.NAMES).title)
        assertEquals("Hyle Deco · Regular · HyleDeco-Regular", items.getValue(ShipItemId.NAMES).detail)
        assertEquals("OFL.txt with your copyright line", items.getValue(ShipItemId.LICENCE).title)
        assertEquals("Copyright 2026 The Hyle Deco Project Authors", items.getValue(ShipItemId.LICENCE).detail)
        assertEquals("DESCRIPTION.en_us.html", items.getValue(ShipItemId.DESCRIPTION).title)
        assertEquals("A draft from your brief, ready to edit", items.getValue(ShipItemId.DESCRIPTION).detail)
        assertEquals("Compiled on this computer", items.getValue(ShipItemId.COMPILED).title)
        assertEquals("fontmake runs here when you build · nothing is sent", items.getValue(ShipItemId.COMPILED).detail)
    }

    @Test
    fun checkFailsShowAsAFailWithALinkToCheck() {
        val item = list(situation(gate = failingGate)).getValue(ShipItemId.CHECK)
        assertEquals(ShipMark.FAIL, item.mark)
        assertEquals("Check has 2 fails", item.title)
        assertEquals(ShipAction("Fix them in Check", ShipRoute.Check), item.action)
    }

    @Test
    fun aMissingDesignerIsAnUnmetLineThatLinksToTheDesignerField() {
        val item = list(situation(info = ShipFixtures.completeInfo.copy(openTypeNameDesigner = null))).getValue(ShipItemId.DESIGNER)
        assertEquals(ShipMark.WARN, item.mark)
        assertEquals("Designer is not set", item.title)
        assertEquals("METADATA needs one", item.detail)
        assertEquals(ShipRoute.FontInfo(ShipFontInfoField.DESIGNER), item.action?.route)
    }

    @Test
    fun aMissingCopyrightLinksToTheCopyrightFieldAndNamesNoValue() {
        val items = list(situation(info = ShipFixtures.completeInfo.copy(copyright = null)))
        val licence = items.getValue(ShipItemId.LICENCE)
        assertEquals(ShipMark.WARN, licence.mark)
        assertEquals(ShipRoute.FontInfo(ShipFontInfoField.COPYRIGHT), licence.action?.route)
        val repository = items.getValue(ShipItemId.REPOSITORY)
        assertEquals(ShipMark.WARN, repository.mark)
        assertEquals("No repository address", repository.title)
        assertEquals(ShipRoute.FontInfo(ShipFontInfoField.COPYRIGHT), repository.action?.route)
    }

    @Test
    fun aLicenceThatIsNotTheOflLinksToTheLicenceField() {
        val info =
            ShipFixtures.completeInfo.copy(
                openTypeNameLicense = "Licensed under the Apache License, Version 2.0",
                openTypeNameLicenseURL = null,
            )
        val item = list(situation(info = info)).getValue(ShipItemId.LICENCE)
        assertEquals(ShipMark.WARN, item.mark)
        assertEquals("Google Fonts takes the Open Font License", item.title)
        assertEquals("This project's licence reads as the Apache License, so no OFL.txt is written", item.detail)
        assertEquals(ShipRoute.FontInfo(ShipFontInfoField.LICENCE_TEXT), item.action?.route)
    }

    @Test
    fun aReservedFontNameIsNamedAndLinksToTheCopyrightField() {
        val info = ShipFixtures.completeInfo.copy(copyright = "Copyright 2026 The Hyle Authors, with Reserved Font Name “Hyle Deco”")
        val item = list(situation(info = info)).getValue(ShipItemId.LICENCE)
        assertEquals(ShipMark.WARN, item.mark)
        assertEquals("Google Fonts does not take a Reserved Font Name", item.title)
        assertNotNull(item.detail)
        assertTrue("Hyle Deco" in item.detail.orEmpty())
        assertEquals(ShipRoute.FontInfo(ShipFontInfoField.COPYRIGHT), item.action?.route)
    }

    @Test
    fun everyLineThatNeedsAFactLinksToAFontInfoValueAndNeverToNothing() {
        val bare = UfoFontInfo(familyName = "F", styleName = "Regular")
        val items = shipChecklist(situation(info = bare, category = null, descriptionBytes = 60, names = Severity.WARN))
        for (item in items.filter { it.mark == ShipMark.WARN }) {
            if (item.id == ShipItemId.CATEGORY || item.id == ShipItemId.DESCRIPTION || item.id == ShipItemId.COMPILED) continue
            val action = assertNotNull(item.action, "${item.id} needs a fact and must say where to set it")
            assertTrue(action.route is ShipRoute.FontInfo, "${item.id}")
        }
        assertEquals(
            setOf(ShipItemId.NAMES, ShipItemId.LICENCE, ShipItemId.DESIGNER, ShipItemId.REPOSITORY, ShipItemId.VENDOR_ID),
            items.filter { it.action != null }.map { it.id }.toSet(),
        )
    }

    @Test
    fun theVendorLineShowsOnlyWhenGoogleFontsWouldWarn() {
        assertNull(list(situation()).get(ShipItemId.VENDOR_ID), "a vendor ID is set: nothing to say in Google Fonts mode")
        val placeholder =
            list(
                situation(info = ShipFixtures.completeInfo.copy(openTypeOS2VendorID = "????")),
            ).getValue(ShipItemId.VENDOR_ID)
        assertEquals(ShipMark.WARN, placeholder.mark)
        assertEquals(ShipRoute.FontInfo(ShipFontInfoField.VENDOR_ID), placeholder.action?.route)
        val unset = list(situation(info = ShipFixtures.completeInfo.copy(openTypeOS2VendorID = null))).getValue(ShipItemId.VENDOR_ID)
        assertEquals(ShipMark.WARN, unset.mark)
    }

    @Test
    fun theCategoryLineSaysWhetherItIsOurReadingOrTheirChoice() {
        val read = list(situation()).getValue(ShipItemId.CATEGORY)
        assertEquals("Category: Display", read.title)
        assertTrue("our heuristic" in read.detail.orEmpty() && "art deco display" in read.detail.orEmpty(), read.detail)
        val chosen = list(situation(categoryClass = null)).getValue(ShipItemId.CATEGORY)
        assertEquals("You chose it.", chosen.detail)
        assertEquals("DISPLAY", chosen.choices?.chosen)
        assertEquals(GfCategory.entries.map { it.protoName }, chosen.choices?.options?.map { it.id })
    }

    @Test
    fun noCategoryIsAnUnmetLineWithTheFiveChoices() {
        val item = list(situation(category = null)).getValue(ShipItemId.CATEGORY)
        assertEquals(ShipMark.WARN, item.mark)
        assertNull(item.choices?.chosen)
        assertEquals(5, item.choices?.options?.size)
    }

    @Test
    fun aShortDescriptionIsFlaggedWithItsSize() {
        val item = list(situation(descriptionBytes = 150)).getValue(ShipItemId.DESCRIPTION)
        assertEquals(ShipMark.WARN, item.mark)
        assertTrue("150 bytes" in item.detail.orEmpty() && "200" in item.detail.orEmpty())
    }

    @Test
    fun aPlatformThatCannotCompileSaysWhyInTheBackendsOwnWords() {
        val item = list(situation(compile = cannot)).getValue(ShipItemId.COMPILED)
        assertEquals(ShipMark.WARN, item.mark)
        assertEquals("The web preview does not compile; build in the desktop app", item.detail)
    }

    @Test
    fun withoutACompilerTheFilesLineSaysTheFolderHasNoFontAndPromisesNoMetadata() {
        val item = list(situation(compile = cannot)).getValue(ShipItemId.FILES)
        assertEquals("The sources, OFL.txt and DESCRIPTION.en_us.html", item.title)
        assertTrue("without a font" in item.detail.orEmpty())
        assertEquals(ShipMark.TODO, item.mark)
        val written = list(situation(compile = cannot, built = ShipBuilt(null, null, withFont = false)))
        assertEquals(ShipMark.DONE, written.getValue(ShipItemId.FILES).mark)
        assertEquals(ShipMark.WARN, written.getValue(ShipItemId.COMPILED).mark, "no font was compiled, so the compile line stays unmet")
    }

    @Test
    fun afterABuildTheFilesAndTheCompileAreDone() {
        val built = ShipBuilt(6, "Fontbakery 0.12.10: 0 fail · 12 warn · 147 pass")
        val items = list(situation(built = built))
        assertEquals(ShipMark.DONE, items.getValue(ShipItemId.FILES).mark)
        assertEquals(ShipMark.DONE, items.getValue(ShipItemId.DESCRIPTION).mark)
        val compiled = items.getValue(ShipItemId.COMPILED)
        assertEquals(ShipMark.DONE, compiled.mark)
        assertEquals("fontmake took 6 s · Fontbakery 0.12.10: 0 fail · 12 warn · 147 pass", compiled.detail)
        assertEquals("fontmake took under 1 s", list(situation(built = ShipBuilt(0, null))).getValue(ShipItemId.COMPILED).detail)
    }

    // ---- Own licence ---------------------------------------------------------------------------------

    @Test
    fun ownLicenceDropsEveryLineThatIsOnlyForGoogleFonts() {
        val ids = shipChecklist(situation(mode = ShipMode.OWN_LICENCE)).map { it.id }
        assertEquals(
            listOf(ShipItemId.CHECK, ShipItemId.NAMES, ShipItemId.LICENCE, ShipItemId.VENDOR_ID, ShipItemId.FILES, ShipItemId.COMPILED),
            ids,
        )
    }

    @Test
    fun ownLicenceOffersOflProprietaryAndOtherAndShowsTheOneTheProjectHas() {
        fun chosen(info: UfoFontInfo) =
            list(situation(info = info, mode = ShipMode.OWN_LICENCE)).getValue(ShipItemId.LICENCE).choices?.chosen
        assertEquals(OWN_OFL, chosen(ShipFixtures.completeInfo))
        val proprietary =
            ShipFixtures.completeInfo.copy(
                openTypeNameLicense = "Proprietary. All rights reserved.",
                openTypeNameLicenseURL = null,
            )
        assertEquals(OWN_PROPRIETARY, chosen(proprietary))
        val other = ShipFixtures.completeInfo.copy(openTypeNameLicense = "Free for friends.", openTypeNameLicenseURL = null)
        assertEquals(OWN_OTHER, chosen(other))
        val silent =
            ShipFixtures.completeInfo.copy(
                openTypeNameLicense = null,
                openTypeNameLicenseURL = null,
                copyright = "Copyright 2026 A",
            )
        assertNull(chosen(silent))
        val options = list(situation(mode = ShipMode.OWN_LICENCE)).getValue(ShipItemId.LICENCE).choices?.options
        assertEquals(listOf("OFL", "Proprietary", "Other"), options?.map { it.label })
    }

    @Test
    fun ownLicenceAlwaysShowsTheVendorId() {
        val set = list(situation(mode = ShipMode.OWN_LICENCE)).getValue(ShipItemId.VENDOR_ID)
        assertEquals(ShipMark.DONE, set.mark)
        assertEquals("Vendor ID: HYLE", set.title)
        val unset = list(situation(info = ShipFixtures.completeInfo.copy(openTypeOS2VendorID = null), mode = ShipMode.OWN_LICENCE))
        assertEquals(ShipMark.WARN, unset.getValue(ShipItemId.VENDOR_ID).mark)
    }

    @Test
    fun ownLicenceWithNoLicenceWordsAtAllAsksForOne() {
        val words = ShipFixtures.completeInfo.copy(openTypeNameLicense = "Proprietary", openTypeNameLicenseURL = null)
        val written = list(situation(info = words, mode = ShipMode.OWN_LICENCE)).getValue(ShipItemId.LICENCE)
        assertEquals(ShipMark.DONE, written.mark)
        assertEquals("LICENSE.txt in your words", written.title)
        val bare = ShipFixtures.completeInfo.copy(openTypeNameLicense = null, openTypeNameLicenseURL = null)
        val item = list(situation(info = bare, mode = ShipMode.OWN_LICENCE)).getValue(ShipItemId.LICENCE)
        assertEquals(ShipMark.WARN, item.mark)
        assertEquals("No licence is chosen", item.title)
    }
}
