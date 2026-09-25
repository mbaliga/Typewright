// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.ui.puck

/**
 * The nine construction-primitive kinds `engine-construct`'s construction grammar builds
 * (`TYPEWRIGHT_BUILD_BRIEF.md` section 10 M2; `docs/TYPEWRIGHT_HANDOFF.md`'s own M2 list; brief
 * sections 5.1/5.3 name Line/Arc/Circle/Ellipse/Superellipse/Rectangle/Rounded
 * rectangle/Stem/Bowl among the puck's own primitives), in the task's own listed order. [label]
 * is this task's own plain, sentence-case naming (the explorer shows no screen for this specific
 * two-level content to transcribe a label style from -- see this file's own `docs/
 * OPEN_QUESTIONS.md` entry). [entryMethodLabels] restates each primitive file's own KDoc entry-
 * method list (`engine-construct`'s `LinePrimitive`/`ArcPrimitive`/etc.), in the same order
 * [defaultPrimitiveInstance] switches on by index -- the two lists must stay in lock-step, which
 * is why both live in this one file rather than drifting apart in two.
 *
 * Every kind but [STEM] and [BOWL] has more than one entry method, so the puck's own primitives
 * menu ([PrimitiveKindMenu] then [PrimitiveEntryMethodMenu]) shows a second list for those;
 * [STEM]/[BOWL] each have exactly one (`StemPrimitive`/`BowlPrimitive` are plain data classes in
 * `engine-construct`, not `sealed class`es with several entry-method subtypes the way the other
 * seven are), so selecting either kind creates its one default instance directly (see [Puck]'s
 * own `PrimitivesMenuStage.KIND` handling).
 *
 * **Deferred, stated plainly (task's own explicit allowance):** picking an entry method here
 * creates a *default-parameterized* instance at the sheet's current view centre
 * ([defaultPrimitiveInstance]), not a multi-step on-canvas point-picking flow (clicking each of
 * "two points", "three points", etc. directly on the sheet). That interactive gesture flow is a
 * large, separate task this one does not attempt -- see this file's own `docs/OPEN_QUESTIONS.md`
 * entry.
 */
enum class PrimitiveKind(
    val label: String,
    val entryMethodLabels: List<String>,
) {
    LINE("Line", listOf("two points", "point, angle, length", "point, tangent to curve")),
    ARC(
        "Arc",
        listOf(
            "three points",
            "start, centre, end",
            "start, end, radius",
            "start, end, bulge",
            "tangent, tangent, radius (fillet)",
            "centre, radius, start, sweep",
        ),
    ),
    CIRCLE("Circle", listOf("centre, radius", "three points", "tangent, tangent, radius", "fit to points")),
    ELLIPSE("Ellipse", listOf("centre, radii", "three points")),
    SUPERELLIPSE("Superellipse", listOf("centre, radii", "three points")),
    RECTANGLE("Rectangle", listOf("two corners", "centre, size")),
    ROUNDED_RECTANGLE("Rounded rectangle", listOf("two corners", "centre, size", "per-corner radius")),
    STEM("Stem", listOf("grid-snapped stem")),
    BOWL("Bowl", listOf("superellipse skeleton + stroke")),
    ;

    companion object {
        /** Every kind, in the task's own listed order. */
        val ORDERED: List<PrimitiveKind> = entries
    }
}
