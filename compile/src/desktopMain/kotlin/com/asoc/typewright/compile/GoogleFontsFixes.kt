// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.compile

/**
 * The fix-ups a compiled font needs to pass Google Fonts' own checker that belong to the build and not to the drawing, run by the same Python
 * that ran fontmake, with fontTools only. They are what `gftools fix-nonhinting` and `gftools fix-font` do to a font from a UFO:
 *
 * - OS/2 `fsType` becomes 0, installable embedding (ufo2ft writes 4, preview and print, unless the UFO says otherwise);
 * - OS/2 `fsSelection` bit 7, `USE_TYPO_METRICS`, is set;
 * - a font that is not hinted (no `fpgm`) gets the `gasp` table that asks for grid-fitting and smoothing at every size, and the `prep` program
 *   that turns on smart dropout control: `PUSHW 0x01FF SCANCTRL PUSHB 0x04 SCANTYPE`, which the checker looks for byte for byte.
 *
 * Each change is printed as a line that begins with [MARKER], so the log can say what was done; anything else the script prints is not for
 * the log. A font that already has a value is left as it is.
 */
internal object GoogleFontsFixes {
    const val MARKER: String = "typewright-fix: "

    /** The line the script prints last when it ran to the end. */
    const val DONE: String = "done"

    /** Run as `python -c SCRIPT <font file>`; it rewrites the file in place and keeps the timestamps fontmake wrote. */
    val SCRIPT: String =
        """
        # typewright-google-fonts-fixes
        import sys
        from fontTools.ttLib import TTFont, newTable
        from fontTools.ttLib.tables import ttProgram

        path = sys.argv[1]
        font = TTFont(path, recalcTimestamp=False)
        said = []
        os2 = font["OS/2"]
        if os2.fsType != 0:
            said.append("OS/2 fsType %d -> 0 (installable embedding)" % os2.fsType)
            os2.fsType = 0
        if not os2.fsSelection & (1 << 7):
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
        print("$MARKER$DONE")
        """.trimIndent()

    /** What the script reported: the changes it made, or null when its output does not show that it ran to the end. */
    fun parse(output: String): List<String>? {
        val lines =
            output
                .lineSequence()
                .map { it.trim() }
                .filter { it.startsWith(MARKER) }
                .map { it.removePrefix(MARKER) }
                .toList()
        if (lines.lastOrNull() != DONE) return null
        return lines.dropLast(1)
    }
}
