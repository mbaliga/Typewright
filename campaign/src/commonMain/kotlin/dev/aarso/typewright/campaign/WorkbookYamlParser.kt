// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.campaign

import dev.aarso.typewright.learn.scenes.yaml.YamlParseException
import dev.aarso.typewright.learn.scenes.yaml.YamlValue
import dev.aarso.typewright.learn.scenes.yaml.parseYaml
import dev.aarso.typewright.learn.scenes.yaml.requireMapping
import dev.aarso.typewright.learn.scenes.yaml.requireScalar
import dev.aarso.typewright.learn.scenes.yaml.scalarOrNull
import dev.aarso.typewright.learn.scenes.yaml.sequenceOrEmpty

/**
 * Parses the Latin workbook's YAML content (a top-level sequence of task mappings, one per
 * [WorkbookTask] -- see [WorkbookLatinContent] for the real, checked-in content) using
 * `learn:scenes`' own hand-rolled YAML parser/subset
 * ([dev.aarso.typewright.learn.scenes.yaml.parseYaml]), rather than a library. This is this
 * task's own instruction, for the identical reason
 * [dev.aarso.typewright.learn.scenes.yaml]'s own top-of-file KDoc gives for `learn:scenes` itself:
 * kaml's Wasm/JS support is, by its own maintainers' words, "highly experimental ... may be
 * removed or modified at any time", and `campaign` (`campaign/build.gradle.kts`'s own
 * `api(project(":learn:scenes"))`) must build for `wasmJs` exactly like `learn:scenes` does. No
 * YAML library is added here either; `THIRD_PARTY.md` needs no new entry.
 *
 * The schema this file maps onto [WorkbookTask] (see [WorkbookGateSpec] and [Demonstration] for
 * their own sub-schemas):
 * ```yaml
 * - index: 1
 *   title: "Choose your reference"
 *   scaffold: true
 *   why: >
 *     ...
 *   demonstration:
 *     kind: scene        # or: kind: stats
 *     sceneId: lineages.transitional
 *     # kind: stats instead uses:
 *     # label: "..."
 *     # stats:
 *     #   - "..."
 *   task: >
 *     ...
 *   controlString: "nnonnonon"   # omitted when a task names none
 *   gate:
 *     summary: "..."
 *     qaFunction: "dev.aarso.typewright.qa.checkNodeEconomy"   # omitted when not implemented
 *     glyphs: [n, o, H, O]                                      # omitted when not applicable
 *     notImplementedReason: >                                   # omitted when qaFunction is set
 *       ...
 *   reflection: >
 *     ...
 * ```
 */
public fun parseWorkbookTasks(yaml: String): List<WorkbookTask> {
    val root = parseYaml(yaml)
    val sequence =
        root as? YamlValue.Sequence
            ?: throw YamlParseException("the workbook document must be a top-level sequence of tasks, found ${root::class.simpleName}")
    return sequence.items.map { item ->
        val mapping = item as? YamlValue.Mapping ?: throw YamlParseException("each workbook task must be a mapping, found $item")
        workbookTaskFromMapping(mapping)
    }
}

private fun workbookTaskFromMapping(mapping: YamlValue.Mapping): WorkbookTask {
    val indexRaw = mapping.requireScalar("index")
    val index = indexRaw.toIntOrNull() ?: throw YamlParseException("workbook task: 'index' must be a whole number, found '$indexRaw'")
    val title = mapping.requireScalar("title")
    val scaffold = booleanFromMapping(mapping, "scaffold", index)
    val why = mapping.requireScalar("why")
    val demonstration = demonstrationFromMapping(mapping.requireMapping("demonstration"), index)
    val task = mapping.requireScalar("task")
    val controlString = mapping.scalarOrNull("controlString")
    val gate = gateSpecFromMapping(mapping.requireMapping("gate"), index)
    val reflection = mapping.requireScalar("reflection")
    return WorkbookTask(
        index = index,
        title = title,
        scaffold = scaffold,
        why = why,
        demonstration = demonstration,
        task = task,
        controlString = controlString,
        gate = gate,
        reflection = reflection,
    )
}

/** `scaffold: true/false`, defaulting to `false` when absent -- mirrors `learn:scenes`' own `Scene.scaffold` parsing exactly. */
private fun booleanFromMapping(
    mapping: YamlValue.Mapping,
    key: String,
    index: Int,
): Boolean {
    val raw = mapping.scalarOrNull(key) ?: return false
    return when (raw.trim().lowercase()) {
        "true" -> true
        "false" -> false
        else -> throw YamlParseException("task $index: '$key' must be true or false, found '$raw'")
    }
}

private fun demonstrationFromMapping(
    mapping: YamlValue.Mapping,
    index: Int,
): Demonstration =
    when (val kind = mapping.requireScalar("kind").trim().lowercase()) {
        "scene" -> {
            Demonstration.SceneDemonstration(sceneId = mapping.requireScalar("sceneId"))
        }

        "stats" -> {
            Demonstration.StatDemonstration(
                label = mapping.requireScalar("label"),
                stats = stringListFromSequence(mapping, "stats", index),
            )
        }

        else -> {
            throw YamlParseException("task $index: demonstration.kind must be 'scene' or 'stats', found '$kind'")
        }
    }

private fun gateSpecFromMapping(
    mapping: YamlValue.Mapping,
    index: Int,
): WorkbookGateSpec {
    val summary = mapping.requireScalar("summary")
    val qaFunction = mapping.scalarOrNull("qaFunction")
    val glyphs = stringListFromSequence(mapping, "glyphs", index)
    val notImplementedReason = mapping.scalarOrNull("notImplementedReason")
    return WorkbookGateSpec(
        summary = summary,
        qaFunction = qaFunction,
        glyphs = glyphs,
        notImplementedReason = notImplementedReason,
    )
}

private fun stringListFromSequence(
    mapping: YamlValue.Mapping,
    key: String,
    index: Int,
): List<String> =
    mapping.sequenceOrEmpty(key).map { item ->
        (item as? YamlValue.Scalar)?.text
            ?: throw YamlParseException("task $index: '$key' entries must be plain scalars, found $item")
    }
