package dev.aarso.typewright.ui.puck

import dev.aarso.typewright.core.geometry.Contour
import dev.aarso.typewright.core.geometry.CurveSegment
import dev.aarso.typewright.core.geometry.Point
import dev.aarso.typewright.core.geometry.Vec2
import dev.aarso.typewright.engine.construct.ArcPrimitive
import dev.aarso.typewright.engine.construct.BowlPrimitive
import dev.aarso.typewright.engine.construct.CirclePrimitive
import dev.aarso.typewright.engine.construct.EllipsePrimitive
import dev.aarso.typewright.engine.construct.FilletSide
import dev.aarso.typewright.engine.construct.LinePrimitive
import dev.aarso.typewright.engine.construct.RectanglePrimitive
import dev.aarso.typewright.engine.construct.RoundedRectanglePrimitive
import dev.aarso.typewright.engine.construct.StemPrimitive
import dev.aarso.typewright.engine.construct.SuperellipsePrimitive
import dev.aarso.typewright.engine.construct.TwoPointLine
import dev.aarso.typewright.ui.sheet.SheetCamera
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * The puck's own Primitives-tool unfolded menu, two stages ([Puck]'s own `handleOutputs`/render
 * block): [KIND] lists [PrimitiveKind.ORDERED] ([PrimitiveKindMenu]); [ENTRY_METHOD] lists the
 * picked kind's own [PrimitiveKind.entryMethodLabels] ([PrimitiveEntryMethodMenu]). [CLOSED] is
 * the puck's ordinary rest state (nothing unfolded).
 */
enum class PrimitivesMenuStage { CLOSED, KIND, ENTRY_METHOD }

/**
 * A live, not-yet-baked instance of one construction-grammar primitive, exactly as the puck's
 * primitives menu creates it ([defaultPrimitiveInstance]) and the Construct inspector reads it
 * (`dev.aarso.typewright.ui.glass.constructInspectorFields`).
 */
data class ActiveConstruction(
    val kind: PrimitiveKind,
    val entryMethodIndex: Int,
    /**
     * The underlying `engine-construct` primitive instance: one of `LinePrimitive`,
     * `ArcPrimitive`, `CirclePrimitive`, `EllipsePrimitive`, `SuperellipsePrimitive`,
     * `RectanglePrimitive`, `RoundedRectanglePrimitive`, `StemPrimitive` or `BowlPrimitive`.
     * Typed `Any` deliberately, not a second UI-side sealed wrapper: these nine families share no
     * common supertype in `engine-construct` (each one's own `realize()` returns a different
     * type -- `Contour`, `CurveSegment.Line`, `CircularArc`, `List<Contour>` -- so a wrapper here
     * would only rename this same disjoint-`when` problem, not remove it), and every reader below
     * narrows it with plain `is` smart-casts. See this file's own `docs/OPEN_QUESTIONS.md` entry.
     */
    val primitive: Any,
    /**
     * "parametric · bake" (`TYPEWRIGHT_BUILD_BRIEF.md`/`docs/TYPEWRIGHT_HANDOFF.md`'s own
     * wording, brief section 10: "every construction stays parametric until baked"). `false`
     * (every instance this task's own flow creates) means "keep recomputing `realize()` on every
     * field change"; `true` would mean a caller kept one `realize()` result as plain geometry and
     * stopped editing this primitive's own fields. Nothing in this task's own scope ever flips it
     * to `true` -- there is no bake action wired to a gesture yet (a later task's job, once
     * on-canvas point-picking exists to actually edit these fields) -- so this flag exists for
     * the Construct inspector's own "parametric · bake" indicator to read honestly (always
     * "parametric" today) rather than for that indicator to be invented as a dead label.
     */
    val baked: Boolean = false,
)

