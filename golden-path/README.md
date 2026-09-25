# golden-path

`golden-path` is the release acceptance harness for `docs/V1_SCOPE.md`'s golden path: one
headless desktop-JVM test per step, driven against the real modules (never the UI), so "the
golden path passes" always means real code ran, not a screenshot or a fixture standing in for
one. It reports the pass count on every prompt's own build and stops a passing step from ever
quietly regressing again.

## Seeds

Every step test starts from one of these fixed seeds, built in `Seeds.kt`; a step never starts
from another step's own output (only the chain tests carry real output forward, and each still
starts its own run from a seed here).

| seed | what | source |
|---|---|---|
| S-ttf | Hyle Deco Regular, this repo's own dogfood font | `fonts/HyleDeco-Regular.ttf` |
| S-ttf2 | a second real, static, OFL font, for the "not just the one fixture" checks | `data/learn-faces/poppins/Poppins-Regular.ttf` |
| S-ufo | S-ttf converted through core-font's `SfntFont.toUfoProject()` bridge, written to a fresh temp directory | `Seeds.newSUfoDirectory()` |
| S-project | S-ttf through the same bridge (`familyName = "Golden Path Seed"`), with every glyph's contours exactly degree-elevated from `writeGlif`-unwritable QUADRATIC to CUBIC (docs/OPEN_QUESTIONS.md item 125) — step 5's own seed, since its job is to measure compiling, not importing | `Seeds.newSProject()` |

## The rules

- **Each step test starts from its seed**, never from a prior step's leftover state.
- **A step that isn't built yet fails with a named reason**, via `GoldenPathSteps.notBuilt(...)`
  (`StepNotBuilt`, never `@Ignore`, never an assumption): `"Step <step> (<name>) not built:
  <missing>; closes in <closesIn>"`, naming the prompt(s) that build it.
- **Step 1 counts as passing only when both `1a` and `1b` pass.**
- **The chain tests (`chainOpen`, `chainScan`) don't count** toward "N/7 steps pass"; they run
  several steps back to back with real output carried forward and stop at the first failure.
- **`goldenPath` never fails on step results** — it prints the pass count and a one-line reason
  per step, always. Only a broken harness (no `GoldenPathTest` results at all) fails it.
- **`goldenPathRatchet` fails the build** when `passing-steps.txt`'s own rule (see its header
  comment) is broken: a listed id stops passing, a passing id isn't listed, an unknown id is
  listed, one of steps `1a`-`7` has no testcase, or anything is `SKIPPED`.
- **To add a step to `passing-steps.txt`**: in the same PR that makes it really pass end to end
  against its real seed, add its id (one of `1a`, `1b`, `2`, `3`, `4`, `5`, `6`, `7`,
  `chain-open`, `chain-scan`) on its own line. `goldenPathRatchet` then holds it there.

## How to run

```
./gradlew :golden-path:goldenPath
```

prints and writes `build/golden-path/summary.txt`. `./gradlew :golden-path:goldenPathRatchet`
enforces the ratchet (CI runs both; only the ratchet can fail the build). Both need `python3` on
`PATH` for `compile`'s and `qa`'s own backend/checker lookups to answer honestly, even while
those backends stay stubs.
