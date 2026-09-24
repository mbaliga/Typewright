# campaign

The workbook engine: tasks as data with why, demonstration, task, gate and reflection, whose gates call qa and the node-economy boxes (handoff M5, brief §9). The twelve-task Latin workbook ships marked SCAFFOLD until the designer's lessons replace it.

## What this task built

- `WorkbookTask.kt`: the data model -- `WorkbookTask` (index, title, scaffold, why, demonstration,
  task, controlString, gate, reflection), `Demonstration` (`SceneDemonstration`, reusing a real
  `learn:scenes` scene, or `StatDemonstration`, real measured numbers on a sample word/font -- see
  `WorkbookTask.kt`'s own KDoc for which of the twelve tasks took which shape and why), and
  `WorkbookGateSpec` (a task's gate as a static declaration: a real `qa` function's name, or an
  honest not-implemented reason).
- `workbook-latin.yaml` (`src/commonMain/resources/campaign/`): the real twelve-task content,
  parsed by `WorkbookYamlParser.kt` (`parseWorkbookTasks`) using `learn:scenes`' own hand-rolled
  YAML parser -- no new YAML library, for the identical Wasm-stability reason `learn:scenes`
  itself gives. `WorkbookLatinContent.kt`/`CampaignResources.kt` (+ jvm/wasmJs actuals) load it,
  mirroring `learn:scenes`' own `CraftResources`/`LineagesResources`/`SceneResources` shape.
- `WorkbookGates.kt`: the real Kotlin functions that run a task's gate against a real
  `core-font` `UfoProject`/compiled-font input and return a real, structured `WorkbookGateResult`
  (pass/fail/warn/info/not-implemented per sub-check, with the real measured detail) --
  `task3SetMetrics`, `task4ControlCharacters`, `task5DeriveTheFamily`, `task6HardLetters`,
  `task9DiacriticsAndAnchors`, `task11Test`, `task12Ship`, and `notImplementedGate` for the rest.

## Which qa function backs each task's gate

| task | gate | qa function |
|---|---|---|
| 1 Choose your reference | not implemented | -- (a judgment call, no automated check) |
| 2 Read it | not implemented | -- (a judgment call, no automated check) |
| 3 Set metrics | overshoot presence | `dev.aarso.typewright.qa.checkOvershootPresence` |
| 4 Control characters | node economy | `dev.aarso.typewright.qa.checkNodeEconomy` |
| 5 Derive the family | geometric sanity | `dev.aarso.typewright.qa` (`GeometricChecks.kt`, six checks run together) |
| 6 The hard letters | geometric sanity | `dev.aarso.typewright.qa` (`GeometricChecks.kt`, six checks run together) |
| 7 Spacing | not implemented | -- (M7 names it; no qa function built yet) |
| 8 Kerning | not implemented | -- (M7 names it; no qa function built yet) |
| 9 Diacritics and anchors | anchor presence | `dev.aarso.typewright.qa.checkAnchorsPresent` |
| 10 Extend a script (optional) | not implemented | -- (no script-specific check exists yet) |
| 11 Test | layer one | `dev.aarso.typewright.qa.LayerOneChecker` (`platformLayerOneChecker`) |
| 12 Ship | repo scaffold sanity | `dev.aarso.typewright.qa.ship` (`generateOflText`/`generateDescriptionHtml`/`generateMetadataPbText`) |

Tasks 1, 2, 7, 8 and 10 have no implemented `qa` function to wire to as of this task (7 and 8 are
named but unbuilt in `docs/TYPEWRIGHT_HANDOFF.md` "M7. Quality gate and Google Fonts pipeline"; 1,
2 and 10 are judgment calls or optional, not measurable properties) -- their gate data says so
honestly (`WorkbookGateSpec.notImplementedReason`), never a fabricated pass/fail. No new check
logic was added to `qa` itself to fill these gaps; that is out of this task's scope.

## Real font input for task 4's gate

There is no SFNT-to-`UfoProject` bridge in `core-font`. This build's own established precedent
(P6's `HyleDecoProjectFontBytes.kt`) is to use `fonts/HyleDeco-Regular.ttf`, this project's own
real test-fixture font, as an honestly-disclosed reference/stand-in: `HyleDecoTask4GateTest.kt`
(`src/jvmTest`) reads it with `core-font`'s real SFNT reader (`readSfntFont`), builds a small real
`UfoProject` around its real `n`/`o`/`H`/`O` glyphs, and runs `WorkbookGates.task4ControlCharacters`
against the real `sans-geometric` node-economy corpus -- see that test's own KDoc for the real
measured numbers and verdicts (`docs/OPEN_QUESTIONS.md` item 86 flags the missing bridge itself as
future work).

## Tests

`commonTest` (`WorkbookYamlParserTest`, `WorkbookLatinContentTest`, `WorkbookGatesTest`,
`WorkbookProgressTest`, `CampaignModuleTest`, all real assertions, no snapshot/golden-file
shortcuts) runs on both `jvm` and `wasmJs` (Node) -- 44 tests, both targets, zero failures as of
this verify pass (2026-09-24, P7 verify -- an earlier draft of this section undercounted by 15
tests, omitting `WorkbookProgressTest` from this list entirely; corrected here, no test content
changed). Two more live in `jvmTest` only, each for a real, disclosed reason rather than convenience:
`HyleDecoTask4GateTest` (file I/O -- `fonts/HyleDeco-Regular.ttf` is read from disk, JVM-only, the
same reason `core-geometry`'s `FitPipelineHyleDecoValidationTest` is JVM-only) and
`WorkbookSceneDemonstrationsJvmTest` (cross-checks task 1's/9's scene ids against `learn:scenes`'
own real `LineagesResources`/`CraftResources` loaders; their wasmJs actual reads Node `fs`
relative to *`learn:scenes`' own* compiled module's `import.meta.url`, which throws when called
from a different module's compiled `wasmJs` test bundle -- `docs/OPEN_QUESTIONS.md` item 87, a
sibling finding to item 47's browser/Node split, both worked around the same way `ui`'s own
`LineagesQuizItemsTest` already established). No new dependency was added anywhere in this
module; `THIRD_PARTY.md` needs no new entry.
