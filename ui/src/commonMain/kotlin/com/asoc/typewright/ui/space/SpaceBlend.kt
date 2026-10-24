// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

/** Why a letter of the control string cannot be compared between the Before and the string as it is now. */
enum class NotComparedWhy {
    /** Drawn now, not drawn when Before was made: Before has no such letter to show. */
    DRAWN_ONLY_NOW,

    /** Drawn at Before, not drawn now: After has no such letter to show. */
    DRAWN_ONLY_THEN,

    /** The same place in the string is a different glyph than it was. */
    DIFFERENT_GLYPH,
}

/** A letter of the control string that is in only one of the two pictures, and why. */
data class NotCompared(
    val char: Char,
    val why: NotComparedWhy,
)

/**
 * One place in the row: the letter that stood there at the Before ([before]) and the one that stands there now
 * ([after]); either can be missing, never both. A place with both slides from one to the other as the page moves
 * between Before and After; a place with only one fades in or out, so a letter that did not exist at the Before is
 * never drawn as if it had, and the Before picture is never the After picture under another name.
 */
class SpaceSlot(
    /** Where the letter is in the control string; the Before's letter is matched to the one now by this and its glyph. */
    val textIndex: Int,
    val before: SpaceCell?,
    val after: SpaceCell?,
) {
    init {
        require(before != null || after != null) { "a place in the row holds a letter from at least one picture" }
    }

    /** Whether the letter is in both pictures, so it can be compared. */
    val inBoth: Boolean get() = before != null && after != null

    /** The furthest left the letter's advance reaches over the whole slide, in font units: a view that holds this holds it at every moment. */
    val reachLeft: Double get() = minOf(before?.x ?: Double.POSITIVE_INFINITY, after?.x ?: Double.POSITIVE_INFINITY)

    /** The furthest right the letter's advance reaches over the whole slide, in font units. */
    val reachRight: Double get() = maxOf(before?.right ?: Double.NEGATIVE_INFINITY, after?.right ?: Double.NEGATIVE_INFINITY)

    /** The letter's origin in font units when the page is [t] of the way from Before (0) to After (1). */
    fun x(t: Float): Double {
        val then = before
        val now = after
        return when {
            then == null -> checkNotNull(now).x
            now == null -> then.x
            else -> then.x + (now.x - then.x) * t
        }
    }

    /** How solid the letter is at [t]: always whole when it is in both pictures, fading in (or out) when it is in one. */
    fun alpha(t: Float): Float =
        when {
            before == null -> t
            after == null -> 1f - t
            else -> 1f
        }

    /** The letter to draw at [t]: the Before's in the first half of the way, the After's in the second; the only one there is when there is one. */
    fun cell(t: Float): SpaceCell {
        val then = before
        val now = after
        return when {
            then == null -> checkNotNull(now)
            now == null -> then
            t >= HALF -> now
            else -> then
        }
    }

    private companion object {
        const val HALF = 0.5f
    }
}

/**
 * The control string as it was at the Before and as it is now, put side by side place by place. Letters are matched by
 * where they stand in the string and by their glyph name, so a string whose drawn letters changed since the Before is
 * still compared letter by letter where it can be, and the letters it cannot compare are named ([notCompared]) instead
 * of the whole picture quietly falling back to the current one.
 *
 * Without a Before ([before] null) every place holds the current letter alone.
 */
