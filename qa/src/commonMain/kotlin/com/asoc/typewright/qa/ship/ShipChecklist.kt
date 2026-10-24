// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.ship

import com.asoc.typewright.project.imports.LicenceKind
import com.asoc.typewright.qa.report.Severity

/**
 * A checklist line's state as a shape and a word, never a colour alone: ● the thing stops the build, ◐ it needs the person (a fact the
 * project does not state), ✓ it is in order, ○ it happens when the build runs.
 */
enum class ShipMark(
    val shape: String,
    val word: String,
) {
    FAIL("●", "fail"),
    WARN("◐", "needs you"),
    DONE("✓", "done"),
    TODO("○", "when you build"),
}

/** Which line of the checklist an item is. */
enum class ShipItemId {
    CHECK,
    NAMES,
    LICENCE,
    DESIGNER,
    REPOSITORY,
    CATEGORY,
    DESCRIPTION,
    VENDOR_ID,
    FILES,
    COMPILED,
}

/**
 * The part of Font info a line's action opens. Each name is the name of the `FontInfoField` of the same name in `ui`, which a test there
 * checks, so a link from Ship lands on the value to change and never on a dead end.
 */
enum class ShipFontInfoField {
    FAMILY,
    STYLE,
    DESIGNER,
    COPYRIGHT,
    LICENCE_TEXT,
    VENDOR_ID,
}

/** Where a line's action goes: the Check page, or a value on Font info. */
sealed interface ShipRoute {
    data object Check : ShipRoute

    data class FontInfo(
        val field: ShipFontInfoField,
    ) : ShipRoute
}

/** A link under a line: the words (without the trailing ›) and where it goes. */
data class ShipAction(
    val label: String,
    val route: ShipRoute,
)

/** A small set of choices on a line: the id and the words of each, and which one is chosen. */
data class ShipChoices(
    val kind: Kind,
    val options: List<ShipOption>,
    val chosen: String?,
) {
    enum class Kind { CATEGORY, LICENCE }
}

data class ShipOption(
    val id: String,
    val label: String,
)

/** One line of the checklist. */
data class ShipItem(
    val id: ShipItemId,
    val mark: ShipMark,
    val title: String,
    val detail: String?,
    val action: ShipAction? = null,
    val choices: ShipChoices? = null,
)

/** Whether this device can compile, as the screen says it: [title] names the line, [words] is the backend's own sentence. */
data class ShipCapability(
    val available: Boolean,
    val title: String,
    val words: String,
)

/**
 * What a finished write adds to the checklist: whether a font was compiled and written ([withFont]; a phone or a browser writes the folder
 * without one), how long the compile took, and the one-line tally of the check on the binary.
 */
data class ShipBuilt(
    val compileSeconds: Int?,
    val layerOneLine: String?,
    val withFont: Boolean = true,
)

/** Everything the checklist is made from. */
class ShipSituation(
    val mode: ShipMode,
    val facts: ShipFacts,
    val gate: ShipGate,
    /** Layer two's tally, `14 checks · 0 fail · 4 warn`, or null when there is no report. */
    val checkTally: String?,
    /** What the Names check found, or null when there is no report. */
    val names: Severity?,
    val category: GfCategory?,
    /**
     * The style class [category] was read from, in words (`art deco display`), when it is Typewright's reading of a class the person gave
     * and not the person's own choice; null otherwise.
     */
    val categoryClass: String?,
    /** The size of the description draft, in bytes. */
    val descriptionBytes: Int,
    val compile: ShipCapability,
    /** Null until a build has finished. */
    val built: ShipBuilt? = null,
)

/**
 * The Ship page's checklist (SCREENS_V1 S16), in the order the board lists it. A line is ✓ only when the project states the fact it is about,
 * ◐ when the project does not (with a link to the Font info value that fixes it), and ● for a Check that fails. Nothing is written for a
 * missing fact: the line says what is missing and where to set it.
 */
