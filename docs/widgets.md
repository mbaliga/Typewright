# Home-screen widgets (Android)

Six standard Android App Widgets that any launcher can host: Pixel Launcher, One UI, Nova, the
owner's Orrery Home. Their look, sizes, states and copy are specified in
`docs/SCREENS_V1.md` §9, which was written first and is the source for law 6; this page is the
implementation map and the owner's checklist. Android only (minSdk 31), in `app-android`.

Nothing here has run on a phone. The container has no device or emulator, so every line about
rendering, resizing, taps, TalkBack and redraw timing below is what the code is written to do,
not something seen (CLAUDE.md law 4). The owner-verify list at the end is what still needs a
phone.

## The six widgets

| | Widget | Template | Small | Medium adds | Large adds | Tap |
|---|---|---|---|---|---|---|
| W1 | Approved | `progress` | `52 / 338` APPROVED and a bar | title "Your font", ◐ Unlocked for editing row (when any), as-of line | ✓ Approved · ◐ Unlocked for editing · ◌ Not yet approved rows | Typewright where you left it |
| W2 | Saved | `stat` | mark + word (✓ Saved, ◐ Saving, ● Save failed, ● Save conflict, ○ Read-only, ◌ Not confirmed) | title "Your font", then "saved 4 Nov, 14:02" or the as-of line, last | one sentence on what the state means, above that line | Typewright where you left it |
| W3 | Recent projects | `list` | label RECENT + the most recent row's title | three 48 dp rows ("338 glyphs" over "OPENED …", OPEN ink block on the open one) | five rows, "N more in Typewright" | a row opens that project; elsewhere Typewright |
| W4 | Workbook | `progress` | TASK 4 OF 12 + title | WORKBOOK · EARLY, "Task 4 · …", twelve-square strip (✓ ■ ◌), "3 of 12 done", as-of line | gate sentence, check counts one per line with marks, SCAFFOLD | the Workbook |
| W5 | Lineages | `card` | era name + date | LINEAGES · n OF 10, caption (2 lines) | caption (5 lines), tags, "Made with …", SCAFFOLD | Learn |
| W6 | Shortcuts | `pads` | one pad, RESUME | RESUME · WORKBOOK · LEARN | a line under each pad: YOUR FONT or TYPEWRIGHT, current task, today's era | each pad its place |

