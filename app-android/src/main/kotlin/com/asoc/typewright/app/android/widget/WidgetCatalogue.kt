// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.app.android.widget

/** The six home-screen widgets of docs/SCREENS_V1.md §9.2, W1 to W6 in order. */
enum class WidgetKind {
    APPROVED,
    SAVED,
    RECENTS,
    WORKBOOK,
    LINEAGES,
    SHORTCUTS,
}

/** A widget's layout size (SCREENS_V1 §9.1 "Three sizes each"). */
enum class WidgetSize {
    SMALL,
    MEDIUM,
    LARGE,
}

/** A width and height in dp. */
data class SizeDp(
    val width: Float,
    val height: Float,
)

/**
 * Where each size starts, per widget (SCREENS_V1 §9.1 "Three sizes each"). On Android 12 and
 * later the launcher shows the largest layout whose start fits the space the widget really has, so
 * these are the keys of the size-to-layout map each provider hands it.
 *
 * Each start is at least as tall as its layout's content at font scale 1 ([contentHeight]), so the
 * layout the launcher picks fits its frame. Above that, medium never starts higher than a typical
 * portrait 4×2 (about 276 × 220 dp) and large never lower than [LARGE_FROM], so a 4×2 shows medium
 * and a 4×3 (about 276 × 337 dp) shows large, as the sizes are meant to go.
 */
object WidgetSizes {
    /** Below this width every widget but Shortcuts shows its small layout: under three cells. */
    private const val WIDE_FROM = 180f

    /** The lowest medium start: one row of cells (about 102 dp in portrait) stays small. */
    private const val MEDIUM_FROM = 110f

    /** The lowest large start: two rows of cells (about 220 dp in portrait) stay medium. */
    private const val LARGE_FROM = 250f

    /** Shortcuts' three pads need this width: three 75 dp blocks, two 8 dp gaps and the frame's sides. */
    private const val PADS_FROM = 270f

    /** Every widget's smallest width: the two cells it resizes down to. */
    private const val SMALL_FROM = 110f

    /** The size at which each of [kind]'s layouts starts. */
    fun breakpoints(kind: WidgetKind): Map<WidgetSize, SizeDp> =
        if (kind == WidgetKind.SHORTCUTS) {
            mapOf(
                WidgetSize.SMALL to SizeDp(SMALL_FROM, contentHeight(kind, WidgetSize.SMALL)),
                WidgetSize.MEDIUM to SizeDp(PADS_FROM, contentHeight(kind, WidgetSize.MEDIUM)),
                WidgetSize.LARGE to SizeDp(PADS_FROM, maxOf(MEDIUM_FROM, roundUp(contentHeight(kind, WidgetSize.LARGE)))),
            )
        } else {
            mapOf(
                WidgetSize.SMALL to SizeDp(SMALL_FROM, SMALL_HEIGHT),
                WidgetSize.MEDIUM to SizeDp(WIDE_FROM, maxOf(MEDIUM_FROM, roundUp(contentHeight(kind, WidgetSize.MEDIUM)))),
                WidgetSize.LARGE to SizeDp(WIDE_FROM, maxOf(LARGE_FROM, roundUp(contentHeight(kind, WidgetSize.LARGE)))),
            )
        }

    /** The layout the launcher picks for [kind] at [widthDp] × [heightDp]: the largest that fits, else small. */
    fun sizeFor(
        kind: WidgetKind,
        widthDp: Float,
        heightDp: Float,
    ): WidgetSize =
        breakpoints(kind)
            .filter { (_, start) -> start.width <= widthDp && start.height <= heightDp }
            .keys
            .maxByOrNull { it.ordinal } ?: WidgetSize.SMALL

