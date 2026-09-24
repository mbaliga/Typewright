package dev.aarso.typewright.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aarso.typewright.ui.tokens.Argb
import dev.aarso.typewright.ui.tokens.CanvasTexture
import dev.aarso.typewright.ui.tokens.SpacingTokens
import dev.aarso.typewright.ui.tokens.Typography
import dev.aarso.typewright.ui.tokens.toColor

/**
 * One label/value pair in the [InspectorRow] (UI_SPEC §3 "Inspector"). [emphasize] renders the
 * value as "an ink block" (the edited or snapped value); [tint] overrides the value's colour for
 * "Cyan value for the snap target; amber for probe readings" -- pass `null` for neither.
 */
data class InspectorField(
    val label: String,
    val value: String,
    val emphasize: Boolean = false,
    val tint: Argb? = null,
)

/**
 * The inspector row (UI_SPEC §3 "Inspector", glass, bottom): "A single row of label/value pairs:
 * label mono 9.5 sp uppercase muted; value 15 sp semibold tabular. The edited or snapped value is
 * an ink block ... Never more than six pairs; wrap is a failure of content, not layout."
 *
 * Task P4b's own scope: "Placeholder content for this task ... show the camera's own X/Y/zoom as
 * a proof the row renders and updates live as you pan" -- see [dev.aarso.typewright.ui.sheet.
 * TypewrightSheet] for the actual fields passed in; real construction-tool values are P5b's job.
 */
@Composable
fun InspectorRow(
    fields: List<InspectorField>,
    texture: CanvasTexture,
    modifier: Modifier = Modifier,
) {
    require(fields.size <= 6) { "UI_SPEC §3 Inspector: \"Never more than six pairs\", got ${fields.size}" }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = SpacingTokens.GUTTER_DP.dp, vertical = SpacingTokens.INSPECTOR_FROM_BOTTOM_DP.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (field in fields) {
            Column {
                BasicText(text = field.label.uppercase(), style = Typography.inspectorLabel.copy(color = texture.muted.toColor()))
                val valueColor = field.tint?.toColor() ?: texture.fg.toColor()
                if (field.emphasize) {
                    Box(modifier = Modifier.background(texture.ink.toColor()).padding(horizontal = 4.dp)) {
                        BasicText(text = field.value, style = Typography.inspectorValue.copy(color = texture.canvas.toColor()))
                    }
                } else {
                    BasicText(text = field.value, style = Typography.inspectorValue.copy(color = valueColor))
                }
            }
        }
    }
}
