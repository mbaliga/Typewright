# Web canvas frame times

**No physical phone was used for this measurement.** CLAUDE.md law 4 stands: this container has
no device and no emulator. What follows instead is the real production Wasm build
(`:app-web:wasmJsBrowserDistribution`), served locally and loaded in this sandbox's own
pre-installed headless Chromium (`/opt/pw-browsers/chromium-1194/chrome-linux/chrome`, driven
through the global `playwright` package -- the same resolution `tools/explorer-shots.mjs`
already uses), with a real, disclosed approximation of a mid-range phone's CPU applied through
Chrome DevTools Protocol's own `Emulation.setCPUThrottlingRate`. This is a real measurement of a
real render under a real, stated approximation of mobile CPU constraints -- genuinely not a
phone, but not nothing either. Everything below is reproducible with the commands in
"Reproducing this".

## Build and load, confirmed

`./gradlew :app-web:wasmJsBrowserDistribution --rerun-tasks` -- **BUILD SUCCESSFUL** (113
actionable tasks, all executed fresh; webpack's own "asset size limit" warnings are expected and
pre-existing, not errors). Served from
`app-web/build/dist/wasmJs/productionExecutable/` via `python3 -m http.server`.

Loaded in a real `Moto G4` device profile (Playwright's bundled descriptor for Motorola's
mid-range line -- 360x640 CSS px, device-pixel ratio 3, `hasTouch: true`; used only for
viewport/DPR/UA emulation, since a viewport preset does not by itself slow down anything -- CPU
speed is emulated separately below). The app rendered correctly: the Draw sheet's header
("DRAW -- pts", "MAP"), the BACKGROUND/SKETCH layer rows, the glyph bounding box with its italic
guideline and baseline/overshoot labels, the "DRAW" watermark, the puck (default-pinned to the
right edge, "SELECT" tool, cursor glyph), and the "Workbook"/"Learn" corner buttons all appeared
exactly as the orchestrator's own pre-P9 baseline screenshot described. No regression from this
task's own upstream changes (the browser-shaping preview and hosted-endpoint client work) was
visible. Two independent fresh loads in this task produced **zero console errors, zero failed
requests, and zero HTTP responses >= 400** -- the transient 404 the orchestrator saw once was not
reproduced either time here, consistent with their own read of it as a one-off (most likely the
browser's automatic `/favicon.ico` probe: `index.html` declares no `<link rel="icon">`, so a
browser that happens to request one gets a real 404 from the static file server; this is cosmetic
and not a priority per this task's own brief, so no source change was made for it -- logged below
instead).

## CPU throttling: rate chosen and verified for real

Chrome DevTools' own CPU-throttling panel and Lighthouse's default mobile simulation both use a
**4x slowdown multiplier** as their standard, documented approximation of a mid-tier mobile CPU
against a development-class machine; 6x is the commonly-cited upper end of that range (closer to
a low-end device, or a mid-tier device under thermal throttling). This measurement uses **4x as
the primary rate** and adds **6x as a second data point**, so the report is a real comparison
across a range rather than one throttled number with no context.

Before trusting this technique on the real app, it was verified against a real CPU-bound
benchmark in this exact browser instance (a 200,000,000-iteration `Math.sqrt` loop,
`performance.now()`-timed, via a real CDP session):

| rate requested | wall time | ratio vs. 1x |
|---|---|---|
| 1x | 381.3 ms | 1.00x |
| 4x | 1534.1 ms | 4.02x |
| 6x | 2263.4 ms | 5.94x |

The measured ratios land within 1% of the requested rate, confirming CDP throttling is genuinely
slowing real JS execution in this real Chromium process, not a no-op.

**Known limitation of the technique, disclosed rather than glossed over:** `Emulation.
setCPUThrottlingRate` throttles the renderer's main thread (JS, layout, style, paint scheduling).
It does not equally slow the GPU/compositor thread, memory bandwidth, or thermal behaviour --
real differences between a datacentre CPU and a mid-range phone's SoC that this technique cannot
reproduce. It is nonetheless the same technique Chrome DevTools' own Performance panel and
Lighthouse use for this exact purpose, which is why it was chosen here.

## Gesture driven, and a real targeting limitation disclosed

