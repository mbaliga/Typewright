# Typewright build pack

Everything a Claude Code session needs to build Typewright, in one directory. Unzip this
into the root of a new repository and paste the prompts from `PROMPTS_CLAUDE_CODE.md` in
order.

```
typewright-build-pack/
  README.md                      this file
  CLAUDE.md                      repo-level instructions for Claude Code (laws, conventions)
  TYPEWRIGHT_BUILD_BRIEF.md      the consolidated brief; supersedes the handoff where they differ
  UI_SPEC.md                     layers, tokens, components, states, motion — derived from the explorer
  PROMPTS_CLAUDE_CODE.md         P0–P9 with Sonnet/Opus routing
  ui/
    typewright-explorer.html     the UI source of truth (open in a browser; phone-first; try Draw)
  docs/
    TYPEWRIGHT_HANDOFF.md        original handoff (still canon for trace chain, geometry, scripts, pipeline)
    KNOWLEDGE.md                 what went wrong with Hyle Deco, and what the app teaches
    RESEARCH_font_quality.md     the research corpus with sources
    COMPETITOR_ANALYSIS.md       why this and not another tracer
    LESSONS_SCAFFOLD.md          scene format + Lineages / Craft / Scripts / Reading scaffolds
    DECISIONS.md                 the decision register from the design sessions
    hyle-outline-explainer.html  the explainer that started this
  data/
    node-economy-latin.json      per-style, per-glyph distributions + per-family counts (10 classes)
    node-economy-latin.compact.json  the app-side pack the explorer embeds
    families.csv                 Google Fonts taxonomy snapshot (google/fonts tags/all/families.csv)
    exemplars.json               the ten lesson faces: family, file, cap and x-height ratios
    scripts/build_node_economy_corpus.py   regenerates the corpus from the repository
  fonts/
    HyleDeco-Regular.ttf, HyleDeco-Italic.ttf   the fixtures
  scripts/templates/hyle-all-templates.zip     capture template sheets, six scripts
```

## How to use

1. Open `ui/typewright-explorer.html` on a phone and on a desktop. Use the Draw sheet: swipe
   the puck, tap it, hold it and spin, drag its grip, swipe the header, open the map. Read
   the Notes for each screen. This is the app.
2. Start Claude Code in the repo. Run P0 on Opus. Read `docs/ARCHITECTURE_REVIEW.md` before
   going on.
3. Run P1 → P9 in order on the models named. Each prompt reads the files it needs.
4. Keep the explorer and the brief in sync: a UI change is made in the explorer first (in
   the chat app), then copied here, then implemented.

## Regenerating the corpus

```
cd data/scripts
python3 build_node_economy_corpus.py --top 30 --tags ../families.csv --out ../node-economy-latin.json
```

Needs `fontTools` and network access to raw.githubusercontent.com. Optional: set
`GOOGLE_FONTS_API_KEY` to add the popularity ranking (P1 wires this in).

## Status markers

[CANON] decided · [CONFIRM] needs Madhav · [SCAFFOLD] placeholder content shipped marked as
such. The list of open items is `TYPEWRIGHT_BUILD_BRIEF.md` §15.
