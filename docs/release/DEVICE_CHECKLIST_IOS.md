# Device checklist: iPad

Status: **nothing here has been run.** The build container had no Mac, no Xcode, no simulator and no iPad (CLAUDE.md law 4). Every row is an unfilled box. The owner fills it on the iPad Pro M4 (or a simulator, and says which), writes what happened, and only then does a row stop being NDV. Evidence labels are `docs/PORTING_PLAN.md` section 0's.

Build under test: the `iOS` workflow's unsigned simulator app, or `app-ios/iosApp` generated with `xcodegen generate` and run from Xcode with your own team. Installing on a real iPad needs your signing; the repository ships none.

How to fill a row: `[x]` only for a pass you watched, `[F]` plus a line for a fail. Record date, device, iPadOS version and build commit at the end.

## 1. Build and launch

| # | Check | Evidence class | Result |
|---|---|---|---|
| 1.1 | The `iOS` workflow's `cores` job passes (pure-module tests on a simulator) | CI-LANE-WRITTEN | [ ] |
| 1.2 | The `app` job builds the simulator slice and the device slice | CI-LANE-WRITTEN | [ ] |
| 1.3 | The app launches to the existing phone frame without a crash | NEEDS-DEVICE-VALIDATION | [ ] |
| 1.4 | Rotation and iPad multitasking sizes do not break the layout (the frame was designed for a phone; say what looks wrong) | NEEDS-DEVICE-VALIDATION | [ ] |
| 1.5 | The Learn tab shows real face fonts, not the default font (bundle resources and the Skiko `Font` call) | NEEDS-DEVICE-VALIDATION | [ ] |
| 1.6 | Learn scenes, the campaign workbook and the corpus-backed checks load (no "resource not found" text) | NEEDS-DEVICE-VALIDATION | [ ] |

## 2. Projects in the sandbox

| # | Check | Evidence class | Result |
|---|---|---|---|
| 2.1 | New project creates `Typewright Projects/<name>` and it shows in Files under "On My iPad" | NEEDS-DEVICE-VALIDATION | [ ] |
| 2.2 | An edit is on disk after the autosave delay | NEEDS-DEVICE-VALIDATION | [ ] |
| 2.3 | Swipe the app away during autosave; reopen: the project opens and is wholly old or wholly new | NEEDS-DEVICE-VALIDATION | [ ] |
| 2.4 | Send the app to the background: the pending edit is flushed (`flushWorkspace`) | NEEDS-DEVICE-VALIDATION | [ ] |
| 2.5 | The last project reopens on relaunch (recents under Application Support) | NEEDS-DEVICE-VALIDATION | [ ] |
| 2.6 | Update or reinstall the app over itself: the saved path still resolves (`SandboxPaths` re-roots a changed container) | NEEDS-DEVICE-VALIDATION | [ ] |
| 2.7 | An approved glyph is still locked after the reopen (law 1) | NEEDS-DEVICE-VALIDATION | [ ] |
| 2.8 | The folder, copied out through Files, opens in FontForge (law 7) | NEEDS-OWNER-VALIDATION | [ ] |
| 2.9 | The project folder is, or is not, in iCloud Backup: write down which (plan section 8, undecided) | NEEDS-DEVICE-VALIDATION | [ ] |

Note: the desktop build still uses its in-memory workspace for parts of the project model, so a row 2 failure may be a known gap shared with the other platforms. Say which.

## 3. Honest stubs

| # | Check | Evidence class | Result |
|---|---|---|---|
| 3.1 | Anything that would compile a font says it cannot on this device and why; nothing pretends to build | NEEDS-DEVICE-VALIDATION | [ ] |
| 3.2 | The layer-one (Fontspector) panel says it is unavailable on iOS | NEEDS-DEVICE-VALIDATION | [ ] |
| 3.3 | The shaping preview says it is not built on iOS | NEEDS-DEVICE-VALIDATION | [ ] |

## 4. Input

| # | Check | Evidence class | Result |
|---|---|---|---|
| 4.1 | Finger drawing and the puck work | NEEDS-DEVICE-VALIDATION | [ ] |
| 4.2 | Apple Pencil draws (pressure and tilt are not wired; hover is not designed) | NEEDS-DEVICE-VALIDATION | [ ] |
| 4.3 | Hardware keyboard shortcuts and the command palette open | NEEDS-DEVICE-VALIDATION | [ ] |
| 4.4 | No haptic is expected on an iPad (it has no motor); nothing errors where one was planned | NEEDS-DEVICE-VALIDATION | [ ] |

## 5. Privacy

| # | Check | Evidence class | Result |
|---|---|---|---|
| 5.1 | No network request leaves the app during normal use (Settings, App Privacy Report, or a proxy) | NEEDS-DEVICE-VALIDATION | [ ] |
| 5.2 | The README and the app's about text name Apple's TestFlight and App Store crash reports before any TestFlight build ships | NEEDS-OWNER-VALIDATION | [ ] |

## Record

| Date | Device | iPadOS | Build commit | Notes |
|---|---|---|---|---|
|  |  |  |  |  |
