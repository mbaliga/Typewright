package dev.aarso.typewright.engine.construct

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.Point
import kotlin.math.roundToInt

/**
 * The construction grammar's Stem primitive (brief section 10 M2: "Stem: a rectangle whose width is
 * the font's stem value, grid snapped"; docs/TYPEWRIGHT_HANDOFF.md's own M2 list, same wording). A
 * vertical [RectanglePrimitive.CentreSize] whose own width is not drawn directly but derived, every
 * [realize] call, from [stemValue] — "the font's stem value" — snapped to the nearest multiple of
 * [gridUnit]. **This is exactly what "every construction stays parametric until baked... change the
 * stem value and every stem updates" (brief section 10) means for this one primitive:** a
 * [StemPrimitive] stores [stemValue] itself, not a pre-computed width, so two stems built with the
 * same [stemValue] and later `.copy(stemValue = newValue)`d stay in lock-step on every [realize]
 * call — there is no cached width anywhere to fall out of sync.
 *
 * [stemValue] is accepted here as a plain `Double` parameter (this task's own scope: "wiring it to
 * an actual font's fontinfo is a later task's job, not yours" — `typewright.json`/UFO `fontinfo`
 * plumbing is out of scope for this file).
 */
data class StemPrimitive(
    /** The stem's horizontal centreline position. */
    val centerX: Double,
    val bottomY: Double,
    val topY: Double,
    /** The font-wide stem width this stem is built from; see this type's own KDoc for why it is a live parameter, not a baked-in width. */
    val stemValue: Double,
    /** The grid every stem edge snaps to, in font units ("integers at rest" is the finer, always-on default; this is the *additional*, coarser design grid a font's stem values are typically kept aligned to). */
    val gridUnit: Double = 1.0,
) {
    init {
        require(topY > bottomY) { "topY ($topY) must be greater than bottomY ($bottomY)" }
        require(stemValue > 0.0) { "stemValue must be positive, was $stemValue" }
        require(gridUnit > 0.0) { "gridUnit must be positive, was $gridUnit" }
    }

    /**
     * This stem's current geometry: a vertical rectangle centred on [centerX], from [bottomY] to
     * [topY], [stemValue] wide, snapped to the nearest multiple of [gridUnit] and then built through
     * [RectanglePrimitive.CentreSize] — the same rectangle machinery every plain rectangle uses, so a
     * stem and a hand-drawn rectangle of the same size are, by construction, identical geometry.
     */
    fun realize(): Contour {
        val snappedWidth = (stemValue / gridUnit).roundToInt() * gridUnit
        require(snappedWidth > 0.0) { "stemValue $stemValue snapped to $gridUnit-unit grid rounds to zero width" }
        val height = topY - bottomY
        val center = Point(centerX.roundToInt(), ((bottomY + topY) / 2.0).roundToInt())
        return RectanglePrimitive.CentreSize(center, snappedWidth, height).realize()
    }
}
