// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.engine.trace

/**
 * Which neighbours count as "adjacent" when [labelComponents] grows one component: the four
 * orthogonal neighbours ([FOUR]) or those plus the four diagonal ones ([EIGHT]).
 *
 * [despeckle] labels foreground with [EIGHT] (two ink pixels touching only at a corner are still
 * the same stroke) and [fillHoles] labels background with [FOUR] (the standard raster-topology
 * pairing: using the *coarser* connectivity for the complementary colour keeps "does this
 * background region touch the border" well-defined and avoids the classic 8-connected-foreground
 * flowing through the gaps of an 8-connected "enclosing" ring of ink one pixel thick -- see
 * [fillHoles]'s KDoc).
 */
enum class Connectivity(
    val offsets: List<Pair<Int, Int>>,
) {
    FOUR(listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)),
    EIGHT(listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)),
}

/**
 * The result of [labelComponents]: every foreground cell's component id in [labels] (`-1` for
 * background, i.e. wherever the caller's predicate was `false`), plus each component's pixel count
 * ([componentSizes]) and whether it touches the grid's outer border ([componentTouchesBorder]),
 * indexed by component id (`0 until componentCount`).
 */
class ComponentLabeling(
    val width: Int,
    val height: Int,
    val labels: IntArray,
    val componentSizes: IntArray,
    val componentTouchesBorder: BooleanArray,
) {
    /** How many distinct components [labelComponents] found. */
    val componentCount: Int get() = componentSizes.size

    /** The component id at ([x], [y]), or `-1` if that cell was not foreground. */
    operator fun get(
        x: Int,
        y: Int,
    ): Int = labels[y * width + x]
}

/**
 * Connected-component labeling (flood fill from every unvisited foreground cell, iteratively --
 * an explicit queue, not recursion, so this does not blow the stack on a large connected region)
 * over a `width x height` grid whose foreground predicate is [isForeground], using [connectivity]
 * to decide which neighbours are "connected". This is the one general labeler [despeckle] and
 * [fillHoles] both build on: [despeckle] passes a raster's own ink predicate, [fillHoles] passes
 * its background (`!ink`) predicate -- the same algorithm either way, never special-cased for one
 * caller.
 */
fun labelComponents(
    width: Int,
    height: Int,
    connectivity: Connectivity = Connectivity.EIGHT,
    isForeground: (x: Int, y: Int) -> Boolean,
): ComponentLabeling {
    require(width > 0 && height > 0) { "a grid must be at least 1x1, was ${width}x$height" }
    val labels = IntArray(width * height) { -1 }
    val sizes = mutableListOf<Int>()
    val touchesBorder = mutableListOf<Boolean>()
    val queue = ArrayDeque<Int>()

    for (startY in 0 until height) {
        for (startX in 0 until width) {
            val startIndex = startY * width + startX
            if (labels[startIndex] != -1 || !isForeground(startX, startY)) continue

            val label = sizes.size
            labels[startIndex] = label
            queue.clear()
            queue.addLast(startIndex)
            var size = 0
            var border = false

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                size++
                val cx = current % width
                val cy = current / width
                if (cx == 0 || cy == 0 || cx == width - 1 || cy == height - 1) border = true

                for ((dx, dy) in connectivity.offsets) {
                    val nx = cx + dx
                    val ny = cy + dy
                    if (nx !in 0 until width || ny !in 0 until height) continue
                    val neighborIndex = ny * width + nx
                    if (labels[neighborIndex] == -1 && isForeground(nx, ny)) {
                        labels[neighborIndex] = label
                        queue.addLast(neighborIndex)
                    }
                }
            }

            sizes.add(size)
            touchesBorder.add(border)
        }
    }

    return ComponentLabeling(width, height, labels, sizes.toIntArray(), touchesBorder.toBooleanArray())
}
