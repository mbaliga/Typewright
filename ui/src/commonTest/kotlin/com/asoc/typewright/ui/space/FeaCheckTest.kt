// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [FeaCheck]: the commonest slips in a `.fea` text, found by line, and nothing flagged in text that is well formed. */
class FeaCheckTest {
    private fun messages(text: String) = FeaCheck.check(text).map { it.line to it.message }

    @Test
    fun aWellFormedKernFeatureHasNoIssues() {
        val text = "# kerning\nfeature kern {\n    pos T o -60;\n    pos @A @V -90;\n} kern;\n"
        assertEquals(emptyList(), FeaCheck.check(text))
    }

    @Test
    fun nestedLookupsTablesAndUnnamedBlocksPass() {
        val text =
            """
            languagesystem DFLT dflt;
            @round = [o O c C];
            lookup tight {
                pos @round @round -10;
            } tight;
            feature kern {
                lookup inner {
                    pos n o -12;
                } inner;
                lookup tight;
            } kern;
            feature cv01 {
                cvParameters {
                    FeatUILabelNameID {
                        name "Alternate n";
                    };
                };
                sub n by n.alt;
            } cv01;
            table GDEF {
                GlyphClassDef [n o], , , ;
            } GDEF;
            """.trimIndent()
        assertEquals(emptyList(), FeaCheck.check(text))
    }

    @Test
    fun anUnclosedBlockIsNamedWhereItWasOpened() {
        assertEquals(listOf(2 to "feature kern is opened here and never closed"), messages("# a\nfeature kern {\n    pos T o -60;\n"))
    }

    @Test
    fun aBlockMustEndWithItsOwnName() {
        val issues = messages("feature kern {\n pos T o -60;\n} kerm;\n")
        assertEquals(1, issues.size)
        assertEquals(3, issues.single().first)
        assertTrue("should end with } kern;" in issues.single().second, issues.single().second)
    }

    @Test
    fun aNamedBlockClosedWithJustASemicolonIsFlagged() {
        val issues = messages("feature kern {\n pos T o -60;\n};\n")
        assertEquals(1, issues.size)
        assertTrue("should end with } kern;" in issues.single().second)
    }

    @Test
    fun aMissingSemicolonAfterTheClosingNameIsFlaggedAtTheEnd() {
        val issues = messages("feature kern {\n pos T o -60;\n} kern")
        assertEquals(listOf(3), issues.map { it.first })
    }

    @Test
    fun anExtraClosingBraceIsFlagged() {
        assertEquals(listOf(3 to "A closing brace with no block open"), messages("feature kern {\n} kern;\n}\n"))
    }

    @Test
    fun squareBracketsMustMatch() {
        assertEquals(listOf(1 to "A square bracket is opened here and never closed"), messages("@a = [n o;\n"))
        assertEquals(listOf(1 to "A closing bracket with no opening one"), messages("@a = n o];\n"))
    }

    @Test
    fun aStringMustEnd() {
        assertEquals(listOf(2 to "A string starts here and never ends"), messages("x;\nname \"abc;\n"))
    }

    @Test
    fun bracesInCommentsAndStringsAreNotCounted() {
        val text = "# } stray\nfeature kern { # { open\n    name \"} not a brace {\";\n    pos T o -60;\n} kern;\n"
        assertEquals(emptyList(), FeaCheck.check(text))
    }

    @Test
    fun issuesComeInLineOrderAndAnEmptyTextHasNone() {
        val text = "feature a {\n} b;\nfeature c {\n"
        val lines = FeaCheck.check(text).map { it.line }
        assertEquals(lines.sorted(), lines)
        assertEquals(emptyList(), FeaCheck.check(""))
    }
}
