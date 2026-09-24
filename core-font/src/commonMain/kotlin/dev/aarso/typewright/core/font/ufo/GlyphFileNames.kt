package dev.aarso.typewright.core.font.ufo

/**
 * Converts a glyph name to the `.glif` file name UFO 3's "Glyph Naming Convention" (part of the
 * spec) requires: illegal filesystem characters replaced, a trailing `_` added after every
 * character that is not its own lowercase form (so `"A"` and `"a"` cannot collide on a
 * case-insensitive filesystem), and Windows-reserved base names (`"con"`, `"aux"`, ...) prefixed
 * with `_`. Ported line-for-line from `fontTools.ufoLib.filenames.userNameToFileName` (MIT;
 * itself copied from ufoLib, copyright the RoboFab developers — see `THIRD_PARTY.md`), which is
 * the algorithm every real UFO tool (RoboFont, FontForge's UFO import, `fontmake`) uses, so a
 * project this module writes gets exactly the file names those tools would also produce or expect.
 *
 * [existing] is every already-used file name, compared case-insensitively (as the spec requires),
 * used to resolve a collision the base algorithm's escaping did not prevent (for example
 * `"A"` and `".notdef"` never collide, but a font with an unusual glyph named literally `"A_"`
 * alongside one named `"A"` would, and this function keeps both by falling back to
 * [handleFileNameClash1] then [handleFileNameClash2], exactly as the reference implementation
 * does — reproduced here mainly to document *why* [UfoProject]'s writer keeps a running "used
 * names" set rather than mapping each glyph name independently).
 */
fun userNameToFileName(
    userName: String,
    existing: Set<String> = emptySet(),
    prefix: String = "",
    suffix: String = "",
): String {
    var name = userName
    require(name.isNotEmpty()) { "a glyph name must not be empty" }
    if (prefix.isEmpty() && name[0] == '.') {
        name = "_" + name.substring(1)
    }
    val filtered = StringBuilder(name.length)
    for (c in name) {
        when {
            c in ILLEGAL_FILE_NAME_CHARACTERS -> {
                filtered.append('_')
            }

            c.lowercaseChar() != c -> {
                filtered.append(c)
                filtered.append('_')
            }

            else -> {
                filtered.append(c)
            }
        }
    }
    name = filtered.toString()

    val sliceLength = MAX_FILE_NAME_LENGTH - prefix.length - suffix.length
    if (name.length > sliceLength) name = name.substring(0, maxOf(0, sliceLength))

    name =
        name
            .split('.')
            .joinToString(".") { part -> if (part.lowercase() in RESERVED_FILE_NAMES) "_$part" else part }

    var fullName = prefix + name + suffix
    if (fullName.lowercase() in existing) {
        fullName = handleFileNameClash1(name, existing, prefix, suffix)
    }
    return fullName
}

/** First collision fallback: append a zero-padded counter to [userName] (already filtered/clipped) until it is unique. */
fun handleFileNameClash1(
    userName: String,
    existing: Set<String>,
    prefix: String = "",
    suffix: String = "",
): String {
    var name = userName
    if (prefix.length + name.length + suffix.length + 15 > MAX_FILE_NAME_LENGTH) {
        val sliceLength = MAX_FILE_NAME_LENGTH - (prefix.length + name.length + suffix.length + 15)
        name = name.substring(0, maxOf(0, sliceLength))
    }
    var counter = 1L
    while (counter < 999_999_999_999_999L) {
        val candidate = prefix + name + counter.toString().padStart(15, '0') + suffix
        if (candidate.lowercase() !in existing) return candidate
        counter += 1
    }
    return handleFileNameClash2(existing, prefix, suffix)
}

/** Second, last-resort collision fallback: `prefix + counter + suffix` for the first unused counter. */
fun handleFileNameClash2(
    existing: Set<String>,
    prefix: String = "",
    suffix: String = "",
): String {
    val maxLength = MAX_FILE_NAME_LENGTH - prefix.length - suffix.length
    require(maxLength > 0) { "no room left for a unique file name after prefix/suffix" }
    var counter = 1L
    while (true) {
        val candidate = prefix + counter.toString() + suffix
        if (candidate.lowercase() !in existing) return candidate
        counter += 1
        require(candidate.length - prefix.length - suffix.length <= maxLength) { "no unique file name could be found" }
    }
}

private const val MAX_FILE_NAME_LENGTH = 255

// OpenType/UFO spec restrictions, mostly Windows filename rules, plus "(" and ")" per the UFO
// spec itself (see fontTools.ufoLib.filenames.illegalCharacters, ported verbatim).
private val ILLEGAL_FILE_NAME_CHARACTERS: Set<Char> =
    (('\u0000'..'\u001F').toSet()) + setOf('"', '*', '+', '/', ':', '<', '>', '?', '[', '\\', ']', '(', ')', '|', '\u007F')

private val RESERVED_FILE_NAMES: Set<String> =
    setOf(
        "aux",
        "clock$",
        "com1",
        "com2",
        "com3",
        "com4",
        "com5",
        "com6",
        "com7",
        "com8",
        "com9",
        "con",
        "lpt1",
        "lpt2",
        "lpt3",
        "lpt4",
        "lpt5",
        "lpt6",
        "lpt7",
        "lpt8",
        "lpt9",
        "nul",
        "prn",
    )
