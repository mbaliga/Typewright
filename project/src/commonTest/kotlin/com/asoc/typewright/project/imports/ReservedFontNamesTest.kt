// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project.imports

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The declarations below are the real copyright lines of the faces in `data/learn-faces` (read with
 * fontTools), plus the shapes the OFL FAQ allows that none of them happens to use.
 */
class ReservedFontNamesTest {
    private fun names(text: String) = parseReservedFontNames(text)

    @Test
    fun aQuotedNameEndingTheSentence() {
        assertEquals(
            listOf("Josefin Sans"),
            names(
                "Copyright 2010 The Josefin Sans Project Authors (https://github.com/ThomasJockin/JosefinSansFont-master), with Reserved Font Name \"Josefin Sans\".",
            ),
        )
        assertEquals(
            listOf("Playfair Display"),
            names(
                "Copyright 2017 The Playfair Display Project Authors (https://github.com/clauseggers/Playfair-Display), with Reserved Font Name \"Playfair Display\".",
            ),
        )
    }

    @Test
    fun curlyQuotes() {
        assertEquals(listOf("Source"), names("© 2023 Adobe (http://www.adobe.com/), with Reserved Font Name ‘Source’"))
        assertEquals(listOf("Foo Bar"), names("with Reserved Font Name “Foo Bar”."))
    }

    @Test
    fun anUnquotedNameStopsAtTheFullStopThatEndsTheSentence() {
        assertEquals(
            listOf("Limelight"),
            names(
                "Copyright (c) 2010 by Sorkin Type Co with Reserved Font Name Limelight.\r\rThis Font Software is licensed under the SIL Open Font License, Version 1.1.",
            ),
        )
        assertEquals(
            listOf("UnifrakturMaguntia"),
            names("Copyright (c) 2010 j. 'mach' wust with Reserved Font Name UnifrakturMaguntia.\nCopyright (c) 2009 Peter Wiegel."),
        )
    }

    @Test
    fun aListOfQuotedNames() {
        assertEquals(
            listOf("Varela", "Varela Round"),
            names("with Reserved Font Names 'Varela' and 'Varela Round'"),
        )
        assertEquals(
            listOf("Abril", "Abril Fatface"),
            names("Copyright (c) 2011, TypeTogether (www.type-together.com), with Reserved Font Names \"Abril\" and \"Abril Fatface\""),
        )
    }

    @Test
    fun aColonAfterTheMarker() {
        assertEquals(listOf("Orbitron"), names("with Reserved Font Name: \"Orbitron\"."))
        assertEquals(listOf("Orbitron"), names("Reserved Font Name: Orbitron"))
    }

    @Test
    fun aTrailingUrlInBracketsIsNotPartOfTheName() {
        assertEquals(listOf("Foo Sans"), names("with Reserved Font Name \"Foo Sans\" (http://foo.example/sans)."))
        assertEquals(listOf("Foo Sans"), names("with Reserved Font Name Foo Sans (http://foo.example/sans)."))
        assertEquals(listOf("Foo"), names("(with Reserved Font Name Foo)"))
    }

    @Test
    fun aNameWithDotsInsideIt() {
        assertEquals(listOf("Foo.Sans"), names("with Reserved Font Name Foo.Sans."))
        assertEquals(listOf("Foo v2.0"), names("with Reserved Font Name \"Foo v2.0\"."))
        assertEquals(listOf("Foo v2.0"), names("with Reserved Font Name Foo v2.0."))
    }

    @Test
    fun anApostropheInsideASingleQuotedName() {
        assertEquals(listOf("Foo's Bar"), names("with Reserved Font Name 'Foo's Bar'."))
    }

    @Test
    fun anUnquotedListSplitsOnCommasAndAnd() {
        assertEquals(listOf("Foo", "Bar", "Baz"), names("with Reserved Font Names Foo, Bar and Baz."))
    }

    @Test
    fun theLicencesOwnBoilerplateIsNotADeclaration() {
        val boilerplate =
            "\"Reserved Font Name\" refers to any names specified as such after the copyright statement(s).\n" +
                "No Modified Version of the Font Software may use the Reserved Font Name(s) " +
                "unless explicit written permission is granted.\n" +
                "Copyright (c) <year>, <copyright holder> (<URL|email>), with Reserved Font Name <Reserved Font Name>."
        assertEquals(emptyList(), names(boilerplate))
    }

    @Test
    fun noReservedFontNameIsNone() {
        assertEquals(emptyList(), names("Copyright 2020 The Jost Project Authors, with no Reserved Font Name."))
        assertEquals(emptyList(), names("Copyright 2020 Foo, without Reserved Font Names."))
        assertEquals(emptyList(), names("Copyright 2020 The Poppins Project Authors (https://github.com/itfoundry/Poppins)"))
    }

    @Test
    fun theCopyrightAndTheLicenceAreReadTogetherWithoutRepeats() {
        val copyright = "Copyright (c) 2010, j. 'mach' wust, with Reserved Font Name UnifrakturMaguntia."
        val licence = "Copyright (c) 2010 with Reserved Font Name unifrakturmaguntia.\n\nThis Font Software is licensed..."
        assertEquals(listOf("UnifrakturMaguntia"), reservedFontNames(copyright, licence, null))
    }
}
