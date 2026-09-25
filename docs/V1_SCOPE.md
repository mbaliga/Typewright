# V1 scope

Written 25 Sep 2026, P10, from `PROMPTS_V1.md` §1 and §2 and `docs/SCREENS_V1.md` §4 and §7.
Plain statement of what ships in V1 and what doesn't. See also `docs/SCREENS_V1.md` and
`docs/INTERACTION_V1.md` for the V1 look and interaction, and `PROMPTS_V1.md` for the
prompts (P10–P19) that build it.

## 1. What V1 is

V1 = M0 + M1 for Latin, end to end, on Linux desktop and Android. The web build ships as a
labelled preview.

Per D4, the web preview has no compile: it exports UFO plus the Ship folder, and the user
builds on desktop. The hosted build endpoint the brief's stack section names is not part of
V1's web preview.

## 2. The golden path

The golden path is the acceptance test for the release. It must run on each V1 target. The
seven steps, exactly as `PROMPTS_V1.md` §1 numbers them:

1. **New project** from a photo or scan of a filled Typewright template sheet, **or open**
   an existing TTF/OTF/UFO.
2. **Capture → Trace**: detect the cells, trace and fit them, review on the scrubber, and
   accept. An accepted glyph is locked (law 1).
3. **Draw / Space**: hand-edit with undo, set the kerning, and see node economy live.
4. **Check**: layer two always. Layer one wherever a runtime exists. The Economy view opens
   from Check.
5. **Compile** to TTF on the device or computer.
6. **Ship**: write the Google Fonts repository folder. Ship stays blocked while Check has
   any fails.
7. Close the app and reopen it. The project is exactly as it was.

`:golden-path`'s `GoldenPathTest` is the acceptance test, one test per step (P10 creates
it). Step 1 is split into two sub-steps because two prompts build them: **1a** (open an
existing TTF/OTF/UFO) and **1b** (new project from a scan). The harness tests them
separately, so each can be held by the ratchet from the prompt that builds it. Step 1 counts
as passing only when both do; the seven steps themselves are unchanged.

| Step | What | Closed by |
|---|---|---|
| 1a | Open an existing TTF/OTF/UFO | P11 + P12 |
| 1b | New project from a photo or scan | P16 |
| 2 | Capture → Trace: detect, trace, fit, review, accept (locks the glyph) | P16 (lock model: P11) |
| 3 | Draw / Space: hand-edit with undo, kerning, live node economy | P11 (history) + P17 |
| 4 | Check: layer two always, layer one where a runtime exists; Economy from Check | P14 |
| 5 | Compile to TTF on the device or computer | P14 |
| 6 | Ship: write the Google Fonts repository folder, blocked while Check has fails | P15 |
| 7 | Close and reopen the app; the project is exactly as it was | P11 |

The dogfood font is the **Hyle Deco redraw** (brief §16): the first real font through this
path, and the Workbook's first playthrough runs on it too.

## 3. In V1, labelled

- **Learn**, with real data.
- The **Workbook**, marked **"Early — lessons in progress"** until the Domestika material
  lands.
- **Kana, Devanagari and Naskh** in Draw/Space with shaping preview, marked **"Preview"**.
- The **web build**, marked **preview** (§1 above).

## 4. Not in V1

- M6 Derive (oblique by skeleton shear, bold by offset, masters and interpolation).
- Nib tools.
- Interpolation and masters.
- GitHub push.
- iOS.
- Windows and macOS.
- Hosted builds (the hosted build endpoint; web V1 has no compile, per D4).
- The vertical margin and proof axis.
- Ambient sound content.

(The last five items are `SCREENS_V1.md` §7's list; the first four are `PROMPTS_V1.md` §1's.)

## 5. Per-target table

Which golden-path steps must pass on each V1 target. Compile on Android (step 5, and the
compiled-binary re-check inside step 6) depends on D5's outcome.

| Step | Linux desktop | Android | Web |
|---|---|---|---|
| 1a Open TTF/OTF/UFO | must pass | must pass | must pass (no compile follows) |
| 1b New project from a scan | must pass | must pass | must pass (no compile follows) |
| 2 Capture → Trace | must pass | must pass | must pass |
| 3 Draw / Space | must pass | must pass | must pass |
| 4 Check | must pass (layer one + two) | must pass (layer two always; layer one only if the D5 spike shows fontbakery fits, else "layer one needs the desktop app") | layer two only, and says so |
| 5 Compile | must pass | must pass, route decided by D5 | not applicable — no compile in V1 (D4); Ship offers UFO + folder as a zip |
| 6 Ship | must pass, writes the repo folder | must pass, writes the repo folder | preview only: exports UFO + Ship folder as a zip, says builds need desktop or Android |
| 7 Reopen unchanged | must pass | must pass (owner-verified: save → kill → restore) | must pass |

## 6. Decisions still owed

From `PROMPTS_V1.md` §2. D1 and D3 are decided. D7's documents are delivered, but its mockup
sources, `ui/v1-screens/`, are not in the repository yet (`docs/OPEN_QUESTIONS.md` item 123),
and P11's UI needs them first. The triage adds D10–D14 (`docs/V1_TRIAGE.md`).