/**
 * A default-parameterized [ActiveConstruction] for [kind]'s [entryMethodIndex]th entry method,
 * anchored near [anchor] (font units -- the sheet's current view centre, per [viewCentreFontUnits],
 * at the moment the puck's primitives menu creates it). Sizes below (100-200 unit radii/lengths, a
 * 44-unit stroke width) are not arbitrary: 44 matches this codebase's own shipped Hyle Deco stem
 * value (CLAUDE.md's own fixture numbers; the explorer's own lens/`.dins` "stem 44"/"Stroke 44 ±
 * 1" examples), and 2.5 for a default superellipse exponent is the classic Piet Hein value
 * `BowlPrimitive`'s own KDoc already cites -- reasonable, grounded defaults for a 1000 UPM font,
 * not invented numbers.
 *
 * **Deferred (task's own explicit allowance):** this is the stopping point -- "selecting an entry
 * method creates a default-parameterized instance... at the sheet's current view centre, in
 * parametric (not yet baked) form" -- not a multi-step on-canvas point-picking flow. Every entry
 * method's own points are placed relative to [anchor] so the result always satisfies that entry
 * method's own `init` requirements (distinct points, a radius large enough to span its chord,
 * non-collinear points, a non-zero bulge, non-parallel fillet lines) without throwing --
 * `PrimitivesConstructionTest` (commonTest) calls every kind's every entry method and asserts
 * `realize()` succeeds. [LinePrimitive.TangentToCurve]'s own third
 * entry method needs an *existing* curve to snap to, which nothing on the sheet exposes yet (the
 * same deferred on-canvas flow); its default instead tangents to a synthetic horizontal reference
 * line through [anchor], documented here rather than silently faked as something more meaningful.
 */
fun defaultPrimitiveInstance(
    kind: PrimitiveKind,
    entryMethodIndex: Int,
    anchor: Point,
): ActiveConstruction {
    val primitive: Any =
        when (kind) {
            PrimitiveKind.LINE -> {
                when (entryMethodIndex) {
                    0 -> {
                        LinePrimitive.TwoPoints(anchor.offset(-100, 0), anchor.offset(100, 0))
                    }

                    1 -> {
                        LinePrimitive.PointAngleLength(anchor, angleRadians = 0.0, length = 200.0)
                    }

                    else -> {
                        val referenceCurve = CurveSegment.Line(anchor.offset(-100, 0).toVec2(), anchor.offset(100, 0).toVec2())
                        LinePrimitive.TangentToCurve(referenceCurve, t = 0.5, length = 200.0)
                    }
                }
            }

            PrimitiveKind.ARC -> {
                when (entryMethodIndex) {
                    0 -> {
                        ArcPrimitive.ThreePoints(anchor.offset(100, 0), anchor.offset(0, 100), anchor.offset(-100, 0))
                    }

                    1 -> {
                        ArcPrimitive.StartCenterEnd(start = anchor.offset(100, 0), center = anchor, end = anchor.offset(0, 100))
                    }

                    2 -> {
                        ArcPrimitive.StartEndRadius(start = anchor.offset(-100, 0), end = anchor.offset(100, 0), radius = 120.0)
                    }

                    3 -> {
                        ArcPrimitive.StartEndBulge(start = anchor.offset(-100, 0), end = anchor.offset(100, 0), bulge = 0.4)
                    }

                    4 -> {
                        val (line1, line2) = defaultFilletLines(anchor)
                        ArcPrimitive.TangentTangentRadius(line1, line2, 30.0, FilletSide.LEFT, FilletSide.LEFT)
                    }

                    else -> {
                        ArcPrimitive.CenterRadiusStartSweep(anchor, radius = 100.0, startAngleRadians = 0.0, sweepRadians = PI / 2.0)
                    }
                }
            }

            PrimitiveKind.CIRCLE -> {
                when (entryMethodIndex) {
                    0 -> {
                        CirclePrimitive.CentreRadius(anchor, radius = 100.0)
                    }

                    1 -> {
                        CirclePrimitive.ThreePoints(anchor.offset(100, 0), anchor.offset(0, 100), anchor.offset(-100, 0))
                    }

                    2 -> {
                        val (line1, line2) = defaultFilletLines(anchor)
                        CirclePrimitive.TangentTangentRadius(line1, line2, 30.0, FilletSide.LEFT, FilletSide.LEFT)
                    }

                    else -> {
                        CirclePrimitive.FitToPoints(
                            listOf(anchor.offset(100, 0), anchor.offset(0, 100), anchor.offset(-100, 0), anchor.offset(0, -100)),
                        )
                    }
                }
            }

            PrimitiveKind.ELLIPSE -> {
                when (entryMethodIndex) {
                    0 -> EllipsePrimitive.CentreRadii(anchor, semiMajor = 120.0, semiMinor = 80.0)
                    else -> EllipsePrimitive.ThreePoints(anchor.offset(-120, 0), anchor.offset(120, 0), anchor.offset(0, 80))
                }
            }

            PrimitiveKind.SUPERELLIPSE -> {
                when (entryMethodIndex) {
                    0 -> {
                        SuperellipsePrimitive.CentreRadii(
                            anchor,
                            semiMajor = 120.0,
                            semiMinor = 80.0,
                            exponent = DEFAULT_SUPERELLIPSE_EXPONENT,
                        )
                    }

                    else -> {
                        SuperellipsePrimitive.ThreePoints(
                            anchor.offset(-120, 0),
                            anchor.offset(120, 0),
                            anchor.offset(0, 80),
                            exponent = DEFAULT_SUPERELLIPSE_EXPONENT,
                        )
                    }
                }
            }

            PrimitiveKind.RECTANGLE -> {
                when (entryMethodIndex) {
                    0 -> RectanglePrimitive.TwoCorners(anchor.offset(-100, -100), anchor.offset(100, 100))
                    else -> RectanglePrimitive.CentreSize(anchor, width = 200.0, height = 200.0)
                }
            }

            PrimitiveKind.ROUNDED_RECTANGLE -> {
                when (entryMethodIndex) {
                    0 -> {
                        RoundedRectanglePrimitive.TwoCorners(anchor.offset(-100, -100), anchor.offset(100, 100), radius = 24.0)
                    }

                    1 -> {
                        RoundedRectanglePrimitive.CentreSize(anchor, width = 200.0, height = 200.0, radius = 24.0)
                    }

                    else -> {
                        RoundedRectanglePrimitive.PerCornerRadius(
                            anchor.offset(-100, -100),
                            anchor.offset(100, 100),
                            topLeftRadius = 24.0,
                            topRightRadius = 24.0,
                            bottomRightRadius = 24.0,
                            bottomLeftRadius = 24.0,
                        )
                    }
                }
            }

            PrimitiveKind.STEM -> {
                StemPrimitive(
                    centerX = anchor.x.toDouble(),
                    bottomY = anchor.y - 150.0,
                    topY = anchor.y + 150.0,
                    stemValue = DEFAULT_FONT_WIDE_STEM,
                )
            }

            PrimitiveKind.BOWL -> {
                BowlPrimitive(
                    center = anchor,
                    skeletonSemiMajor = 120.0,
                    skeletonSemiMinor = 90.0,
                    strokeWidth = DEFAULT_FONT_WIDE_STEM,
                )
            }
        }
    return ActiveConstruction(kind, entryMethodIndex, primitive)
}

