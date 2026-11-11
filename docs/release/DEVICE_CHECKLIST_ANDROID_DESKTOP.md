# Device checklist: Android in desktop mode

Status: **nothing here has been run.** This container has no phone, tablet, DeX, Chromebook or emulator (CLAUDE.md law 4). Every row is an unfilled NEEDS-DEVICE-VALIDATION (NDV) box for the owner to fill on a real device.

What exists to test: the manifest hints only, in `app-android/src/main/AndroidManifest.xml`: `touchscreen` is not required, the activity is resizeable, and its `<layout>` asks for a 900x700 dp default and a 360x480 dp minimum. No desktop-specific input code exists; the Ctrl K palette and the arrow-key room navigation come from the shared UI and are unproven on a hardware keyboard.

How to fill a row: `[x]` only for a pass you watched, `[F]` plus a line of what you saw for a fail.

| # | Check | Evidence class | Result |
|---|---|---|---|
| 1 | Samsung DeX or desktop mode: the app opens in a freeform window at about 900x700 dp | NEEDS-DEVICE-VALIDATION | [ ] |
| 2 | The window cannot be resized below about 360x480 dp | NEEDS-DEVICE-VALIDATION | [ ] |
| 3 | Resizing the window keeps the open project (the workspace lives in `TypewrightApplication`, per process; activity recreation on resize is untested) | NEEDS-DEVICE-VALIDATION | [ ] |
| 4 | Resizing does not lose an unsaved edit | NEEDS-DEVICE-VALIDATION | [ ] |
| 5 | Hardware keyboard: Ctrl K opens the command palette | NEEDS-DEVICE-VALIDATION | [ ] |
| 6 | Hardware keyboard: left and right arrows move between rooms | NEEDS-DEVICE-VALIDATION | [ ] |
| 7 | Mouse: click selects, the wheel scrolls where scrolling exists | NEEDS-DEVICE-VALIDATION | [ ] |
| 8 | Launching on a device with no touchscreen (Chromebook or a desktop-mode display without touch) is not blocked by Play/installer filtering | NEEDS-DEVICE-VALIDATION | [ ] |
| 9 | Freeform window on a Chromebook (ChromeOS): opens, resizes, and survives minimise and restore | NEEDS-DEVICE-VALIDATION | [ ] |
| 10 | Not covered: hover, right-click menus, modifier-drag and per-event pointer-type detection. They need the Draw room, which is not built. | NOT-APPLICABLE (feature not built) | n/a |

## Run log

| Date | Device and Android version | Mode (DeX, ChromeOS, other) | Build commit | Rows filled | Notes |
|---|---|---|---|---|---|
| | | | | | |
