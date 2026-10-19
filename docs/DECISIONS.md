# Decision register — 22–23 September 2026 design sessions

Decisions made by Madhav in conversation, recorded so the build does not re-ask them.

| # | question | decision |
|---|---|---|
| D1 | v1 platforms | Android, Linux (JVM desktop), Web (Kotlin/Wasm). Windows/macOS later on the same desktop target; iOS/iPadOS later, which is why compile sits behind an interface with fontc planned. |
| D2 | Node-economy judgement | A box-and-whisker per glyph against the top 30 fonts per style, the user's glyph as a point. Style classes from the Google Fonts taxonomy. Not an invented threshold. |
| D3 | Corpus ranking | Both, as a toggle: Google's Drawing quality score (default, shipped) and popularity via the Fonts API (needs a key at build time). |
| D4 | Style identification | Declared in the brief, inferred from measurable features, confirmed by the user. The inference is a lesson. |
| D5 | Lessons content | Scaffold now, marked SCAFFOLD; Madhav's Domestika material augments, improves and steers. |
| D6 | "Cinematic" means | Motion and scale only. No narration. Ambient sound optional, off by default, for staying in flow. Lessons are live geometry, never video. |
| D7 | Visual style | Minimal and brutalist: hard edges, ink blocks, opaque paper, no shadows or blur, whitespace and the bloom instead of boxes and rules. |
| D8 | Bloom | Canvas lines and grain fade as they approach any UI element; letters never fade. |
| D9 | Navigation | The room model, evolved: one sheet, one plane; the grid and content move, the glass stays. Rooms are the pipeline stages; zoom is depth; a map on pinch-out. |
| D10 | Selection | Ink blocks with paper text for anything selected; plain ink text for everything else. ("undeleted" read as "unselected" — [CONFIRM]) |
| D11 | Tools on a phone | A floating, pinned tool puck: swipe to cycle, tap to unfold a collapsible list, hold for a spinnable radial, drag the grip to re-pin. Contextual actions on a hold over the object, not in the puck. |
| D12 | Trace stages | The stage strip is a scrubber, not a row of steps. Three stages by default with the rest behind "adjust" is the lean; [CONFIRM]. |
| D13 | Editing tools | Tiered: spiro/Hobby curves, transforms, layers, kerning groups + .fea, guides, palette commands in v1; nib tools and components v1.5; interpolation v2; no manual hinting. |
| D14 | Shaping in v1 | HarfBuzz via the platform stacks (TextRunShaper, Skiko, browser), not JNI. |
| D15 | Fixtures | The shipped Hyle Deco TTFs are the regression suite, stated on-curve·off-curve·total (restated 2026-09-24, P0c, written reason: docs/ARCHITECTURE_REVIEW.md section 5 items 13–22): T 1,763·0·1,763 → 8·0·8, o 80·0·80 → 16·16·32, n 44·0·44 → 14·8·22, H 1,252·0·1,252 → 12·0·12. |
| D16 | Model routing | Opus for the architecture review, the fitter, construction geometry/spiro and the style-detector features; Sonnet for everything else; the chat app for UI iteration on the explorer. |
| D17 | Licence | FSL-1.1-ALv2 for the app, Apache-2.0 for the engine, CC0-1.0 for template sheets and data packs. Fonts people make with Typewright are theirs. Decided 25 Sep 2026 by Madhav; `docs/LICENSING.md`. |
| D18 | The declared brief vs. the Brief's ethos | One record, one reader: the Brief's `ethos` is the source of truth; the declared fields (`model`, `use`, `styleClass.declared`) are derived from it, never a second copy a person could set independently. `readAs` is the one reader; `qa:corpus`'s older `rankStyleClasses` is deprecated, not removed. Decided 18 Oct 2026 by Madhav; `docs/HANDOFF_BRIEF_FOLD.md`, `docs/BRIEF.md` "One record". |
| D19 | The explorer is the look; the deck earns its place | **Two directions, 19 Oct 2026, from the owner's review.** (1) Where `ui/typewright-explorer.html` (and `UI_SPEC.md`) and the V1 canvas boards disagree on how something looks, the explorer wins, because it is what the owner has verified by eye; the canvas still governs the S02 layout, the other V1 boards and the deck's geometry, drawn in the explorer's tokens. This reverses the earlier "canvas wins" table; its other rows stand. (2) The arc deck stays, and each part has one job: left leaves (Back, or Close only where leaving discards something), centre is the toggle arc, which holds this page's views (one module, `DeckSeats`, says which), and the section arc, which is live; right does (the step's one dominant action, removed from the page, or ⋯). A control with no job is hidden. Borrowed from the owner's Bocal dock: rules, not look. `docs/HANDOFF_BRIEF_AMENDMENT_1.md`, `SCREENS_V1` §2. |
| D20 | Themes: one file per theme, guardrails enforced, built-ins read-only | A theme is a small JSON file with every colour role, saved to the app's config (law 7: exportable). The guardrails of SCREENS_V1 §3.2 are enforced, with ΔE as CIE76 in CIELAB because OKLab × 100 is too small a unit for "within ΔE 20 of the close red"; the close red lives in `CloseToken` and no role may sit near it. Colour literals are allowed only in `ui/tokens` and `ui/theme`, and a test fails on any other. Decided 19 Oct 2026 for the P13 theme system; `docs/OPEN_QUESTIONS.md` 148 and 149. |

Still open: the name, the vertical margin/proof axis, default texture, the bundled UI face, the
hosted endpoint's home.
