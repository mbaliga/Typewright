// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.learn.scenes

import dev.aarso.typewright.learn.scenes.yaml.YamlParseException
import dev.aarso.typewright.learn.scenes.yaml.YamlValue
import dev.aarso.typewright.learn.scenes.yaml.parseYaml
import dev.aarso.typewright.learn.scenes.yaml.requireScalar
import dev.aarso.typewright.learn.scenes.yaml.sequenceOrEmpty

/**
 * Parses a strand's identify-it exercise bank YAML (e.g.
 * `scenes/lineages/identify-it-bank.yaml`) into the [ExerciseBankEntry] list
 * [StrandSequencer.plan] takes as its `bank` argument.
 *
 * There is no CANON schema for this — `docs/LESSONS_SCAFFOLD.md` section 1 only defines a
 * [Scene] and its own optional inline `exercise:` block ([Exercise]: `word`, `face`, `options`,
 * `answer`, `giveaway`); it says nothing about the separate bank section 2 describes ("Identify-
 * it bank (faces not on stage)"), because [ExerciseBankEntry] (`learn/scenes`'s own type,
 * `StrandSequencer.kt`) is a narrower shape than [Exercise] — just [ExerciseBankEntry.face],
 * [ExerciseBankEntry.answer] and [ExerciseBankEntry.giveaway], with no `word` or `options` at
 * all. This file's YAML shape mirrors that real type exactly: a top-level `bank:` sequence of
 * mappings, each with exactly those three keys. See `identify-it-bank.yaml`'s own header
 * comment for why this reads the real type over the task brief's paraphrase of it.
 *
 * @throws YamlParseException on malformed input, the same way [parseScene] does for a [Scene].
 */
public fun parseExerciseBank(yaml: String): List<ExerciseBankEntry> {
    val root = parseYaml(yaml)
    val mapping =
        root as? YamlValue.Mapping
            ?: throw YamlParseException("an exercise bank document must be a mapping at its top level")
    return mapping.sequenceOrEmpty("bank").map { item ->
        val entry =
            item as? YamlValue.Mapping
                ?: throw YamlParseException("each exercise bank 'bank' entry must be a mapping of face/answer/giveaway")
        ExerciseBankEntry(
            face = entry.requireScalar("face"),
            answer = entry.requireScalar("answer"),
            giveaway = entry.requireScalar("giveaway"),
        )
    }
}
