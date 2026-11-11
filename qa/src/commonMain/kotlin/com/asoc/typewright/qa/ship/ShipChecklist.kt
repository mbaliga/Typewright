// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.ship

import com.asoc.typewright.project.imports.LicenceKind
import com.asoc.typewright.qa.report.FontInfoTarget
import com.asoc.typewright.qa.report.Severity
import com.asoc.typewright.qa.report.withThousands

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
    BRANCH,
    DATE_ADDED,
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

    /** The Font info value [field] names. */
    data class FontInfo(
        val field: ShipFontInfoField,
    ) : ShipRoute

    /** The part of Font info that a Check finding's action names; the screen lands on the first value of it that is not set. */
    data class FontInfoAt(
        val target: FontInfoTarget,
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

/**
 * A short text a line lets the person type in place: [kind] says which fact it is, [value] is what is stated now (null when nothing is), and
 * [hint] says what to type. The vendor ID goes into the project's font info; the branch and the day Google Fonts added the family are
 * Ship's own, kept for this run only, because the project has no place for them.
 */
data class ShipInput(
    val kind: Kind,
    val value: String?,
    val hint: String,
) {
    enum class Kind { VENDOR_ID, BRANCH, DATE_ADDED }
}

/**
 * One line of the checklist. A line with [inDetails] is not one of the board's rows: the page keeps it behind its Details link, with a count
 * of how many of them still need the person.
 */
data class ShipItem(
    val id: ShipItemId,
    val mark: ShipMark,
    val title: String,
    val detail: String?,
    val action: ShipAction? = null,
    val choices: ShipChoices? = null,
    val input: ShipInput? = null,
    val inDetails: Boolean = false,
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

/**
 * What the Names check found, for the checklist: its [severity], the one [found] sentence it gave (what is odd, or why it could not
 * measure), and how many family names it [compared] this one with in our corpus (null when it compared none).
 */
data class ShipNames(
    val severity: Severity,
    val found: String,
    val compared: Int?,
)

/** Everything the checklist is made from. */
class ShipSituation(
    val mode: ShipMode,
    val facts: ShipFacts,
    val gate: ShipGate,
    /** Layer two's tally, `14 checks · 0 fail · 4 warn`, or null when there is no report. */
    val checkTally: String?,
    /** What the Names check found, or null when there is no report. */
    val names: ShipNames?,
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
    /** The branch the person has stated for `upstream.yaml`, or null. */
    val branch: String? = null,
    /** The day the person has stated Google Fonts added the family, or null. */
    val dateAdded: String? = null,
)

/**
 * The Ship page's checklist (SCREENS_V1 S16), in the order the board lists it, with the lines the board does not have after them and marked
 * [ShipItem.inDetails]. A line is ✓ only when the project states the fact it is about, ◐ when the project does not (with a link to the Font
 * info value that fixes it, or a place to type it), and ● for a Check that fails. Nothing is written for a missing fact: the line says what
 * is missing and where to set it.
 */
fun shipChecklist(situation: ShipSituation): List<ShipItem> =
    when (situation.mode) {
        ShipMode.GOOGLE_FONTS -> googleFontsChecklist(situation)
        ShipMode.OWN_LICENCE -> ownLicenceChecklist(situation)
    }

private fun googleFontsChecklist(s: ShipSituation): List<ShipItem> {
    val files =
        if (s.compile.available) {
            filesLine(s, "METADATA.pb and upstream.yaml", "Written with the font, from the lines above")
        } else {
            filesLine(
                s,
                "The sources, OFL.txt and DESCRIPTION.en_us.html",
                "Written without a font; METADATA.pb and upstream.yaml describe the font file, so they wait for one",
            )
        }
    return listOfNotNull(
        checkLine(s),
        namesLine(s),
        googleFontsLicenceLine(s),
        designerLine(s),
        descriptionLine(s),
        compiledLine(s),
        categoryLine(s),
        repositoryLine(s),
        vendorLine(s, alwaysShown = false)?.copy(inDetails = true),
        branchLine(s),
        dateAddedLine(s),
        files.copy(inDetails = true),
    )
}

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
    val toNames = ShipAction("Set them in Font info", ShipRoute.FontInfo(ShipFontInfoField.FAMILY))
    val found = s.names
    return when {
        found == null -> {
            ShipItem(ShipItemId.NAMES, ShipMark.WARN, "Names have not been checked", names.ifEmpty { null }, toNames)
        }

        found.severity == Severity.PASS -> {
            // What was measured, and no more: a name is unique among the families it was compared with, not in the world.
            val compared = found.compared?.takeIf { it > 0 }
            if (compared != null) {
                ShipItem(
                    ShipItemId.NAMES,
                    ShipMark.DONE,
                    "No name clash among ${compared.withThousands()} families",
                    "In our corpus; our heuristic · $names",
                )
            } else {
                ShipItem(ShipItemId.NAMES, ShipMark.DONE, "Names are set", "Not compared with other families here · $names")
            }
        }

        found.severity == Severity.FAIL -> {
            ShipItem(ShipItemId.NAMES, ShipMark.FAIL, "Names need attention", found.found, toNames)
        }

        found.severity == Severity.WARN -> {
            // Checked, and something odd found: the line says what, and does not say it was not checked.
            ShipItem(ShipItemId.NAMES, ShipMark.WARN, "Names need a look", found.found, toNames)
        }

        else -> {
            ShipItem(ShipItemId.NAMES, ShipMark.WARN, "Names could not be measured", found.found, toNames)
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
        ShipItem(ShipItemId.REPOSITORY, ShipMark.DONE, "Repository address is set", url, inDetails = true)
    } else {
        ShipItem(
            ShipItemId.REPOSITORY,
            ShipMark.WARN,
            "No repository address",
            "Google Fonts wants it in the copyright line, in brackets",
            ShipAction("Font info", ShipRoute.FontInfo(ShipFontInfoField.COPYRIGHT)),
            inDetails = true,
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
        ShipItem(ShipItemId.CATEGORY, ShipMark.DONE, "Category: ${s.category.label}", where, null, choices, inDetails = true)
    } else {
        ShipItem(
            ShipItemId.CATEGORY,
            ShipMark.WARN,
            "Category is not chosen",
            "METADATA needs one of these five",
            null,
            choices,
            inDetails = true,
        )
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
    val input = ShipInput(ShipInput.Kind.VENDOR_ID, vendor?.takeUnless { unset }, "Four letters, such as ACME")
    return when {
        unset -> {
            val why =
                if (s.mode == ShipMode.GOOGLE_FONTS) {
                    "Four letters that are yours; Google Fonts' checker warns on ????"
                } else {
                    "Four letters that are yours, in the font's OS/2 table; none is set"
                }
            ShipItem(ShipItemId.VENDOR_ID, ShipMark.WARN, "Vendor ID is not set", why, input = input)
        }

        alwaysShown -> {
            ShipItem(ShipItemId.VENDOR_ID, ShipMark.DONE, "Vendor ID: $vendor", null, input = input)
        }

        else -> {
            null
        }
    }
}

private fun branchLine(s: ShipSituation): ShipItem? {
    if (!s.compile.available && s.built?.withFont != true) return null
    val branch = s.branch
    val input = ShipInput(ShipInput.Kind.BRANCH, branch, "The branch, such as main")
    return if (branch != null) {
        ShipItem(ShipItemId.BRANCH, ShipMark.DONE, "upstream.yaml follows branch $branch", "You stated it", input = input, inDetails = true)
    } else {
        ShipItem(
            ShipItemId.BRANCH,
            ShipMark.WARN,
            "upstream.yaml needs a branch",
            "It names the branch your repository publishes from; the project does not know it, so the file is left out until you state one",
            input = input,
            inDetails = true,
        )
    }
}

private fun dateAddedLine(s: ShipSituation): ShipItem? {
    if (!s.compile.available && s.built?.withFont != true) return null
    val day = s.dateAdded
    val input = ShipInput(ShipInput.Kind.DATE_ADDED, day, "The day, as 2026-10-24")
    return if (day != null) {
        ShipItem(ShipItemId.DATE_ADDED, ShipMark.DONE, "METADATA.pb date_added: $day", "You stated it", input = input, inDetails = true)
    } else {
        ShipItem(
            ShipItemId.DATE_ADDED,
            ShipMark.WARN,
            "METADATA.pb has no date_added",
            "Google Fonts sets it on the day it accepts the family; state the day here if you know it",
            input = input,
            inDetails = true,
        )
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

/** The lines of [items] that are the board's own rows. */
fun List<ShipItem>.mainRows(): List<ShipItem> = filter { !it.inDetails }

/** The lines of [items] that sit behind the page's Details link. */
fun List<ShipItem>.detailRows(): List<ShipItem> = filter { it.inDetails }
