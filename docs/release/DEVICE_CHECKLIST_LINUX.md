# Device checklist: Linux

Status: **nothing here has been run.** This container has no Linux desktop, no Steam Deck and no Termux:X11 (CLAUDE.md law 4). Every row below is an unfilled box. The owner fills it on a real machine, writes what happened, and only then does a row stop being NDV. The evidence labels are PORTING_PLAN section 0's.

Build under test: the app-image directory from `./gradlew :app-desktop:createDistributable`, installed with `packaging/linux/install.sh`. The `.deb`/`.rpm`/AppImage formats are declared but have never been run, and are not covered here.

How to fill a row: replace `[ ]` with `[x]` only for a pass you watched. For a fail write `[F]` and a line of what you saw. Record the date, the machine and the build commit in the table at the end.

## 1. Install and uninstall (a real desktop)

| # | Check | Evidence class | Result |
|---|---|---|---|
| 1.1 | `packaging/linux/install.sh` into the default prefix (`~/.local`) finishes with the "Installed" message | NEEDS-DEVICE-VALIDATION | [ ] |
| 1.2 | `~/.local/bin/typewright` starts the app and a window appears | NEEDS-DEVICE-VALIDATION | [ ] |
| 1.3 | The launcher entry appears in the desktop's application menu (it names no icon; a missing icon is expected, OQ-25) | NEEDS-DEVICE-VALIDATION | [ ] |
| 1.4 | `install.sh --uninstall` removes `lib/typewright`, `bin/typewright` and the `.desktop` entry, and leaves your projects alone | NEEDS-DEVICE-VALIDATION | [ ] |
| 1.5 | Installing twice in a row works (the second run replaces the first) | NEEDS-DEVICE-VALIDATION | [ ] |

## 2. Save, kill -9, reopen

Run `tools/device/linux-save-kill-restore.sh PROJECT_DIR`. It snapshots the project's files, waits while you edit, sends SIGKILL, snapshots again, restarts the app and snapshots again. It judges nothing.

| # | Check | Evidence class | Result |
|---|---|---|---|
| 2.1 | An edit made in the app is on disk after the autosave delay, before the kill | NEEDS-DEVICE-VALIDATION | [ ] |
| 2.2 | After `kill -9`, the project folder still opens | NEEDS-DEVICE-VALIDATION | [ ] |
| 2.3 | The edit is present after the reopen | NEEDS-DEVICE-VALIDATION | [ ] |
| 2.4 | `build/.session-lock` exists while open; what the app does with the stale one after SIGKILL is recorded (not assumed) | NEEDS-DEVICE-VALIDATION | [ ] |
| 2.5 | An approved glyph is still locked after the reopen (law 1) | NEEDS-DEVICE-VALIDATION | [ ] |
| 2.6 | The folder opens in FontForge (law 7) | NEEDS-OWNER-VALIDATION | [ ] |

Note: the running app may still be using its in-memory workspace stopgap for parts of the project, so a row 2 failure may be a known gap and not a Linux problem. Say which in the result.

## 3. Steam Deck

| # | Check | Evidence class | Result |
|---|---|---|---|
| 3.1 | Desktop Mode: the app installs through `install.sh` and starts | NEEDS-DEVICE-VALIDATION | [ ] |
| 3.2 | Desktop Mode: the folder picker opens (the order is zenity, kdialog, then Swing) | NEEDS-DEVICE-VALIDATION | [ ] |
| 3.3 | Desktop Mode: touchscreen and trackpad both drive the window | NEEDS-DEVICE-VALIDATION | [ ] |
| 3.4 | Gaming Mode under gamescope: the app can be added as a non-Steam game and starts | NEEDS-DEVICE-VALIDATION | [ ] |
| 3.5 | Gaming Mode: the window fits the 1280x800 screen and the controls are reachable | NEEDS-DEVICE-VALIDATION | [ ] |

## 4. Redmagic-Edge under Termux:X11

Software GL, no Vulkan. Whether an arm64 JVM app starts here at all is unknown, and this repo builds only x86_64 Linux binaries today, so a failure at 4.1 is a likely and informative result.

| # | Check | Evidence class | Result |
|---|---|---|---|
| 4.1 | A JVM and the app start under Termux:X11 | NEEDS-DEVICE-VALIDATION | [ ] |
| 4.2 | The window draws (Skiko rendering on software GL) | NEEDS-DEVICE-VALIDATION | [ ] |
| 4.3 | Touch input reaches the canvas | NEEDS-DEVICE-VALIDATION | [ ] |

## 5. HiDPI

| # | Check | Evidence class | Result |
|---|---|---|---|
| 5.1 | KDE Plasma at 200% scale: text and canvas are sharp and correctly sized | NEEDS-DEVICE-VALIDATION | [ ] |
| 5.2 | GNOME at 200% scale: same | NEEDS-DEVICE-VALIDATION | [ ] |
| 5.3 | A fractional scale (125% or 150%) on either: same | NEEDS-DEVICE-VALIDATION | [ ] |
| 5.4 | Wayland and X11 sessions both start (note which was used) | NEEDS-DEVICE-VALIDATION | [ ] |

## Run log

| Date | Machine and OS | Display server | Build commit | Rows filled | Notes |
|---|---|---|---|---|---|
| | | | | | |
