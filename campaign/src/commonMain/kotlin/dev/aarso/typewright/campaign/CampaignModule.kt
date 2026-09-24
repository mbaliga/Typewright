package dev.aarso.typewright.campaign

/**
 * Names this module. Its first real API has now landed alongside this object -- see this
 * module's own README for the map:
 * - [WorkbookTask], [Demonstration], [WorkbookGateSpec] (`WorkbookTask.kt`): the workbook's data
 *   model.
 * - [WorkbookLatinContent] (`WorkbookLatinContent.kt`): the real twelve-task Latin workbook,
 *   loaded from this module's own checked-in YAML content (`WorkbookYamlParser.kt`).
 * - [WorkbookGates] (`WorkbookGates.kt`): the real Kotlin functions that run a task's gate
 *   against a real project.
 */
object CampaignModule {
    const val NAME: String = "campaign"
}
