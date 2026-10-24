# Ship: the repository folder, the build that makes it, and what blocks it

Ship (S16 and S17 in `docs/SCREENS_V1.md`) turns the open project into a folder a person can push to their own repository and, if they
want it there, hand to Google Fonts: the UFO exactly as the project holds it, the compiled font, and the files Google Fonts' onboarding asks
for. It produces a folder and a piece of text to paste. It does not push to GitHub, which is not in V1 (the page says so in one line).

Where the code is: the folder and the rules are pure (`qa/ship`, Apache-2.0, no I/O), the build is `ui/ship/ShipController.kt` (no Compose),
the screens are `ShipScreen.kt` and `ShipPages.kt`, and writing a whole tree at once is `project/TreeWriter.kt` (plus
`FolderTreeWriter.kt` for a computer's folder). `ShipScreen(texture)` is the one entry point the router calls.

## The block rule

Ship is **blocked while Check has any fail**. Check's fails are the fourteen layer-two checks' fails on the open project, and, once the
font has been compiled, Google Fonts' own profile's fails on the compiled binary (layer one). `shipGate` is the whole rule, and a warn, a
note or a check that could not measure never blocks. A layer one that did not run (a phone, a browser, no Python) contributes nothing,
and the page says that it did not run instead of calling it a pass.

- The primary circle on Ready is dimmed to 35% while blocked. Tapping it says why in a line ("Ship is blocked while Check has fails:
  <titles>. Fix them in Check."), and the ink block under the checklist says "Build is blocked while Check has fails. N to go." with each
  fail as one line and a way to Check.
- A blocked ship never starts. Nothing is compiled, nothing is written, and the target folder is not touched (not even a working folder
  beside it). The golden path holds that: step 6 gives Hyle Deco, which Check fails, to the controller and looks at the target afterwards.
- A fail found on the compiled binary stops the ship after the compile and before any file is written ("Stopped": the fails, one line each).

## The folder (Google Fonts mode)

Every file is a function of the project's own facts. A fact the project does not state is never replaced by a guess or a placeholder: the
line of the file that would carry it is left out, or the whole file is, and the page shows an **unmet checklist line** with a link to the
Font info field that fixes it. Nothing is written for a missing fact.

| Path | What it is made from |
|---|---|
| `OFL.txt` | The project's copyright line, then the Open Font License text exactly as Fontbakery holds it (`OflBody.kt`; its check compares the body). Left out, and an unmet line, when there is no copyright line or the licence is not the OFL. |
| `AUTHORS.txt` | The designer, when the project names one. |
| `DESCRIPTION.en_us.html` | A draft written from the project's facts and its brief (genre, uses, scripts, what the font covers), over 200 bytes, with the repository address as a link when the copyright line has one. It is a draft to edit, and the checklist says so. |
| `METADATA.pb` | Name, style, weight, file name, PostScript name, full name, copyright, category, subsets (`menu`, `latin`, `latin-ext` by what the font maps), `date_added` (the day it was built), the repository when known. `designer` and `category` are left out when unknown. |
| `upstream.yaml` | `name`, `repository_url` when known, `branch: main` and the `files:` map that says where each file goes in `ofl/<family>/`. |
| `fonts/ttf/<Family>-<Style>.ttf` | The compiled font. |
| `sources/<Family>-<Style>.ufo/` | The project's UFO, byte for byte as the session wrote it (read from the same consistent snapshot the compiler reads). |
| `documentation/GOOGLE_FONTS_REQUEST.md` | The text to paste into a new issue on the Google Fonts repository: the family, the facts above, what the build found (the tally of the check on the binary) and what is still unmet. |

On a phone and in a browser there is no compiler, so the font, `METADATA.pb` and `upstream.yaml` (which describe the font file) are
left out, each with its reason, and the page says why in one line.

**Own licence mode** (the second toggle) drops everything that is only for Google Fonts. It writes the font, the sources and the licence
file the project's licence calls for: `OFL.txt` for the OFL (with the copyright line first), otherwise `LICENSE.txt` made from the licence
text the project states. The checklist carries a choice (OFL, Proprietary, Other) whose result is the Licence value in Font info.

## The build (S17)

Four steps, each ○ waiting, ◐ running, ✓ done or ● failed, with the time each took, the elapsed time, and the compiler's output on request
("Show fontmake output"):

1. **Write the UFO from the project.** One consistent snapshot of the project's files (an autosave cannot race the build).
2. **fontmake · UFO to TTF.** The platform compiler. The build copy is a temporary directory; the project is never written to.
3. **Check the compiled binary.** Google Fonts' profile, run by Fontbakery or Fontspector, on the compiled bytes and under the name the
   folder will give the font (`<Family>-<Style>.ttf`), since the profile judges a file by its name and an adopted UFO can be called
   anything. A fail stops the ship (the block rule above).
4. **Write the repository folder.** The whole tree at once.

Errors are one plain sentence ("Ship stopped while compiling the project with fontmake") with the compiler's output behind a link. **Close
cancels** the build: the compiler and everything it started are stopped, its temporary directory is removed, and the page says "Cancelled.
Your project and the folder are as they were."

**Nothing is written until everything before it has passed**, and the write is all or nothing:

- On a computer (`FolderTreeWriter`) the tree is first written to a hidden sibling of the target, on the same filesystem. If the target does
  not exist the sibling is renamed to it in one step. If it does (the person chose their repository), each file is moved over its old one
  after the old one is set aside, the UFO folder is replaced as a whole (so a glyph deleted since the last write does not linger), and any
  failure puts every set-aside file back and removes what was created. Files in the target that the tree does not name (`.git`, a
  README) are never touched. Cancelling before the move removes the sibling and leaves the target as it was; the move itself is not
  cancelled half-way.
- Over a `ProjectStore` (Android's document tree) each file is written atomically and a failure or a cancel restores the previous bytes of
  every file already written (`StoreTreeWriter`). The stale files of the replaced UFO are deleted last.
- In a browser the tree becomes a zip (`ZipTreeWriter` over `ProjectZip`), and the browser downloads it.

## Google Fonts fix-ups at compile

The first run of Google Fonts' profile on a plain fontmake build of Hyle Deco found four binary-level fails that are ours to fix, not the
owner's: `fstype` (ufo2ft writes `fsType` 4 unless the UFO says otherwise), `gasp` (no table), `os2/use_typo_metrics` (bit 7 of
`fsSelection` not set) and `smart_dropout` (no `prep` program). The desktop backend (`compile/GoogleFontsFixes.kt`, on by default,
`CompileOptions.googleFontsFixes`) opens the compiled font with fontTools and sets `fsType` to 0 (installable embedding), sets
`USE_TYPO_METRICS`, and, for a font that is not hinted, adds the `gasp` table (grid-fit and smooth at every size) and the standard
unhinted `prep` (`SCANCTRL`, `SCANTYPE`). Each change is one INFO line in the build log ("Google Fonts fix-up: …") so the compile page
and the "Show fontmake output" link say what was done; the project's UFO is never touched. Builds are unhinted unless `ttfautohint` is on
the `PATH`, and the log says "not hinted".

## Platforms

| Platform | The right circle | Where the folder goes |
|---|---|---|
| Desktop | **↑ Build** | A folder, chosen with the platform's folder chooser (a Swing chooser, owner-verified only). The last choice is remembered for the session. |
| Android | **↑ Build** | A folder under Storage Access Framework (`AndroidShipSupport.chooseFolder`), written through `StoreTreeWriter`. Compile-verified only: the chooser and the write are owner-verified on a device. The compile step says honestly that building the font on a phone is not available in this version, and the folder is written without it. |
| Web | **↓ Download** | A zip the browser downloads (`hyle-deco-gf.zip` for Hyle Deco), with one line saying why ("Builds need the desktop app for now…"). The font is not in it. Browser behaviour is owner-verified only. |

## Acceptance against Google Fonts' own profile

`:ui:pythonIntegrationTest` (`ShipPythonIntegrationTest`) ships the real Hyle Deco (opened through the importer) with the real fontmake
and Fontbakery 0.12.10, then runs `fontbakery check-googlefonts` on the folder laid out as `ofl/hyledeco/` from `upstream.yaml`'s own
map. The only FAIL left is `metadata/empty_designer`: Hyle Deco's font info names no designer, which is a fact only its owner has, so
`METADATA.pb` carries no `designer` line (a placeholder would be a lie) and the checklist shows "Designer is not set" with a link to Font
info. The warnings are listed in the test output and are not blockers (`gpos_kerning_info`, `contour_count`, `outline_jaggy_segments`,
`outline_semi_vertical`, `unreachable_glyphs`, `vendor_id` ("????", an unmet checklist line too), `article/images`, and others).

Hyle Deco is a traced font and Check fails it, so in the app Ship is blocked for it. The test therefore gives the controller its real
layer-two report with the fails flipped to passes, a fixture that the app never does; everything after that is real.

The golden path's step 6 ships a clean seed (`CleanSeed`, "Seed Boxes", a made-up box font that covers Google Fonts' Latin core and states
every fact) and finds **no FAIL at all** in the same Fontbakery run on the assembled family directory.

## Screenshots

`docs/screenshots/ship/` holds the pages at phone width (390 dp, density 2), drawn from the open sample project by
`ShipScreenshotTest`: Ready, Blocked (the real Hyle Deco, four fails, each named), Building mid-way, Failed, Stopped (a fail on the
compiled binary), Done, Own licence and the web's zip. Ready, Done and Own licence show a project Check has nothing against, which for
Hyle Deco means its real report with the fails flipped to passes (a fixture; the app never does it), so their "Check has no fails" line is
the fixture's. The building and result pages use stand-in compiler output where a real build would print its own. They are for a person to put
beside the boards; no test asserts that they look right.

## What only a device or an owner can show

- The Swing folder chooser, the Android document-tree chooser and write, and the browser download are owner-verified; this environment
  has no phone and no display. Their code is compiled, and the pure parts (the writers, the rules, the pages' text) are tested.
- Whether Google Fonts accepts `upstream.yaml` as written (`branch: main` is an assumption for a repository the person has yet to make)
  and a `date_added` that is the build date.
- Fontspector's path is unverified here (no binary); Fontbakery is what ran for real.