    /**
     * The height in dp, at font scale 1, of [kind]'s [size] layout with every optional part shown
     * and every text at its line limit: the frame's padding plus its parts, top to bottom, as
     * `res/layout/widget_*.xml` stacks them. A line includes Android's font padding.
     */
    fun contentHeight(
        kind: WidgetKind,
        size: WidgetSize,
    ): Float =
        when (kind) {
            WidgetKind.APPROVED -> {
                val head = TITLE + 2f + NUMBER + GAP + BAR
                FRAME +
                    when (size) {
                        WidgetSize.SMALL -> NUMBER + GAP + BAR
                        WidgetSize.MEDIUM -> head + GAP + BODY + AS_OF
                        WidgetSize.LARGE -> head + 3 * (GAP + BODY) + AS_OF
                    }
            }

            WidgetKind.SAVED -> {
                val head = TITLE + 4f + STATE + AS_OF
                FRAME +
                    when (size) {
                        WidgetSize.SMALL -> STATE
                        WidgetSize.MEDIUM -> head
                        WidgetSize.LARGE -> head + GAP + 4 * BODY
                    }
            }

            WidgetKind.RECENTS -> {
                FRAME +
                    when (size) {
                        WidgetSize.SMALL -> LABEL + TOUCH
                        WidgetSize.MEDIUM -> LABEL + 3 * TOUCH
                        WidgetSize.LARGE -> LABEL + 5 * TOUCH + GAP + LABEL
                    }
            }

            WidgetKind.WORKBOOK -> {
                val medium = LABEL + 2f + 2 * TITLE + STRIP + GAP + LABEL + AS_OF
                FRAME +
                    when (size) {
                        WidgetSize.SMALL -> LABEL + 2f + TITLE
                        WidgetSize.MEDIUM -> medium
                        WidgetSize.LARGE -> medium + GAP + 2 * BODY + CHECKS + GAP + LABEL
                    }
            }

            WidgetKind.LINEAGES -> {
                val head = LABEL + 2f + TITLE
                FRAME +
                    when (size) {
                        WidgetSize.SMALL -> TITLE
                        WidgetSize.MEDIUM -> head + GAP + 2 * BODY
                        WidgetSize.LARGE -> head + GAP + 5 * BODY + GAP + 2 * LABEL + GAP + 2 * BODY + GAP + LABEL
                    }
            }

            // Shortcuts has no top or bottom padding, so a one-row widget keeps its 48 dp pads.
            WidgetKind.SHORTCUTS -> {
                when (size) {
                    WidgetSize.SMALL, WidgetSize.MEDIUM -> TOUCH
                    WidgetSize.LARGE -> TOUCH + 4f + 2 * LABEL
                }
            }
        }

    private fun roundUp(dp: Float): Float = kotlin.math.ceil(dp / 5f) * 5f

    // Parts, in dp at font scale 1 (SCREENS_V1 §9.1 "Type"; res/values/widget_dimens.xml).
    private const val FRAME = 24f // 12 dp top and bottom
    private const val LABEL = 16f // a line of mono 12 sp
    private const val TITLE = 20f // a line of sans 15 sp
    private const val BODY = 20f // a line of sans 14 sp at 1.2 spacing
    private const val NUMBER = 29f // mono 22 sp
    private const val STATE = 24f // sans 18 sp
    private const val TOUCH = 48f // a tappable row or pad
    private const val GAP = 6f
    private const val BAR = 4f
    private const val AS_OF = GAP + LABEL // the as-of line under its gap
    private const val STRIP = 8f + 14f // the Workbook strip under its 8 dp gap
    private const val CHECKS = 4f + 5 * LABEL + 4 * 2f // up to five check lines, 2 dp apart, under a 4 dp gap

    /** The smallest layout's own start, which the launcher also falls back to below every start. */
    private const val SMALL_HEIGHT = 40f
}

/**
 * Whether a widget may show a project's name (SCREENS_V1 §9.1 "Privacy"). A project's name is the
 * family name its maker typed and also its folder's name, and a home screen is public.
 */
enum class ProjectNames {
    SHOWN,
    WITHHELD,
}

/** The privacy setting every widget draws with. */
object WidgetPrivacy {
    /**
     * Withheld until the owner decides otherwise (docs/OPEN_QUESTIONS.md item 131): the widgets say
     * "Your font" and identify a recent project by its glyph count and the time it was opened.
     */
    val projectNames: ProjectNames = ProjectNames.WITHHELD
}

/**
 * The state shapes of SCREENS_V1 §9.1: the explorer's five `mk-*` marks plus the ink block.
 * Each is always drawn next to its word, or given a spoken name where it stands alone.
 */
enum class Mark {
    /** ✓ done: approved, saved, a task done, a check passing. */
    PASS,

    /** ◐ part way: unlocked for editing, a change waiting to save, a check warning. */
    WARN,

    /** ● needs you: a save failed or conflicts, a check failing. */
    FAIL,

    /** ○ for information: read-only, an information check. */
    INFO,

    /** ◌ not started or not known: not yet approved, a task to do, a save not confirmed. */
    TODO,

    /** ■ the current one: the task you're on, the project open now. */
    BLOCK,
}
