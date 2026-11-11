#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Wraps the Linux app image Gradle builds (JRE bundled by jpackage) into one AppImage file.
#
#   ./gradlew :app-desktop:packageAppImage
#   APPIMAGETOOL=/path/to/appimagetool-x86_64.AppImage tools/release/build_appimage.sh [output-dir]
#
# appimagetool is not downloaded here: fetch it yourself from https://github.com/AppImage/appimagetool/releases,
# check its checksum, and point APPIMAGETOOL at it. Python, fontmake and Fontbakery are not inside the AppImage;
# the app finds them on the machine it runs on (docs/release/LINUX_PACKAGING.md).
#
# This has not been run to completion in the build container (no appimagetool, no desktop session); the
# owner or CI runs it, and starts the result once on a real Linux desktop before release
# (docs/release/RELEASE_CHECKLIST.md).
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
image="$root/app-desktop/build/compose/binaries/main/app/typewright"
out="${1:-$root/app-desktop/build/release}"
version="$(sed -n 's/^VERSION_NAME=//p' "$root/version.properties")"
tool="${APPIMAGETOOL:-}"

[ -d "$image/lib/runtime" ] || { echo "No app image at $image. Run ./gradlew :app-desktop:packageAppImage first." >&2; exit 1; }
[ -n "$tool" ] && [ -x "$tool" ] || { echo "Set APPIMAGETOOL to an executable appimagetool (see this script's header)." >&2; exit 1; }

appdir="$(mktemp -d)/Typewright.AppDir"
trap 'rm -rf "$(dirname "$appdir")"' EXIT
mkdir -p "$appdir/usr" "$out"
cp -a "$image/." "$appdir/usr/"
cp "$root/docs/branding/app-icon-light.png" "$appdir/typewright.png"
cp "$root/docs/branding/app-icon-light.png" "$appdir/.DirIcon"

cat > "$appdir/typewright.desktop" <<'DESKTOP'
[Desktop Entry]
Type=Application
Name=Typewright
Comment=Hand-made letterforms to fonts of Google Fonts quality
Exec=typewright
Icon=typewright
Categories=Graphics;
Terminal=false
DESKTOP

cat > "$appdir/AppRun" <<'APPRUN'
#!/bin/sh
here="$(dirname "$(readlink -f "$0")")"
exec "$here/usr/bin/typewright" "$@"
APPRUN
chmod +x "$appdir/AppRun"

# --appimage-extract-and-run lets appimagetool itself run where FUSE is not available (a CI container).
ARCH="$(uname -m)" "$tool" --appimage-extract-and-run "$appdir" "$out/Typewright-$version-$(uname -m).AppImage"
echo "Wrote $out/Typewright-$version-$(uname -m).AppImage"