- **D2 — Name clearance for "Typewright"** (needed by P18): start the TM-A search now; the
  Android application id already carries the name.
- **D4 — Web V1 compile** (needed by P14): recommendation is no compile in V1 web — export
  UFO plus the Ship folder, build on desktop; revisit the hosted endpoint as a paid product
  later.
- **D5 — Android compile route** (needed after P14's spike): recommendation is Chaquopy,
  now that it's confirmed MIT since 12.0.1; the real risk is native wheels on Android
  (skia-pathops, cffsubr, booleanOperations), with fontc over JNI as the fallback if the
  spike fails.
- **D6 — Workbook in V1: "Early", or hidden until Domestika** (needed by P18):
  recommendation is to ship it as Early — the gates are real.
- **D8 — The brief's open CONFIRMs** (3 vs 8 trace stages, default texture, the bundled UI
  face, margin/proof axis; needed by P13/P16): recommendation is 3 stages by default, Paper
  texture, IBM Plex Sans and IBM Plex Mono bundled (not Inter), margin/proof left for v1.1.
- **D9 — minSdk 31** (needed by P18): recommendation is to keep it.

## 7. Where this differs from the brief

`TYPEWRIGHT_BUILD_BRIEF.md` §1–4 and §12–16, read against this scope:

- **Milestone scope.** Brief §13 lists the full milestone plan, M0 through M6 plus M-web, as
  the build's arc. V1 says V1 is only M0 + M1 for Latin, end to end, on desktop and Android:
  M2 (Draw), M3 (Learn), M4 (Workbook) and M5 (Scripts) ship partial and labelled Early or
  Preview (§3 above), not complete; M6 Derive is entirely out of V1 (§4 above).
- **GitHub push.** Brief §12 says "pipeline and repo layout unchanged" from
  `docs/TYPEWRIGHT_HANDOFF.md` §M7, which names GitHub push as one of the Ship pipeline's two
  routes (manual upload, or GitHub push from the app). V1 says GitHub push is out of V1;
  P15 leaves it out and the UI says so in one line.
- **The hosted web compile.** Brief §3 says the v1 compile backend for web is "a hosted build
  endpoint; the UI says so," and brief §13's M-web milestone names the same hosted build
  endpoint. V1 plans on D4's recommendation, which Madhav still owes: web V1 has no compile at all — not even a hosted one. It exports
  UFO plus the Ship folder and the user builds on desktop; the hosted endpoint is deferred to
  a later paid product.
- **The 8 trace stages.** Brief §7 calls the trace chain "the eight stages," then separately
  proposes a three-stage default view and flags the count itself `[CONFIRM: the explorer
  shows all eight]` (repeated at brief §15 item 4). V1 plans on D8's recommendation (owed): 3 stages — Clean · Fit ·
  Review — by default, matching the SCREENS_V1 mockups, with the other five behind "adjust."
- **The bundled UI face.** Brief §6 and §15 item 6 propose bundling Inter (OFL) for
  cross-platform identity. V1 plans on D8's recommendation (owed; `docs/SCREENS_V1.md` header): IBM Plex Sans and IBM
  Plex Mono, bundled, instead — one family for sentences and mono labels, which also fixes
  the web build's missing monospace.
- **The margin and proof axis.** Brief §4.3 describes every room as having a margin above it
  (the workbook task) and a proof below it on the same sheet, pulled up or down, as a
  `[CONFIRM]` proposal the explorer doesn't yet show. V1 plans on D8's recommendation (owed): this is left for v1.1, not
  built for V1.
- **Web as an equal target.** Brief §3 lists "v1 targets: Android, Linux (JVM desktop), Web
  (Kotlin/Wasm)" without distinguishing them. V1 says Web ships as a labelled preview only,
  carrying no compile step and a degraded Ship (§5 above), while desktop and Android carry
  the full golden path.
- **Windows, macOS and iOS.** Brief §3 places Windows and macOS as "Later (the same JVM
  desktop target; no new UI work)" and iOS/iPadOS as "Later... see the compile note" —
  language that leaves open how soon "later" is relative to V1. V1 says all three are out of
  V1 outright (§4 above).
- **Chaquopy's licence and brief §15 item 7.** Already corrected in place in
  `TYPEWRIGHT_BUILD_BRIEF.md` §3 and closed at §15 item 7 (P10, this prompt): Chaquopy is MIT
  since 12.0.1, not "since v14" as the brief said before this correction.
- **Nastaliq and other scripts.** The brief itself names no script coverage claim in §1–4 or
  §12–16 beyond "the scripts the incumbents ignore (Devanagari and the other Indic scripts,
  South East Asian scripts, Arabic, kana)" (brief §1). V1 says only Latin ships end to end;
  Kana, Devanagari and Naskh are Preview-labelled in Draw/Space only (§3 above); the other
  Indic scripts, South East Asian scripts and the rest of Arabic are out of V1 entirely.
  (Nastaliq itself is excluded even from the multi-script *plan* — `docs/TYPEWRIGHT_HANDOFF.md`
  §4 M8 already says so; the brief does not repeat or contradict that.)