/** Two perpendicular [TwoPointLine]s meeting at [anchor] -- a corner for [ArcPrimitive.TangentTangentRadius]/[CirclePrimitive.TangentTangentRadius]'s own default. */
private fun defaultFilletLines(anchor: Point): Pair<TwoPointLine, TwoPointLine> =
    TwoPointLine(anchor.offset(-100, 0), anchor) to TwoPointLine(anchor, anchor.offset(0, 100))

private fun Point.offset(
    dx: Int,
    dy: Int,
): Point = Point(x + dx, y + dy)

/** HyleDeco's own shipped stem value (CLAUDE.md's own fixture numbers; the explorer's own "stem 44" examples) -- a grounded default, not an arbitrary one. */
const val DEFAULT_FONT_WIDE_STEM: Double = 44.0

/** Piet Hein's own classic superellipse exponent (`BowlPrimitive`'s own KDoc cites it), used as this menu's own default when a superellipse's exponent is not otherwise fixed. */
const val DEFAULT_SUPERELLIPSE_EXPONENT: Double = 2.5

/**
 * The Construct inspector's own four per-primitive numbers (brief section 10 / `docs/
 * TYPEWRIGHT_HANDOFF.md`'s own "stem (font-wide), contrast, exponent, width" list), read from a
 * live [ActiveConstruction] rather than invented. [fontWideStem] is always present (a font-wide
 * setting, not per-primitive -- see [DEFAULT_FONT_WIDE_STEM]'s own KDoc for why this app has no
 * real UFO `fontinfo` to read it from yet); [contrast]/[exponent]/[width] are `null` when the
 * active primitive's own entry method genuinely has no such number (e.g. a plain rectangle has no
 * contrast or exponent) -- `constructInspectorFields` renders a null as "--", never a fabricated
 * value.
 */
