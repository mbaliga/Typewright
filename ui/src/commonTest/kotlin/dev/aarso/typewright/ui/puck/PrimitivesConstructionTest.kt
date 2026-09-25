// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.puck

import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.engine.construct.ArcPrimitive
import dev.aarso.typewright.engine.construct.BowlPrimitive
import dev.aarso.typewright.engine.construct.CirclePrimitive
import dev.aarso.typewright.engine.construct.EllipsePrimitive
import dev.aarso.typewright.engine.construct.LinePrimitive
import dev.aarso.typewright.engine.construct.RectanglePrimitive
import dev.aarso.typewright.engine.construct.RoundedRectanglePrimitive
import dev.aarso.typewright.engine.construct.StemPrimitive
import dev.aarso.typewright.engine.construct.SuperellipsePrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [defaultPrimitiveInstance]'s own honesty check (that file's own KDoc: "every entry method's own
 * points are placed relative to `anchor` so the result always satisfies that entry method's own
 * `init` requirements... without throwing"): every [PrimitiveKind]'s every entry method is asked
 * for its default instance and [realizeGenerically] is called on the result -- a construction
 * whose own `init` block or `realize()` throws fails this test loudly, matching this codebase's
 * own "honesty rule applied to a failure mode" (`docs/OPEN_QUESTIONS.md` item 24's own phrase for
 * the identical pattern in `Booleans.kt`).
 */
class PrimitivesConstructionTest {
    private val anchor = Point(500, 300)

    @Test
    fun everyKindsEveryEntryMethodRealizesWithoutThrowing() {
        for (kind in PrimitiveKind.ORDERED) {
            for (entryMethodIndex in kind.entryMethodLabels.indices) {
                val active = defaultPrimitiveInstance(kind, entryMethodIndex, anchor)
                assertEquals(kind, active.kind, "kind mismatch for $kind/$entryMethodIndex")
                assertEquals(entryMethodIndex, active.entryMethodIndex)
                assertEquals(false, active.baked, "a freshly created instance is never baked")
                realizeGenerically(active.primitive)
            }
        }
    }

    @Test
    fun stemsConstructValuesExposeItsOwnStemValueAsWidthAndTheFontWideStemSeparately() {
        val active = defaultPrimitiveInstance(PrimitiveKind.STEM, 0, anchor)
        val values = constructValuesFor(active, fontWideStem = 44.0)
        assertEquals(44.0, values.fontWideStem)
        assertEquals(44.0, values.width) // StemPrimitive's own stemValue, read directly.
        assertNull(values.contrast)
        assertNull(values.exponent)
        assertEquals(false, values.baked)
    }

    @Test
    fun bowlsConstructValuesExposeContrastExponentAndStrokeWidth() {
        val active = defaultPrimitiveInstance(PrimitiveKind.BOWL, 0, anchor)
        val values = constructValuesFor(active)
        assertEquals(1.0, values.contrast) // BowlPrimitive's own default contrast (monolinear).
        assertEquals(2.0, values.exponent) // BowlPrimitive's own default skeletonExponent.
        assertEquals(DEFAULT_FONT_WIDE_STEM, values.width) // strokeWidth, read directly.
    }

    @Test
    fun aRectangleHasAWidthButNoContrastOrExponent() {
        val active = defaultPrimitiveInstance(PrimitiveKind.RECTANGLE, 1, anchor) // CentreSize(width=200)
        val values = constructValuesFor(active)
        assertEquals(200.0, values.width)
        assertNull(values.contrast)
        assertNull(values.exponent)
    }

    @Test
    fun anArcsWidthIsItsRealizedDiameter() {
        // CenterRadiusStartSweep(radius = 100.0) -> diameter 200.
        val active = defaultPrimitiveInstance(PrimitiveKind.ARC, 5, anchor)
        val values = constructValuesFor(active)
        assertEquals(200.0, values.width)
    }

    @Test
    fun anEllipsesExponentReadsAsTheTrueEllipseConstantTwo() {
        val active = defaultPrimitiveInstance(PrimitiveKind.ELLIPSE, 0, anchor)
        val values = constructValuesFor(active)
        assertEquals(2.0, values.exponent)
        assertTrue(values.width!! > 0.0)
    }

    /** Calls the one `realize()` every `engine-construct` primitive family exposes, generically over [ActiveConstruction.primitive]'s own `Any` type -- see that field's own KDoc for why there is no shared interface to call through instead. */
    private fun realizeGenerically(primitive: Any) {
        when (primitive) {
            is LinePrimitive -> primitive.realize()
            is ArcPrimitive -> primitive.realize()
            is CirclePrimitive -> primitive.realize()
            is EllipsePrimitive -> primitive.realize()
            is SuperellipsePrimitive -> primitive.realize()
            is RectanglePrimitive -> primitive.realize()
            is RoundedRectanglePrimitive -> primitive.realize()
            is StemPrimitive -> primitive.realize()
            is BowlPrimitive -> primitive.realize()
            else -> error("unrecognized primitive type: $primitive")
        }
    }
}
