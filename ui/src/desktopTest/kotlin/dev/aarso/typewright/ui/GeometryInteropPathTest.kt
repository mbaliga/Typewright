package dev.aarso.typewright.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.ContourPoint
import dev.aarso.typewright.core.geometry.CurveFormat
import dev.aarso.typewright.core.geometry.Glyph
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * desktopTest, not commonTest: constructing a real `androidx.compose.ui.graphics.Path` and
 * calling into it (`getBounds()` etc.) works on desktop's Skia-backed `Path` with no special test
 * infrastructure, but on Android's host (non-instrumented) unit tests `Path` wraps the real
 * `android.graphics.Path`, whose native methods are stubbed to throw ("Method moveTo in
 * android.graphics.Path not mocked") unless a Robolectric shadow is set up -- which this module
 * does not have. Living here, alongside this module's other Compose-rendering tests
 * (`ScreenshotHarness`'s own desktopTest-only precedent), tests the real geometry-to-Path bridge
 * for real on one platform rather than failing on Android CI for infrastructure this task's own
 * scope did not call for adding.
 */

/** A straight-sided square [Contour], on-curve-only ([CurveFormat.QUADRATIC], so every segment is a plain line). */
private fun squareContour(size: Int): Contour =
    Contour(
        listOf(
            ContourPoint(Point(0, 0), onCurve = true),
            ContourPoint(Point(size, 0), onCurve = true),
            ContourPoint(Point(size, size), onCurve = true),
            ContourPoint(Point(0, size), onCurve = true),
        ),
        CurveFormat.QUADRATIC,
    )

/** One [CurveFormat.CUBIC] triangle-ish shape with real off-curve control points, big enough that a cubic Bezier's own bulge visibly overshoots its anchor points -- proves [Path.addContour] really calls [Path.cubicTo] rather than approximating with straight lines. */
private fun bulgingCubicContour(): Contour =
    Contour(
        listOf(
            ContourPoint(Point(0, 0), onCurve = true),
            ContourPoint(Point(0, 300), onCurve = false),
            ContourPoint(Point(600, 300), onCurve = false),
            ContourPoint(Point(600, 0), onCurve = true),
            ContourPoint(Point(600, -300), onCurve = false),
            ContourPoint(Point(0, -300), onCurve = false),
        ),
        CurveFormat.CUBIC,
    )

private fun identityTransform(v: Vec2): Offset = Offset(v.x.toFloat(), v.y.toFloat())

class GeometryInteropPathTest {
    @Test
    fun addContourOfASquareProducesAPathWithTheSquaresOwnBounds() {
        val path = Path().addContour(squareContour(100), ::identityTransform)
        val bounds = path.getBounds()
        assertEquals(0f, bounds.left)
        assertEquals(0f, bounds.top)
        assertEquals(100f, bounds.right)
        assertEquals(100f, bounds.bottom)
    }

    @Test
    fun addContourAppliesTheGivenTransform() {
        val scaleAndOffset: (Vec2) -> Offset = { v -> Offset((v.x * 2.0 + 10.0).toFloat(), (v.y * 2.0 + 5.0).toFloat()) }
        val path = Path().addContour(squareContour(50), scaleAndOffset)
        val bounds = path.getBounds()
        assertEquals(10f, bounds.left)
        assertEquals(5f, bounds.top)
        assertEquals(110f, bounds.right)
        assertEquals(105f, bounds.bottom)
    }

    @Test
    fun toComposePathOfATwoContourGlyphCoversBothContoursBounds() {
        val outer = squareContour(200)
        val inner =
            Contour(
                listOf(
                    ContourPoint(Point(300, 300), onCurve = true),
                    ContourPoint(Point(350, 300), onCurve = true),
                    ContourPoint(Point(350, 350), onCurve = true),
                    ContourPoint(Point(300, 350), onCurve = true),
                ),
                CurveFormat.QUADRATIC,
            )
        val glyph = Glyph("two-contour", advanceWidth = 400, contours = listOf(outer, inner))
        val path = glyph.toComposePath(::identityTransform)
        val bounds = path.getBounds()
        assertEquals(0f, bounds.left)
        assertEquals(0f, bounds.top)
        assertEquals(350f, bounds.right)
        assertEquals(350f, bounds.bottom)
    }

    @Test
    fun addContourOfACubicContourBulgesFarFromItsOwnOnCurveAnchors() {
        // This contour's only two *on-curve* anchors are (0,0) and (600,0) -- both at y=0. A
        // path that (incorrectly) treated a cubic segment as a straight line to its own anchor,
        // ignoring the two off-curve control points entirely, would stay flat at y=0 the whole
        // way around (bounds height exactly 0). The real control points (y=+-300) pull a true
        // cubic Bezier well off that line: at the segment's own midpoint (t=0.5) the textbook
        // cubic Bezier formula gives y = 3/8 * 300 = 112.5 for the top segment (control points
        // both at y=300) and y = -112.5 for the bottom one -- comfortably clear of a 0-height
        // bounding box, and still safely inside the true, convex-hull-bounded maximum of +-300
        // that the curve can never exceed.
        val path = Path().addContour(bulgingCubicContour(), ::identityTransform)
        val bounds = path.getBounds()
        assertTrue(bounds.top < -100f, "expected the cubic curve to bulge below y=-100, bounds were $bounds")
        assertTrue(bounds.bottom > 100f, "expected the cubic curve to bulge above y=100, bounds were $bounds")
    }
}
