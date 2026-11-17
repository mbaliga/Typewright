# Device checklist: Ubuntu Touch

Status: **nothing here has been run.** This container has no Ubuntu Touch phone (CLAUDE.md law 4). Every row is an unfilled box; the owner fills it on a device, writes what happened, and only then does a row stop being NDV. Evidence labels are PORTING_PLAN section 0's.

Builds under test: the `.click` files from the `Ubuntu Touch` workflow (`docs/UBUNTU_TOUCH_PORT.md`). The native click needs an Ubuntu Touch **24.04** image (OQ-37: the OnePlus 6 is read as 20.04-only). Record the image, device and build commit at the end.

How to fill a row: `[x]` only for a pass you watched; `[F]` plus a line of what you saw for a fail.

## 1. Native click (`typewright.asoc`)

Install with `clickable install --arch arm64` or `pkcon install-local --allow-untrusted <file>` over adb/SSH (unsigned build).

| # | Check | Evidence class | Result |
|---|---|---|---|
| 1.1 | The click installs (unconfined click: expect the system to say it is unconfined) | NEEDS-DEVICE-VALIDATION | [ ] |
| 1.2 | "Typewright" appears in the app scope with the placeholder icon | NEEDS-DEVICE-VALIDATION | [ ] |
| 1.3 | Tapping it opens a window (not a blank one). If blank: relaunch from a terminal with `TYPEWRIGHT_RENDER=software sh <click dir>/typewright-launch.sh` and record whether that fixes it | NEEDS-DEVICE-VALIDATION | [ ] |
| 1.4 | Read `~/.cache/typewright/launch.log`: no `WARN libawt_xawt.so has unresolved libraries` line. If there is one, paste it (it names the missing X11 library) | NEEDS-DEVICE-VALIDATION | [ ] |
| 1.5 | The window fills the screen, or you can make it. Record its size | NEEDS-DEVICE-VALIDATION | [ ] |
| 1.6 | UI scale is usable. Record `GRID_UNIT_PX` (`echo $GRID_UNIT_PX` in the app environment) and the scale in the log; if wrong, retry with `TYPEWRIGHT_UI_SCALE=` | NEEDS-DEVICE-VALIDATION | [ ] |

## 2. Touch and the control deck

| # | Check | Evidence class | Result |
|---|---|---|---|
| 2.1 | A tap activates a control | NEEDS-DEVICE-VALIDATION | [ ] |
| 2.2 | Dragging the puck moves it (single touch as pointer) | NEEDS-DEVICE-VALIDATION | [ ] |
| 2.3 | Press-and-hold behaves as on Android | NEEDS-DEVICE-VALIDATION | [ ] |
| 2.4 | Two-finger swipe between rooms: expected NOT to work through emulated mouse; record what happens | NEEDS-DEVICE-VALIDATION | [ ] |
| 2.5 | The on-screen keyboard appears for a text field (Maliit under XMir is a known weak spot); record whether it does | NEEDS-DEVICE-VALIDATION | [ ] |

## 3. Save, background, kill, reopen (golden-path step 7 analogue)

Lomiri freezes an unfocused app about 1.5 s after suspend. `app-desktop` flushes only on window close, so this is the likely failure.

| # | Check | Evidence class | Result |
|---|---|---|---|
| 3.1 | An edit is on disk after the autosave delay while the app stays focused | NEEDS-DEVICE-VALIDATION | [ ] |
| 3.2 | Edit, swipe the app away to the background within a second, then kill it. Reopen: is the edit there? Record exactly | NEEDS-DEVICE-VALIDATION | [ ] |
| 3.3 | Edit, wait past the autosave delay, background, kill, reopen: the edit is there | NEEDS-DEVICE-VALIDATION | [ ] |
| 3.4 | Where the project folder lives (which writable path under `unconfined`) and that it is reachable from the file manager | NEEDS-DEVICE-VALIDATION | [ ] |
| 3.5 | An approved glyph is still locked after reopening (law 1) | NEEDS-DEVICE-VALIDATION | [ ] |

## 4. Preview click (`typewright-preview.asoc`), companion only

This click is not the port. These rows decide whether it is worth shipping at all.

| # | Check | Evidence class | Result |
|---|---|---|---|
| 4.1 | It installs and shows "Typewright Preview" | NEEDS-DEVICE-VALIDATION | [ ] |
| 4.2 | `webapp-container` loads the bundled `file://` page (not a blank/error page) | NEEDS-DEVICE-VALIDATION | [ ] |
| 4.3 | WasmGC runs (the app UI draws). Record the engine user agent | NEEDS-DEVICE-VALIDATION | [ ] |
| 4.4 | No network request is made at launch (law 3); check with a network monitor if available | NEEDS-DEVICE-VALIDATION | [ ] |
| 4.5 | Edits survive backgrounding and a relaunch (storage API), or the app says plainly they do not | NEEDS-DEVICE-VALIDATION | [ ] |
| 4.6 | `navigator.language` gives a sane locale (ARCHITECTURE_REVIEW item 13 saw `en-US@posix` blank pages) | NEEDS-DEVICE-VALIDATION | [ ] |

## Run record

| Date | Device and image | Click build (workflow run, commit) | Rows passed | Notes |
|---|---|---|---|---|
| | | | | |