fun shipChecklist(situation: ShipSituation): List<ShipItem> =
    when (situation.mode) {
        ShipMode.GOOGLE_FONTS -> googleFontsChecklist(situation)
        ShipMode.OWN_LICENCE -> ownLicenceChecklist(situation)
    }

private fun googleFontsChecklist(s: ShipSituation): List<ShipItem> =
    listOfNotNull(
        checkLine(s),
        namesLine(s),
        googleFontsLicenceLine(s),
        designerLine(s),
        repositoryLine(s),
        categoryLine(s),
        descriptionLine(s),
        vendorLine(s, alwaysShown = false),
        if (s.compile.available) {
            filesLine(s, "METADATA.pb and upstream.yaml", "Written with the font, from the lines above")
        } else {
            filesLine(
                s,
                "The sources, OFL.txt and DESCRIPTION.en_us.html",
                "Written without a font; METADATA.pb and upstream.yaml describe the font file, so they wait for one",
            )
        },
        compiledLine(s),
    )

private fun ownLicenceChecklist(s: ShipSituation): List<ShipItem> =
    listOfNotNull(
        checkLine(s),
        namesLine(s),
        ownLicenceLine(s),
        vendorLine(s, alwaysShown = true),
        filesLine(s, "The font, its sources and the licence file", "Written together; nothing here is for Google Fonts"),
        compiledLine(s),
    )

private fun checkLine(s: ShipSituation): ShipItem =
    if (s.gate.blocked) {
        ShipItem(ShipItemId.CHECK, ShipMark.FAIL, s.gate.title(), null, ShipAction("Fix them in Check", ShipRoute.Check))
    } else {
        ShipItem(ShipItemId.CHECK, ShipMark.DONE, s.gate.title(), s.checkTally)
    }

private fun namesLine(s: ShipSituation): ShipItem {
    val facts = s.facts
    val names = listOfNotNull(facts.family, facts.style, facts.postScriptName ?: facts.fileStem).joinToString(" · ")
    return when (s.names) {
        Severity.PASS -> {
            ShipItem(ShipItemId.NAMES, ShipMark.DONE, "Names are unique", names)
        }

        Severity.FAIL -> {
            ShipItem(
                ShipItemId.NAMES,
                ShipMark.FAIL,
                "Names need attention",
                "Check's Names line says why",
                ShipAction("Set them in Font info", ShipRoute.FontInfo(ShipFontInfoField.FAMILY)),
            )
        }

        null, Severity.WARN, Severity.NOT_MEASURED -> {
            ShipItem(
                ShipItemId.NAMES,
                ShipMark.WARN,
                "Names have not been checked",
                names.ifEmpty { null },
                ShipAction("Set them in Font info", ShipRoute.FontInfo(ShipFontInfoField.FAMILY)),
            )
        }
    }
}

private fun googleFontsLicenceLine(s: ShipSituation): ShipItem {
    val facts = s.facts
    val copyright = facts.copyright
    val toCopyright = ShipAction("Set it in Font info", ShipRoute.FontInfo(ShipFontInfoField.COPYRIGHT))
    return when {
        copyright == null -> {
            ShipItem(
                ShipItemId.LICENCE,
                ShipMark.WARN,
                "OFL.txt needs your copyright line",
                "Its first line is your copyright line, and none is set",
                toCopyright,
            )
        }

        facts.licence != LicenceKind.OFL -> {
            ShipItem(
                ShipItemId.LICENCE,
                ShipMark.WARN,
                "Google Fonts takes the Open Font License",
                "This project's licence reads as ${licenceWords(facts.licence)}, so no OFL.txt is written",
                ShipAction("Choose the licence in Font info", ShipRoute.FontInfo(ShipFontInfoField.LICENCE_TEXT)),
            )
        }

        facts.reservedFontNames.isNotEmpty() -> {
            ShipItem(
                ShipItemId.LICENCE,
                ShipMark.WARN,
                "Google Fonts does not take a Reserved Font Name",
                "The copyright line reserves ${facts.reservedFontNames.joinToString(" and ") { "“$it”" }}",
                toCopyright,
            )
        }

        else -> {
            ShipItem(ShipItemId.LICENCE, ShipMark.DONE, "OFL.txt with your copyright line", copyright.substringBefore(" (http"))
        }
    }
}

