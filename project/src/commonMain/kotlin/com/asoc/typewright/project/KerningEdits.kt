// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

/** Where a kerning value is written for a pair of glyphs that are in groups. */
enum class KernScope {
    /** At the level the pair already gets its value from, or, for a pair with none, at the groups the two glyphs are in. */
    GROUPS,

    /** On the two glyphs alone, as an exception to whatever the groups say. */
    PAIR_ONLY,
}

/**
 * The commands that set and remove one pair's kerning, aimed at the right level of the
 * specification's three: a pair between two grouped glyphs is written as the group pair, so that
 * every glyph of both groups follows, unless the person asks for [KernScope.PAIR_ONLY]. Pure: each
 * function reads a [KerningTable] and returns the [FontCommand] to run, or null where nothing would change.
 */
object KerningEdits {
    /** The entry a write for ([first], [second]) goes to under [scope]. */
    fun targetKey(
        table: KerningTable,
        first: String,
        second: String,
        scope: KernScope,
    ): KernKey {
        if (scope == KernScope.PAIR_ONLY) return KernKey(first, second)
        val resolved = table.resolve(first, second)
        resolved.key?.let { return it }
        return KernKey(table.groupOf(first, KernSide.FIRST) ?: first, table.groupOf(second, KernSide.SECOND) ?: second)
    }

    /**
     * Sets the kerning of ([first], [second]) to [value] in [master], or null when it already is.
     * A value of zero removes the entry unless the entry is an exception that has to stay to keep a
     * stronger-than-zero group value from applying (the specification keeps zeros only for that).
     */
    fun set(
        master: String,
        table: KerningTable,
        first: String,
        second: String,
        value: Double,
        scope: KernScope,
    ): FontCommand? = setAt(master, table, first, second, targetKey(table, first, second, scope), value)

    /** [set] with the entry already chosen: used while a drag keeps writing to the entry it began on. */
    fun setAt(
        master: String,
        table: KerningTable,
        first: String,
        second: String,
        key: KernKey,
        value: Double,
    ): FontCommand? {
        val current = table.entry(key)
        val stored =
            if (value == 0.0 && table.resolve(first, second, ignoring = key).value == 0.0) {
                null
            } else {
                value
            }
        if (current == stored) return null
        return SetKerning(master, key.first, key.second, stored)
    }

    /** Removes the entry a write for ([first], [second]) under [scope] would go to, or null where there is none to remove. */
    fun remove(
        master: String,
        table: KerningTable,
        first: String,
        second: String,
        scope: KernScope,
    ): FontCommand? {
        val key =
            when (scope) {
                KernScope.PAIR_ONLY -> KernKey(first, second)
                KernScope.GROUPS -> table.resolve(first, second).key ?: return null
            }
        if (table.entry(key) == null) return null
        return SetKerning(master, key.first, key.second, null)
    }
}