Sizes are the space Android 12+ really gives a widget, not the legacy `70n − 30` minimums: on a
typical portrait phone a 2×1 is about 130 × 102 dp, a 4×1 about 276 × 102, a 4×2 about
276 × 220, a 4×3 about 276 × 337 (Android's sizing table; landscape rows are about 51, 117 and
183 dp). Each size starts where its layout fits at font scale 1, with every optional part shown
(`WidgetSizes.contentHeight` adds the layout's parts up; a test checks every start against it):

| Widget | Medium from | Large from | So, in portrait |
|---|---|---|---|
| W1 Approved | 180 × 135 dp | 180 × 250 dp | 2×1, 2×2, 4×1 small · 3×2, 4×2 medium · 4×3 up large |
| W2 Saved | 180 × 110 dp | 180 × 250 dp | the same |
| W3 Recent projects | 180 × 185 dp | 180 × 305 dp | the same |
| W4 Workbook | 180 × 150 dp | 180 × 310 dp | the same |
| W5 Lineages | 180 × 110 dp | 180 × 275 dp | the same |
| W6 Shortcuts | 270 × 48 dp | 270 × 110 dp | 2×1, 3×1 one pad · 4×1 three pads · 4×2 up three pads with lines |

The provider hands the launcher all three as a size-to-layout map
(`RemoteViews(Map<SizeF, RemoteViews>)`), so resizing never wakes the app. Defaults: W1 and W2
2×1, W3–W5 4×2, W6 4×1; every one resizes down to 2×1 and up freely. At a larger font scale a
layout can still be taller than its frame; W1, W2 and W4 keep their content in a region that
takes what the as-of line leaves and clips at its bottom, so the as-of line is the last thing to
go.

## Where the data comes from

| Widget | Source | Class |
|---|---|---|
| W1, W2, W4, W6 (first and second pad lines) | the open project, as last published | `WidgetPublisher` → `WidgetSnapshotStore` (`noBackupFilesDir/widgets/open-project.properties`) |
| W1 | glyphs in every master; `locked` locks = approved, `unlocked` locks = unlocked for editing | `ProjectWidgetSnapshots.openProject` over `FontState.locks` |
| W2 | `ProjectSession.saveStatus` | `ProjectWidgetSnapshots.saveStateOf`, `savedView` |
| W4 | the twelve tasks (`workbook-latin.yaml`), each gate run on the default master, the project's own style class when the corpus has it (else `sans-geometric`), confirmed tasks from `typewright.json` | `WorkbookSummaries` over `:campaign`'s `runAllWorkbookGates` and `campaignProgress` |
| W3 | the app's `recent-projects.json`, read fresh on every redraw; OPEN from the running process's open project | `ProjectWorkspace.loadRecentProjects()` (added for this) |
| W5, W6 (third pad line) | the ten Lineages era scenes, one per local day, in play order | `LineagesResources.loadEraScenes()`, `lineageFor` |

The publisher waits until the open project has been still for one second, then checks which
widgets are placed (`publishPlan`). With none that reads the snapshot (W1, W2, W4, W6) it writes
nothing and removes the old snapshot, so an editing session pays nothing for widgets nobody
placed; the Workbook gates run only while a W4 or W6 is placed, and otherwise the snapshot says
the Workbook was not worked out. The first widget of a snapshot kind to be placed asks the
publisher to publish again (`onEnabled` → `WidgetPublisher.republish`). Closing the project
writes "no project open". A process that starts with nothing open (a widget redraw, a cold
start) writes nothing, so the last snapshot keeps its as-of time, which is printed with its
date so it stays true without a redraw. If a save was still pending when Typewright stopped, W2
says ◌ Not confirmed rather than ◐ Saving: the snapshot names the process that wrote it, and a
later process knows the writer is gone; the save check below is what makes a later process
redraw W2.

## Redraws

- **Pushed:** `TypewrightWidgets.refresh(context, kinds)` sends each provider an explicit
  `APPWIDGET_UPDATE` with its own ids. The publisher calls it after each snapshot (every widget
  but W5) and whenever the recent list changes (W3).
- **Midnight:** W5 and W6 (whose large layout names today's era) each set the same inexact,
  non-waking `AlarmManager.set(RTC, …)` for a minute after local midnight when they redraw; it
  redraws both, and is cancelled only when neither is placed (`WidgetAlarms`).
- **Save check:** when the publisher writes a snapshot whose save is still waiting, and a W2 or
  W3 is placed, it sets one more such alarm about two minutes ahead (and cancels it on the next
  publish that isn't waiting). It redraws W2 and W3 and, if this same process is still saving,
  looks again two minutes later. If Typewright was stopped meanwhile, the alarm starts a new
  process: W2 then shows ◌ Not confirmed and W3 drops its OPEN block.
- No exact-alarm permission, no `updatePeriodMillis` (it is 0 everywhere), no WorkManager or
  JobScheduler.
- **System:** every provider also redraws on `TIME_SET`, `TIMEZONE_CHANGED` and
  `LOCALE_CHANGED`, because it prints dates and its own words.
- Work runs off the main thread in `goAsync()`, on the application's coroutine scope, with an
  eight-second timeout; a failure draws the widget's own error state.

## Taps

Every tap is an immutable `PendingIntent` for `MainActivity` with an explicit component, the
action `com.asoc.typewright.action.OPEN_FROM_WIDGET`, a destination extra (HOME, WORKBOOK,
LEARN, PROJECT) and a request code unique to the widget id and slot. No URL scheme, no
broadcast that changes anything: no widget edits a project (law 1).

- **HOME** brings Typewright forward where you left it, overlays included; a fresh start
  shows the sheet, and the app reopens the last project itself, as on any launch.
- **WORKBOOK** and **LEARN** open those overlays (`TypewrightAppNavState`).
- **PROJECT** (a W3 row) carries an opaque key, the first 128 bits of a SHA-256 of the
  project's location, never the folder or URI. The app looks the key up in its own recent
  list, checks the folder is reachable, opens the tapped project, and only then closes the one
  it replaces (`ProjectWorkspace.open` leaves that to its caller), with Learn and the Workbook
  closed. Until it has, the activity shows the plain canvas instead of the app, so the app's
  own reopen-last can't race it. A folder that moved, a lost grant, a folder that is no longer
  a project or a damaged project is said in one line (a toast), and the open project stays
  open, undo history included. The replaced project's recent-list summary isn't refreshed on
  this path (it is when a project is closed from the palette).
- A relaunch from Overview carries `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` and the task's first
  intent; it is not taken as a tap, so an old widget tap never replays.

## Privacy

The widgets show counts, dates and times, and the Workbook's and Learn's bundled text. They
**withhold project names** (`WidgetPrivacy.projectNames = WITHHELD`): a project's name is the
family name its maker typed and also its folder's name (`ProjectWorkspace.create` makes
`<name>/`; the palette's new project is `Untitled/`), and a home screen is public. The open
project is "Your font"; a recent project is its glyph count over the time it was opened. If the
owner allows names (`docs/OPEN_QUESTIONS.md` item 131), that one value changes; the content
functions take the setting and are tested both ways. The snapshot file holds the name (for that
switch) and otherwise only counts and times (a test checks it carries no glyph name, master path
or save-failure text); it lives under `noBackupFilesDir`, and the app also has
`allowBackup="false"`. W3 rows never carry a folder (a test checks that too), and withheld rows
carry no name. No network, no
new permission, no telemetry, no new dependency: `app-android` gained a project dependency on
`:campaign` (which brings `:qa`, `:qa:corpus` and `:learn:scenes`, already in the APK through
`:ui`).

## Colour and shape

Paper by day and Black by night (`values/` and `values-night/widget_colors.xml`), following the
phone's setting. No violet, cyan, red or green anywhere: nothing on a home screen is selected or
snapped to, and the one red belongs to the deck. Every state is one of the explorer's five
marks, traced into vector drawables (`widget_mark_pass/warn/fail/info/todo`), or the ink block
(`widget_mark_block`), always next to its word. All text is fg: Paper's muted token is 4.45:1 on
its canvas, under UI_SPEC §6's 4.5:1 floor (`docs/OPEN_QUESTIONS.md` item 133). Inside the frame
everything is square; the outer corner is the launcher's own.

## Files

- `app-android/src/main/kotlin/com/asoc/typewright/app/android/widget/`:
  `WidgetCatalogue.kt` (kinds, sizes, marks), `WidgetContent.kt` (what each widget shows per
  state and size), `ProjectWidgetSnapshot.kt`, `WorkbookSummary.kt`, `WidgetLaunch.kt` (tap
  extras and the recent key) — all plain Kotlin, unit-tested — and `WidgetPublisher.kt`,
  `WidgetSnapshotStore.kt`, `WidgetRenderer.kt`, `WidgetProviders.kt` (with `WidgetAlarms`),
  `TypewrightWidgets.kt` (the Android side).
- `TypewrightApplication.kt` (scope, publisher, opening a tapped project) and `MainActivity.kt`
  (landing a tap).
- `res/layout/widget_*.xml` (six widgets, six picker previews, one loading frame),
  `res/xml/widget_info_*.xml`, `res/drawable/widget_*.xml`, `res/values*/widget_*.xml`, and the
  six receivers in `AndroidManifest.xml`.
- Tests: `app-android/src/test/kotlin/com/asoc/typewright/app/android/widget/` (51 tests) and two
  new cases in `project/src/commonTest/.../ProjectWorkspaceTest.kt`.

## Known gaps

- **W4 and the in-app Workbook can disagree until P12.** W4 runs the gates on the open project
  ("your font" means the open project). The Workbook screen still runs them on the Hyle Deco
  sample until P12 replaces it, so a tap on W4 can open a Workbook showing different progress
  (`docs/OPEN_QUESTIONS.md` item 132).
- **Android has almost no touch path to open a project yet** (S01 is P11's UI half; the palette
  needs a hardware keyboard). Until then most phones will show "No project open", and W3's rows
  are the only touch way to reopen a recent project. The widgets' own words say only what is
  true today ("Typewright reopens your last project when it starts"), never "open one".
- **W3's OPEN block can outlive Typewright.** It is drawn from the running process at each
  redraw. After Typewright is swiped away with nothing waiting to save, nothing redraws W3, so
  OPEN stays until the next redraw (Typewright reopens that project when it next starts). With a
  save waiting, the save check clears it.
- **W1's count at 2×1 on a narrow grid.** `52 / 338` in mono 22 sp is about 106 dp; a 2×1 on a
  five-column portrait grid gives about 102 dp inside the padding, so a count of eight
  characters or more can end in an ellipsis there. Wider grids and 3×1 have room. Not fixed: the
  spec sets the size, and the owner should see it first.
- **At font scale above 1** a layout can be taller than the size it was chosen for; its content
  is cut at the bottom of its region (W1, W2, W4 keep the as-of line), not reflowed.
- **No mockup boards** for W1–W6 (`ui/v1-screens/` is still absent, item 123); §9 is text only.
- The app has no launcher icon yet (lint's `MissingApplicationIcon`, before this change), so
  the picker's fallback `previewImage` is a plain vector, not the app's mark.

## Owner-verify (needs the phone)

1. Each widget appears in the launcher's picker with its name, description and preview, in
   Pixel Launcher, One UI, Nova and Orrery Home.
2. Each widget at 2×1, 2×2, 3×1, 4×1, 3×2, 4×2, 4×3 and 5×4 shows the layout the size table says,
   with nothing clipped at font scale 1.0, in English and an RTL language; at 1.3 and 2.0 the
   as-of line stays and only content is cut. Check the real sizes on each launcher: the
   breakpoints assume Android's sizing table, and a denser grid can give a 4×3 less than 305 dp.
3. W6 in a landscape one-row cell keeps 48 dp pads; WORKBOOK fits its pad at 4×1.
4. Paper by day, Black by night, switching with the system dark theme; no colour other than the
   canvas and fg.
5. W1/W2/W4 after opening a project, editing a glyph and waiting a second: the counts, save word
   and as-of time update; after closing the project: "No project open". No widget shows a
   project's name.
6. W2 after force-stopping (or swiping away) Typewright mid-save shows ◌ Not confirmed within a
   few minutes (allow for Doze); after a clean save, ✓ Saved with its time.
7. W3: rows in most-recently-opened order; tapping a row opens that project from cold and from
   warm; a project whose folder was deleted, or a folder that is no longer a project, gives the
   one-line message and leaves the open project open with its undo history.
8. W4 and W5 open the Workbook and Learn; W6's three pads each land where they say; RESUME and
   W1/W2 return to Learn or the Workbook if that is where you left the app. Relaunching
   Typewright from Overview after a reboot doesn't replay the widget tap that started it.
9. W5 and W6's era line turn to the next era after local midnight (allow for Doze: it may come
   when the phone next wakes), with only W6 placed too, and after changing the time zone or
   language.
10. Placing a W4 while a project is open shows its Workbook within a second or two; with no
    widgets placed, editing doesn't write `noBackupFilesDir/widgets/open-project.properties`.
11. TalkBack reads every widget's words, each strip square ("Task 4, current"), and each W3 row
    ("Open recent project. 338 glyphs. opened …").
12. OEM battery managers (One UI, RedMagic) don't stop the redraws or the two alarms after the
    app is swiped away; on Android 15+, a force-stopped app's widgets grey out until it is opened
    (system behaviour).
