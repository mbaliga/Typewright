// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.qa.ship

import dev.aarso.typewright.core.font.ufo.UfoFontInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShipPipelineTest {
    @Test
    fun copyrightLineMatchesTheGfPatternExactly() {
        assertEquals(
            "Copyright 2026 The Hyle Deco Project Authors (https://github.com/example/hyle-deco)",
            gfCopyrightLine(2026, "Hyle Deco", "https://github.com/example/hyle-deco"),
        )
    }

    @Test
    fun oflTextStartsWithTheCopyrightLineAndCarriesTheStandardBody() {
        val text = generateOflText("Hyle Deco", 2026, "https://github.com/example/hyle-deco")
        val lines = text.lines()
        assertEquals("Copyright 2026 The Hyle Deco Project Authors (https://github.com/example/hyle-deco)", lines[0])
        assertEquals("", lines[1])
        assertTrue(text.contains("This Font Software is licensed under the SIL Open Font License, Version 1.1."))
        assertTrue(text.contains("SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007"))
        assertTrue(text.contains("https://openfontlicense.org"))
        assertTrue(text.trimEnd().endsWith("OTHER DEALINGS IN THE FONT SOFTWARE."))
        // The licence body itself never changes per family -- only the first line does.
        val other = generateOflText("Other Family", 2020, "https://example.com/other")
        assertEquals(text.substringAfter("\n\n"), other.substringAfter("\n\n"))
    }

    @Test
    fun descriptionHtmlNamesTheFamilyAndLinksTheGitUrl() {
        val html = generateDescriptionHtml("Hyle Deco", "https://github.com/example/hyle-deco")
        assertTrue(html.contains("Hyle Deco"))
        assertTrue(html.contains("<a href=\"https://github.com/example/hyle-deco\">"))
        assertTrue(html.startsWith("<p>"))
    }

    @Test
    fun descriptionHtmlEscapesItsInputs() {
        val html = generateDescriptionHtml("A & B", "https://example.com/\"quote\"")
        assertTrue(html.contains("A &amp; B"))
        assertTrue(html.contains("&quot;quote&quot;"))
    }

    @Test
    fun metadataPbCarriesTheRequestedFieldsAndTheSameCopyrightPattern() {
        val fontInfo = UfoFontInfo(familyName = "Hyle Deco", styleName = "Regular")
        val text = generateMetadataPbText(fontInfo, "Hyle Deco", "A. Designer", 2026, "https://github.com/example/hyle-deco")

        assertTrue(text.contains("name: \"Hyle Deco\""))
        assertTrue(text.contains("designer: \"A. Designer\""))
        assertTrue(text.contains("license: \"OFL\""))
        assertTrue(text.contains("style: \"normal\""))
        assertTrue(text.contains("filename: \"HyleDeco-Regular.ttf\""))
        assertTrue(text.contains("post_script_name: \"HyleDeco-Regular\""))
        assertTrue(text.contains("full_name: \"Hyle Deco Regular\""))
        assertTrue(
            text.contains(
                "copyright: \"Copyright 2026 The Hyle Deco Project Authors (https://github.com/example/hyle-deco)\"",
            ),
        )
    }

    @Test
    fun metadataPbDetectsAnItalicStyleName() {
        val fontInfo = UfoFontInfo(styleName = "Bold Italic")
        val text = generateMetadataPbText(fontInfo, "Hyle Deco", "A. Designer", 2026, "https://example.com/hd")
        assertTrue(text.contains("style: \"italic\""))
    }

    @Test
    fun metadataPbDefaultsStyleNameToRegularWhenFontInfoHasNone() {
        val text = generateMetadataPbText(UfoFontInfo(), "Hyle Deco", "A. Designer", 2026, "https://example.com/hd")
        assertTrue(text.contains("full_name: \"Hyle Deco Regular\""))
        assertTrue(text.contains("style: \"normal\""))
    }

    @Test
    fun metadataPbNamesFieldsItDoesNotInventRatherThanFabricatingValues() {
        val text = generateMetadataPbText(UfoFontInfo(), "Hyle Deco", "A. Designer", 2026, "https://example.com/hd")
        assertTrue(text.contains("# category:"))
        assertTrue(text.contains("# date_added:"))
        assertTrue(text.contains("# subsets:"))
    }
}
