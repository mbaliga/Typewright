// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

import com.asoc.typewright.core.font.ufo.parsePlistDict

/**
 * The fix-ups a compiled font needs to pass Google Fonts' own checker that belong to the build and not to the drawing, run by the same Python
 * that ran fontmake, with fontTools only. They are what `gftools fix-nonhinting` and `gftools fix-font` do to a font from a UFO:
 *
 * - OS/2 `fsType` becomes 0, installable embedding (ufo2ft writes 4, preview and print, when the UFO says nothing);
 * - OS/2 `fsSelection` bit 7, `USE_TYPO_METRICS`, is set;
 * - a font that is not hinted (no `fpgm`) gets the `gasp` table that asks for grid-fitting and smoothing at every size, and the `prep` program
 *   that turns on smart dropout control: `PUSHW 0x01FF SCANCTRL PUSHB 0x04 SCANTYPE`, which the checker looks for byte for byte.
 *
 * **A value the project states is never changed.** The embedding bits (`openTypeOS2Type`) are a licence setting, and `openTypeOS2Selection`
 * is the person's own choice of style flags. When the UFO's `fontinfo.plist` has the key, [statedBy] says so, the script is told, and it
 * leaves that value as the project wrote it and reports the line instead, behind [KEPT_MARKER]. Google Fonts' own checker then names what
 * is wrong on the compiled font, and the person decides. A font that already has a `gasp` or a `prep` keeps it.
 *
 * Each change is printed as a line that begins with [MARKER], and each value left alone as a line that begins with [KEPT_MARKER], so the
 * log can say what was done and what was not; anything else the script prints is not for the log.
 */
internal object GoogleFontsFixes {
    const val MARKER: String = "typewright-fix: "

    /** The line the script prints for a value it left alone because the project states it. */
    const val KEPT_MARKER: String = "typewright-kept: "

    /** What the script is told when the project states the embedding bits. */
    const val STATED_FS_TYPE: String = "fsType"

    /** What the script is told when the project states the OS/2 selection flags. */
    const val STATED_FS_SELECTION: String = "fsSelection"

    /** The line the script prints last when it ran to the end. */
    const val DONE: String = "done"

    /**
     * Run as `python -c SCRIPT <font file> <stated>...`, where each `<stated>` is [STATED_FS_TYPE] or [STATED_FS_SELECTION]; it rewrites the
     * file in place and keeps the timestamps fontmake wrote.
     */
    val SCRIPT: String =
        """
        # typewright-google-fonts-fixes
        import sys
        from fontTools.ttLib import TTFont, newTable
        from fontTools.ttLib.tables import ttProgram

        path = sys.argv[1]
        stated = set(sys.argv[2:])
        font = TTFont(path, recalcTimestamp=False)
        said = []
        kept = []
        os2 = font["OS/2"]
        if os2.fsType != 0:
            if "$STATED_FS_TYPE" in stated:
                kept.append("OS/2 fsType stays %d: the project states it (openTypeOS2Type); Google Fonts asks for 0" % os2.fsType)
            else:
                said.append("OS/2 fsType %d -> 0 (installable embedding)" % os2.fsType)
                os2.fsType = 0
        if not os2.fsSelection & (1 << 7):
            if "$STATED_FS_SELECTION" in stated:
                kept.append("OS/2 fsSelection stays without USE_TYPO_METRICS: the project states it (openTypeOS2Selection)")
            else:
                os2.fsSelection |= 1 << 7
                said.append("OS/2 fsSelection: USE_TYPO_METRICS set")
        if "fpgm" not in font:
            if "gasp" not in font:
                gasp = newTable("gasp")
                gasp.gaspRange = {0xFFFF: 0x000F}
                font["gasp"] = gasp
                said.append("gasp table added (grid-fit and smooth at every size)")
            if "prep" not in font:
                prep = newTable("prep")
                prep.program = ttProgram.Program()
                prep.program.fromAssembly(["PUSHW[]", "511", "SCANCTRL[]", "PUSHB[]", "4", "SCANTYPE[]"])
                font["prep"] = prep
                said.append("prep table added (smart dropout control, as for any unhinted font)")
        if said:
            font.save(path)
        for line in said:
            print("$MARKER" + line)
        for line in kept:
            print("$KEPT_MARKER" + line)
        print("$MARKER$DONE")
        """.trimIndent()

    /** What the script reported: the changes it made, and the values it left alone because the project states them. */
    class Report(
        val changes: List<String>,
        val kept: List<String>,
    )

    /** What the script reported, or null when its output does not show that it ran to the end. */
    fun parseReport(output: String): Report? {
        val lines = output.lineSequence().map { it.trim() }.toList()
        val marked = lines.filter { it.startsWith(MARKER) }.map { it.removePrefix(MARKER) }
        if (marked.lastOrNull() != DONE) return null
        val kept = lines.filter { it.startsWith(KEPT_MARKER) }.map { it.removePrefix(KEPT_MARKER) }
        return Report(marked.dropLast(1), kept)
    }

    /** The changes the script made, or null when its output does not show that it ran to the end. */
    fun parse(output: String): List<String>? = parseReport(output)?.changes

    /**
     * What the project states that the script must not change, by the names the script knows: [STATED_FS_TYPE] when the UFO's
     * `fontinfo.plist` has `openTypeOS2Type` and [STATED_FS_SELECTION] when it has `openTypeOS2Selection`. A `fontinfo.plist` that cannot be
     * read states nothing here (fontmake refuses such a UFO before the script runs).
     */
    fun statedBy(ufoFiles: Map<String, ByteArray>): List<String> {
        val text = ufoFiles["fontinfo.plist"]?.decodeToString() ?: return emptyList()
        val keys = runCatching { parsePlistDict(text).entries.map { it.first } }.getOrDefault(emptyList())
        return listOfNotNull(
            STATED_FS_TYPE.takeIf { "openTypeOS2Type" in keys },
            STATED_FS_SELECTION.takeIf { "openTypeOS2Selection" in keys },
        )
    }
}