class SpaceBlend private constructor(
    val slots: List<SpaceSlot>,
    val notCompared: List<NotCompared>,
    private val slotOfAfterCell: Map<Int, Int>,
    private val slotOfBeforeCell: Map<Int, Int>,
) {
    /** Whether every letter in either picture is in both. */
    val allCompared: Boolean get() = notCompared.isEmpty()

    /** The place of the current string's cell [index], or null. */
    fun slotOfAfter(index: Int): Int? = slotOfAfterCell[index]

    /** The place of the Before's cell [index], or null. */
    fun slotOfBefore(index: Int): Int? = slotOfBeforeCell[index]

    /** The current string's cell that stands where the Before's cell [index] did, or null where the current string has none. */
    fun afterIndexOfBefore(index: Int): Int? = slotOfBefore(index)?.let { slots[it].after?.index }

    /** The Before's cell that stood where the current string's cell [index] stands, or null where the Before had none. */
    fun beforeIndexOfAfter(index: Int): Int? = slotOfAfter(index)?.let { slots[it].before?.index }

    /** One or two plain sentences naming the letters that cannot be compared and why, or null when all can. */
    fun sentence(): String? {
        if (notCompared.isEmpty()) return null
        val parts = ArrayList<String>()

        fun letters(why: NotComparedWhy) = notCompared.filter { it.why == why }.map { it.char.toString() }.distinct()
        letters(NotComparedWhy.DRAWN_ONLY_NOW).takeIf { it.isNotEmpty() }?.let {
            val one = it.size == 1
            parts +=
                "${list(
                    it,
                )} ${if (one) "was" else "were"} not drawn when Before was made, so Before leaves ${if (one) "it" else "them"} out."
        }
        letters(NotComparedWhy.DRAWN_ONLY_THEN).takeIf { it.isNotEmpty() }?.let {
            val one = it.size == 1
            parts += "${list(it)} ${if (one) "is" else "are"} not drawn now, so After leaves ${if (one) "it" else "them"} out."
        }
        letters(NotComparedWhy.DIFFERENT_GLYPH).takeIf { it.isNotEmpty() }?.let {
            parts += "${list(it)} ${if (it.size == 1) "is" else "are"} a different glyph now than at Before."
        }
        return parts.joinToString(" ")
    }

    private fun list(words: List<String>): String =
        when (words.size) {
            1 -> words[0]
            2 -> "${words[0]} and ${words[1]}"
            else -> words.dropLast(1).joinToString(", ") + " and " + words.last()
        }

    companion object {
        /** [after] and [before] put side by side; [before] null means there is no Before, so every place holds an [after] letter alone. */
        fun of(
            after: SpaceString,
            before: SpaceString?,
        ): SpaceBlend {
            val slots = ArrayList<SpaceSlot>()
            val notCompared = ArrayList<NotCompared>()
            val afterSlot = HashMap<Int, Int>()
            val beforeSlot = HashMap<Int, Int>()
            if (before == null) {
                for (cell in after.cells) {
                    afterSlot[cell.index] = slots.size
                    slots += SpaceSlot(cell.textIndex, null, cell)
                }
                return SpaceBlend(slots, notCompared, afterSlot, beforeSlot)
            }
            val nowAt = after.cells.associateBy { it.textIndex }
            val thenAt = before.cells.associateBy { it.textIndex }
            for (at in (nowAt.keys + thenAt.keys).sorted()) {
                val now = nowAt[at]
                val then = thenAt[at]
                when {
                    now != null && then != null && now.glyph.name == then.glyph.name -> {
                        afterSlot[now.index] = slots.size
                        beforeSlot[then.index] = slots.size
                        slots += SpaceSlot(at, then, now)
                    }

                    now != null && then != null -> {
                        beforeSlot[then.index] = slots.size
                        slots += SpaceSlot(at, then, null)
                        afterSlot[now.index] = slots.size
                        slots += SpaceSlot(at, null, now)
                        notCompared += NotCompared(now.char, NotComparedWhy.DIFFERENT_GLYPH)
                    }

                    now != null -> {
                        afterSlot[now.index] = slots.size
                        slots += SpaceSlot(at, null, now)
                        notCompared += NotCompared(now.char, NotComparedWhy.DRAWN_ONLY_NOW)
                    }

                    then != null -> {
                        beforeSlot[then.index] = slots.size
                        slots += SpaceSlot(at, then, null)
                        notCompared += NotCompared(then.char, NotComparedWhy.DRAWN_ONLY_THEN)
                    }
                }
            }
            return SpaceBlend(slots, notCompared, afterSlot, beforeSlot)
        }
    }
}