private fun ownLicenceLine(s: ShipSituation): ShipItem {
    val facts = s.facts
    val choices =
        ShipChoices(
            ShipChoices.Kind.LICENCE,
            listOf(ShipOption(OWN_OFL, "OFL"), ShipOption(OWN_PROPRIETARY, "Proprietary"), ShipOption(OWN_OTHER, "Other")),
            chosen =
                when (facts.licence) {
                    LicenceKind.OFL -> OWN_OFL
                    LicenceKind.PROPRIETARY -> OWN_PROPRIETARY
                    LicenceKind.NONE -> null
                    else -> OWN_OTHER
                },
        )
    val toLicence = ShipAction("Write it in Font info", ShipRoute.FontInfo(ShipFontInfoField.LICENCE_TEXT))
    return when {
        facts.licence == LicenceKind.NONE -> {
            ShipItem(
                ShipItemId.LICENCE,
                ShipMark.WARN,
                "No licence is chosen",
                "Pick one; its file is written with the font",
                null,
                choices,
            )
        }

        facts.licence == LicenceKind.OFL && facts.copyright == null -> {
            ShipItem(
                ShipItemId.LICENCE,
                ShipMark.WARN,
                "OFL.txt needs your copyright line",
                "Its first line is your copyright line, and none is set",
                ShipAction("Set it in Font info", ShipRoute.FontInfo(ShipFontInfoField.COPYRIGHT)),
                choices,
            )
        }

        facts.licence == LicenceKind.OFL -> {
            ShipItem(
                ShipItemId.LICENCE,
                ShipMark.DONE,
                "OFL.txt with your copyright line",
                facts.copyright?.substringBefore(" (http"),
                null,
                choices,
            )
        }

        facts.licenceText == null && facts.licenceUrl == null -> {
            ShipItem(
                ShipItemId.LICENCE,
                ShipMark.WARN,
                "LICENSE.txt has no words yet",
                "Write your licence in Font info; the file is made from it",
                toLicence,
                choices,
            )
        }

        else -> {
            ShipItem(ShipItemId.LICENCE, ShipMark.DONE, "LICENSE.txt in your words", licenceWords(facts.licence), null, choices)
        }
    }
}

/** The ids of Own licence's three choices. */
const val OWN_OFL: String = "ofl"
const val OWN_PROPRIETARY: String = "proprietary"
const val OWN_OTHER: String = "other"

private fun licenceWords(kind: LicenceKind): String =
    when (kind) {
        LicenceKind.OFL -> "the SIL Open Font License"
        LicenceKind.APACHE -> "the Apache License"
        LicenceKind.PROPRIETARY -> "proprietary"
        LicenceKind.USE_LIMITED -> "a licence that limits how the font may be used"
        LicenceKind.OTHER -> "a licence Typewright does not recognise"
        LicenceKind.NONE -> "no licence"
    }

private fun designerLine(s: ShipSituation): ShipItem {
    val designer = s.facts.designer
    return if (designer != null) {
        ShipItem(ShipItemId.DESIGNER, ShipMark.DONE, "Designer: $designer", null)
    } else {
        ShipItem(
            ShipItemId.DESIGNER,
            ShipMark.WARN,
            "Designer is not set",
            "METADATA needs one",
            ShipAction("Font info", ShipRoute.FontInfo(ShipFontInfoField.DESIGNER)),
        )
    }
}

