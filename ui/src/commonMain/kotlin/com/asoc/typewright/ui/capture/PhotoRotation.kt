// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

/**
 * The turn a camera's still needs before it is the way up the person held the phone. A sensor's picture is stored in its own
 * orientation and the camera reports how far it must be turned clockwise; the picture decoder does not read EXIF, so the stored
 * file has to be turned for real. Plain arithmetic, so it is tested off the device.
 */
object PhotoRotation {
    /** [degrees] as 0, 90, 180 or 270 clockwise; a negative turn counts the other way round. Anything but a quarter turn is refused. */
    fun normalised(degrees: Int): Int {
        require(degrees % QUARTER == 0) { "rotation must be a multiple of 90 degrees, was $degrees" }
        return ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN
    }

    /** Whether turning by [degrees] swaps width and height. */
    fun swapsSides(degrees: Int): Boolean = normalised(degrees) % HALF_TURN != 0

    /** The width and height after turning a [width] by [height] picture by [degrees]. */
    fun turnedSize(
        width: Int,
        height: Int,
        degrees: Int,
    ): Pair<Int, Int> = if (swapsSides(degrees)) height to width else width to height

    /** Where the pixel at ([x], [y]) of a [width] by [height] picture lands after a clockwise turn of [degrees]. */
    fun turnedPosition(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        degrees: Int,
    ): Pair<Int, Int> =
        when (normalised(degrees)) {
            0 -> x to y
            QUARTER -> (height - 1 - y) to x
            HALF_TURN -> (width - 1 - x) to (height - 1 - y)
            else -> y to (width - 1 - x)
        }

    private const val QUARTER = 90
    private const val HALF_TURN = 180
    private const val FULL_TURN = 360
}
