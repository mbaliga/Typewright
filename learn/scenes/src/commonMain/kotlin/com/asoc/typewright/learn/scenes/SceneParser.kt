// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.learn.scenes

import com.asoc.typewright.learn.scenes.yaml.YamlParseException
import com.asoc.typewright.learn.scenes.yaml.YamlValue
import com.asoc.typewright.learn.scenes.yaml.mappingOrNull
import com.asoc.typewright.learn.scenes.yaml.parseYaml
import com.asoc.typewright.learn.scenes.yaml.requireMapping
import com.asoc.typewright.learn.scenes.yaml.requireScalar
import com.asoc.typewright.learn.scenes.yaml.scalarOrNull
import com.asoc.typewright.learn.scenes.yaml.sequenceOrEmpty

/**
 * Parses one scene's YAML text (`docs/LESSONS_SCAFFOLD.md` section 1, CANON) into a [Scene].
 * [com.asoc.typewright.learn.scenes.yaml.parseYaml] does the generic YAML-subset parsing; this
 * file is the schema-specific mapping from that generic tree onto [Scene]'s typed fields, so a
 * malformed scene fails with a message naming the field and, where useful, the offending text —
 * never a silent default.
 *
 * @throws YamlParseException if [yaml] is not valid YAML in this parser's supported subset, or
 *   describes something that is not a well-formed [Scene] (a missing required field, an unknown
 *   [Strand]/[Align] value, a [Stage.stress] sequence that is not exactly two numbers, and so on).
 */
public fun parseScene(yaml: String): Scene {
    val root = parseYaml(yaml)
    val mapping = root as? YamlValue.Mapping ?: throw YamlParseException("a scene document must be a mapping at its top level")
    return sceneFromMapping(mapping)
}

private fun sceneFromMapping(mapping: YamlValue.Mapping): Scene {
    val id = mapping.requireScalar("id")
    val strand = strandFromScalar(mapping.requireScalar("strand"), id)
    val title = mapping.requireScalar("title")
    val era = mapping.scalarOrNull("era")
    val duration =
        mapping.requireScalar("duration").toIntOrNull()
            ?: throw YamlParseException("scene '$id': 'duration' must be a whole number of seconds")
    val faces = facesFromSequence(mapping, id)
    val stage = stageFromMapping(mapping.requireMapping("stage"), id)
    val caption = captionFromMapping(mapping.requireMapping("caption"), id)
    val callouts = calloutsFromSequence(mapping, id)
    val exercise = mapping.mappingOrNull("exercise")?.let { exerciseFromMapping(it, id) }
    val scaffold = scaffoldFromMapping(mapping, id)
    return Scene(
        id = id,
        strand = strand,
        title = title,
        era = era,
        duration = duration,
        faces = faces,
        stage = stage,
        caption = caption,
        callouts = callouts,
        exercise = exercise,
        scaffold = scaffold,
    )
}

/**
 * `scaffold: true/false` (see [Scene.scaffold]'s KDoc for why this field exists), optional and
 * defaulting to `false` like [callouts]/[exercise] above rather than required — a scene with no
 * opinion on the question is not silently marked SCAFFOLD.
 */
private fun scaffoldFromMapping(
    mapping: YamlValue.Mapping,
    sceneId: String,
): Boolean {
    val raw = mapping.scalarOrNull("scaffold") ?: return false
    return when (raw.trim().lowercase()) {
        "true" -> true
        "false" -> false
        else -> throw YamlParseException("scene '$sceneId': 'scaffold' must be true or false, found '$raw'")
    }
}

private fun strandFromScalar(
    raw: String,
    sceneId: String,
): Strand =
    when (raw.trim().lowercase()) {
        "lineages" -> Strand.LINEAGES

        "anatomy" -> Strand.ANATOMY

        "craft" -> Strand.CRAFT

        "scripts" -> Strand.SCRIPTS

        "reading" -> Strand.READING

        else -> throw YamlParseException(
            "scene '$sceneId': unknown strand '$raw' (expected one of lineages, anatomy, craft, scripts, reading)",
        )
    }

private fun alignFromScalar(
    raw: String,
    sceneId: String,
): Align =
    when (raw.trim().lowercase()) {
        "xheight" -> Align.XHEIGHT

        "capheight" -> Align.CAPHEIGHT

        "baseline" -> Align.BASELINE

        else -> throw YamlParseException(
            "scene '$sceneId': unknown stage.align '$raw' (expected one of xheight, capheight, baseline)",
        )
    }

