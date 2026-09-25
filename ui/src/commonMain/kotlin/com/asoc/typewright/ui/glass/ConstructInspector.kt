// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.glass

import com.asoc.typewright.ui.puck.ActiveConstruction
import com.asoc.typewright.ui.puck.constructValuesFor
import com.asoc.typewright.ui.tokens.toFixedString

/**
 * The Construct inspector's own field content (P5b task: "select and adapt the subset that fits
 * the brief's own explicit ask here: stem (as a font-wide value), contrast, exponent, width, and
 * a parametric/baked indicator"). [InspectorRow]'s own "never more than six pairs" rule means the
 * explorer's own much larger Desktop-frame `.dins` panel (a Node card, a Glyph checklist card, a
 * Metrics card -- grep `class="dins"`) cannot be reproduced verbatim here; these five fields are
 * this task's own reading of which subset fits one [InspectorRow], not a literal transcription of
 * any one explorer screen.
 *
 * **The "parametric · bake" toggle has no explorer look to reproduce, stated plainly rather than
 * invented:** `TYPEWRIGHT_BUILD_BRIEF.md`/`docs/TYPEWRIGHT_HANDOFF.md`'s own wording is the only
 * source for this phrase; the explorer's own CSS carries a dead, never-instantiated `.bake` rule
 * (`ui/typewright-explorer.html` line 224) with no markup anywhere using it. [STATE_LABEL]'s own
 * row below is this task's own reasonable reading of [InspectorField]'s existing label/value/
 * emphasize pattern applied to that phrase -- an adaptation, not a reproduction of a shown
 * screen -- logged in `docs/OPEN_QUESTIONS.md` per this task's own instruction.
 */
fun constructInspectorFields(active: ActiveConstruction): List<InspectorField> {
    val values = constructValuesFor(active)
    return listOf(
        InspectorField(label = "STEM", value = values.fontWideStem.toFixedString(0)),
        InspectorField(label = "CONTRAST", value = values.contrast?.toFixedString(2) ?: NO_VALUE),
        InspectorField(label = "EXPONENT", value = values.exponent?.toFixedString(2) ?: NO_VALUE),
        InspectorField(label = "WIDTH", value = values.width?.toFixedString(0) ?: NO_VALUE),
        InspectorField(
            label = STATE_LABEL,
            value = if (values.baked) "BAKE" else "PARAMETRIC",
            // Matches inspectorFields()'s own existing precedent (TypewrightSheet.kt's camera
            // ZOOM field): the row's own "headline" value is always emphasized, not only when
            // edited/snapped -- InspectorRow's own documented, reused pattern, not new chrome.
            emphasize = true,
        ),
    )
}

private const val STATE_LABEL = "STATE"
private const val NO_VALUE = "—"
