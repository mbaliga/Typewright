// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.puck

import com.asoc.typewright.core.geometry.Vec2

/**
 * Where the puck's box was last laid out inside its parent, in dp, and the one conversion the puck's
 * pointer handling needs: a position reported inside that box, as a position in the parent.
 *
 * The gesture machine reasons in the parent's coordinates (the puck's centre, the radial's hub and the
 * grip's hit region are all parent dp), but a pointer reports positions relative to the box it landed
 * in. The box also follows a finger that drags the grip, so a conversion that used the position at the
 * start of the gesture would be wrong from the second move on. [topLeftDp] is updated every time the box
 * is placed, and a pointer position is converted with the placement it was reported against, so the
 * conversion is exact however far behind the box lags the finger.
 */
class PuckPlacement(
    var topLeftDp: Vec2 = Vec2(0.0, 0.0),
) {
    /** [local], dp from the box's top-left, as dp in the parent. */
    fun toParent(local: Vec2): Vec2 = local + topLeftDp
}