data class ConstructValues(
    val fontWideStem: Double,
    val contrast: Double?,
    val exponent: Double?,
    val width: Double?,
    val baked: Boolean,
)

/** [ConstructValues] for [active], reading its own [ActiveConstruction.primitive] generically where `engine-construct`'s own `realize()` already exposes the number (width), and per-kind where it does not (contrast, exponent -- input parameters, not output geometry). */
fun constructValuesFor(
    active: ActiveConstruction,
    fontWideStem: Double = DEFAULT_FONT_WIDE_STEM,
): ConstructValues =
    ConstructValues(
        fontWideStem = fontWideStem,
        contrast = active.primitive.constructContrast(),
        exponent = active.primitive.constructExponent(),
        width = active.primitive.constructWidth(),
        baked = active.baked,
    )

/** The primitive's own contrast (only [BowlPrimitive] has one); `null` otherwise. */
private fun Any.constructContrast(): Double? = (this as? BowlPrimitive)?.contrast

/** The primitive's own Lame exponent ([BowlPrimitive]'s skeleton, [SuperellipsePrimitive]'s own field, or `2.0` for a true [EllipsePrimitive] -- the exponent-2 case of the same family); `null` otherwise. */
private fun Any.constructExponent(): Double? =
    when (this) {
        is BowlPrimitive -> skeletonExponent
        is SuperellipsePrimitive.CentreRadii -> exponent
        is SuperellipsePrimitive.ThreePoints -> exponent
        is EllipsePrimitive -> 2.0
        else -> null
    }

/**
 * The primitive's own characteristic width, read as directly as each kind allows: a stroke/stem
 * width for [BowlPrimitive]/[StemPrimitive] (the input parameter itself, most honest); a line's
 * own length; an arc's own diameter (`realize().radius * 2`); a shape-primitive's own realized
 * [Contour] bounding-box width for every kind whose own `realize()` already produces one (Circle,
 * Ellipse, Superellipse, Rectangle, Rounded rectangle) -- one general bounding-box reader
 * ([Contour.boundingWidthUnits]), not nine separate per-variant field reads.
 */
private fun Any.constructWidth(): Double? =
    when (this) {
        is LinePrimitive -> {
            val line = realize()
            (line.end - line.start).length()
        }

        is ArcPrimitive -> {
            realize().radius * 2.0
        }

        is CirclePrimitive -> {
            realize().boundingWidthUnits()
        }

        is EllipsePrimitive -> {
            realize().boundingWidthUnits()
        }

        is SuperellipsePrimitive -> {
            realize().boundingWidthUnits()
        }

        is RectanglePrimitive -> {
            realize().boundingWidthUnits()
        }

        is RoundedRectanglePrimitive -> {
            realize().boundingWidthUnits()
        }

        is StemPrimitive -> {
            stemValue
        }

        is BowlPrimitive -> {
            strokeWidth
        }

        else -> {
            null
        }
    }

/** This closed contour's own axis-aligned bounding-box width, in font units -- the one generic "how big is this shape" reader every `Contour`-realizing primitive above shares. */
private fun Contour.boundingWidthUnits(): Double {
    val xs = points.map { it.point.x }
    return (xs.max() - xs.min()).toDouble()
}

/**
 * The sheet's current view centre, in font units, y up (CLAUDE.md's own "y up" convention;
 * `dev.aarso.typewright.ui.sheet.GridAndMetrics.kt`'s own `worldToScreenFontUp` negates y going
 * world-to-screen for the identical reason). Defined locally, not by importing or editing that
 * file, per this task's own instruction to touch shared `sheet/` files as little as possible --
 * this is the one small inverse it needs, restated rather than reused across a package boundary.
 */
fun viewCentreFontUnits(
    camera: SheetCamera,
    canvasSizeDp: Vec2,
): Point {
    val screenCenter = Vec2(canvasSizeDp.x / 2.0, canvasSizeDp.y / 2.0)
    val world = camera.screenToFont(screenCenter)
    return Point(world.x.roundToInt(), (-world.y).roundToInt())
}