private fun repositoryLine(s: ShipSituation): ShipItem {
    val url = s.facts.repositoryUrl
    return if (url != null) {
        ShipItem(ShipItemId.REPOSITORY, ShipMark.DONE, "Repository address is set", url)
    } else {
        ShipItem(
            ShipItemId.REPOSITORY,
            ShipMark.WARN,
            "No repository address",
            "Google Fonts wants it in the copyright line, in brackets",
            ShipAction("Font info", ShipRoute.FontInfo(ShipFontInfoField.COPYRIGHT)),
        )
    }
}

private fun categoryLine(s: ShipSituation): ShipItem {
    val choices =
        ShipChoices(
            ShipChoices.Kind.CATEGORY,
            GfCategory.entries.map { ShipOption(it.protoName, it.label) },
            s.category?.protoName,
        )
    return if (s.category != null) {
        val where =
            if (s.categoryClass != null) {
                "Read from your style class, ${s.categoryClass}, by our heuristic. Choose another if it is wrong."
            } else {
                "You chose it."
            }
        ShipItem(ShipItemId.CATEGORY, ShipMark.DONE, "Category: ${s.category.label}", where, null, choices)
    } else {
        ShipItem(ShipItemId.CATEGORY, ShipMark.WARN, "Category is not chosen", "METADATA needs one of these five", null, choices)
    }
}

private fun descriptionLine(s: ShipSituation): ShipItem {
    val built = s.built != null
    return when {
        s.descriptionBytes <= DESCRIPTION_MIN_BYTES -> {
            ShipItem(
                ShipItemId.DESCRIPTION,
                ShipMark.WARN,
                "DESCRIPTION.en_us.html is short",
                "The draft is ${s.descriptionBytes} bytes; Google Fonts' checker wants more than $DESCRIPTION_MIN_BYTES. Write your own.",
            )
        }

        built -> {
            ShipItem(ShipItemId.DESCRIPTION, ShipMark.DONE, "DESCRIPTION.en_us.html", "A draft from your brief, ready to edit")
        }

        else -> {
            ShipItem(ShipItemId.DESCRIPTION, ShipMark.TODO, "DESCRIPTION.en_us.html", "A draft from your brief, ready to edit")
        }
    }
}

private fun vendorLine(
    s: ShipSituation,
    alwaysShown: Boolean,
): ShipItem? {
    val vendor = s.facts.vendorId
    val unset = vendor == null || vendor.all { it == '?' }
    val toVendor = ShipAction("Font info", ShipRoute.FontInfo(ShipFontInfoField.VENDOR_ID))
    return when {
        unset -> {
            ShipItem(
                ShipItemId.VENDOR_ID,
                ShipMark.WARN,
                "Vendor ID is not set",
                "Four letters that are yours; Google Fonts' checker warns on ????",
                toVendor,
            )
        }

        alwaysShown -> {
            ShipItem(ShipItemId.VENDOR_ID, ShipMark.DONE, "Vendor ID: $vendor", null)
        }

        else -> {
            null
        }
    }
}

private fun filesLine(
    s: ShipSituation,
    title: String,
    detail: String,
): ShipItem =
    if (s.built != null) {
        ShipItem(ShipItemId.FILES, ShipMark.DONE, title, "Written")
    } else {
        ShipItem(ShipItemId.FILES, ShipMark.TODO, title, detail)
    }

private fun compiledLine(s: ShipSituation): ShipItem =
    when {
        s.built != null && s.built.withFont -> {
            val took = s.built.compileSeconds?.let { "fontmake took ${if (it < 1) "under 1 s" else "$it s"}" }
            ShipItem(
                ShipItemId.COMPILED,
                ShipMark.DONE,
                s.compile.title,
                listOfNotNull(took, s.built.layerOneLine).joinToString(" · ").ifEmpty { null },
            )
        }

        s.compile.available -> {
            ShipItem(ShipItemId.COMPILED, ShipMark.TODO, s.compile.title, s.compile.words)
        }

        else -> {
            ShipItem(ShipItemId.COMPILED, ShipMark.WARN, s.compile.title, s.compile.words)
        }
    }
