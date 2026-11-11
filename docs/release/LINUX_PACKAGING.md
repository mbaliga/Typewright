# Linux packaging

## The one format: AppImage

Recommendation: ship **AppImage**, one file per release, and nothing else for V1.

| | AppImage | Flatpak | .deb / .rpm |
|---|---|---|---|
| Needs the app to reach the person's own Python and `fontmake` | yes, runs as the person's user, sees `PATH` and `TYPEWRIGHT_PYTHON` | **no**: the sandbox hides the host's Python. The fix is `flatpak-spawn --host` or bundling Python, either of which defeats the sandbox or breaks "Python is detected, not bundled" | yes |
| Needs root or a store account | no | no for a user install; a Flathub listing needs review and a manifest that builds from source | root to install |
| Opens and saves project folders anywhere (UFO directories, the Ship repository folder) | yes | only through portals and granted paths; every folder picker and `zenity`/`kdialog` call changes | yes |
| One file to host on GitHub Releases | yes | a bundle, or a Flathub repo | one per distribution family |
| Maintenance | one script | a manifest, a runtime to track, review | two packagers, repo metadata |

The deciding fact is that Typewright's compile step (`fontmake`) and layer-one check (Fontbakery) run from a
Python on the person's machine (`THIRD_PARTY.md`, "Python tools the desktop app runs (not bundled, not linked)").
A Flatpak sandbox would cut the app off from exactly that. AppImage keeps the app an ordinary process.

A Flatpak on Flathub, if wanted later, is a separate decision with its own cost; this file does not plan it.

## How it is built

1. `./gradlew :app-desktop:packageAppImage` builds the **jpackage app image** (a directory) with a JRE bundled by
   jpackage's `jlink` step, trimmed to the modules `suggestRuntimeModules` names. In the Compose Gradle plugin,
   `TargetFormat.AppImage` is this directory; it is not the AppImage file. The licence files (`LICENSE`,
   `LICENSES/`, `THIRD_PARTY.md`, `TRADEMARKS.md`, `PRIVACY.md`) are copied into the image's `lib/app/resources`.
2. `APPIMAGETOOL=/path/to/appimagetool tools/release/build_appimage.sh` wraps the directory into
   `app-desktop/build/release/Typewright-<version>-<arch>.AppImage`, with a `.desktop` file, the icon
   (`docs/branding/app-icon-light.png`) and an `AppRun` that starts the bundled launcher.
3. The version comes from `version.properties` (`1.0.0-rc1` is packaged as `1.0.0`, because jpackage accepts no suffix).

**What was and was not run where this was written (the build container).** Step 1 runs to the end and produces the
app image with the JRE and the licence files in it. Step 2 was **not** run: the container has no `appimagetool`, and
the tool is a download from GitHub that is checked by hand. The resulting file has not been started on a Linux
desktop by anyone; that is on the release checklist (`RELEASE_CHECKLIST.md`) and is owner-verified.

Architecture: x86_64 only. The container builds for its own architecture, and no aarch64 build is planned for V1.

## Python and fontmake: detected, not bundled

The app looks for a Python 3 that has `fontmake` and `ufo2ft` (`compile/PythonToolchain.kt`: `TYPEWRIGHT_PYTHON`
first, then `python3`/`python` on `PATH`). When there is none, the app does not crash and does not hide it: the
layer-one part of Check, and Compile in Ship, say in a sentence why they cannot run and how to fix it
(`Install Python 3, then run: pip install fontmake`; for a Python that lacks the packages,
`pip install fontmake`, or `skia-pathops` for overlap removal). There is **no separate first-run dialog**: the
message appears where the person needs the tool. A first-run panel on Home is a possible addition and is filed in
`docs/OPEN_QUESTIONS.md`.

Recommended instructions for the download page (the release notes copy them):

```
python3 -m venv ~/.typewright-python
~/.typewright-python/bin/pip install fontmake fontbakery
export TYPEWRIGHT_PYTHON=~/.typewright-python/bin/python    # add to your shell profile
```

(`tools/requirements-golden-path.txt` is the set the project's CI resolves with and tests against.)

## Not covered

Windows, macOS and Flatpak/Snap are not in V1. No signing: AppImages are not signed here, so the release notes give
a SHA-256 checksum for the file. Whether the AppImage opens on a given distribution (FUSE 2 is needed to mount it, or
`--appimage-extract-and-run`) is verified by running it, not by this document.
