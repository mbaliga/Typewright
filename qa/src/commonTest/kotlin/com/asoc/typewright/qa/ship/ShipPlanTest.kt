// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.ship

import com.asoc.typewright.core.font.ufo.PlistValue
import com.asoc.typewright.core.font.ufo.UfoFontInfo
import com.asoc.typewright.qa.ship.ShipFixtures.text
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShipPlanTest {
    private val plan = ShipFixtures.plan()

    @Test
    fun googleFontsModeWritesExactlyTheFilesOfTheUpstreamLayout() {
        assertEquals(
            listOf(
                "AUTHORS.txt",
                "DESCRIPTION.en_us.html",
                "METADATA.pb",
                "OFL.txt",
                "documentation/GOOGLE_FONTS_REQUEST.md",
                "fonts/ttf/HyleDeco-Regular.ttf",
                "sources/HyleDeco-Regular.ufo/fontinfo.plist",
                "sources/HyleDeco-Regular.ufo/glyphs/A_.glif",
                "sources/HyleDeco-Regular.ufo/glyphs/contents.plist",
                "sources/HyleDeco-Regular.ufo/metainfo.plist",
                "upstream.yaml",
            ),
            plan.paths,
        )
        assertEquals(emptyList(), plan.omissions)
    }

    @Test
    fun theTreeFoldsTheUfoIntoOneLineAndTheUfoIsTheOnlyOwnedDirectory() {
        assertEquals(
            listOf(
                "AUTHORS.txt",
                "DESCRIPTION.en_us.html",
                "METADATA.pb",
                "OFL.txt",
                "documentation/GOOGLE_FONTS_REQUEST.md",
                "fonts/ttf/HyleDeco-Regular.ttf",
                "sources/HyleDeco-Regular.ufo/",
                "upstream.yaml",
            ),
            plan.tree,
        )
        assertEquals(listOf("sources/HyleDeco-Regular.ufo"), plan.ownedDirectories)
        val noUfo =
            buildShipPlan(
                ShipFixtures.inputs().let {
                    ShipInputs(
                        it.mode,
                        it.facts,
                        it.font,
                        it.brief,
                        it.ufoFolderName,
                        emptyMap(),
                        it.binary,
                        it.category,
                        it.layerOne,
                        it.dateAdded,
                        it.branch,
                    )
                },
            )
        assertEquals(emptyList(), noUfo.ownedDirectories)
    }

    @Test
    fun theTreeOnThePageIsTheBoardsFiveLinesAndTheRequestTextIsAlsoWritten() {
        assertEquals(
            listOf(
                "fonts/ttf/HyleDeco-Regular.ttf",
                "sources/HyleDeco-Regular.ufo",
                "OFL.txt · AUTHORS.txt",
                "DESCRIPTION.en_us.html",
                "METADATA.pb · upstream.yaml",
            ),
            plan.boardLines,
        )
        assertEquals(listOf(REQUEST_TEXT_PATH), plan.alsoWritten)
        // Every file of the folder is on exactly one of the two, so the page cannot promise less than is written.
        assertEquals(
            plan.tree.map { it.removeSuffix("/") }.sorted(),
            (plan.boardLines.flatMap { it.split(" · ") } + plan.alsoWritten).sorted(),
        )
    }

    @Test
    fun aLineOfTheBoardsTreeHoldsOnlyTheFilesThatAreThere() {
        val noDesigner = ShipFixtures.plan(ShipFixtures.completeInfo.copy(openTypeNameDesigner = null))
        assertEquals("OFL.txt", noDesigner.boardLines[2])
        val noFont = ShipFixtures.plan(binary = null)
        assertEquals(listOf("sources/HyleDeco-Regular.ufo", "OFL.txt · AUTHORS.txt", "DESCRIPTION.en_us.html"), noFont.boardLines)
        val noBranch = buildShipPlan(ShipFixtures.inputs(branch = null))
        assertEquals("METADATA.pb", noBranch.boardLines.last())
        val own = ShipFixtures.plan(mode = ShipMode.OWN_LICENCE)
        assertEquals(listOf("fonts/ttf/HyleDeco-Regular.ttf", "sources/HyleDeco-Regular.ufo", "OFL.txt"), own.boardLines)
        assertEquals(emptyList(), own.alsoWritten)
        val proprietary =
            ShipFixtures.plan(
                ShipFixtures.completeInfo.copy(openTypeNameLicense = "Proprietary. Licensed to one studio.", openTypeNameLicenseURL = null),
                mode = ShipMode.OWN_LICENCE,
            )
        assertEquals("LICENSE.txt", proprietary.boardLines[2])
    }

    @Test
    fun theSourcesAreTheProjectsUfoByteForByteAndTheFontIsTheCompiledBinary() {
        for ((inside, bytes) in ShipFixtures.ufo) {
            assertContentEquals(bytes, plan.files.getValue("sources/HyleDeco-Regular.ufo/$inside"), inside)
        }
        assertContentEquals(ShipFixtures.binary, plan.files.getValue("fonts/ttf/HyleDeco-Regular.ttf"))
    }

    @Test
    fun theOflStartsWithTheProjectsCopyrightLineAndIsFollowedByTheLicenceUnaltered() {
        val ofl = plan.text("OFL.txt")
        assertEquals(ShipFixtures.COPYRIGHT, ofl.lines().first())
        assertEquals(ShipFixtures.COPYRIGHT + "\n" + OFL_BODY + "\n", ofl)
        assertTrue(ofl.contains("SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007"))
        assertTrue(ofl.endsWith("OTHER DEALINGS IN THE FONT SOFTWARE.\n"))
    }

    @Test
    fun theOflBodyIsWrappedTheWayGoogleFontsKeepsIt() {
        // The lines Google Fonts' own template wraps differently from SIL's page: a different wrapping is what its checker reports.
        assertTrue(OFL_BODY.contains("development of collaborative font projects, to support the font creation\nefforts of academic"))
        assertTrue(OFL_BODY.startsWith("\nThis Font Software is licensed under the SIL Open Font License, Version 1.1.\n"))
    }

    @Test
    fun theDescriptionIsADraftFromTheProjectAndItsBriefWithNoPlaceholder() {
        val html = plan.text("DESCRIPTION.en_us.html")
        assertEquals(
            "<p>\nHyle Deco is an art deco display typeface for watch faces and posters.\n</p>\n" +
                "<p>\nThe style in this folder is Regular. It has 337 glyphs covering Latin. " +
                "It is drawn on an em of 1000 units, with a cap height of 700 and an x-height of 500.\n</p>\n" +
                "<p>\nTo contribute, see <a href=\"https://github.com/example/hyle-deco\">github.com/example/hyle-deco</a>\n</p>\n",
            html,
        )
        assertTrue(html.encodeToByteArray().size > DESCRIPTION_MIN_BYTES)
        assertFalse(html.contains("Replace", ignoreCase = true))
        assertFalse(html.contains("TODO"))
    }

    @Test
    fun theDescriptionEscapesWhatTheProjectSays() {
        val info = ShipFixtures.completeInfo.copy(familyName = "A & B <C>")
        val html = ShipFixtures.plan(info).text("DESCRIPTION.en_us.html")
        assertTrue(html.contains("A &amp; B &lt;C&gt; is"), html)
    }

    @Test
    fun theDescriptionSaysNothingTheBriefDoesNotSay() {
        val inputs = ShipFixtures.inputs(brief = ShipBrief())
        val html = buildShipPlan(inputs).text("DESCRIPTION.en_us.html")
        assertTrue(html.startsWith("<p>\nHyle Deco is a typeface.\n</p>\n"), html)
        assertFalse(html.contains(" for "))
    }

    @Test
    fun theMetadataIsGoogleFontsProtobufTextWithTheProjectsValues() {
        assertEquals(
            """name: "Hyle Deco"
designer: "A. Designer"
license: "OFL"
category: "DISPLAY"
date_added: "2026-10-24"
fonts {
  name: "Hyle Deco"
  style: "normal"
  weight: 400
  filename: "HyleDeco-Regular.ttf"
  post_script_name: "HyleDeco-Regular"
  full_name: "Hyle Deco Regular"
  copyright: "${ShipFixtures.COPYRIGHT}"
}
subsets: "latin"
subsets: "latin-ext"
subsets: "menu"
source {
  repository_url: "https://github.com/example/hyle-deco"
}
""",
            plan.text("METADATA.pb"),
        )
    }

    @Test
    fun upstreamYamlMapsTheFilesIntoTheFamilyDirectory() {
        assertEquals(
            """name: Hyle Deco
repository_url: "https://github.com/example/hyle-deco"
branch: main
files:
  OFL.txt: OFL.txt
  DESCRIPTION.en_us.html: DESCRIPTION.en_us.html
  METADATA.pb: METADATA.pb
  fonts/ttf/HyleDeco-Regular.ttf: HyleDeco-Regular.ttf
""",
            plan.text("upstream.yaml"),
        )
        assertEquals("hyledeco", plan.familyDirectory)
        assertEquals(
            setOf("OFL.txt", "DESCRIPTION.en_us.html", "METADATA.pb", "HyleDeco-Regular.ttf"),
            plan.familyFiles.keys,
        )
        assertContentEquals(ShipFixtures.binary, plan.familyFiles.getValue("HyleDeco-Regular.ttf"))
        assertContentEquals(plan.files.getValue("METADATA.pb"), plan.familyFiles.getValue("METADATA.pb"))
    }

    @Test
    fun theRequestTextStatesOnlyWhatIsTrue() {
        val text = plan.requestText
        assertNotNull(text)
        assertEquals(plan.text(REQUEST_TEXT_PATH), text)
        assertTrue(text.contains("- Family: Hyle Deco (Regular, 400)"))
        assertTrue(text.contains("- Designer: A. Designer"))
        assertTrue(text.contains("- Category: Display"))
        assertTrue(text.contains("- Upstream repository: https://github.com/example/hyle-deco"))
        assertTrue(text.contains("- Sources: `sources/HyleDeco-Regular.ufo` (UFO 3)"))
        assertTrue(text.contains("Fontbakery 0.12.10's Google Fonts profile on the compiled font: 0 fail · 12 warn · 147 pass"))
        assertTrue(text.contains("The full report is not attached."))
    }

    @Test
    fun theRequestTextLeavesOutALayerOneTheBuildDidNotRun() {
        val inputs = ShipFixtures.inputs(layerOne = null)
        assertFalse(buildShipPlan(inputs).requestText.orEmpty().contains("Fontbakery"))
    }

    // ---- what the project does not state -----------------------------------------------------------

    @Test
    fun withoutADesignerThereIsNoDesignerLineAndNoAuthorsFile() {
        val plan = ShipFixtures.plan(ShipFixtures.completeInfo.copy(openTypeNameDesigner = null))
        assertFalse("AUTHORS.txt" in plan.files)
        assertFalse(plan.text("METADATA.pb").contains("designer"))
        assertFalse(plan.text(REQUEST_TEXT_PATH).contains("Designer"))
        assertTrue(plan.omissions.any { it.path == "AUTHORS.txt" })
        assertTrue(plan.omissions.any { it.path == "METADATA.pb designer" })
    }

    @Test
    fun withoutACopyrightThereIsNoOflAndNoCopyrightLine() {
        val plan = ShipFixtures.plan(ShipFixtures.completeInfo.copy(copyright = null))
        assertFalse("OFL.txt" in plan.files)
        assertFalse(plan.text("METADATA.pb").contains("copyright"))
        assertFalse(plan.text("METADATA.pb").contains("repository_url"), "the address lives in the copyright line")
        assertFalse(plan.text("upstream.yaml").contains("repository_url"))
        assertFalse("OFL.txt" in plan.text("upstream.yaml"), "a file that is not written is not mapped")
        assertTrue(plan.omissions.any { it.path == "OFL.txt" })
    }

    @Test
    fun aLicenceThatIsNotTheOflGetsNoOflFile() {
        val info =
            ShipFixtures.completeInfo.copy(
                openTypeNameLicense = "Licensed under the Apache License, Version 2.0",
                openTypeNameLicenseURL = null,
            )
        val plan = ShipFixtures.plan(info)
        assertFalse("OFL.txt" in plan.files)
        assertEquals("the project's licence is not the SIL Open Font License", plan.omissions.single { it.path == "OFL.txt" }.reason)
    }

    @Test
    fun withoutACategoryTheMetadataHasNone() {
        val plan = ShipFixtures.plan(category = null)
        assertFalse(plan.text("METADATA.pb").contains("category"))
        assertTrue(plan.omissions.any { it.path == "METADATA.pb category" })
        assertFalse(plan.text(REQUEST_TEXT_PATH).contains("Category"))
    }

    @Test
    fun aStyleThatIsNotAWeightAndNoWeightClassLeavesTheWeightOut() {
        val info = ShipFixtures.completeInfo.copy(styleName = "Wobbly", other = emptyList())
        val plan = ShipFixtures.plan(info)
        assertFalse(plan.text("METADATA.pb").contains("weight"))
        assertTrue(plan.omissions.any { it.path == "METADATA.pb weight" })
    }

    @Test
    fun anItalicIsNamedItalicInTheMetadata() {
        val info = ShipFixtures.completeInfo.copy(styleName = "Bold Italic", other = emptyList())
        val metadata = ShipFixtures.plan(info).text("METADATA.pb")
        assertTrue(metadata.contains("  style: \"italic\"\n  weight: 700\n"), metadata)
        assertTrue(metadata.contains("filename: \"HyleDeco-BoldItalic.ttf\""))
    }

    @Test
    fun withoutABinaryNoFileThatDescribesOneIsWritten() {
        val plan = ShipFixtures.plan(binary = null)
        assertFalse(plan.paths.any { it.startsWith("fonts/") })
        assertFalse("METADATA.pb" in plan.files)
        assertFalse("upstream.yaml" in plan.files)
        assertTrue("OFL.txt" in plan.files && "DESCRIPTION.en_us.html" in plan.files)
        assertTrue(plan.paths.any { it.startsWith("sources/") })
        assertEquals(setOf("fonts/ttf/HyleDeco-Regular.ttf", "METADATA.pb", "upstream.yaml"), plan.omissions.map { it.path }.toSet())
        assertFalse(plan.text(REQUEST_TEXT_PATH).contains("Font file"))
    }

    @Test
    fun aFamilyWithNoNamesIsNamedAfterItsUfoFolder() {
        val inputs =
            ShipFixtures.inputs(
                info = UfoFontInfo(copyright = ShipFixtures.COPYRIGHT, openTypeNameLicense = ShipFixtures.OFL_TEXT),
            )
        val plan = buildShipPlan(inputs)
        assertTrue(plan.paths.contains("sources/HyleDeco-Regular.ufo/metainfo.plist"))
        assertTrue(plan.paths.contains("fonts/ttf/HyleDeco-Regular.ttf"))
    }

    // ---- what only the person can state --------------------------------------------------------------

    @Test
    fun aDateAddedThePersonDidNotStateIsNotWrittenAndNoOtherDateStandsInForIt() {
        val plan = buildShipPlan(ShipFixtures.inputs(dateAdded = null))
        val metadata = plan.text("METADATA.pb")
        assertFalse(metadata.contains("date_added"))
        assertFalse(
            Regex("""\d{4}-\d{2}-\d{2}""").containsMatchIn(metadata),
            "no date at all: the day the folder is made is not the day Google adds it",
        )
        assertEquals(DATE_ADDED_UNKNOWN, plan.omissions.single { it.path == "METADATA.pb date_added" }.reason)
        assertTrue(
            buildShipPlan(ShipFixtures.inputs()).text("METADATA.pb").contains("date_added: \"2026-10-24\"\n"),
            "a stated day is written as stated",
        )
    }

    @Test
    fun withoutABranchThePersonStatedThereIsNoUpstreamYamlAndNothingIsGuessed() {
        val plan = buildShipPlan(ShipFixtures.inputs(branch = null))
        assertFalse("upstream.yaml" in plan.files)
        assertEquals(UPSTREAM_WITHOUT_BRANCH, plan.omissions.single { it.path == "upstream.yaml" }.reason)
        assertTrue("METADATA.pb" in plan.files, "the rest of the folder is written")
        assertFalse(plan.paths.any { it.endsWith("upstream.yaml") })
    }

    @Test
    fun aBranchThePersonStatesIsWrittenAsStatedAndNoOtherBranchAppears() {
        val yaml = buildShipPlan(ShipFixtures.inputs(branch = "trunk")).text("upstream.yaml")
        assertTrue(yaml.contains("\nbranch: trunk\n"), yaml)
        assertFalse(yaml.contains("main"))
    }

    // ---- names that are not ASCII ----------------------------------------------------------------------

    @Test
    fun aFamilyWithAnAccentShipsUnderOneAsciiNameAndKeepsItsLettersInsideTheFiles() {
        val info = ShipFixtures.completeInfo.copy(familyName = "Hylé Deco", postscriptFontName = null)
        val plan = ShipFixtures.plan(info)
        // Every path of the folder, the font, the UFO and the request, is plain ASCII, and they all use the one stem.
        assertTrue(plan.paths.all { path -> path.all { it.code in 0x20..0x7E } }, plan.paths.toString())
        assertTrue("fonts/ttf/HyleDeco-Regular.ttf" in plan.files)
        assertTrue("sources/HyleDeco-Regular.ufo/metainfo.plist" in plan.files)
        assertEquals("hyledeco", plan.familyDirectory)
        assertEquals(setOf("OFL.txt", "DESCRIPTION.en_us.html", "METADATA.pb", "HyleDeco-Regular.ttf"), plan.familyFiles.keys)
        assertEquals("hyle-deco-gf", ShipFacts.of(info).folderName, "the proposed folder name is ASCII too, and is not cut at the accent")
        // The names inside the files keep the person's letters.
        val metadata = plan.text("METADATA.pb")
        assertTrue(metadata.contains("name: \"Hylé Deco\"\n"), metadata)
        assertTrue(metadata.contains("  full_name: \"Hylé Deco Regular\"\n"), metadata)
        assertTrue(metadata.contains("  filename: \"HyleDeco-Regular.ttf\"\n"), metadata)
        assertTrue(metadata.contains("  post_script_name: \"HyleDeco-Regular\"\n"), "a PostScript name is ASCII by the specification")
        assertTrue(plan.text("upstream.yaml").contains("name: \"Hylé Deco\"\n"))
        assertTrue(plan.text("DESCRIPTION.en_us.html").contains("Hylé Deco is"))
    }

    @Test
    fun aFamilyThatHasNoAsciiLettersAtAllIsNamedAfterItsUfoFolderAndNeverEmpty() {
        val info = ShipFixtures.completeInfo.copy(familyName = "あいう", postscriptFontName = null)
        val facts = ShipFacts.of(info)
        assertNull(facts.fileStem, "nothing of it is left to name a file")
        assertEquals("HyleDeco-Regular", shipFileStem(facts, "HyleDeco-Regular.ufo"))
        assertEquals("font", shipFileStem(facts, "あ.ufo"), "even the folder has no ASCII letter: a plain name, never an empty one")
        assertEquals("", googleFontsDirectoryName("あいう"))
        assertNull(facts.folderName)
    }

    // ---- Own licence ---------------------------------------------------------------------------------

    @Test
    fun ownLicenceModeWritesNothingThatIsOnlyForGoogleFonts() {
        val plan = ShipFixtures.plan(mode = ShipMode.OWN_LICENCE)
        for (only in listOf("METADATA.pb", "upstream.yaml", "DESCRIPTION.en_us.html", "AUTHORS.txt", REQUEST_TEXT_PATH)) {
            assertFalse(only in plan.files, only)
        }
        assertNull(plan.requestText)
        assertNull(plan.familyDirectory)
        assertEquals(emptyMap(), plan.familyFiles)
        assertEquals(
            listOf(
                "OFL.txt",
                "fonts/ttf/HyleDeco-Regular.ttf",
                "sources/HyleDeco-Regular.ufo/fontinfo.plist",
                "sources/HyleDeco-Regular.ufo/glyphs/A_.glif",
                "sources/HyleDeco-Regular.ufo/glyphs/contents.plist",
                "sources/HyleDeco-Regular.ufo/metainfo.plist",
            ),
            plan.paths,
        )
    }

    @Test
    fun ownLicenceWithAProprietaryLicenceWritesTheProjectsOwnWords() {
        val info =
            ShipFixtures.completeInfo.copy(
                copyright = "Copyright 2026 Studio",
                openTypeNameLicense = "Proprietary. Licensed to one studio.",
                openTypeNameLicenseURL = "https://example.org/eula",
            )
        val plan = ShipFixtures.plan(info, mode = ShipMode.OWN_LICENCE)
        assertFalse("OFL.txt" in plan.files)
        assertEquals(
            "Copyright 2026 Studio\n\nProprietary. Licensed to one studio.\n\nhttps://example.org/eula\n",
            plan.text("LICENSE.txt"),
        )
    }

    @Test
    fun ownLicenceWithNoWordsWritesNoLicenceFileAndSaysSo() {
        val info =
            ShipFixtures.completeInfo.copy(
                openTypeNameLicense = null,
                openTypeNameLicenseURL = null,
                copyright = "Copyright 2026 Studio",
            )
        val plan = ShipFixtures.plan(info, mode = ShipMode.OWN_LICENCE)
        assertFalse("LICENSE.txt" in plan.files)
        assertFalse("OFL.txt" in plan.files)
        assertEquals("the project states no licence text of its own", plan.omissions.single { it.path == "LICENSE.txt" }.reason)
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    @Test
    fun protobufStringsEscapeOnlyWhatMustBeEscaped() {
        assertEquals("\"a\\\\b\\\"c\\n\\td\"", protoString("a\\b\"c\n\td"))
        assertEquals("\"Julián Ødegård\"", protoString("Julián Ødegård"), "letters outside ASCII are written as they are")
        assertEquals("\"\\x01\"", protoString("\u0001"))
    }

    @Test
    fun yamlScalarsAreQuotedWhenPlainWouldBeWrong() {
        assertEquals("Hyle Deco", yamlScalar("Hyle Deco"))
        assertEquals("fonts/ttf/A-B.ttf", yamlScalar("fonts/ttf/A-B.ttf"))
        assertEquals("\"https://x.org/a\"", yamlScalar("https://x.org/a"))
        assertEquals("\"yes\"", yamlScalar("yes"))
        assertEquals("\"a: b\"", yamlScalar("a: b"))
        assertEquals("\"#x\"", yamlScalar("#x"))
        assertEquals("\"say \\\"hi\\\"\"", yamlScalar("say \"hi\""))
    }

    @Test
    fun categoriesAreReadFromTheStyleAtlasKeys() {
        val expected =
            mapOf(
                "sans-geometric" to GfCategory.SANS_SERIF,
                "sans-grotesque" to GfCategory.SANS_SERIF,
                "sans-neogrotesque" to GfCategory.SANS_SERIF,
                "sans-humanist" to GfCategory.SANS_SERIF,
                "sans-rounded" to GfCategory.SANS_SERIF,
                "sans-superellipse" to GfCategory.SANS_SERIF,
                "sans-glyphic" to GfCategory.SANS_SERIF,
                "serif-garalde" to GfCategory.SERIF,
                "serif-transitional" to GfCategory.SERIF,
                "serif-didone" to GfCategory.SERIF,
                "serif-venetian" to GfCategory.SERIF,
                "serif-scotch" to GfCategory.SERIF,
                "serif-fatface" to GfCategory.DISPLAY,
                "slab" to GfCategory.SERIF,
                "script-formal" to GfCategory.HANDWRITING,
                "script-informal" to GfCategory.HANDWRITING,
                "script-handwritten" to GfCategory.HANDWRITING,
                "display-artdeco" to GfCategory.DISPLAY,
                "display-techno" to GfCategory.DISPLAY,
                "display-stencil" to GfCategory.DISPLAY,
                "display-inline" to GfCategory.DISPLAY,
                "blackletter" to GfCategory.DISPLAY,
                "monospace" to GfCategory.MONOSPACE,
            )
        for ((key, category) in expected) assertEquals(category, GfCategory.fromStyleKey(key), key)
        assertNull(GfCategory.fromStyleKey("something-new"))
        assertNull(GfCategory.fromStyleKey(null))
    }

    @Test
    fun theSubsetsFollowTheCharactersTheFontMaps() {
        fun subsets(codepoints: Set<Int>) =
            subsetsOf(ShipFixtures.inputs(font = ShipFixtures.font.let { ShipFont(1, codepoints, it.scripts, null, null) }))
        assertEquals(listOf("latin", "latin-ext", "menu"), subsets((0x41..0x5A).toSet() + 0x100))
        assertEquals(listOf("latin", "menu"), subsets((0x41..0x5A).toSet()))
        assertEquals(listOf("menu"), subsets(setOf(0x915)), "only Latin is read; a Devanagari font gets menu alone")
    }

    @Test
    fun theGoogleFontsDirectoryIsTheFamilyInLowerCaseWithoutSpaces() {
        assertEquals("hyledeco", googleFontsDirectoryName("Hyle Deco"))
        assertEquals("opensans", googleFontsDirectoryName("Open Sans"))
        assertEquals("a1b", googleFontsDirectoryName("A-1 B!"))
    }

    @Test
    fun theFontIsNamedByTheFactsTheProjectStatesNotByTheFolderOfAnAdoptedUfo() {
        val facts = ShipFacts.of(ShipFixtures.completeInfo)
        assertEquals("HyleDeco-Regular", shipFileStem(facts, "Regular.ufo"))
        val noStyle = ShipFacts.of(ShipFixtures.completeInfo.copy(styleName = null))
        assertEquals("Master-1", shipFileStem(noStyle, "Master-1.ufo"), "with no style the folder it already has names it")
        // And the plan uses the very same name for the font, the UFO and the request.
        val plan = ShipFixtures.plan(ShipFixtures.completeInfo)
        assertTrue("fonts/ttf/HyleDeco-Regular.ttf" in plan.files)
    }

    @Test
    fun theWeightClassInOtherIsWhatTheMetadataStates() {
        val info = ShipFixtures.completeInfo.copy(other = listOf("openTypeOS2WeightClass" to PlistValue.PInteger(300)))
        assertTrue(ShipFixtures.plan(info).text("METADATA.pb").contains("  weight: 300\n"))
    }
}
