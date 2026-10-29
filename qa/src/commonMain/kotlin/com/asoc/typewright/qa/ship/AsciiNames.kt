// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.ship

// The names a repository folder gives its files (the font, the UFO, the family directory) are plain ASCII, whatever the family is called:
// the same printable ASCII (0x20 to 0x7E) that the compile backend gives its own copy of the UFO and the font it writes, because a Java whose
// file-name encoding is ASCII cannot write any other name, and because Google Fonts' tooling expects them. The names inside the files
// (METADATA.pb's `name`, the OFL's copyright line, the font's own name table) keep the person's letters.

/**
 * [text] in plain ASCII: a letter of Latin-1 or Latin Extended-A becomes the ASCII letters it is written with (`é` is `e`, `ß` is `ss`, `Æ` is
 * `AE`), and anything else outside printable ASCII (a letter of another script, a combining mark, a symbol, a control character) is left out,
 * because there are no ASCII letters that stand for it. Printable ASCII is kept as it is.
 */
internal fun asciiFolded(text: String): String {
    val out = StringBuilder(text.length)
    for (c in text) {
        when {
            c.code in ASCII_FIRST..ASCII_LAST -> out.append(c)
            else -> FOLDED[c]?.let { out.append(it) }
        }
    }
    return out.toString()
}

private const val ASCII_FIRST = 0x20
private const val ASCII_LAST = 0x7E

private val FOLDED: Map<Char, String> =
    buildMap {
        fun add(
            letters: String,
            to: String,
        ) {
            for (letter in letters) put(letter, to)
        }
        add("ÀÁÂÃÄÅĀĂĄǍ", "A")
        add("àáâãäåāăąǎ", "a")
        add("ÆǼ", "AE")
        add("æǽ", "ae")
        add("ÇĆĈĊČ", "C")
        add("çćĉċč", "c")
        add("ĎĐÐ", "D")
        add("ďđð", "d")
        add("ÈÉÊËĒĔĖĘĚ", "E")
        add("èéêëēĕėęě", "e")
        add("ĜĞĠĢ", "G")
        add("ĝğġģ", "g")
        add("ĤĦ", "H")
        add("ĥħ", "h")
        add("ÌÍÎÏĨĪĬĮİǏ", "I")
        add("ìíîïĩīĭįıǐ", "i")
        add("Ĳ", "IJ")
        add("ĳ", "ij")
        add("Ĵ", "J")
        add("ĵ", "j")
        add("Ķ", "K")
        add("ķĸ", "k")
        add("ĹĻĽĿŁ", "L")
        add("ĺļľŀł", "l")
        add("ÑŃŅŇ", "N")
        add("ñńņňŉ", "n")
        add("ÒÓÔÕÖØŌŎŐǑǾ", "O")
        add("òóôõöøōŏőǒǿ", "o")
        add("Œ", "OE")
        add("œ", "oe")
        add("ŔŖŘ", "R")
        add("ŕŗř", "r")
        add("ŚŜŞŠ", "S")
        add("śŝşš", "s")
        add("ß", "ss")
        add("ŢŤŦ", "T")
        add("ţťŧ", "t")
        add("Þ", "Th")
        add("þ", "th")
        add("ÙÚÛÜŨŪŬŮŰŲǓ", "U")
        add("ùúûüũūŭůűųǔ", "u")
        add("Ŵ", "W")
        add("ŵ", "w")
        add("ÝŶŸ", "Y")
        add("ýÿŷ", "y")
        add("ŹŻŽ", "Z")
        add("źżž", "z")
    }
