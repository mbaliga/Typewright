// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.campaign

/**
 * The Latin workbook's real content: paths and loaders for `workbook-latin.yaml`
 * ([RESOURCE_PATH]), checked in under
 * `campaign/src/commonMain/resources/campaign/workbook-latin.yaml` and read back through
 * [readCampaignResourceText] -- see that function's own KDoc for why this file lives directly
 * under this module's own resources rather than being `Sync`'d in from `data/`, and for the
 * jvm/wasmJs split. Follows `learn:scenes`' own `CraftResources`/`LineagesResources` shape
 * (a path constant plus a `load*` function) rather than inventing a different one.
 *
 * All twelve tasks carry `scaffold: true` (docs/TYPEWRIGHT_HANDOFF.md "M5. Campaign, the
 * workbook" `[SCAFFOLD, awaiting the Domestika lessons]`) -- first-draft content that ships
 * marked SCAFFOLD until Madhav's material replaces or augments it.
 */
public object WorkbookLatinContent {
    /** Path, relative to this module's resources root, of the Latin workbook's YAML content. */
    public const val RESOURCE_PATH: String = "campaign/workbook-latin.yaml"

    /** [RESOURCE_PATH], parsed into the twelve [WorkbookTask]s, in task order (1-12). */
    public fun load(): List<WorkbookTask> = parseWorkbookTasks(readCampaignResourceText(RESOURCE_PATH))
}