The plan was to drive the puck's grip-drag (repositioning the puck via its small top-of-disc
handle, `PuckGestureMachine.GripDragging`). In practice, from this headless session, no candidate
press point tested -- including points computed directly from `PuckGeometry.gripVisualCenter`'s
own formula and cross-checked against the grip bar's actual on-screen pixel position in a
baseline screenshot -- reliably engaged `GripDragging`; every attempt instead engaged `Swiping`
(UI_SPEC §3's "drag on the puck body cycles tools every 34 dp of travel"), confirmed visually
across the mid-gesture screenshots below (the puck's tool label visibly changes: SELECT -> PEN ->
BOOLEAN -> MEASURE -> PRIMITIVES as the gesture's vertical travel crosses each 34 dp step). This
is logged as a real limitation of coordinate-targeting a canvas-only UI from a headless session,
not a claimed bug in `PuckGestureMachine` -- the coordinate math checks out on paper and this task
could not fully account for the discrepancy in the time available (see
`docs/OPEN_QUESTIONS.md`).

Swiping is still a real, single-pointer drag gesture recognised by the same
`PuckGestureMachine`, driven here with Playwright's real `page.mouse` API (down on the puck disc,
a continuous two-loop circular path over 4 real seconds, up), and it does exercise real,
continuous canvas repaints (the tool icon and label genuinely redraw as each step is crossed) --
not an idle static frame. Frame timestamps were collected via a small script injected into the
real page that pushes `performance.now()` on every real `requestAnimationFrame` callback for the
duration of the gesture; deltas, median and p95 are computed from the real timestamps read back
afterward.

Screenshots (paths under this session's scratchpad, also attached to the chat):
- Baseline load, unthrottled: puck at "SELECT".
- Mid-gesture, unthrottled (1x): puck at "PEN".
- Mid-gesture, 6x throttled: puck at "BOOLEAN".

## Frame times

Puck-drag gesture, 4 real seconds, at three CPU rates. `fps (median)` = `1000 / median`.

| rate | frames | median | p95 | mean | min | max | fps (median) |
|---|---|---|---|---|---|---|---|
| 1x (unthrottled) | 510 | 16.8 ms | 17.6 ms | 16.9 ms | 2.9 ms | 41.1 ms | 59.5 |
| 4x (CDP) | 510 | 16.4 ms | 20.6 ms | 17.1 ms | 3.6 ms | 91.6 ms | 61.0 |
| 6x (CDP) | 520 | 16.6 ms | 22.7 ms | 17.3 ms | 3.1 ms | 90.5 ms | 60.2 |

(A second, independent full run earlier in this task -- different exact drag path, also
Swiping -- produced the same qualitative shape: median pinned at ~16.7-17.0 ms across all three
rates, p95 climbing from 17.6 ms (1x) to 20.7 ms (4x) to 22.5-23.1 ms (6x). The pattern is
reproducible, not a one-off.)

## Reading these numbers honestly

The **median** frame time stays pinned at vsync (~16.7 ms, 60 fps) at every throttle level tested,
including 6x. This specific gesture on this specific screen (Draw room, vector line/rect
redraws, a small icon swap) is not CPU-heavy enough on the *typical* frame for a 4-6x main-thread
slowdown to push the median below 60 fps in this measurement. Read this as "Typewright's Draw
sheet is cheap enough, per frame, that this workload's typical frame keeps up even under a real
4-6x main-thread slowdown" -- not as "Typewright is fast on a real mid-range phone", since GPU/
compositor cost (the part this technique does not slow down) is doing real work here too and a
real device's GPU is also slower than a datacentre GPU, in a way this measurement cannot speak to.

The **tail** does degrade, measurably and consistently across three runs: p95 grows from ~17.6 ms
at 1x to ~20.6-22.7 ms at 4-6x (roughly +20% to +30%), and the worst observed single frame grows
from ~41 ms to ~90 ms. That is real, main-thread-bound cost becoming visible only once the CPU is
constrained -- the kind of occasional stutter (a missed vsync every so often, not a sustained
frame-rate drop) a real mid-range phone would plausibly show on this same interaction, even though
the median frame budget holds.

## Reproducing this

```
./gradlew :app-web:wasmJsBrowserDistribution --rerun-tasks
cd app-web/build/dist/wasmJs/productionExecutable && python3 -m http.server 8791
```

Then, with the global `playwright` package and this sandbox's own Chromium
(`executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome'`), load
`http://localhost:8791/index.html`, start a `requestAnimationFrame`-pushing collector via
`page.evaluate`, drive `page.mouse` down/move-loop/up against the puck, read the collected
timestamps back, and repeat after a `context.newCDPSession(page).send('Emulation.
setCPUThrottlingRate', { rate })` call. The exact script used for this task's own measurement is
`frame_times.mjs`, alongside its screenshots, in this session's scratchpad (path given in the
final report; not committed to the repository, since it is a one-off measurement script rather
than an app source file).