private fun facesFromSequence(
    mapping: YamlValue.Mapping,
    sceneId: String,
): List<FaceRef> =
    mapping.sequenceOrEmpty("faces").map { item ->
        val faceMapping =
            item as? YamlValue.Mapping
                ?: throw YamlParseException("scene '$sceneId': each 'faces' entry must be a mapping of key/family")
        FaceRef(
            key = faceMapping.requireScalar("key"),
            family = faceMapping.requireScalar("family"),
            source = faceSourceFromScalar(faceMapping.scalarOrNull("source"), sceneId),
            path = faceMapping.scalarOrNull("path"),
        )
    }

/**
 * `faces[].source: corpus/project` ([FaceSource]'s own KDoc), optional and defaulting to
 * [FaceSource.CORPUS] like every other Craft-strand addition here — an existing `faces` entry
 * with no `source` key keeps its original CANON meaning unchanged.
 */
private fun faceSourceFromScalar(
    raw: String?,
    sceneId: String,
): FaceSource =
    when (raw?.trim()?.lowercase()) {
        null, "corpus" -> FaceSource.CORPUS

        "project" -> FaceSource.PROJECT

        else -> throw YamlParseException(
            "scene '$sceneId': unknown faces[].source '$raw' (expected corpus or project)",
        )
    }

private fun stageFromMapping(
    mapping: YamlValue.Mapping,
    sceneId: String,
): Stage =
    Stage(
        sample = mapping.requireScalar("sample"),
        align = alignFromScalar(mapping.requireScalar("align"), sceneId),
        from = mapping.requireScalar("from"),
        to = mapping.requireScalar("to"),
        stress = stressFromMapping(mapping, sceneId),
        pipeline = mapping.scalarOrNull("pipeline"),
    )

private fun stressFromMapping(
    mapping: YamlValue.Mapping,
    sceneId: String,
): Pair<Double, Double>? {
    val value = mapping["stress"] ?: return null
    val items = (value as? YamlValue.Sequence)?.items ?: throw YamlParseException("scene '$sceneId': stage.stress must be a list")
    if (items.size != 2) {
        throw YamlParseException("scene '$sceneId': stage.stress must have exactly 2 numbers (from, to), found ${items.size}")
    }
    val (fromRaw, toRaw) = items
    val from =
        (fromRaw as? YamlValue.Scalar)?.text?.toDoubleOrNull()
            ?: throw YamlParseException("scene '$sceneId': stage.stress's first value must be a number")
    val to =
        (toRaw as? YamlValue.Scalar)?.text?.toDoubleOrNull()
            ?: throw YamlParseException("scene '$sceneId': stage.stress's second value must be a number")
    return from to to
}

private fun captionFromMapping(
    mapping: YamlValue.Mapping,
    sceneId: String,
): Caption =
    Caption(
        tool = mapping.requireScalar("tool"),
        text = mapping.requireScalar("text"),
        tags = stringListFromSequence(mapping, "tags", sceneId),
    )

private fun calloutsFromSequence(
    mapping: YamlValue.Mapping,
    sceneId: String,
): List<Callout> =
    mapping.sequenceOrEmpty("callouts").map { item ->
        val calloutMapping =
            item as? YamlValue.Mapping
                ?: throw YamlParseException("scene '$sceneId': each 'callouts' entry must be a mapping of glyph/part/label")
        Callout(
            glyph = calloutMapping.requireScalar("glyph"),
            part = calloutMapping.requireScalar("part"),
            label = calloutMapping.requireScalar("label"),
        )
    }

private fun exerciseFromMapping(
    mapping: YamlValue.Mapping,
    sceneId: String,
): Exercise =
    Exercise(
        word = mapping.requireScalar("word"),
        face = mapping.requireScalar("face"),
        options = stringListFromSequence(mapping, "options", sceneId),
        answer = mapping.requireScalar("answer"),
        giveaway = mapping.requireScalar("giveaway"),
    )

private fun stringListFromSequence(
    mapping: YamlValue.Mapping,
    key: String,
    sceneId: String,
): List<String> =
    mapping.sequenceOrEmpty(key).map { item ->
        (item as? YamlValue.Scalar)?.text
            ?: throw YamlParseException("scene '$sceneId': '$key' entries must be plain scalars, found $item")
    }
